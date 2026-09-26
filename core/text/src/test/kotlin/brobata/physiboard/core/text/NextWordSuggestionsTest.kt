package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.NgramStore
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: autocorrect-suggestions.md SS4, "Test cases" T50 (this module's slice). */
class NextWordSuggestionsTest {

    private val en = LanguageCode.of("en")!!

    @Test
    fun `starter words exclude one-letter words and anything but letters and apostrophes`() {
        val dict = DictionaryIndex.build(en, listOf(WordFrequency("the", 220), WordFrequency("a", 250), WordFrequency("123", 240), WordFrequency("we'll", 100)))
        val starters = StarterWords.of(dict, UserWordStore.empty())
        assertTrue("the" in starters)
        assertTrue("we'll" in starters)
        assertFalse("a" in starters)
        assertFalse("123" in starters)
    }

    @Test
    fun `starter words exclude default user words but include personal words first`() {
        val dict = DictionaryIndex.build(en, listOf(WordFrequency("the", 220)))
        val userWords = UserWordStore.of(defaultWords = listOf(WordFrequency("BlackBerry", 25)), personalWords = emptyList())
            .withPersonalWordAdded("PhysiBoard", nowMillis = 100)
        val starters = StarterWords.of(dict, userWords)
        assertEquals("PhysiBoard", starters.first())
        assertFalse("BlackBerry" in starters, "default user words are excluded from starters (SS4)")
    }

    /**
     * spec SS4's "top 48... excluding..." reads as filter-then-rank: an ineligible spelling
     * occupying one of the raw top-N frequency slots must be replaced by the next-ranked eligible
     * word, not just dropped. Ten one-letter words outrank forty eligible two-letter ones here; a
     * filter-after-limit implementation would return only the eligible words that survived inside
     * the original top-48 window (38 of them), not all 40 that actually qualify.
     */
    @Test
    fun `an ineligible word occupying a top-frequency slot is backfilled by the next eligible one`() {
        val junk = ('a'..'j').map { WordFrequency(it.toString(), frequency = 1000) } // 10 one-letter, highest frequency
        val eligible = ('a'..'z').flatMap { a -> ('a'..'z').map { b -> "$a$b" } }.take(40)
            .mapIndexed { index, word -> WordFrequency(word, frequency = 100 - index) }
        val dict = DictionaryIndex.build(en, junk + eligible)
        val starters = StarterWords.of(dict, UserWordStore.empty())
        assertEquals(40, starters.size, "all 40 eligible words should surface, not just the ones inside the raw top-48 window: $starters")
        assertTrue(junk.none { it.word in starters }, "one-letter words are still excluded")
    }

    @Test
    fun `starter words cap at 48 and are ordered by frequency then length`() {
        // 60 distinct alphabetic two-letter spellings ("aa", "ab", ...), frequency increasing with
        // each: the eligibility filter rejects digits, so a real alphabetic fixture is needed to
        // isolate the 48-cap and the frequency ordering.
        val words = ('a'..'z').flatMap { a -> ('a'..'z').map { b -> "$a$b" } }.take(60)
        val entries = words.mapIndexed { index, word -> WordFrequency(word, index + 1) }
        val dict = DictionaryIndex.build(en, entries)
        val starters = StarterWords.of(dict, UserWordStore.empty())
        assertEquals(48, starters.size)
        assertEquals(entries.last().word, starters.first(), "the highest-frequency entry should rank first")
    }

    @Test
    fun `next-word predictions come from learned bigrams before starter words`() {
        val dict = DictionaryIndex.build(en, listOf(WordFrequency("the", 220), WordFrequency("a", 250)))
        val store = NgramStore.empty().learn("en", "how", "are", nowMillis = 100)
        val predictions = NextWordSuggestions.of(store, "en", "how", dict, UserWordStore.empty(), limit = 3)
        assertEquals("are", predictions.first())
    }

    @Test
    fun `next-word predictions fill remaining slots with starter words, skipping duplicates`() {
        val dict = DictionaryIndex.build(en, listOf(WordFrequency("bin", 220), WordFrequency("the", 200)))
        val store = NgramStore.empty().learn("en", "ich", "bin", nowMillis = 100)
        val predictions = NextWordSuggestions.of(store, "en", "ich", dict, UserWordStore.empty(), limit = 3)
        assertEquals(listOf("bin", "the"), predictions)
    }

    @Test
    fun `no bigrams at all falls back to plain starter words`() {
        val dict = DictionaryIndex.build(en, listOf(WordFrequency("the", 220)))
        val predictions = NextWordSuggestions.of(NgramStore.empty(), "en", NgramStore.SENTENCE_START, dict, UserWordStore.empty(), limit = 3)
        assertEquals(listOf("the"), predictions)
    }
}
