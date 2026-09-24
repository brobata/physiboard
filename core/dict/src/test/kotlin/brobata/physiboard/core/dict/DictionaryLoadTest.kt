package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DictionaryIndex.fromPbdBytes] is the contract the running keyboard's asset loader depends on:
 * a well-formed `.pbd` becomes a queryable index, and anything else (missing, truncated,
 * corrupted, or simply the wrong bytes) becomes null rather than a thrown exception. spec:
 * autocorrect-suggestions.md §2 point 3.
 */
class DictionaryLoadTest {

    private val english = LanguageCode.of("en")!!
    private val sample = listOf(
        WordFrequency("the", 222),
        WordFrequency("cat", 150),
        WordFrequency("catalog", 90),
        WordFrequency("dog", 180),
    )

    @Test
    fun `T-a builder's output round-trips into a queryable index`() {
        val bytes = PbdWriter.write(english, sample)
        val index = assertNotNull(DictionaryIndex.fromPbdBytes(bytes))

        assertEquals(english, index.language)
        assertEquals(sample.size, index.wordCount)
        assertTrue(index.contains("the"))
        assertTrue(index.contains("cat"))
        assertTrue(index.contains("catalog"))
        assertEquals(222, index.frequencyOf("the"))
    }

    @Test
    fun `T-empty bytes (a missing asset read as zero bytes) yield no index`() {
        assertNull(DictionaryIndex.fromPbdBytes(ByteArray(0)))
    }

    @Test
    fun `T-truncated bytes yield no index`() {
        val bytes = PbdWriter.write(english, sample)
        assertNull(DictionaryIndex.fromPbdBytes(bytes.copyOf(12)))
    }

    @Test
    fun `T-a corrupted checksum yields no index`() {
        val bytes = PbdWriter.write(english, sample)
        val corrupted = bytes.copyOf()
        val last = corrupted.size - 1
        corrupted[last] = (corrupted[last] + 1).toByte()
        assertNull(DictionaryIndex.fromPbdBytes(corrupted))
    }

    @Test
    fun `T-bytes that are not a pbd file at all yield no index`() {
        assertNull(DictionaryIndex.fromPbdBytes("not a dictionary".toByteArray()))
    }
}
