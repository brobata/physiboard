package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: autocorrect-suggestions.md SS3, "Test cases" T8 (this module's slice: proximity ranking). */
class SuggestionRankingTest {

    private val en = LanguageCode.of("en")!!

    private fun dict(vararg entries: Pair<String, Int>): DictionaryIndex =
        DictionaryIndex.build(en, entries.map { WordFrequency(it.first, it.second) })

    @Test
    fun `T8 keyboard proximity ranks an adjacent-key typo above a distant one`() {
        val index = dict("hallo" to 200)
        val withProximity = RankingOptions(useKeyboardProximity = true)

        // "hsllo": a for s is a QWERTY-adjacent substitution, should still surface "hallo".
        val adjacent = SuggestionRanking.suggest("hsllo", listOf(index), UserWordStore.empty(), withProximity)
        assertTrue(adjacent.any { it.word == "hallo" }, "adjacent-key typo should still suggest hallo")

        // "hmllo": a for m is a distant substitution (m is nowhere near a on a QWERTY grid);
        // with proximity ranking on, this same-length substitution is filtered out.
        val distant = SuggestionRanking.suggest("hmllo", listOf(index), UserWordStore.empty(), withProximity)
        assertTrue(distant.none { it.word == "hallo" }, "distant-key typo should be filtered by proximity ranking")

        // With proximity off, the distant substitution is not filtered.
        val distantNoProximity = SuggestionRanking.suggest("hmllo", listOf(index), UserWordStore.empty(), RankingOptions(useKeyboardProximity = false))
        assertTrue(distantNoProximity.any { it.word == "hallo" }, "without proximity ranking, hallo should still surface")
    }

    @Test
    fun `effective frequency maps the 0-255 raw scale monotonically`() {
        val low = SuggestionRanking.effectiveFrequency(30)
        val high = SuggestionRanking.effectiveFrequency(222)
        assertTrue(low < high)
        assertEquals(1.0, SuggestionRanking.effectiveFrequency(0))
    }

    @Test
    fun `a completion must be longer than the typed word`() {
        val index = dict("hall" to 200, "hallo" to 200)
        val results = SuggestionRanking.suggest("hall", listOf(index), UserWordStore.empty(), RankingOptions())
        assertTrue(results.none { it.word == "hall" }, "the word itself is never suggested")
        assertTrue(results.any { it.word == "hallo" })
    }
}
