package brobata.physiboard.core.text

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictNormalization
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.RuleSet
import brobata.physiboard.core.dict.SubstitutionMatcher
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency

/**
 * The measured constants of the context-aware path, bundled so the evaluation harness (SS12) can
 * sweep them; the keyboard always runs [DEFAULT]. [mixupWords] are the confusion-set words the
 * mix-up fix may act on (the shipped [WordMixups.sets] unless a measurement says otherwise).
 */
data class ContextTuning(
    val correction: ContextCorrection.Tuning = ContextCorrection.DEFAULT_TUNING,
    val mixups: WordMixups.Tuning = WordMixups.DEFAULT_TUNING,
    val mixupWords: Set<String> = WordMixups.sets.flatten().toSet(),
) {
    companion object {
        val DEFAULT: ContextTuning = ContextTuning()
    }
}

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
    /**
     * Set only when the mix-up fix rewrote the word before the one just finished (SS10's
     * exception): that word as the field held it, and what replaced it. [before]/[after] still
     * describe the word just finished. `:ime`'s next-word learning has already learned the word
     * before as it was typed, one boundary earlier, and reads these to learn the fixed one instead.
     */
    val previousWordBefore: String? = null,
    val previousWordAfter: String? = null,
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

    /** How many characters before the tracked word the caller should supply: two long words and their spaces. */
    const val CONTEXT_WINDOW: Int = 64

    /**
     * [textBeforeCursor] is the text before the cursor ending with the tracked word, [CONTEXT_WINDOW]
     * characters of context ahead of it: the hard-boundary scan window (SS7.2 step 3) and the
     * previous words the context prior and the mix-up check read (SS9, SS10).
     * [trackedWord] is the current word as tracked, already re-synced from the field.
     * [dictionaries] is primary dictionary first. [memory] carries undo/rejection state forward.
     */
    fun evaluate(
        trackedWord: String,
        textBeforeCursor: String,
        boundaryChar: Char,
        ruleSets: List<RuleSet>,
        dictionaries: List<DictionaryIndex>,
        userWords: UserWordStore,
        settings: AutocorrectSettings,
        rankingOptions: RankingOptions,
        lengthChangeAllowance: Int,
        memory: AutocorrectMemory,
        contextModel: ContextModel? = null,
        contextTuning: ContextTuning = ContextTuning.DEFAULT,
    ): BoundaryEvaluation {
        val evaluation = evaluateOnce(
            trackedWord, textBeforeCursor, boundaryChar, ruleSets, dictionaries, userWords, settings, rankingOptions, lengthChangeAllowance,
            memory, contextModel, contextTuning,
        )
        // Whatever the outcome, this boundary was evaluated on a read that agreed: the word just
        // finished becomes the word before (AutocorrectMemory.afterEvaluatedBoundary).
        return evaluation.copy(memory = evaluation.memory.afterEvaluatedBoundary(trackedWord))
    }

    private fun evaluateOnce(
        trackedWord: String,
        textBeforeCursor: String,
        boundaryChar: Char,
        ruleSets: List<RuleSet>,
        dictionaries: List<DictionaryIndex>,
        userWords: UserWordStore,
        settings: AutocorrectSettings,
        rankingOptions: RankingOptions,
        lengthChangeAllowance: Int,
        memory: AutocorrectMemory,
        contextModel: ContextModel?,
        contextTuning: ContextTuning,
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
        val textBeforeWord = if (WordChars.straightenAll(textBeforeCursor).endsWith(trackedWord)) textBeforeCursor.dropLast(trackedWord.length) else textBeforeCursor
        if (hasHardBoundaryBeforeCursor(textBeforeWord)) {
            return BoundaryEvaluation(memory.afterBoundaryWithoutReplacement(), BoundaryOutcome.CommitPlain, attempt("hard_boundary_before_cursor"))
        }

        fun isKnown(word: String): Boolean = dictionaries.any { it.contains(word) } || userWords.isKnown(word)

        if (settings.autoCorrectEnabled) {
            val textForMatch = textBeforeCursor + boundaryChar
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

        if (contextModel != null) {
            return evaluateWithContext(
                trackedWord, textBeforeCursor, textBeforeWord, dictionaries, userWords, settings, lengthChangeAllowance, memory, contextModel,
                contextTuning, ::skipped, ::applied,
            )
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
     * Steps 8 and 9 of SS7.2 when the word-pair table is loaded (SS16 W2, W5), then the mix-up
     * check of the previous word (SS10's exception). The word just finished is settled first:
     * case repair, then for any word no dictionary or word store spells this way
     * [ContextCorrection] (which also weighs an accent or apostrophe repair, `dont` -> `don't`). A
     * word spelled exactly as some dictionary or word store has it is never replaced (SS10).
     *
     * Then, with `fix_word_mixups` on, the word before it is judged from both sides with the
     * settled word on its right ([WordMixups]). The previous word is read from [textBeforeCursor]
     * itself, so a fix can only ever rewrite text the field just reported, and only when the two
     * words are separated by exactly one space; an editor that reports nothing never gets this
     * far (DriftCheck). Both edits are one replacement of the span from the previous word to the
     * cursor, so one Backspace puts back exactly what was typed, and the rejection it records
     * covers each word of it (SS7.5). The previous word must have been typed here in sequence and
     * not be pinned by the user ([AutocorrectMemory.previousWordTyped], [AutocorrectMemory.isPinned]).
     */
    private fun evaluateWithContext(
        trackedWord: String,
        textBeforeCursor: String,
        textBeforeWord: String,
        dictionaries: List<DictionaryIndex>,
        userWords: UserWordStore,
        settings: AutocorrectSettings,
        lengthChangeAllowance: Int,
        memory: AutocorrectMemory,
        model: ContextModel,
        tuning: ContextTuning,
        skipped: (String, Int?) -> BoundaryDebugInfo,
        applied: (String, String, Int?) -> BoundaryDebugInfo,
    ): BoundaryEvaluation {
        // A window as long as the caller was asked for was cut from a longer text: its first word may be cut too.
        val truncated = textBeforeCursor.length >= trackedWord.length + CONTEXT_WINDOW
        val previous = SentenceContext.before(textBeforeWord, textBeforeWord.length, truncated)

        var settled = trackedWord
        var source: String? = null
        var reason: String
        when {
            memory.isRejected(trackedWord) -> reason = "rejected_by_user"
            else -> {
                val repaired = primaryCaseRepair(trackedWord, dictionaries.firstOrNull(), dictionaries, userWords)
                when {
                    repaired != null -> { settled = repaired; source = "PRIMARY_CASE"; reason = "" }
                    ContextCorrection.isExactlyKnown(trackedWord, dictionaries, userWords) -> reason = "known_word"
                    else -> when (val decision = ContextCorrection.decide(trackedWord, previous, model, dictionaries, userWords, settings, lengthChangeAllowance, tuning.correction)) {
                        is ContextCorrection.Result.Commit -> { settled = decision.replacement; source = "CONTEXT"; reason = "" }
                        is ContextCorrection.Result.Leave -> reason = decision.reason
                    }
                }
            }
        }

        var mixupStart = -1
        var mixupReplacement = ""
        // The window must end with the word this engine was asked about, or "the previous word"
        // could be that word itself (DriftCheck guarantees it for the keyboard; this keeps the
        // public function safe on its own). The previous word must stand on its own: the start of
        // the text, a space, or an opening quote or bracket before it, never a symbol (`@your`,
        // `site.com/its`), and exactly one space after it. It must also be a word typed here, in
        // sequence, and not one the user chose on purpose: a word the field merely shows (the
        // cursor was moved next to it, the input restarted) or one put back by an undo or taken
        // from a suggestion is the user's text, not a slip (AutocorrectMemory).
        val mixupAllowed = settings.fixWordMixups && previous is Preceding.Word && previous.gap == " " &&
            WordChars.straightenAll(textBeforeCursor).endsWith(trackedWord) &&
            standsAlone(textBeforeWord, previous.start) && !memory.isRejected(previous.text) &&
            memory.previousWordTyped && !memory.isPinned(previous.text)
        if (mixupAllowed) {
            val before = SentenceContext.before(textBeforeWord, previous.start, truncated)
            val beforeId = when (before) {
                Preceding.SentenceStart -> ContextModel.SENTENCE_START
                Preceding.Unknown -> ContextModel.NO_CONTEXT
                is Preceding.Word -> model.idOf(before.key)
            }
            val twin = WordMixups.judge(previous.key, beforeId, WordChars.straightenAll(settled).lowercase(), model, tuning.mixups, tuning.mixupWords)
            if (twin != null) {
                mixupStart = previous.start
                mixupReplacement = twinAsTyped(previous.text, twin, dictionaries, textBeforeCursor.substring(previous.start))
            }
        }

        if (source == null && mixupStart < 0) {
            return BoundaryEvaluation(memory.afterBoundaryWithoutReplacement(), BoundaryOutcome.CommitPlain, skipped(reason, null))
        }
        val original: String
        val replacement: String
        if (mixupStart >= 0) {
            // The field's own text from the previous word to the cursor, apostrophes exactly as typed.
            original = textBeforeCursor.substring(mixupStart)
            // A word this boundary did not correct goes back exactly as the field holds it (a curly apostrophe stays curly).
            val current = if (source == null) textBeforeCursor.takeLast(trackedWord.length) else settled
            replacement = mixupReplacement + " " + current
        } else {
            original = trackedWord
            replacement = settled
        }
        val ops = listOf(EditorOp.DeleteSurrounding(original.length, 0), EditorOp.CommitText(replacement), EditorOp.Haptic)
        val label = listOfNotNull(source, if (mixupStart >= 0) "WORD_MIXUP" else null).joinToString("+")
        // before/after describe the word just finished, never the span: `:ime` learns "the
        // completed word" from them (SS4, SS7.3), and "its tail" is not a word. The mix-up itself
        // is named in the reason.
        val mixupOriginal = if (mixupStart >= 0) textBeforeCursor.substring(mixupStart, mixupStart + (original.length - trackedWord.length - 1)) else null
        val mixupNote = if (mixupOriginal != null) "previous $mixupOriginal -> $mixupReplacement" else ""
        val debug = applied(label, settled, null).copy(
            reason = mixupNote,
            previousWordBefore = mixupOriginal,
            previousWordAfter = mixupReplacement.takeIf { mixupOriginal != null },
        )
        return BoundaryEvaluation(memory.afterReplacement(original, replacement), BoundaryOutcome.Replaced(ops, original, replacement, addWordCandidate = null), debug)
    }

    private const val OPENERS = "\"([{\u201C\u2018\u00AB"

    /** Whether the word starting at [start] of [text] stands on its own: text start, whitespace or an opening quote or bracket before it. */
    internal fun standsAlone(text: String, start: Int): Boolean {
        val before = text.getOrNull(start - 1) ?: return true
        return before.isWhitespace() || before in OPENERS
    }

    /**
     * The twin spelled as the dictionary has it (`I'll`, never `i'll`), then cased like the word it
     * replaces, with the apostrophe style the user's own text uses.
     */
    internal fun twinAsTyped(typed: String, twin: String, dictionaries: List<DictionaryIndex>, span: String): String {
        val entries = mutableListOf<WordFrequency>()
        dictionaries.firstOrNull()?.entriesForExactKey(twin, limit = 8, into = entries)
        val spelled = entries.filter { WordChars.straightenAll(it.word).equals(twin, ignoreCase = true) }.maxByOrNull { it.frequency }?.word ?: twin
        // The pronoun is a capital whatever the list says: `i'll` is always `I'll`.
        val pronoun = if (spelled.startsWith("i'")) "I" + spelled.substring(1) else spelled
        val cased = CasingRules.forTypedWord(typed, pronoun)
        val curly = span.firstOrNull { WordChars.isApostrophe(it) && it != '\'' }
        return if (curly != null) cased.replace('\'', curly) else cased
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
    internal fun primaryCaseRepair(word: String, primaryDict: DictionaryIndex?, dictionaries: List<DictionaryIndex>, userWords: UserWordStore): String? {
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
