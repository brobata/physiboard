package brobata.physiboard.core.dict

/**
 * The keyboard's per-keystroke query surface over one loaded dictionary: exact membership,
 * prefix completion and edit-distance neighbourhood retrieval, each returning raw frequencies
 * for `:core:text` to score. Built once when a `.pbd` is loaded ([from]); every query method
 * here is total (no exceptions, no unmatched case) and allocates nothing proportional to the
 * dictionary's size, only to the query itself and to the results handed back. spec:
 * autocorrect-suggestions.md §3 (what candidate retrieval must answer; scoring itself and the
 * correction decision are `:core:text`'s) and §10 (the known-word membership check that
 * protects a correctly spelled word from being overwritten).
 *
 * Sorting by raw frequency descending is equivalent to sorting by the effective frequency
 * `(raw / 255) ^ 0.75 * 1600` from autocorrect-suggestions.md §3.2, because that mapping is
 * monotonically increasing in `raw`. Results below are ordered by raw frequency so a caller
 * does not need this index to duplicate the scoring formula to get the right order.
 */
class DictionaryIndex private constructor(
    val language: LanguageCode,
    private val normalizedKeys: Array<String>,
    private val groupOffsets: IntArray,
    private val entryWords: Array<String>,
    private val entryFrequencies: IntArray,
    private val lengthOrder: IntArray,
    private val lengthBucketStart: IntArray,
) {
    /** Number of distinct spellings held, across every normalized key. */
    val wordCount: Int get() = entryWords.size

    /**
     * Whether [word] is a known word: some entry's normalized key equals [word]'s normalized
     * key. This is the check that protects a correctly spelled word from autocorrection.
     * spec: autocorrect-suggestions.md §10.
     */
    fun contains(word: String): Boolean = groupIndexOf(DictNormalization.normalizedKey(word)) >= 0

    /**
     * The highest raw frequency among entries sharing [word]'s normalized key, or 0 when the
     * word is not known. Total: never throws. A caller that must distinguish "known with
     * frequency 0" from "unknown" should use [contains] instead; in practice a build pipeline
     * has no reason to emit a zero-frequency entry.
     */
    fun frequencyOf(word: String): Int {
        val group = groupIndexOf(DictNormalization.normalizedKey(word))
        return if (group < 0) 0 else entryFrequencies[groupOffsets[group]]
    }

    /**
     * Appends every entry whose normalized key starts with [prefix]'s normalized key, most
     * frequent first, up to [limit], into [into]. Returns the number appended. An empty or
     * all-punctuation [prefix] normalizes to the empty key and matches nothing, since no real
     * dictionary word normalizes to it either. spec: autocorrect-suggestions.md §3.4
     * (completions); the specific "up to 200, fall back to a shorter prefix" policy there is
     * call-site composition over this primitive, not part of this index.
     */
    fun prefixLookup(prefix: String, limit: Int, into: MutableList<WordFrequency>): Int {
        if (limit <= 0) return 0
        val key = DictNormalization.normalizedKey(prefix)
        if (key.isEmpty()) return 0
        val start = lowerBound(key)
        val end = upperBoundForPrefix(key, start)
        if (start >= end) return 0

        val candidates = ArrayList<WordFrequency>(minOf(limit, entryWords.size))
        for (g in start until end) {
            for (i in groupOffsets[g] until groupOffsets[g + 1]) {
                candidates.add(WordFrequency(entryWords[i], entryFrequencies[i]))
            }
        }
        candidates.sortWith(compareByDescending<WordFrequency> { it.frequency }.thenBy { it.word })
        var added = 0
        for (candidate in candidates) {
            if (added >= limit) break
            into.add(candidate)
            added++
        }
        return added
    }

    /**
     * Appends every entry under the exact normalized key of [word] (the case and accent
     * variants of one spelling, such as `perche`, `perché`, `Perché`), most frequent first, up
     * to [limit], into [into]. Returns the number appended. Serves the single-letter cases of
     * autocorrect-suggestions.md §3.4 ("the top 5 entries for that key").
     */
    fun entriesForExactKey(word: String, limit: Int, into: MutableList<WordFrequency>): Int {
        if (limit <= 0) return 0
        val group = groupIndexOf(DictNormalization.normalizedKey(word))
        if (group < 0) return 0
        val entries = (groupOffsets[group] until groupOffsets[group + 1])
            .map { WordFrequency(entryWords[it], entryFrequencies[it]) }
            .sortedWith(compareByDescending<WordFrequency> { it.frequency }.thenBy { it.word })
        var added = 0
        for (entry in entries) {
            if (added >= limit) break
            into.add(entry)
            added++
        }
        return added
    }

    /**
     * Appends every normalized key within [maxDistance] of [word]'s normalized key, sorted by
     * distance ascending, then raw frequency descending, then spelling length ascending, up to
     * [limit], into [into]. Returns the number appended. Only the single highest-frequency
     * entry of each matching key is returned, since that is all autocorrect-suggestions.md
     * §3.5 keeps once distance is greater than 0. Distance is [EditDistance.osaDistance].
     * spec: autocorrect-suggestions.md §3.4.
     *
     * Candidates are restricted up front to normalized keys whose length is within
     * [maxDistance] of [word]'s, since no edit sequence shorter than that can bridge a larger
     * length gap; this keeps a query proportional to the words near the typed length rather
     * than the whole dictionary.
     */
    fun neighbours(word: String, maxDistance: Int, limit: Int, into: MutableList<ScoredCandidate>): Int {
        if (limit <= 0 || maxDistance < 0) return 0
        val key = DictNormalization.normalizedKey(word)
        if (key.isEmpty()) return 0

        val maxKnownLength = lengthBucketStart.size - 2
        val minLen = maxOf(0, key.length - maxDistance)
        val maxLen = minOf(maxKnownLength, key.length + maxDistance)
        if (minLen > maxLen) return 0

        val found = ArrayList<ScoredCandidate>()
        for (len in minLen..maxLen) {
            for (i in lengthBucketStart[len] until lengthBucketStart[len + 1]) {
                val group = lengthOrder[i]
                val distance = EditDistance.osaDistance(key, normalizedKeys[group], maxDistance)
                if (distance > maxDistance) continue
                val bestIndex = groupOffsets[group]
                found.add(ScoredCandidate(entryWords[bestIndex], entryFrequencies[bestIndex], distance))
            }
        }
        found.sortWith(
            compareBy<ScoredCandidate> { it.distance }
                .thenByDescending { it.frequency }
                .thenBy { it.word.length },
        )
        var added = 0
        for (candidate in found) {
            if (added >= limit) break
            into.add(candidate)
            added++
        }
        return added
    }

    private fun groupIndexOf(key: String): Int {
        var lo = 0
        var hi = normalizedKeys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = normalizedKeys[mid].compareTo(key)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = normalizedKeys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (normalizedKeys[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun upperBoundForPrefix(prefix: String, from: Int): Int {
        var i = from
        while (i < normalizedKeys.size && normalizedKeys[i].startsWith(prefix)) i++
        return i
    }

    companion object {
        /** Builds an index from a decoded `.pbd` document. */
        fun from(document: PbdDocument): DictionaryIndex = build(document.language, document.entries)

        /** Builds an index directly from a word list, for tests and for composing sources. */
        fun build(language: LanguageCode, entries: List<WordFrequency>): DictionaryIndex {
            val byKey = LinkedHashMap<String, MutableList<WordFrequency>>()
            for (entry in entries) {
                val key = DictNormalization.normalizedKey(entry.word)
                byKey.getOrPut(key) { mutableListOf() }.add(entry)
            }
            val sortedKeys = byKey.keys.sorted()

            val groupOffsets = IntArray(sortedKeys.size + 1)
            val words = ArrayList<String>(entries.size)
            val freqs = ArrayList<Int>(entries.size)
            for ((index, key) in sortedKeys.withIndex()) {
                groupOffsets[index] = words.size
                val group = byKey.getValue(key)
                    .sortedWith(compareByDescending<WordFrequency> { it.frequency }.thenBy { it.word })
                for (item in group) {
                    words.add(item.word)
                    freqs.add(item.frequency)
                }
            }
            groupOffsets[sortedKeys.size] = words.size

            val maxLength = sortedKeys.maxOfOrNull { it.length } ?: 0
            val lengthCounts = IntArray(maxLength + 1)
            for (key in sortedKeys) lengthCounts[key.length]++
            val lengthBucketStart = IntArray(maxLength + 2)
            for (len in lengthCounts.indices) lengthBucketStart[len + 1] = lengthBucketStart[len] + lengthCounts[len]
            val cursor = lengthBucketStart.copyOf()
            val lengthOrder = IntArray(sortedKeys.size)
            for ((groupIndex, key) in sortedKeys.withIndex()) {
                val pos = cursor[key.length]
                lengthOrder[pos] = groupIndex
                cursor[key.length] = pos + 1
            }

            return DictionaryIndex(
                language = language,
                normalizedKeys = sortedKeys.toTypedArray(),
                groupOffsets = groupOffsets,
                entryWords = words.toTypedArray(),
                entryFrequencies = freqs.toIntArray(),
                lengthOrder = lengthOrder,
                lengthBucketStart = lengthBucketStart,
            )
        }
    }
}
