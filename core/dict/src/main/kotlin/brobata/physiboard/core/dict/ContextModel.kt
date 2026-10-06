package brobata.physiboard.core.dict

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.math.ln

/**
 * The word-pair table autocorrect reads the sentence with: how often each dictionary word
 * follows another, counted from Tatoeba's English sentences (CC BY 2.0 FR) by
 * `scripts/build_bigrams.py`, which also documents the byte layout this reads. spec:
 * autocorrect-suggestions.md §16 W5 (the context prior) and docs/dictionaries.md, "The word-pair
 * table".
 *
 * Everything is held in primitive arrays: the words as one UTF-8 blob with an offset per id, an
 * id permutation sorted by word bytes for the lookup (a binary search over the blob, no boxed
 * map), the unigram counts, and the pairs as a CSR matrix (a row offset per id, then the next ids
 * ascending within each row with their counts). The real English table is 53,000 words and
 * 500,000 pairs, about 5 MB resident; building it is a few array copies and one sort.
 *
 * Probabilities are interpolated Kneser-Ney with absolute discounting ([DISCOUNT]): the pair
 * count less the discount over the row total, plus the discount mass the row gave up spread over
 * the continuation distribution (how many distinct words each word follows). That lower order is
 * what makes a word that only ever follows one other word ("francisco") cheap after it and dear
 * anywhere else, which a plain unigram backoff cannot say; the additive floor ([FLOOR]) keeps a
 * dictionary word the table never saw at a small, finite probability rather than zero, so a
 * candidate outside the table still ranks, just last. One parameter, no tuning table, and the
 * backoff weight grows on its own for a sparse context.
 *
 * [read] never throws for a malformed file: a bad magic, version, checksum or any offset outside
 * the file yields null, and the keyboard corrects without context exactly as it did before the
 * table existed.
 */
class ContextModel private constructor(
    private val wordBytes: ByteArray,
    /** Byte offset of each id's word in [wordBytes]; size V + 1 so `offsets[id + 1]` ends the last word. Id 0 (sentence start) is empty. */
    private val wordOffsets: IntArray,
    /** Ids 1..V-1 ordered by their word bytes, for the binary search in [idOf]. */
    private val sortedIds: IntArray,
    private val unigrams: IntArray,
    private val rowOffsets: IntArray,
    private val nextIds: IntArray,
    private val counts: ShortArray,
    private val rowTotals: IntArray,
    private val continuations: IntArray,
) {
    /** Number of ids, the sentence-start context included. */
    val vocabularySize: Int get() = unigrams.size

    /** Number of stored pairs. */
    val pairCount: Int get() = nextIds.size

    private val totalUnigrams: Long = unigrams.fold(0L) { acc, c -> acc + c } - unigrams[0]

    /** The id of [word] (lowercase, straight apostrophe, as the builder tokenised it), or [NO_CONTEXT] when the table never saw it. */
    fun idOf(word: String): Int {
        if (word.isEmpty()) return NO_CONTEXT
        val query = word.toByteArray(Charsets.UTF_8)
        var low = 0
        var high = sortedIds.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val id = sortedIds[mid]
            val cmp = compareWord(id, query)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return id
            }
        }
        return NO_CONTEXT
    }

    /** The word for [id], or "" for the sentence-start context and any id out of range. */
    fun wordOf(id: Int): String {
        if (id <= 0 || id >= wordOffsets.size - 1) return ""
        return String(wordBytes, wordOffsets[id], wordOffsets[id + 1] - wordOffsets[id], Charsets.UTF_8)
    }

    /** How many times [nextId] followed [previousId], 0 when never or when either is [NO_CONTEXT]. */
    fun pairCount(previousId: Int, nextId: Int): Int {
        if (previousId < 0 || previousId >= rowOffsets.size - 1 || nextId < 0) return 0
        val index = rowIndexOf(previousId, nextId)
        return if (index < 0) 0 else counts[index].toInt() and 0xFFFF
    }

    /** The unigram count of [id], 0 for [NO_CONTEXT]; for [SENTENCE_START] the number of sentences counted. */
    fun unigramCount(id: Int): Int = if (id < 0 || id >= unigrams.size) 0 else unigrams[id]

    /**
     * `ln P(next | previous)`. [previousId] is [SENTENCE_START], a word id, or [NO_CONTEXT] (the
     * previous word is unknown to the table, or there is no usable one), which falls back to the
     * unigram distribution. [nextId] may be [NO_CONTEXT] for a word outside the table, which gets
     * the floor probability.
     */
    fun logProbability(previousId: Int, nextId: Int): Double {
        val hasRow = previousId >= 0 && previousId < rowOffsets.size - 1 && rowTotals[previousId] > 0
        if (!hasRow) return ln(unigramProbability(nextId))
        val total = rowTotals[previousId]
        val index = if (nextId < 0) -1 else rowIndexOf(previousId, nextId)
        val count = if (index < 0) 0.0 else (counts[index].toInt() and 0xFFFF).toDouble()
        val distinct = rowOffsets[previousId + 1] - rowOffsets[previousId]
        val direct = maxOf(count - DISCOUNT, 0.0) / total
        val backoffWeight = DISCOUNT * distinct / total
        return ln(direct + backoffWeight * continuationProbability(nextId))
    }

    /** `ln P(next | previous)` by words; both must already be lowercase with straight apostrophes. */
    fun logProbability(previousWord: String?, nextWord: String): Double =
        logProbability(if (previousWord == null) NO_CONTEXT else idOf(previousWord), idOf(nextWord))

    private fun unigramProbability(id: Int): Double {
        val count = if (id <= 0 || id >= unigrams.size) 0 else unigrams[id]
        return (count + FLOOR) / (totalUnigrams + FLOOR * unigrams.size)
    }

    private fun continuationProbability(id: Int): Double {
        val count = if (id <= 0 || id >= continuations.size) 0 else continuations[id]
        return (count + FLOOR) / (nextIds.size + FLOOR * continuations.size)
    }

    private fun rowIndexOf(previousId: Int, nextId: Int): Int {
        var low = rowOffsets[previousId]
        var high = rowOffsets[previousId + 1] - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = nextIds[mid]
            when {
                value < nextId -> low = mid + 1
                value > nextId -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun compareWord(id: Int, query: ByteArray): Int {
        val start = wordOffsets[id]
        val end = wordOffsets[id + 1]
        var i = start
        var j = 0
        while (i < end && j < query.size) {
            val a = wordBytes[i].toInt() and 0xFF
            val b = query[j].toInt() and 0xFF
            if (a != b) return a - b
            i++
            j++
        }
        return (end - start) - query.size
    }

    companion object {
        /** The id of the sentence-start context: what precedes the first word of a sentence. */
        const val SENTENCE_START: Int = 0

        /** "No usable previous word": a word the table never saw, or something other than a word before the cursor. */
        const val NO_CONTEXT: Int = -1

        /** Absolute discount per seen pair; 0.75 is the textbook value and the table is large enough not to need an estimate. */
        const val DISCOUNT: Double = 0.75

        /** The additive floor that keeps an unseen word above zero probability. */
        const val FLOOR: Double = 0.5

        private const val MAGIC = 0x50424731
        private const val VERSION = 1
        private const val MAX_VOCABULARY = 1 shl 20
        private const val MAX_PAIRS = 1 shl 24

        /** Parses a whole `.bigrams` file; null for anything that is not an intact, version-1 table. */
        fun read(bytes: ByteArray): ContextModel? = try {
            parse(bytes)
        } catch (e: BufferUnderflowException) {
            null
        } catch (e: IndexOutOfBoundsException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

        private fun parse(bytes: ByteArray): ContextModel? {
            if (bytes.size < 4 + 2 + 2 + 4 + 4) return null
            val crc = CRC32()
            crc.update(bytes, 0, bytes.size - 4)
            val buffer = ByteBuffer.wrap(bytes)
            val expectedCrc = buffer.getInt(bytes.size - 4).toLong() and 0xFFFFFFFFL
            if (crc.value != expectedCrc) return null

            if (buffer.getInt() != MAGIC) return null
            if ((buffer.getShort().toInt() and 0xFFFF) != VERSION) return null
            buffer.getShort() // reserved
            val vocabulary = buffer.getInt()
            if (vocabulary < 1 || vocabulary > MAX_VOCABULARY) return null

            val wordOffsets = IntArray(vocabulary + 1)
            val wordStart = buffer.position() // the lengths are interleaved with the bytes, so the blob is copied out below
            var blobSize = 0
            for (id in 1 until vocabulary) {
                val length = buffer.getShort().toInt() and 0xFFFF
                blobSize += length
                buffer.position(buffer.position() + length)
            }
            val wordBytes = ByteArray(blobSize)
            buffer.position(wordStart)
            var out = 0
            for (id in 1 until vocabulary) {
                val length = buffer.getShort().toInt() and 0xFFFF
                wordOffsets[id] = out
                buffer.get(wordBytes, out, length)
                out += length
            }
            wordOffsets[vocabulary] = out

            val unigrams = IntArray(vocabulary)
            for (id in 0 until vocabulary) {
                unigrams[id] = buffer.getInt()
                if (unigrams[id] < 0) return null
            }

            val rowOffsets = IntArray(vocabulary + 1)
            for (i in 0..vocabulary) {
                rowOffsets[i] = buffer.getInt()
                if (rowOffsets[i] < 0 || (i > 0 && rowOffsets[i] < rowOffsets[i - 1])) return null
            }
            if (rowOffsets[0] != 0) return null
            val pairs = rowOffsets[vocabulary]
            if (pairs > MAX_PAIRS) return null
            if (buffer.remaining() < pairs * 6L + 4) return null

            val nextIds = IntArray(pairs)
            val continuations = IntArray(vocabulary)
            for (row in 0 until vocabulary) {
                var previous = -1
                for (i in rowOffsets[row] until rowOffsets[row + 1]) {
                    val id = buffer.getInt()
                    if (id <= 0 || id >= vocabulary || id <= previous) return null
                    nextIds[i] = id
                    continuations[id]++
                    previous = id
                }
            }
            val counts = ShortArray(pairs)
            val rowTotals = IntArray(vocabulary)
            for (row in 0 until vocabulary) {
                var total = 0L
                for (i in rowOffsets[row] until rowOffsets[row + 1]) {
                    val count = buffer.getShort()
                    counts[i] = count
                    total += count.toInt() and 0xFFFF
                }
                if (total > Int.MAX_VALUE) return null
                rowTotals[row] = total.toInt()
            }
            if (buffer.remaining() != 4) return null

            val sortedIds = IntArray(maxOf(0, vocabulary - 1)) { it + 1 }
            sortByWord(sortedIds, wordBytes, wordOffsets)
            return ContextModel(wordBytes, wordOffsets, sortedIds, unigrams, rowOffsets, nextIds, counts, rowTotals, continuations)
        }

        /** A bottom-up merge sort over ids by their word bytes: the standard library sorts an IntArray only numerically, and boxing 53,000 ids for a comparator is what this class exists to avoid. */
        private fun sortByWord(ids: IntArray, blob: ByteArray, offsets: IntArray) {
            fun compare(a: Int, b: Int): Int {
                var i = offsets[a]
                var j = offsets[b]
                val endA = offsets[a + 1]
                val endB = offsets[b + 1]
                while (i < endA && j < endB) {
                    val x = blob[i].toInt() and 0xFF
                    val y = blob[j].toInt() and 0xFF
                    if (x != y) return x - y
                    i++
                    j++
                }
                return (endA - offsets[a]) - (endB - offsets[b])
            }
            var source = ids
            var target = IntArray(ids.size)
            var width = 1
            while (width < ids.size) {
                var start = 0
                while (start < ids.size) {
                    val middle = minOf(start + width, ids.size)
                    val end = minOf(start + 2 * width, ids.size)
                    var i = start
                    var j = middle
                    var k = start
                    while (i < middle && j < end) {
                        target[k++] = if (compare(source[i], source[j]) <= 0) source[i++] else source[j++]
                    }
                    while (i < middle) target[k++] = source[i++]
                    while (j < end) target[k++] = source[j++]
                    start = end
                }
                val swap = source
                source = target
                target = swap
                width *= 2
            }
            if (source !== ids) System.arraycopy(source, 0, ids, 0, ids.size)
        }
    }
}
