package brobata.physiboard.core.dict

import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The reader runs on a background thread at keyboard start-up, where an uncaught exception
 * kills the whole keyboard process. So a damaged file of any shape must come back as a typed
 * [PbdReadResult.Failed], never as a throw. These tests mutate a valid file byte by byte and
 * truncate it at every length, which covers the negative counts, oversized counts and
 * out-of-range word offsets that a single flipped bit in a length field produces.
 */
class PbdReaderNeverThrowsTest {

    private val english = LanguageCode.of("en")!!
    private val valid = PbdWriter.write(
        english,
        listOf(
            WordFrequency("the", 222),
            WordFrequency("a", 200),
            WordFrequency("café", 40),
            WordFrequency("zephyr", 1),
        ),
    )

    /** Values chosen to turn a length or count byte negative, huge, or zero. */
    private val mutations = listOf<(Byte) -> Byte>(
        { b -> (b + 1).toByte() },
        { b -> (b - 1).toByte() },
        { 0 },
        { 0xFF.toByte() },
        { 0x7F },
        { 0x80.toByte() },
        { b -> (b.toInt() xor 0x55).toByte() },
    )

    @Test
    fun `every single-byte mutation of a valid file is refused, never thrown`() {
        for (index in valid.indices) {
            for ((m, mutate) in mutations.withIndex()) {
                val corrupted = valid.copyOf()
                corrupted[index] = mutate(corrupted[index])
                if (corrupted.contentEquals(valid)) continue
                val result = try {
                    PbdReader.read(corrupted)
                } catch (t: Throwable) {
                    throw AssertionError("byte $index mutation $m threw ${t::class.simpleName}: ${t.message}", t)
                }
                if (index in refusalNotRequired) continue
                assertIs<PbdReadResult.Failed>(result, "byte $index mutation $m should be refused")
            }
        }
    }

    /**
     * Header bytes the checksum does not cover and the format lets vary: the language field
     * (a mutation can spell another valid code) and the reserved field (ignored for forward
     * compatibility). A mutation there may load; it must still never throw.
     */
    private val refusalNotRequired = 6 until 12

    @Test
    fun `every truncation of a valid file is refused, never thrown`() {
        for (length in 0 until valid.size) {
            val truncated = valid.copyOf(length)
            val result = try {
                PbdReader.read(truncated)
            } catch (t: Throwable) {
                throw AssertionError("truncation to $length threw ${t::class.simpleName}: ${t.message}", t)
            }
            assertIs<PbdReadResult.Failed>(result, "truncation to $length should be refused")
        }
    }

    @Test
    fun `a truncation shorter than a whole block frame is reported as truncated`() {
        // A file cut off after the WORD block's tag but before its length is missing a
        // required field, which is a truncation, not a checksum disagreement.
        val cut = valid.copyOf(PbdFormat.HEADER_BYTES + 2)
        val failed = assertIs<PbdReadResult.Failed>(PbdReader.read(cut))
        assertEquals(PbdFormatError.Truncated, failed.error)
    }

    @Test
    fun `a stream that fails mid-read is refused, never thrown`() {
        val failing = object : InputStream() {
            private var position = 0
            override fun read(): Int {
                if (position >= PbdFormat.HEADER_BYTES + 6) throw IOException("disk went away")
                return valid[position++].toInt() and 0xFF
            }
        }
        val result = PbdReader.read(failing)
        val failed = assertIs<PbdReadResult.Failed>(result)
        assertIs<PbdFormatError.Unreadable>(failed.error)
    }
}
