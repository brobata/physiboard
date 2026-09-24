package brobata.physiboard.core.dict

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The neighbour search runs once per keystroke on the real 80,000-entry list, so it has to be
 * bounded in work and allocation, and any bounding must not change what it finds. Two checks:
 * the results equal a brute-force reference over every key (the definition of "the same results
 * as before"), and a batch of real queries completes inside a generous wall-clock bound that the
 * unbounded walk (one full distance table per key in the length window, three arrays each)
 * does not meet. spec: autocorrect-suggestions.md SS3.4.
 */
class NeighbourSearchBoundTest {

    private val queries = listOf(
        "recieve", "teh", "wierd", "definately", "seperate", "occured", "adress", "goverment",
        "hte", "becuase", "wich", "thier", "untill", "tommorow", "acheive", "neccessary",
        "a", "an", "the", "hello", "keyboard", "xzqv", "pneumonoultramicroscopic",
    )
    private val maxDistance = 2
    private val limit = 20

    private val document: PbdDocument? by lazy {
        assetBytes()?.let { (PbdReader.read(it) as? PbdReadResult.Loaded)?.document }
    }

    @Test
    fun `neighbours on the real English list match a brute-force reference for every query`() {
        val doc = assertNotNull(document, "app/src/main/assets/dictionaries/en.pbd is missing")
        val index = DictionaryIndex.from(doc)
        val reference = BruteForceNeighbours(doc.entries)
        for (query in queries) {
            val actual = mutableListOf<ScoredCandidate>()
            // Unlimited, and with the spelling as a final tie-break on both sides: the index's
            // own order leaves ties on (distance, frequency, length) unspecified, so this compares
            // the set and the promised order, not an accident of iteration.
            index.neighbours(query, maxDistance, Int.MAX_VALUE, actual)
            assertEquals(reference.neighbours(query, maxDistance, Int.MAX_VALUE).sortedWith(tieBroken), actual.sortedWith(tieBroken), "query '$query'")
            assertTrue(actual.isNotEmpty() || query == "xzqv" || query == "pneumonoultramicroscopic", "query '$query' found nothing")
        }
    }

    private val tieBroken = compareBy<ScoredCandidate> { it.distance }.thenByDescending { it.frequency }.thenBy { it.word.length }.thenBy { it.word }

    @Test
    fun `a batch of neighbour queries on the real English list completes within the bound`() {
        val doc = assertNotNull(document, "app/src/main/assets/dictionaries/en.pbd is missing")
        val index = DictionaryIndex.from(doc)
        val into = ArrayList<ScoredCandidate>()
        // Warm-up: JIT and class loading are not what this test measures.
        repeat(2) { for (query in queries) { into.clear(); index.neighbours(query, maxDistance, limit, into) } }

        val rounds = 10
        val started = System.nanoTime()
        repeat(rounds) { for (query in queries) { into.clear(); index.neighbours(query, maxDistance, limit, into) } }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val perQueryMs = elapsedMs.toDouble() / (rounds * queries.size)
        println("neighbours(): ${queries.size * rounds} queries in $elapsedMs ms, ${"%.2f".format(perQueryMs)} ms per query")
        // A keystroke budget: generous for a slow CI box, far below what the unbounded walk takes.
        assertTrue(perQueryMs < 4.0, "neighbours() averaged ${"%.2f".format(perQueryMs)} ms per query; the bound is 4 ms")
    }

    /** The definition of the query: every normalized key within the distance, best entry per key, sorted as the index promises. */
    private class BruteForceNeighbours(entries: List<WordFrequency>) {
        private val bestPerKey: Map<String, WordFrequency> = entries
            .groupBy { DictNormalization.normalizedKey(it.word) }
            .mapValues { (_, group) -> group.sortedWith(compareByDescending<WordFrequency> { it.frequency }.thenBy { it.word }).first() }

        fun neighbours(word: String, maxDistance: Int, limit: Int): List<ScoredCandidate> {
            val key = DictNormalization.normalizedKey(word)
            if (key.isEmpty()) return emptyList()
            return bestPerKey.entries
                .mapNotNull { (candidateKey, best) ->
                    val distance = EditDistance.osaDistance(key, candidateKey, maxDistance)
                    if (distance > maxDistance) null else ScoredCandidate(best.word, best.frequency, distance)
                }
                .sortedWith(compareBy<ScoredCandidate> { it.distance }.thenByDescending { it.frequency }.thenBy { it.word.length })
                .take(limit)
        }
    }

    private fun assetBytes(): ByteArray? = repoRoot()
        ?.resolve("app/src/main/assets/dictionaries/en.pbd")
        ?.takeIf { it.isFile }
        ?.readBytes()

    private fun repoRoot(): File? {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        return null
    }
}
