package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DictionaryIndexTest {

    private val language = LanguageCode.of("en")!!
    private val index = DictionaryIndex.build(
        language,
        listOf(
            WordFrequency("cat", 500),
            WordFrequency("cats", 300),
            WordFrequency("car", 400),
            WordFrequency("care", 350),
            WordFrequency("dog", 600),
            WordFrequency("café", 40),
            WordFrequency("Café", 90),
            WordFrequency("a", 1000),
        ),
    )

    @Test
    fun `T-a bundled word is a known word by exact spelling`() {
        assertTrue(index.contains("cat"))
        assertTrue(index.contains("dog"))
    }

    @Test
    fun `T-a word never seen is not known`() {
        assertFalse(index.contains("xyzzy"))
    }

    @Test
    fun `T-membership is checked after normalization so an accent variant is known too`() {
        assertTrue(index.contains("cafe"))
        assertTrue(index.contains("CAFE"))
    }

    @Test
    fun `T-frequencyOf returns the highest frequency in the normalized group`() {
        assertEquals(90, index.frequencyOf("cafe"))
        assertEquals(90, index.frequencyOf("Café"))
    }

    @Test
    fun `T-frequencyOf is 0 for an unknown word without throwing`() {
        assertEquals(0, index.frequencyOf("nope"))
    }

    @Test
    fun `T-prefixLookup finds every completion under a prefix, most frequent first`() {
        val results = mutableListOf<WordFrequency>()
        val added = index.prefixLookup("ca", limit = 10, into = results)

        // cat, cats, car, care, café and Café (two spellings of one normalized key) all
        // normalize to a key starting with "ca".
        assertEquals(6, added)
        assertEquals(setOf("cat", "cats", "car", "care", "café", "Café"), results.map { it.word }.toSet())
    }

    @Test
    fun `T-prefixLookup respects the limit and orders by frequency descending`() {
        val results = mutableListOf<WordFrequency>()
        index.prefixLookup("ca", limit = 1, into = results)

        assertEquals(1, results.size)
        assertEquals("cat", results.single().word) // highest frequency (500) among "ca*" entries
    }

    @Test
    fun `T-prefixLookup on an empty or punctuation-only prefix matches nothing`() {
        val results = mutableListOf<WordFrequency>()
        assertEquals(0, index.prefixLookup("", limit = 10, into = results))
        assertEquals(0, index.prefixLookup("!!!", limit = 10, into = results))
    }

    @Test
    fun `T-entriesForExactKey returns every spelling sharing a normalized key`() {
        val results = mutableListOf<WordFrequency>()
        val added = index.entriesForExactKey("cafe", limit = 5, into = results)

        assertEquals(2, added)
        assertEquals(listOf("Café", "café"), results.map { it.word })
    }

    @Test
    fun `T-neighbours finds a one-letter typo within edit distance 1`() {
        val results = mutableListOf<ScoredCandidate>()
        index.neighbours("kat", maxDistance = 1, limit = 5, into = results)

        assertTrue(results.any { it.word == "cat" && it.distance == 1 })
    }

    @Test
    fun `T-neighbours excludes candidates further than the given distance`() {
        val results = mutableListOf<ScoredCandidate>()
        index.neighbours("dog", maxDistance = 1, limit = 5, into = results)

        assertFalse(results.any { it.word == "cat" })
    }

    @Test
    fun `T-neighbours orders by distance ascending then frequency descending`() {
        val results = mutableListOf<ScoredCandidate>()
        index.neighbours("car", maxDistance = 2, limit = 10, into = results)

        val distances = results.map { it.distance }
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun `T-an empty dictionary answers every query without throwing`() {
        val empty = DictionaryIndex.build(language, emptyList())
        val prefixResults = mutableListOf<WordFrequency>()
        val neighbourResults = mutableListOf<ScoredCandidate>()

        assertFalse(empty.contains("anything"))
        assertEquals(0, empty.frequencyOf("anything"))
        assertEquals(0, empty.prefixLookup("a", 10, prefixResults))
        assertEquals(0, empty.neighbours("a", 2, 10, neighbourResults))
    }

    @Test
    fun `T-topByFrequency returns the highest-frequency spelling per key, most frequent first`() {
        // spec: autocorrect-suggestions.md SS4's starter words, "the top 48 entries by effective
        // frequency across the primary dictionary". "café"/"Café" share one key; only the more
        // frequent spelling (Café, 90) should represent it.
        val results = mutableListOf<WordFrequency>()
        index.topByFrequency(3, results)
        assertEquals(listOf("a" to 1000, "dog" to 600, "cat" to 500), results.map { it.word to it.frequency })
    }

    @Test
    fun `T-topByFrequency on an empty dictionary returns nothing`() {
        val empty = DictionaryIndex.build(language, emptyList())
        val results = mutableListOf<WordFrequency>()
        assertEquals(0, empty.topByFrequency(10, results))
    }
}
