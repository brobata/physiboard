package brobata.physiboard.core.dict

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * Produces a `.pbd` file from a word list. The only producer of the format the build pipeline
 * and the hosting repository are meant to use; see [PbdReader] for the byte layout this
 * writes.
 */
object PbdWriter {

    /** Writes [entries] for [language] and returns the encoded bytes. */
    fun write(language: LanguageCode, entries: List<WordFrequency>): ByteArray {
        val out = ByteArrayOutputStream()
        writeTo(out, language, entries)
        return out.toByteArray()
    }

    /**
     * Writes [entries] for [language] to [output] (left open; the caller closes it). Entries
     * are de-duplicated by exact spelling (a later entry for the same spelling overwrites an
     * earlier one), sorted into ascending natural string order, the on-disk order [PbdReader]
     * relies on for prefix and exact-key search, and written as a single `WORD` block; there
     * is no `BIGR` block yet, since no milestone produces bigram data to put in one.
     *
     * @throws IllegalArgumentException when a frequency does not fit the format's unsigned
     * 16-bit field, or a word's UTF-8 encoding is longer than 65,535 bytes.
     */
    fun writeTo(output: OutputStream, language: LanguageCode, entries: List<WordFrequency>) {
        val byWord = LinkedHashMap<String, Int>()
        for (entry in entries) {
            require(entry.frequency in 0..0xFFFF) {
                "frequency ${entry.frequency} for '${entry.word}' does not fit an unsigned 16-bit field"
            }
            byWord[entry.word] = entry.frequency
        }
        val sortedWords = byWord.keys.sorted()
        val wordBytes = sortedWords.map { word ->
            val bytes = word.toByteArray(Charsets.UTF_8)
            require(bytes.size <= 0xFFFF) { "word '$word' is too long to encode (${bytes.size} bytes)" }
            bytes
        }

        val block = ByteArrayOutputStream()
        DataOutputStream(block).apply {
            writeInt(sortedWords.size)
            var offset = 0
            for ((index, word) in sortedWords.withIndex()) {
                val bytes = wordBytes[index]
                writeInt(offset)
                writeShort(bytes.size)
                writeShort(byWord.getValue(word))
                offset += bytes.size
            }
            for (bytes in wordBytes) write(bytes)
            flush()
        }
        val blockBytes = block.toByteArray()

        // The checksum covers everything after the header as written to disk, which is the
        // WORD block's tag and length framing as well as its payload, not just the payload
        // (PbdReader computes it the same way, over every byte its CheckedInputStream sees).
        val body = ByteArrayOutputStream()
        DataOutputStream(body).apply {
            writeInt(PbdFormat.WORD_BLOCK_TAG)
            writeInt(blockBytes.size)
            write(blockBytes)
            flush()
        }
        val bodyBytes = body.toByteArray()

        val checksum = CRC32()
        checksum.update(bodyBytes)

        val data = DataOutputStream(output)
        data.writeInt(PbdFormat.MAGIC)
        data.writeShort(PbdFormat.CURRENT_VERSION)
        val langBytes = language.value.toByteArray(Charsets.US_ASCII)
        data.write(langBytes)
        repeat(PbdFormat.LANGUAGE_FIELD_BYTES - langBytes.size) { data.writeByte(0) }
        data.writeShort(0) // reserved, must be zero
        data.writeInt(sortedWords.size)
        data.writeInt(checksum.value.toInt())

        data.write(bodyBytes)
        data.flush()
    }
}
