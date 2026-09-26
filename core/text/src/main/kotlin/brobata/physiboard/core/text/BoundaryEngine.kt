package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictNormalization
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.RuleSet
import brobata.physiboard.core.dict.SubstitutionMatcher
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency

/** What happened at a word boundary (Space, Enter, or boundary punctuation). spec: autocorrect-suggestions.md SS7.2. */
sealed class BoundaryOutcome {
    /**
     * The word was replaced (by a text-replacement rule, a case repair, or an automatic
     * correction). [ops] deletes the typed word and commits [replacement]; the boundary character
     * itself is not included and is the caller's own commit, per SS7.3. [addWordCandidate] is set
     * when the result is not a known word (spec SS6.2).
     */
    data class Replaced(val ops: List<EditorOp>, val original: String, val replacement: String, val addWordCandidate: String?) : BoundaryOutcome()

    /** Nothing replaced the word (it was blank, hit a hard boundary, or no rule/correction applied); commit the boundary as typed. */
    object CommitPlain : BoundaryOutcome()
}

/**
 * What one boundary attempt should write to the debug capture. spec: autocorrect-suggestions.md
 * SS7.2 ("Each attempt is recorded in the debug capture (section 12) with its outcome") and SS9's
 * outcome/reason vocabulary; app-shell.md SS11 ("Autocorrection rows carry type (commit or
 * attempt), trigger, a source string, outcome (applied, skipped, not_applicable), before, after,
 * reason, distance and kind"). [before]/[after] are filled in by the caller from
 * [BoundaryOutcome.Replaced] (empty for a non-replacement, matching the "pure noise" row SS11
 * describes: an `auto_replace_disabled` attempt with blank before/after). `kind` is left for a
 * future caller; this module has nothing further to say about it.
 */
data class BoundaryDebugInfo(
    val type: String,
    val trigger: String,
    val outcome: String,
    val reason: String,
    val before: String = "",
    val after: String = "",
    val source: String? = null,
    val distance: Int? = null,
    /**
     * The literal boundary character this evaluation ran for (`' '`, `'\n'`, or a boundary
     * punctuation mark). spec: SS4, "next-word context" needs to tell a soft boundary (Space,
     * comma, semicolon, colon) from a hard one (a period, Enter, or anything else); the `trigger`
     * string alone collapses every punctuation mark to `other`.
     */
    val boundaryChar: Char = ' ',
) {
    companion object {
        /** spec app-shell.md SS11: `space`, `enter`, `suggestion_tap`, `other`; a boundary evaluation is never `suggestion_tap`. */
        fun triggerFor(boundaryChar: Char): String = when (boundaryChar) {
            ' ' -> "space"
            '\n' -> "enter"
            else -> "other"
        }

        /** spec: SS4, "After a 'soft' boundary (Space, comma, semicolon or colon)". Every other boundary (a period, Enter, `!`, `?`, ...) is hard. */
        fun isSoftBoundary(boundaryChar: Char): Boolean = boundaryChar == ' ' || boundaryChar == ',' || boundaryChar == ';' || boundaryChar == ':'
    }
}

/** [BoundaryEngine.evaluate]'s full result: the outcome the caller acts on, plus what it should record. */
data class BoundaryEvaluation(val memory: AutocorrectMemory, val outcome: BoundaryOutcome, val debug: BoundaryDebugInfo)

/**
 * The single boundary engine 3.0 uses in place of 2.x's two parallel engines (spec: autocorrect-
 * suggestions.md SS18, "collapse the three boundary paths ... into one"). Implements
 * autocorrect-suggestions.md SS7.2's order of operations from "text replacement" onward; the
 * caller is responsible for step 1 (syncing the tracker, [CurrentWordTracker.syncedFrom]) before
 * calling this, and for committing the boundary character itself afterward (SS7.3), which is
 * deliberately left to the caller since it interacts with comma space and French spacing
 * ([CommaSpace], [FrenchSpacing]) that this module already exposes separately.
 */
object BoundaryEngine {

    /**
     * [textBeforeCursor32] is the 32-character hard-boundary scan window (SS7.2 step 3).
     * [trackedWord] is the current word as tracked, already re-synced from the field.
     * [dictionaries] is primary dictionary first. [memory] carries undo/rejection state forward.
     */
    fun evaluate(
        trackedWord: String,
        textBeforeCursor32: String,
        boundaryChar: Char,
        ruleSets: List<RuleSet>,
        dictionaries: List<DictionaryIndex>,
        userWords: UserWordStore,
        settings: AutocorrectSettings,
        rankingOptions: RankingOptions,
        lengthChangeAllowance: Int,
        memory: AutocorrectMemory,
    ): BoundaryEvaluation {
        val trigger = BoundaryDebugInfo.triggerFor(boundaryChar)
        // spec app-shell.md SS11/T28: the one row this store treats as pure noise is an
        // `auto_replace_disabled` attempt with blank before and after, so that reason alone omits
        // them; every other attempt carries the typed word, which is useful even when nothing ran.
        fun attempt(reason: String, before: String = trackedWord) =
            BoundaryDebugInfo(type = "attempt", trigger = trigger, outcome = "not_applicable", reason = reason, before = before, boundaryChar = boundaryChar)
        fun skipped(reason: String, distance: Int? = null) =
            BoundaryDebugInfo(type = "attempt", trigger = trigger, outcome = "skipped", reason = reason, before = trackedWord, distance = distance, boundaryChar = boundaryChar)
        fun applied(source: String, after: String, distance: Int? = null) = BoundaryDebugInfo(
            type = "commit", trigger = trigger, outcome = "applied", reason = "", before = trackedWord, after = after,
            source = source, distance = distance, boundaryChar = boundaryChar,
        )

        // spec: SS7.5, "the undo memory is cleared ... when a boundary passes without a
        // replacement": a blank word and a hard boundary are both boundaries that pass without one.
        if (trackedWord.isBlank()) {
            return BoundaryEvaluation(memory.afterBoundaryWithoutReplacement(), BoundaryOutcome.CommitPlain, attempt("empty_word", before = ""))
        }
        // [trackedWord] folds apostrophes to the straight one (CurrentWordTracker); the field holds
        // the key as pressed, so the comparison folds the window the same way (WordChars.straightenAll),
        // exactly as DriftCheck does, or a curly apostrophe would keep the word inside the scan.
        val textBeforeWord = if (WordChars.straightenAll(textBeforeCursor32).endsWith(trackedWord)) textBeforeCursor32.dropLast(trackedWord.length) else textBeforeCursor32
        if (hasHardBoundaryBeforeCursor(textBeforeWord)) {
            return BoundaryEvaluation(memory.afterBoundaryWithoutReplacement(), BoundaryOutcome.CommitPlain, attempt("hard_boundary_before_cursor"))
        }

        fun isKnown(word: String): Boolean = dictionaries.any { it.contains(word) } || userWords.isKnown(word)

        if (settings.autoCorrectEnabled) {
            val textForMatch = textBeforeCursor32 + boundaryChar
            val match = SubstitutionMatcher.match(textForMatch, ruleSets, ::isKnown)
            if (match != null) {
                val ops = listOf(
                    EditorOp.DeleteSurrounding(match.matchedText.length, 0),
                    EditorOp.CommitText(match.replacement),
                    EditorOp.Haptic,
                )
                val candidate = match.replacement.takeIf { !isKnown(it) }
                val newMemory = memory.afterReplacement(match.matchedText, match.replacement)
                val debug = BoundaryDebugInfo(
                    type = "commit", trigger = trigger, outcome = "applied", reason = "",
                    before = match.matchedText, after = match.replacement, source = "TEXT_REPLACEMENT", boundaryChar = boundaryChar,
                )
                return BoundaryEvaluation(newMemory, BoundaryOutcome.Replaced(ops, match.matchedText, match.replacement, candidate), debug)
            }
        }

        if (!settings.autoReplaceOnSpaceEnter) {
            return BoundaryEvaluation(memory.afterBoundaryWithoutReplacement(), BoundaryOutcome.CommitPlain, attempt("auto_replace_disabled", before = ""))
        }

        val primaryDict = dictionaries.firstOrNull()
        if (!memory.isRejected(trackedWord)) {
            val repaired = primaryCaseRepair(trackedWord, primaryDict, dictionaries, userWords)
            if (repaired != null) {
                val ops = listOf(EditorOp.DeleteSurrounding(trackedWord.length, 0), EditorOp.CommitText(repaired), EditorOp.Haptic)
                val newMemory = memory.afterReplacement(trackedWord, repaired)
                return BoundaryEvaluation(newMemory, BoundaryOutcome.Replaced(ops, trackedWord, repaired, addWordCandidate = null), applied("PRIMARY_CASE", repaired))
            }
        }

        val suggestions = SuggestionRanking.suggest(trackedWord, dictionaries, userWords, rankingOptions, limit = 2)
        val top = suggestions.getOrNull(0)
        if (top != null) {
            val facts = buildFacts(trackedWord, top, suggestions.getOrNull(1), dictionaries, userWords, memory)
            val decision = AutocorrectDecision.evaluate(facts, settings.maxAutoReplaceDistance, lengthChangeAllowance)
            when (decision) {
                is AutocorrectOutcome.Commit -> {
                    val ops = listOf(EditorOp.DeleteSurrounding(trackedWord.length, 0), EditorOp.CommitText(decision.recased), EditorOp.Haptic)
                    val candidate = decision.recased.takeIf { !isKnown(it) }
                    val newMemory = memory.afterReplacement(trackedWord, decision.recased)
                    return BoundaryEvaluation(newMemory, BoundaryOutcome.Replaced(ops, trackedWord, decision.recased, candidate), applied(top.source.name, decision.recased, top.distance))
                }
                AutocorrectOutcome.SameReplacement -> {
                    return BoundaryEvaluation(memory.afterBoundaryWithoutReplacement(), BoundaryOutcome.CommitPlain, attempt("same_replacement"))
                }
                is AutocorrectOutcome.Refuse -> {
                    return BoundaryEvaluation(memory.afterBoundaryWithoutReplacement(), BoundaryOutcome.CommitPlain, skipped(decision.reason.name.lowercase(), top.distance))
                }
            }
        }

        return BoundaryEvaluation(memory.afterBoundaryWithoutReplacement(), BoundaryOutcome.CommitPlain, skipped("no_suggestion"))
    }

    /**
     * spec: SS7.2 step 3: the 32 characters before the cursor are scanned backward past
     * whitespace and boundary punctuation; if the first other character is not a letter, digit
     * or apostrophe (an emoji, say), no correction runs. [textBeforeWord] is that 32-character
     * window with the tracked word itself already excluded, since the word's own trailing letter
     * would otherwise always satisfy the scan and make it a no-op.
     */
    private fun hasHardBoundaryBeforeCursor(textBeforeWord: String): Boolean {
        var i = textBeforeWord.length
        while (i > 0 && (textBeforeWord[i - 1].isWhitespace() || textBeforeWord[i - 1] in WordChars.BOUNDARY_PUNCTUATION)) i--
        if (i == 0) return false
        val ch = textBeforeWord[i - 1]
        return !(ch.isLetterOrDigit() || WordChars.isApostrophe(ch))
    }

    /**
     * spec: SS7.2 step 8, "Primary case repair". The "no entry spelled exactly as typed" test
     * covers every source of known words, not only the primary list: the personal store is
     * "merged into every dictionary that is loaded" and "a personal word is never autocorrected"
     * (SS6.1), and SS10 forbids overwriting a word that is in any active dictionary, so a
     * lowercase spelling the user added, or one a secondary dictionary carries, is left alone
     * even when the primary list knows only the capitalised form.
     */
    private fun primaryCaseRepair(word: String, primaryDict: DictionaryIndex?, dictionaries: List<DictionaryIndex>, userWords: UserWordStore): String? {
        if (primaryDict == null) return null
        if (word.isEmpty() || word.none { it.isLetter() } || word.any { it.isUpperCase() }) return null
        if (spelledExactlyAsTyped(word, dictionaries, userWords)) return null
        val entries = mutableListOf<WordFrequency>()
        primaryDict.entriesForExactKey(word, limit = 8, into = entries)
        return entries.firstOrNull { it.word.equals(word, ignoreCase = true) && it.word.any { c -> c.isUpperCase() } }?.word
    }

    /** Whether some active dictionary or the personal store holds [word] in exactly this spelling and case. spec: SS6.1, SS10. */
    private fun spelledExactlyAsTyped(word: String, dictionaries: List<DictionaryIndex>, userWords: UserWordStore): Boolean {
        if (userWords.personalWords().any { it.word == word } || userWords.defaultWords().any { it.word == word }) return true
        val entries = mutableListOf<WordFrequency>()
        return dictionaries.any { dict ->
            entries.clear()
            dict.entriesForExactKey(word, limit = 8, into = entries)
            entries.any { it.word == word }
        }
    }

    private fun buildFacts(
        word: String,
        top: RankedSuggestion,
        runnerUp: RankedSuggestion?,
        dictionaries: List<DictionaryIndex>,
        userWords: UserWordStore,
        memory: AutocorrectMemory,
    ): AutocorrectCandidateFacts {
        // A case variant is decided first: normalizedKey folds case as well as accents, so a
        // candidate differing only in case shares the word's key and would otherwise be classified
        // as orthographic, leaving the case-variant leg of KnownWordGate (SS9, "Known word" row)
        // unreachable. spec: SS9 facts table.
        val sameKey = DictNormalization.normalizedKey(word) == DictNormalization.normalizedKey(top.word)
        val isCaseVariant = word.equals(top.word, ignoreCase = true) && word != top.word
        val isOrthographic = sameKey && word != top.word && !isCaseVariant
        val isKnown = dictionaries.any { it.contains(word) } || userWords.isKnown(word)
        // spec: SS9 facts, read with SS6.1 (the personal store "is merged into every dictionary
        // that is loaded", so a personal spelling counts as an entry of the primary list too) and
        // SS10: "exact primary case" is the same "spelled exactly as typed" test step 8 applies.
        val exactPrimaryCaseExists = spelledExactlyAsTyped(word, dictionaries, userWords)
        val exactKnownExists = userWords.isKnown(word) || dictionaries.any { dict ->
            val entries = mutableListOf<WordFrequency>()
            dict.entriesForExactKey(word, limit = 8, into = entries)
            entries.any { it.word.equals(word, ignoreCase = true) }
        }
        return AutocorrectCandidateFacts(
            word = word,
            candidate = top.word,
            distance = top.distance,
            isCurrentWordCandidate = true,
            isOrthographicVariant = isOrthographic,
            isCaseVariant = isCaseVariant,
            isKnown = isKnown,
            exactKnownExists = exactKnownExists,
            exactPrimaryCaseExists = exactPrimaryCaseExists,
            isRejected = memory.isRejected(word),
            topScore = top.score,
            runnerUpScore = runnerUp?.score,
            runnerUpIsCurrentWordCandidate = true,
        )
    }
}
