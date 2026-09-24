package brobata.physiboard.core.dict

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

/**
 * Reads a `.pbd` dictionary file.
 *
 * ## Byte layout (format version 1)
 *
 * A `.pbd` file is a 20-byte fixed header followed by a chain of self-describing blocks
 * running to end of file. All multi-byte integers are big-endian. This layout is a contract
 * with the build script that produces the file (see [PbdWriter]) and with the hosting repo
 * that serves it: changing it means bumping [PbdFormat.CURRENT_VERSION].
 *
 * ```
 * offset  size  field
 * 0       4     magic, the ASCII bytes "PBD1"
 * 4       2     formatVersion (UInt16); this reader accepts version 1 only
 * 6       4     languageCode: up to 4 ASCII lowercase letters, NUL-padded; version 1 always
 *               writes a two-letter code followed by two zero bytes
 * 10      2     reserved, written as zero; a reader ignores it rather than rejecting a file
 *               that one day sets bits here, so a future minor addition need not be a new
 *               major version
 * 12      4     wordCount (Int32), the number of entries in the WORD block, repeated there so
 *               it is self-checking
 * 16      4     checksum (UInt32, CRC-32), computed over every byte from offset 20 to the end
 *               of the file
 * 20      ...   one or more blocks, each:
 *                 tag     4 bytes ASCII, e.g. "WORD"
 *                 length  Int32, the byte length of this block's payload (not counting the
 *                         tag or this length field)
 *                 payload `length` bytes
 * ```
 *
 * A reader that does not recognize a block's tag skips exactly `length` bytes and moves on to
 * the next one; this is how a later milestone adds a `BIGR` bigram block that an old reader
 * (or a reader of a file with no such block at all) simply steps over, with no format-version
 * bump and no failure. Exactly one `WORD` block is required. Its payload is:
 *
 * ```
 * offset  size          field
 * 0       4             entryCount (Int32)
 * 4       entryCount*8  the offset table: one 8-byte record per entry, entries sorted by their
 *                       word in ascending natural string order:
 *                         wordOffset  Int32,  byte offset into the word-bytes blob below
 *                         wordLength  UInt16, byte length of the word's UTF-8 encoding
 *                         frequency   UInt16, the word's raw frequency, 0..65535 (this format
 *                                     has no clamp; the old format's clamp to 0..255, which
 *                                     saturated nearly every Norwegian and Ukrainian word,
 *                                     is gone)
 * ...     ...           the word-bytes blob: every word's UTF-8 bytes, concatenated in the
 *                       same order as the offset table, each one addressed by its record's
 *                       (wordOffset, wordLength)
 * ```
 *
 * The offset table is a flat, fixed-stride array, so a loader can binary-search it directly
 * (decoding only the two words it compares against at each step) or memory-map the whole file
 * and hand out `(word, frequency)` pairs on demand, without first decoding the file into a
 * tree of objects. This reader decodes eagerly into a [PbdDocument] for simplicity and
 * testability, but the layout itself does not require that of every reader.
 *
 * [read] never throws for a malformed file: it returns [PbdReadResult.Failed] with a
 * [PbdFormatError] describing exactly what was wrong, so a corrupt download or a build from a
 * future version leaves the keyboard without that dictionary rather than crashing it.
 */
object PbdReader {

    /**
     * The largest WORD block this reader will allocate for. The biggest real list is well
     * under a million entries; a count above this can only come from a damaged length field,
     * and refusing it here keeps a flipped bit from turning into a multi-gigabyte allocation.
     */
    internal const val MAX_ENTRIES = 1 shl 20

    /** The largest block payload this reader will buffer, for the same reason as [MAX_ENTRIES]. */
    internal const val MAX_BLOCK_BYTES = 32 shl 20

    /** Reads a complete `.pbd` file already held in memory. */
    fun read(bytes: ByteArray): PbdReadResult = read(DataInputStream(bytes.inputStream()))

    /** Reads a `.pbd` file from a stream, consuming it to the end (or to the first error). */
    fun read(input: InputStream): PbdReadResult = read(DataInputStream(input))

    /**
     * Every failure becomes a typed refusal. This runs on a start-up thread where an escaped
     * exception kills the keyboard process, so the last two catches are a safety net for any
     * damage the explicit checks in [readHeaderAndBody] did not anticipate.
     */
    private fun read(header: DataInputStream): PbdReadResult = try {
        readHeaderAndBody(header)
    } catch (e: EOFException) {
        PbdReadResult.Failed(PbdFormatError.Truncated)
    } catch (e: IOException) {
        PbdReadResult.Failed(PbdFormatError.Unreadable(e.message ?: e::class.java.simpleName))
    } catch (e: RuntimeException) {
        PbdReadResult.Failed(PbdFormatError.CorruptBlock(e.message ?: e::class.java.simpleName))
    }

    private fun readHeaderAndBody(header: DataInputStream): PbdReadResult {
        val magic = header.readInt()
        if (magic != PbdFormat.MAGIC) return PbdReadResult.Failed(PbdFormatError.BadMagic)

        val version = header.readUnsignedShort()
        if (version != PbdFormat.CURRENT_VERSION) {
            return PbdReadResult.Failed(PbdFormatError.UnsupportedVersion(version))
        }

        val langBytes = ByteArray(PbdFormat.LANGUAGE_FIELD_BYTES)
        header.readFully(langBytes)
        val langRaw = String(langBytes, Charsets.US_ASCII).trimEnd('\u0000')
        val language = LanguageCode.of(langRaw)
            ?: return PbdReadResult.Failed(PbdFormatError.InvalidLanguageCode(langRaw))

        header.readUnsignedShort() // reserved, ignored for forward compatibility
        val headerWordCount = header.readInt()
        val expectedChecksum = header.readInt().toLong() and 0xFFFFFFFFL

        // Everything from here on counts towards the checksum, so it is read through a
        // checksum-computing wrapper rather than the raw header stream.
        val crc = CRC32()
        val body = DataInputStream(CheckedInputStream(header, crc))

        var decodedWords: DecodedWordBlock? = null
        var structuralError: PbdFormatError? = null

        while (true) {
            val tag = readBlockTag(body) ?: break
            val length = body.readInt()
            if (length < 0) {
                // The frame cannot be trusted past this point, so the rest of the file is
                // drained through the checksum and the complaint waits its turn below.
                structuralError = PbdFormatError.CorruptBlock("block length $length is negative")
                drain(body)
                break
            }
            if (tag == PbdFormat.WORD_BLOCK_TAG && decodedWords == null) {
                when (val block = readWordBlock(body, length)) {
                    is WordBlockOutcome.Decoded -> {
                        decodedWords = block.words
                        if (block.words.words.size != headerWordCount) {
                            structuralError = PbdFormatError.WordCountMismatch(headerWordCount, block.words.words.size)
                        }
                    }
                    is WordBlockOutcome.Corrupt -> {
                        structuralError = block.error
                        drain(body)
                        break
                    }
                }
            } else {
                skipFully(body, length.toLong())
            }
        }

        // The checksum is the authoritative "is this file intact" answer: check it before any
        // more specific structural complaint, since a corrupt tag or length is exactly the
        // kind of damage the checksum exists to catch.
        val actualChecksum = crc.value
        if (actualChecksum != expectedChecksum) {
            return PbdReadResult.Failed(PbdFormatError.ChecksumMismatch(expectedChecksum, actualChecksum))
        }
        structuralError?.let { return PbdReadResult.Failed(it) }
        val words = decodedWords ?: return PbdReadResult.Failed(PbdFormatError.MissingWordBlock)

        val entries = List(words.words.size) { WordFrequency(words.words[it], words.frequencies[it]) }
        return PbdReadResult.Loaded(PbdDocument(language, version, entries))
    }

    /**
     * Reads the next block's tag, or null at a clean end of file. A tag cut off after one to
     * three bytes is a truncated file, not a checksum disagreement, so it is reported as such.
     */
    private fun readBlockTag(body: DataInputStream): Int? {
        val first = body.read()
        if (first < 0) return null
        var tag = first
        repeat(3) {
            val next = body.read()
            if (next < 0) throw EOFException()
            tag = (tag shl 8) or next
        }
        return tag
    }

    /**
     * Decodes a WORD block payload of [length] bytes, validating every count and offset
     * against the block's own bounds before allocating or indexing anything.
     */
    private fun readWordBlock(body: DataInputStream, length: Int): WordBlockOutcome {
        if (length < 4) return WordBlockOutcome.Corrupt(PbdFormatError.CorruptBlock("WORD block length $length is too short"))
        if (length > MAX_BLOCK_BYTES) {
            return WordBlockOutcome.Corrupt(PbdFormatError.CorruptBlock("WORD block length $length exceeds $MAX_BLOCK_BYTES"))
        }
        val blockCount = body.readInt()
        if (blockCount < 0 || blockCount > MAX_ENTRIES) {
            return WordBlockOutcome.Corrupt(PbdFormatError.CorruptBlock("WORD block entry count $blockCount is out of range"))
        }
        val tableBytes = 4L + blockCount * 8L
        if (tableBytes > length) {
            return WordBlockOutcome.Corrupt(
                PbdFormatError.CorruptBlock("WORD block entry count $blockCount does not fit its length $length"),
            )
        }
        val offsets = IntArray(blockCount)
        val lengths = IntArray(blockCount)
        val frequencies = IntArray(blockCount)
        for (i in 0 until blockCount) {
            offsets[i] = body.readInt()
            lengths[i] = body.readUnsignedShort()
            frequencies[i] = body.readUnsignedShort()
        }
        val blob = ByteArray((length - tableBytes).toInt())
        body.readFully(blob)
        for (i in 0 until blockCount) {
            val offset = offsets[i]
            if (offset < 0 || offset > blob.size - lengths[i]) {
                return WordBlockOutcome.Corrupt(
                    PbdFormatError.CorruptBlock("entry $i at offset $offset length ${lengths[i]} lies outside the word bytes"),
                )
            }
        }
        val words = Array(blockCount) { i -> String(blob, offsets[i], lengths[i], Charsets.UTF_8) }
        return WordBlockOutcome.Decoded(DecodedWordBlock(words, frequencies))
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) throw EOFException()
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    /** Consumes the rest of the stream so the checksum covers the whole file. */
    private fun drain(input: InputStream) {
        val scratch = ByteArray(8192)
        while (input.read(scratch) != -1) { /* counting only */ }
    }

    private class DecodedWordBlock(val words: Array<String>, val frequencies: IntArray)

    private sealed class WordBlockOutcome {
        class Decoded(val words: DecodedWordBlock) : WordBlockOutcome()
        class Corrupt(val error: PbdFormatError) : WordBlockOutcome()
    }
}
