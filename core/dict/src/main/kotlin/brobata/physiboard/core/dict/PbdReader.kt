package brobata.physiboard.core.dict

import java.io.DataInputStream
import java.io.EOFException
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

    /** Reads a complete `.pbd` file already held in memory. */
    fun read(bytes: ByteArray): PbdReadResult = read(DataInputStream(bytes.inputStream()))

    /** Reads a `.pbd` file from a stream, consuming it to the end (or to the first error). */
    fun read(input: InputStream): PbdReadResult = read(DataInputStream(input))

    private fun read(header: DataInputStream): PbdReadResult = try {
        readHeaderAndBody(header)
    } catch (e: EOFException) {
        PbdReadResult.Failed(PbdFormatError.Truncated)
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
            val tag = try {
                body.readInt()
            } catch (e: EOFException) {
                break
            }
            val length = body.readInt()
            if (tag == PbdFormat.WORD_BLOCK_TAG && decodedWords == null) {
                val blockCount = body.readInt()
                val offsets = IntArray(blockCount)
                val lengths = IntArray(blockCount)
                val frequencies = IntArray(blockCount)
                for (i in 0 until blockCount) {
                    offsets[i] = body.readInt()
                    lengths[i] = body.readUnsignedShort()
                    frequencies[i] = body.readUnsignedShort()
                }
                val blob = ByteArray(length - 4 - blockCount * 8)
                body.readFully(blob)
                val words = Array(blockCount) { i -> String(blob, offsets[i], lengths[i], Charsets.UTF_8) }
                decodedWords = DecodedWordBlock(words, frequencies)
                if (blockCount != headerWordCount) {
                    structuralError = PbdFormatError.WordCountMismatch(headerWordCount, blockCount)
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

    private class DecodedWordBlock(val words: Array<String>, val frequencies: IntArray)
}
