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
    ): Pair<AutocorrectMemory, BoundaryOutcome> {
        if (trackedWord.isBlank()) {
            return memory to BoundaryOutcome.CommitPlain
        }
        val textBeforeWord = if (textBeforeCursor32.endsWith(trackedWord)) textBeforeCursor32.dropLast(trackedWord.length) else textBeforeCursor32
        if (hasHardBoundaryBeforeCursor(textBeforeWord)) {
            return memory to BoundaryOutcome.CommitPlain
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
                return newMemory to BoundaryOutcome.Replaced(ops, match.matchedText, match.replacement, candidate)
            }
        }

        if (!settings.autoReplaceOnSpaceEnter) {
            return memory.afterBoundaryWithoutReplacement() to BoundaryOutcome.CommitPlain
        }

        val primaryDict = dictionaries.firstOrNull()
        if (!memory.isRejected(trackedWord)) {
            val repaired = primaryCaseRepair(trackedWord, primaryDict)
            if (repaired != null) {
                val ops = listOf(EditorOp.DeleteSurrounding(trackedWord.length, 0), EditorOp.CommitText(repaired), EditorOp.Haptic)
                val newMemory = memory.afterReplacement(trackedWord, repaired)
                return newMemory to BoundaryOutcome.Replaced(ops, trackedWord, repaired, addWordCandidate = null)
            }
        }

        val suggestions = SuggestionRanking.suggest(trackedWord, dictionaries, userWords, rankingOptions, limit = 2)
        val top = suggestions.getOrNull(0)
        if (top != null) {
            val facts = buildFacts(trackedWord, top, suggestions.getOrNull(1), dictionaries, userWords, memory)
            val decision = AutocorrectDecision.evaluate(facts, settings.maxAutoReplaceDistance, lengthChangeAllowance)
            if (decision is AutocorrectOutcome.Commit) {
                val ops = listOf(EditorOp.DeleteSurrounding(trackedWord.length, 0), EditorOp.CommitText(decision.recased), EditorOp.Haptic)
                val candidate = decision.recased.takeIf { !isKnown(it) }
                val newMemory = memory.afterReplacement(trackedWord, decision.recased)
                return newMemory to BoundaryOutcome.Replaced(ops, trackedWord, decision.recased, candidate)
            }
        }

        return memory.afterBoundaryWithoutReplacement() to BoundaryOutcome.CommitPlain
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

    /** spec: SS7.2 step 8, "Primary case repair". */
    private fun primaryCaseRepair(word: String, primaryDict: DictionaryIndex?): String? {
        if (primaryDict == null) return null
        if (word.isEmpty() || word.none { it.isLetter() } || word.any { it.isUpperCase() }) return null
        val entries = mutableListOf<WordFrequency>()
        primaryDict.entriesForExactKey(word, limit = 8, into = entries)
        if (entries.any { it.word == word }) return null
        return entries.firstOrNull { it.word.equals(word, ignoreCase = true) && it.word.any { c -> c.isUpperCase() } }?.word
    }

    private fun buildFacts(
        word: String,
        top: RankedSuggestion,
        runnerUp: RankedSuggestion?,
        dictionaries: List<DictionaryIndex>,
        userWords: UserWordStore,
        memory: AutocorrectMemory,
    ): AutocorrectCandidateFacts {
        val sameKey = DictNormalization.normalizedKey(word) == DictNormalization.normalizedKey(top.word)
        val isOrthographic = sameKey && word != top.word
        val isCaseVariant = word.equals(top.word, ignoreCase = true) && word != top.word && !isOrthographic
        val isKnown = dictionaries.any { it.contains(word) } || userWords.isKnown(word)
        val primaryDict = dictionaries.firstOrNull()
        val exactEntries = mutableListOf<WordFrequency>()
        primaryDict?.entriesForExactKey(word, limit = 8, into = exactEntries)
        val exactPrimaryCaseExists = exactEntries.any { it.word == word }
        val exactKnownExists = dictionaries.any { dict ->
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
