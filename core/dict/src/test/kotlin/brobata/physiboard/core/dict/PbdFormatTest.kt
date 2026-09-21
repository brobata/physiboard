package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Round-trips a small dictionary through [PbdWriter] and [PbdReader] and proves the reader
 * rejects a corrupted or wrong-version file with a typed [PbdFormatError] rather than throwing.
 */
class PbdFormatTest {

    private val english = LanguageCode.of("en")!!
    private val sample = listOf(
        WordFrequency("the", 222),
        WordFrequency("a", 200),
        WordFrequency("café", 40),
        WordFrequency("Café", 15),
        WordFrequency("zephyr", 1),
    )

    @Test
    fun `T-roundtrip a small dictionary through the writer and reader`() {
        val bytes = PbdWriter.write(english, sample)
        val result = PbdReader.read(bytes)

        val loaded = assertIs<PbdReadResult.Loaded>(result)
        assertEquals(english, loaded.document.language)
        assertEquals(1, loaded.document.formatVersion)
        assertEquals(sample.size, loaded.document.entries.size)
        assertEquals(sample.toSet(), loaded.document.entries.toSet())

        // The on-disk contract: entries come back sorted by natural string order.
        val words = loaded.document.entries.map { it.word }
        assertEquals(words.sorted(), words)
    }

    @Test
    fun `T-reading from a stream yields the same document as reading from bytes`() {
        val bytes = PbdWriter.write(english, sample)
        val fromBytes = assertIs<PbdReadResult.Loaded>(PbdReader.read(bytes))
        val fromStream = assertIs<PbdReadResult.Loaded>(PbdReader.read(bytes.inputStream()))
        assertEquals(fromBytes.document, fromStream.document)
    }

    @Test
    fun `T-a single flipped byte in the word block is rejected as a checksum mismatch`() {
        val bytes = PbdWriter.write(english, sample)
        val corrupted = bytes.copyOf()
        val lastIndex = corrupted.size - 1
        corrupted[lastIndex] = (corrupted[lastIndex] + 1).toByte()

        val result = PbdReader.read(corrupted)
        val failed = assertIs<PbdReadResult.Failed>(result)
        assertIs<PbdFormatError.ChecksumMismatch>(failed.error)
    }

    @Test
    fun `T-a bad magic number is rejected`() {
        val bytes = PbdWriter.write(english, sample)
        val corrupted = bytes.copyOf()
        corrupted[0] = 0x00

        val result = PbdReader.read(corrupted)
        val failed = assertIs<PbdReadResult.Failed>(result)
        assertEquals(PbdFormatError.BadMagic, failed.error)
    }

    @Test
    fun `T-an unsupported format version is rejected`() {
        val bytes = PbdWriter.write(english, sample)
        val corrupted = bytes.copyOf()
        // Byte 5 is the low byte of the UInt16 formatVersion field.
        corrupted[5] = 99

        val result = PbdReader.read(corrupted)
        val failed = assertIs<PbdReadResult.Failed>(result)
        assertEquals(PbdFormatError.UnsupportedVersion(99), failed.error)
    }

    @Test
    fun `T-a truncated file is refused rather than crashing the reader`() {
        val bytes = PbdWriter.write(english, sample)
        val truncated = bytes.copyOf(10)

        val result = PbdReader.read(truncated)
        assertIs<PbdReadResult.Failed>(result)
    }

    @Test
    fun `T-an unknown trailing block is skipped so old readers tolerate a future BIGR block`() {
        val bytes = PbdWriter.write(english, sample)

        // Append a block this reader does not know, exactly as a future milestone's BIGR
        // block would arrive: tag "ZZZZ", a length, and that many payload bytes, with the
        // header's checksum recomputed to cover it. Rebuilding the whole file end to end
        // through the writer's own layout keeps this test honest about what "the checksum
        // covers everything after the header" actually means.
        val body = bytes.copyOfRange(PbdFormat.HEADER_BYTES, bytes.size)
        val extraTag = byteArrayOf('Z'.code.toByte(), 'Z'.code.toByte(), 'Z'.code.toByte(), 'Z'.code.toByte())
        val extraPayload = byteArrayOf(1, 2, 3, 4)
        val extraLength = intToBytes(extraPayload.size)
        val newBody = body + extraTag + extraLength + extraPayload

        val crc = java.util.zip.CRC32()
        crc.update(newBody)

        val header = bytes.copyOf(PbdFormat.HEADER_BYTES)
        val checksumBytes = intToBytes(crc.value.toInt())
        checksumBytes.copyInto(header, destinationOffset = PbdFormat.HEADER_BYTES - 4)

        val rebuilt = header + newBody
        val result = PbdReader.read(rebuilt)

        val loaded = assertIs<PbdReadResult.Loaded>(result)
        assertEquals(sample.toSet(), loaded.document.entries.toSet())
    }

    @Test
    fun `T-writer rejects a frequency that does not fit 16 bits`() {
        assertTrue(
            runCatching {
                PbdWriter.write(english, listOf(WordFrequency("overflow", 70_000)))
            }.isFailure,
        )
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )
}
