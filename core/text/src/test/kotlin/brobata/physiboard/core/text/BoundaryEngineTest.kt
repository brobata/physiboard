package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.RuleSet
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: autocorrect-suggestions.md SS7.2, "Test cases" T39-T43, T89 (this module's slice). */
class BoundaryEngineTest {

    private val en = LanguageCode.of("en")!!

    private fun dict(vararg entries: Pair<String, Int>): DictionaryIndex =
        DictionaryIndex.build(en, entries.map { WordFrequency(it.first, it.second) })

    private fun evaluate(
        word: String,
        boundary: Char,
        dictionaries: List<DictionaryIndex>,
        ruleSets: List<RuleSet> = emptyList(),
        settings: AutocorrectSettings = AutocorrectSettings(),
        memory: AutocorrectMemory = AutocorrectMemory(),
    ) = BoundaryEngine.evaluate(
        trackedWord = word,
        textBeforeCursor32 = word,
        boundaryChar = boundary,
        ruleSets = ruleSets,
        dictionaries = dictionaries,
        userWords = UserWordStore.empty(),
        settings = settings,
        rankingOptions = RankingOptions(),
        lengthChangeAllowance = 2,
        memory = memory,
    )

    @Test
    fun `T40 a word known through the active dictionaries is left alone`() {
        val (_, outcome) = evaluate(
            "that",
            ' ',
            dictionaries = listOf(dict("trat" to 255, "that" to 100)),
            settings = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1),
        )
        assertEquals(BoundaryOutcome.CommitPlain, outcome)
    }

    @Test
    fun `T41 primary case repair fills in the only capitalized entry`() {
        val (_, outcome) = evaluate(
            "problem",
            ' ',
            dictionaries = listOf(dict("Problem" to 220, "problemlos" to 255)),
            settings = AutocorrectSettings(autoReplaceOnSpaceEnter = true),
        )
        val replaced = outcome as BoundaryOutcome.Replaced
        assertEquals("Problem", replaced.replacement)
    }

    @Test
    fun `T42 a word already spelled exactly as typed is not case-repaired`() {
        val (_, outcome) = evaluate(
            "und",
            ' ',
            dictionaries = listOf(dict("und" to 255, "Und" to 200)),
            settings = AutocorrectSettings(autoReplaceOnSpaceEnter = true),
        )
        assertEquals(BoundaryOutcome.CommitPlain, outcome)
    }

    @Test
    fun `T89 everything disabled leaves the word untouched`() {
        val (_, outcome) = evaluate(
            "id",
            ' ',
            dictionaries = listOf(dict("id" to 100)),
            settings = AutocorrectSettings(autoCorrectEnabled = false, autoReplaceOnSpaceEnter = false, suggestionsEnabled = false),
        )
        assertEquals(BoundaryOutcome.CommitPlain, outcome)
    }

    @Test
    fun `a blank current word commits the boundary plainly`() {
        val (_, outcome) = evaluate("", ' ', dictionaries = listOf(dict("hello" to 100)))
        assertEquals(BoundaryOutcome.CommitPlain, outcome)
    }

    @Test
    fun `a hard boundary before the cursor blocks correction`() {
        val (_, outcome) = BoundaryEngine.evaluate(
            trackedWord = "lo",
            textBeforeCursor32 = "@lo", // a non-word symbol directly before the tracked word
            boundaryChar = ' ',
            ruleSets = emptyList(),
            dictionaries = listOf(dict("lo" to 10, "loo" to 200)),
            userWords = UserWordStore.empty(),
            settings = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1),
            rankingOptions = RankingOptions(),
            lengthChangeAllowance = 0,
            memory = AutocorrectMemory(),
        )
        assertEquals(BoundaryOutcome.CommitPlain, outcome)
    }

    // -----------------------------------------------------------------------------------------
    // B9: primary case repair must respect every source of "spelled exactly as typed". spec:
    // autocorrect-suggestions.md SS6.1 ("a personal word is never autocorrected"; the personal
    // store "is merged into every dictionary that is loaded"), SS10.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `SS6-1 a user-added lowercase word is not case-repaired from the primary dictionary`() {
        val userWords = UserWordStore.empty().withPersonalWordAdded("paris", nowMillis = 0L)
        val (_, outcome) = BoundaryEngine.evaluate(
            trackedWord = "paris", textBeforeCursor32 = "paris", boundaryChar = ' ',
            ruleSets = emptyList(), dictionaries = listOf(dict("Paris" to 200)), userWords = userWords,
            settings = AutocorrectSettings(autoReplaceOnSpaceEnter = true), rankingOptions = RankingOptions(),
            lengthChangeAllowance = 2, memory = AutocorrectMemory(),
        )
        assertEquals(BoundaryOutcome.CommitPlain, outcome)
    }

    @Test
    fun `SS10 a word spelled exactly as typed in a secondary dictionary is not case-repaired`() {
        val (_, outcome) = evaluate(
            "chef",
            ' ',
            dictionaries = listOf(dict("Chef" to 200), dict("chef" to 150)),
            settings = AutocorrectSettings(autoReplaceOnSpaceEnter = true),
        )
        assertEquals(BoundaryOutcome.CommitPlain, outcome)
    }

    @Test
    fun `SS7-2 the hard-boundary scan still excludes a tracked word the field spells with a curly apostrophe`() {
        // The tracker folds apostrophes to the straight one; the field holds the key as pressed.
        // If the two are compared without folding, the word is never excluded from the 32-character
        // window, the scan stops on its own trailing letter, and the emoji before it is never seen.
        val (_, outcome) = BoundaryEngine.evaluate(
            trackedWord = "we'll", textBeforeCursor32 = "\uD83D\uDE42 we\u2019ll", boundaryChar = ' ',
            ruleSets = emptyList(), dictionaries = listOf(dict("We'll" to 200)), userWords = UserWordStore.empty(),
            settings = AutocorrectSettings(autoReplaceOnSpaceEnter = true), rankingOptions = RankingOptions(),
            lengthChangeAllowance = 2, memory = AutocorrectMemory(),
        )
        assertEquals(BoundaryOutcome.CommitPlain, outcome)
    }

    @Test
    fun `SS7-5 a boundary on a blank word or behind a hard boundary clears the undo memory`() {
        val memory = AutocorrectMemory().afterReplacement("teh", "the")
        val (afterBlank, _) = evaluate("", ' ', dictionaries = listOf(dict("the" to 100)), memory = memory)
        assertEquals(null, afterBlank.lastReplacement)

        val (afterHard, _) = BoundaryEngine.evaluate(
            trackedWord = "lo", textBeforeCursor32 = "@lo", boundaryChar = ' ',
            ruleSets = emptyList(), dictionaries = listOf(dict("lo" to 10)), userWords = UserWordStore.empty(),
            settings = AutocorrectSettings(autoReplaceOnSpaceEnter = true), rankingOptions = RankingOptions(),
            lengthChangeAllowance = 0, memory = memory,
        )
        assertEquals(null, afterHard.lastReplacement)
    }
}
