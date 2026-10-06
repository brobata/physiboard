package brobata.physiboard.core.dict

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ContextModel] against a table built here byte by byte (the layout `scripts/build_bigrams.py`
 * documents) and against the shipped English table. The reader must refuse a damaged file with
 * null, never an exception, because a context model that fails to load leaves autocorrect exactly
 * as it was; one that throws takes the keyboard down.
 */
class ContextModelTest {

    /**
     * Encodes a table the way the build script does. [words] are ids 1..n in order; [pairs] are
     * (previous word or "" for sentence start, next word, count).
     */
    private fun encode(words: List<String>, unigrams: List<Int>, sentences: Int, pairs: List<Triple<String, String, Int>>, corrupt: ((ByteArray) -> Unit)? = null): ByteArray {
        val ids = mapOf("" to 0) + words.withIndex().associate { (i, w) -> w to i + 1 }
        val body = ByteArrayOutputStream()
        val out = DataOutputStream(body)
        out.writeInt(0x50424731)
        out.writeShort(1)
        out.writeShort(0)
        out.writeInt(ids.size)
        for (w in words) {
            val bytes = w.toByteArray(Charsets.UTF_8)
            out.writeShort(bytes.size)
            out.write(bytes)
        }
        out.writeInt(sentences)
        for (u in unigrams) out.writeInt(u)
        val rows = pairs.groupBy { ids.getValue(it.first) }.mapValues { (_, list) -> list.map { ids.getValue(it.second) to it.third }.sortedBy { it.first } }
        val offsets = IntArray(ids.size + 1)
        for (i in 0 until ids.size) offsets[i + 1] = offsets[i] + (rows[i]?.size ?: 0)
        for (o in offsets) out.writeInt(o)
        for (i in 0 until ids.size) for ((next, _) in rows[i].orEmpty()) out.writeInt(next)
        for (i in 0 until ids.size) for ((_, count) in rows[i].orEmpty()) out.writeShort(count)
        out.flush()
        val bytes = body.toByteArray()
        corrupt?.invoke(bytes)
        val crc = CRC32().also { it.update(bytes) }
        val withCrc = ByteArrayOutputStream()
        DataOutputStream(withCrc).apply { write(bytes); writeInt(crc.value.toInt()); flush() }
        return withCrc.toByteArray()
    }

    private val words = listOf("the", "cat", "its", "it's", "tail", "wagged", "dog")
    private val unigrams = listOf(100, 30, 20, 25, 10, 5, 15)
    private val pairs = listOf(
        Triple("", "the", 60), Triple("", "it's", 20),
        Triple("the", "cat", 20), Triple("the", "dog", 10), Triple("the", "tail", 3),
        Triple("wagged", "its", 4),
        Triple("its", "tail", 8),
    )
    private val table = encode(words, unigrams, 80, pairs)

    @Test
    fun `T-a table round-trips its words, ids, unigrams and pair counts`() {
        val model = assertNotNull(ContextModel.read(table))
        assertEquals(8, model.vocabularySize)
        assertEquals(7, model.pairCount)
        for ((i, w) in words.withIndex()) {
            assertEquals(i + 1, model.idOf(w), w)
            assertEquals(w, model.wordOf(i + 1))
            assertEquals(unigrams[i], model.unigramCount(i + 1))
        }
        assertEquals(ContextModel.NO_CONTEXT, model.idOf("zebra"))
        assertEquals(ContextModel.NO_CONTEXT, model.idOf(""))
        assertEquals(80, model.unigramCount(ContextModel.SENTENCE_START))
        assertEquals(20, model.pairCount(model.idOf("the"), model.idOf("cat")))
        assertEquals(8, model.pairCount(model.idOf("its"), model.idOf("tail")))
        assertEquals(0, model.pairCount(model.idOf("it's"), model.idOf("tail")))
        assertEquals(60, model.pairCount(ContextModel.SENTENCE_START, model.idOf("the")))
        assertEquals(0, model.pairCount(ContextModel.NO_CONTEXT, model.idOf("the")))
    }

    @Test
    fun `T-a seen pair outranks an unseen one and the unseen one still has finite probability`() {
        val model = assertNotNull(ContextModel.read(table))
        val seen = model.logProbability("its", "tail")
        val unseen = model.logProbability("it's", "tail")
        assertTrue(seen > unseen, "seen=$seen unseen=$unseen")
        assertTrue(unseen.isFinite())
        // A word outside the table gets the floor, below every word the table knows.
        assertTrue(model.logProbability("its", "zebra") < unseen)
        // No context at all falls back to the unigram distribution: "the" is the commonest word.
        assertTrue(model.logProbability(null, "the") > model.logProbability(null, "wagged"))
    }

    @Test
    fun `T-the probabilities of one row sum to about one`() {
        val model = assertNotNull(ContextModel.read(table))
        val the = model.idOf("the")
        // Every id in the table, plus the floor mass the unseen ids share: the interpolation is a
        // proper distribution over the vocabulary, so the row's masses add up to one.
        var sum = 0.0
        for (id in 1 until model.vocabularySize) sum += exp(model.logProbability(the, id))
        assertTrue(sum > 0.98 && sum < 1.02, "row mass $sum")
    }

    @Test
    fun `T-a damaged table reads as null rather than throwing`() {
        assertNull(ContextModel.read(ByteArray(0)))
        assertNull(ContextModel.read(table.copyOf(table.size - 1)), "truncated")
        assertNull(ContextModel.read(table.copyOf(40)), "cut mid-table")
        assertNull(ContextModel.read(encode(words, unigrams, 80, pairs) { it[0] = 0x41 }), "bad magic, crc recomputed")
        assertNull(ContextModel.read(encode(words, unigrams, 80, pairs) { it[5] = 2 }), "future version")
        assertNull(ContextModel.read(table.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }), "bad checksum")
        // A row offset pointing past the pair arrays, with the checksum made good again.
        val offsetsAt = 4 + 2 + 2 + 4 + words.sumOf { 2 + it.toByteArray().size } + 4 * (words.size + 1)
        assertNull(ContextModel.read(encode(words, unigrams, 80, pairs) { it[offsetsAt + 4 * 8 + 3] = 0x7F }), "offset out of range")
    }

    @Test
    fun `T-the shipped English table loads, answers the cases the plan names and reports its cost`() {
        val bytes = assertNotNull(assetBytes(), "app/src/main/assets/dictionaries/en.bigrams is missing")
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val started = System.nanoTime()
        val model = assertNotNull(ContextModel.read(bytes), "the shipped table failed to parse")
        val loadMs = (System.nanoTime() - started) / 1_000_000.0
        System.gc()
        val after = runtime.totalMemory() - runtime.freeMemory()
        println("en.bigrams: vocabulary=${model.vocabularySize} pairs=${model.pairCount} load=${"%.1f".format(loadMs)} ms resident~${(after - before) / 1024} KB (file ${bytes.size / 1024} KB)")

        assertEquals(16, model.pairCount(model.idOf("wagged"), model.idOf("its")))
        assertEquals(0, model.pairCount(model.idOf("wagged"), model.idOf("it's")))
        assertEquals(59, model.pairCount(model.idOf("its"), model.idOf("tail")))
        assertEquals(4046, model.pairCount(model.idOf("more"), model.idOf("than")))
        assertEquals(0, model.pairCount(model.idOf("more"), model.idOf("then")))
        assertEquals(118, model.pairCount(model.idOf("definitely"), model.idOf("not")))
        assertEquals(23169, model.pairCount(ContextModel.SENTENCE_START, model.idOf("it's")))
        assertTrue(model.logProbability("definitely", "not") > model.logProbability("defiantly", "not"))
        assertTrue(loadMs < 2000, "load took $loadMs ms")
    }

    private fun assetBytes(): ByteArray? = repoRoot()
        ?.resolve("app/src/main/assets/dictionaries/en.bigrams")
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
