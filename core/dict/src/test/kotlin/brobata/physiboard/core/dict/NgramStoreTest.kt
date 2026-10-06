package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: autocorrect-suggestions.md SS4, "Learned bigrams" and "Starter words". */
class NgramStoreTest {

    @Test
    fun `prefix normalization lowercases, folds ligatures, strips accents, keeps digits, drops punctuation`() {
        assertEquals("perche", NgramPrefix.of("perché"))
        assertEquals("loeil", NgramPrefix.of("l'œil"))
        assertEquals("gpt4", NgramPrefix.of("GPT-4"))
    }

    @Test
    fun `learning a new pair starts at count 1`() {
        val store = NgramStore.empty().learn("en", NgramPrefix.of("ich"), "bin", nowMillis = 100)
        val predictions = store.predict("en", NgramPrefix.of("ich"), limit = 3)
        assertEquals(listOf(Bigram("en", "ich", "bin", count = 1, lastUsedMillis = 100)), predictions)
    }

    @Test
    fun `learning the same pair again increments count and refreshes recency`() {
        var store = NgramStore.empty().learn("en", "how", "are", nowMillis = 100)
        store = store.learn("en", "how", "are", nowMillis = 200)
        val predictions = store.predict("en", "how", limit = 3)
        assertEquals(1, predictions.size)
        assertEquals(2, predictions.first().count)
        assertEquals(200, predictions.first().lastUsedMillis)
    }

    @Test
    fun `unlearning takes back one learn, and a pair learned once goes`() {
        var store = NgramStore.empty().learn("en", "bigger", "then", nowMillis = 100)
        store = store.learn("en", "bigger", "then", nowMillis = 200)
        store = store.unlearn("en", "bigger", "then")
        assertEquals(1, store.predict("en", "bigger", limit = 3).single().count)
        store = store.unlearn("en", "bigger", "then")
        assertEquals(emptyList(), store.predict("en", "bigger", limit = 3))
        // A pair never learned is left as it is.
        assertEquals(emptyList(), store.unlearn("en", "bigger", "than").allRows())
    }

    @Test
    fun `predictions are ordered by count descending then recency descending`() {
        var store = NgramStore.empty()
        store = store.learn("en", "how", "are", nowMillis = 100)
        store = store.learn("en", "how", "is", nowMillis = 300)
        store = store.learn("en", "how", "are", nowMillis = 400) // "are" now count 2, most recent
        store = store.learn("en", "how", "do", nowMillis = 200)  // count 1, older than "is"
        val predictions = store.predict("en", "how", limit = 3).map { it.nextWord }
        assertEquals(listOf("are", "is", "do"), predictions)
    }

    @Test
    fun `sentence start is its own bucket, distinct from a same-spelled ordinary prefix`() {
        val store = NgramStore.empty().learn("en", NgramStore.SENTENCE_START, "the", nowMillis = 100)
        assertEquals(listOf("the"), store.predict("en", NgramStore.SENTENCE_START, 3).map { it.nextWord })
        assertEquals(emptyList(), store.predict("en", "the", 3))
    }

    @Test
    fun `locales do not leak into each other`() {
        val store = NgramStore.empty().learn("en", "how", "are", nowMillis = 100)
        assertEquals(emptyList(), store.predict("fr", "how", 3))
    }

    @Test
    fun `forget removes one pair without touching the others under the same prefix`() {
        var store = NgramStore.empty()
        store = store.learn("en", "how", "are", nowMillis = 100)
        store = store.learn("en", "how", "is", nowMillis = 100)
        store = store.forget("en", "how", "are")
        assertEquals(listOf("is"), store.predict("en", "how", 3).map { it.nextWord })
    }

    @Test
    fun `forgetEverywhere removes a word as a next word under every prefix and locale`() {
        var store = NgramStore.empty()
        store = store.learn("en", "how", "cats", nowMillis = 100)
        store = store.learn("en", NgramStore.SENTENCE_START, "cats", nowMillis = 100)
        store = store.learn("fr", "les", "cats", nowMillis = 100)
        store = store.forgetEverywhere("cats")
        assertEquals(emptyList(), store.predict("en", "how", 3))
        assertEquals(emptyList(), store.predict("en", NgramStore.SENTENCE_START, 3))
        assertEquals(emptyList(), store.predict("fr", "les", 3))
    }

    @Test
    fun `predict respects the limit`() {
        var store = NgramStore.empty()
        for (word in listOf("apple", "banana", "cherry", "date", "elder")) store = store.learn("en", "how", word, nowMillis = 100)
        assertEquals(3, store.predict("en", "how", 3).size)
    }

    @Test
    fun `a blank next word is never learned`() {
        val store = NgramStore.empty().learn("en", "how", "  ", nowMillis = 100)
        assertTrue(store.allRows().isEmpty())
    }

    @Test
    fun `mergedWith keeps the higher count between the in-memory overlay and a late load`() {
        val overlay = NgramStore.empty().learn("en", "how", "are", nowMillis = 100).learn("en", "how", "are", nowMillis = 200)
        val merged = overlay.mergedWith(listOf(Bigram("en", "how", "are", count = 1, lastUsedMillis = 50)))
        assertEquals(2, merged.predict("en", "how", 1).first().count)
    }

    @Test
    fun `mergedWith takes the loaded row when it is more advanced than the overlay`() {
        val overlay = NgramStore.empty().learn("en", "how", "are", nowMillis = 100)
        val merged = overlay.mergedWith(listOf(Bigram("en", "how", "are", count = 5, lastUsedMillis = 999)))
        assertEquals(5, merged.predict("en", "how", 1).first().count)
    }

    @Test
    fun `mergedWith adds a row the overlay never had`() {
        val merged = NgramStore.empty().mergedWith(listOf(Bigram("en", "how", "are", count = 1, lastUsedMillis = 100)))
        assertEquals(listOf("are"), merged.predict("en", "how", 3).map { it.nextWord })
    }

    @Test
    fun `of rebuilds a store from persisted rows that learn and predict correctly afterward`() {
        val persisted = listOf(Bigram("en", "how", "are", count = 3, lastUsedMillis = 100))
        var store = NgramStore.of(persisted)
        assertEquals(listOf("are"), store.predict("en", "how", 3).map { it.nextWord })
        store = store.learn("en", "how", "are", nowMillis = 200)
        assertEquals(4, store.predict("en", "how", 3).first().count)
    }
}
