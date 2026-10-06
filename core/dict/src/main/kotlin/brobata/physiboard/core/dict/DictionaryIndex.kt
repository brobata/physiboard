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

        // A one-letter prefix matches thousands of entries; only the best [limit] of them are
        // wanted, so they are selected through a bounded heap rather than sorted in full.
        val first = groupOffsets[start]
        val last = groupOffsets[end]
        val matchCount = last - first
        val selected: List<WordFrequency>
        if (matchCount <= limit) {
            val all = ArrayList<WordFrequency>(matchCount)
            for (i in first until last) all.add(WordFrequency(entryWords[i], entryFrequencies[i]))
            all.sortWith(BEST_FIRST)
            selected = all
        } else {
            val worstFirst = java.util.PriorityQueue(limit + 1, BEST_FIRST.reversed())
            for (i in first until last) {
                worstFirst.add(WordFrequency(entryWords[i], entryFrequencies[i]))
                if (worstFirst.size > limit) worstFirst.poll()
            }
            selected = worstFirst.toMutableList().apply { sortWith(BEST_FIRST) }
        }
        into.addAll(selected)
        return selected.size
    }

    /**
     * Appends up to [limit] of the highest-frequency entries in the whole dictionary, one per
     * normalized key (that key's own most-frequent spelling), into [into]. Returns the number
     * appended. Serves autocorrect-suggestions.md SS4's starter words: "the top 48 entries by
     * effective frequency across the primary dictionary". Uses the same bounded-heap technique as
     * [prefixLookup] so a caller does not pay for sorting the whole dictionary just to read off a
     * small head.
     */
    fun topByFrequency(limit: Int, into: MutableList<WordFrequency>): Int {
        if (limit <= 0) return 0
        // Answered once and remembered. The list this returns depends on nothing but the index,
        // which never changes, and working it out means walking every word in the dictionary:
        // on the maintainer's phone that was 140 ms, paid on every keystroke that emptied a word
        // and sent the strip looking for something to suggest next, which is why holding
        // Backspace crawled (2026-09-29).
        cachedTop?.let { cached ->
            if (cachedTopLimit >= limit) {
                val take = cached.take(limit)
                into.addAll(take)
                return take.size
            }
        }
        val worstFirst = java.util.PriorityQueue(limit + 1, BEST_FIRST.reversed())
        for (group in normalizedKeys.indices) {
            var bestIndex = groupOffsets[group]
            for (i in groupOffsets[group] until groupOffsets[group + 1]) {
                if (entryFrequencies[i] > entryFrequencies[bestIndex]) bestIndex = i
            }
            worstFirst.add(WordFrequency(entryWords[bestIndex], entryFrequencies[bestIndex]))
            if (worstFirst.size > limit) worstFirst.poll()
        }
        val selected = worstFirst.toMutableList().apply { sortWith(BEST_FIRST) }
        cachedTop = selected.toList()
        cachedTopLimit = limit
        into.addAll(selected)
        return selected.size
    }

    /** The last [topByFrequency] answer, reused while it is at least as long as what is asked for. */
    private var cachedTop: List<WordFrequency>? = null
    private var cachedTopLimit: Int = 0

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
            .sortedWith(BEST_FIRST)
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
     * This runs on every keystroke, so it must not visit the whole list. The sorted key array is
     * walked as if it were a trie: one distance-table row per character of the current key,
     * kept for the length of the common prefix with the next key (so "abandon" and "abandoned"
     * share seven rows), and whenever the smallest value in a row already exceeds
     * [maxDistance] every key sharing that prefix is skipped in one binary search, since no
     * extension of the prefix can get any closer. Keys longer than the query plus [maxDistance]
     * are skipped the same way; keys shorter than the query minus it are not scored. Together
     * that bounds the work to the few hundred prefixes within reach of the query rather than
     * the tens of thousands of keys in the length window, and to one scratch table per call
     * rather than three arrays per key. The results are exactly those of scoring every key.
     */
    fun neighbours(word: String, maxDistance: Int, limit: Int, into: MutableList<ScoredCandidate>): Int {
        if (limit <= 0 || maxDistance < 0) return 0
        val key = DictNormalization.normalizedKey(word)
        if (key.isEmpty() || normalizedKeys.isEmpty()) return 0

        val queryLength = key.length
        val minLength = maxOf(0, queryLength - maxDistance)
        val maxLength = minOf(lengthBucketStart.size - 2, queryLength + maxDistance)
        if (minLength > maxLength) return 0

        val walk = PrefixWalk(key, maxLength, maxDistance)
        val found = ArrayList<ScoredCandidate>()
        var i = 0
        var previous = ""
        var validDepth = 0
        while (i < normalizedKeys.size) {
            val candidate = normalizedKeys[i]
            var depth = minOf(commonPrefixLength(previous, candidate), validDepth)
            var prunedAt = -1
            while (depth < candidate.length) {
                if (depth == maxLength) {
                    // Every later key with this prefix is longer still.
                    prunedAt = depth
                    break
                }
                depth++
                walk.fillRow(depth, candidate)
                if (walk.rowMinimum(depth) > maxDistance) {
                    prunedAt = depth
                    break
                }
            }
            previous = candidate
            validDepth = depth
            if (prunedAt >= 0) {
                i = endOfPrefixRun(candidate, prunedAt, i + 1)
                continue
            }
            if (candidate.length >= minLength) {
                val distance = walk.distanceAt(depth)
                if (distance <= maxDistance) {
                    val bestIndex = groupOffsets[i]
                    found.add(ScoredCandidate(entryWords[bestIndex], entryFrequencies[bestIndex], distance))
                }
            }
            i++
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

    /**
     * The optimal-string-alignment table for one query against a growing candidate prefix, one
     * row per prefix length, in a single flat array reused for the whole walk. Row `d` holds the
     * distances of the candidate's first `d` characters against every prefix of the query, the
     * same recurrence as [EditDistance.osaDistance] (which is symmetric, so which string is
     * the row and which the column does not matter).
     *
     * Only the diagonal band `|d - j| <= maxDistance` is computed: a cell outside it is at least
     * its distance from the diagonal, so it can never be within reach, and the cell just outside
     * each end of the band holds `maxDistance + 1`, a lower bound on its true value that keeps
     * every result within reach exact (anything derived from it exceeds [maxDistance] too). That
     * makes a row cost the band's width, not the query's length.
     */
    private class PrefixWalk(private val query: String, maxDepth: Int, private val maxDistance: Int) {
        private val width = query.length + 1
        private val rows = IntArray((maxDepth + 1) * width)
        private val outOfReach = maxDistance + 1

        init {
            for (j in 0 until width) rows[j] = j
        }

        fun fillRow(depth: Int, candidate: String) {
            val current = depth * width
            val previous = current - width
            val beforePrevious = previous - width
            val candidateChar = candidate[depth - 1]
            rows[current] = depth
            val lo = maxOf(1, depth - maxDistance)
            val hi = minOf(width - 1, depth + maxDistance)
            if (lo > 1) rows[current + lo - 1] = outOfReach
            if (hi + 1 < width) rows[current + hi + 1] = outOfReach
            for (j in lo..hi) {
                val cost = if (query[j - 1] == candidateChar) 0 else 1
                var value = minOf(rows[current + j - 1] + 1, rows[previous + j] + 1, rows[previous + j - 1] + cost)
                if (depth > 1 && j > 1 && candidateChar == query[j - 2] && candidate[depth - 2] == query[j - 1]) {
                    value = minOf(value, rows[beforePrevious + j - 2] + 1)
                }
                rows[current + j] = value
            }
        }

        /** The smallest value in the band of row [depth] (column 0 included when the band reaches it); everything outside the band is out of reach. */
        fun rowMinimum(depth: Int): Int {
            val base = depth * width
            val lo = maxOf(1, depth - maxDistance)
            val hi = minOf(width - 1, depth + maxDistance)
            var minimum = if (lo == 1 || depth <= maxDistance) rows[base] else outOfReach
            for (j in lo..hi) if (rows[base + j] < minimum) minimum = rows[base + j]
            return minimum
        }

        fun distanceAt(depth: Int): Int = rows[depth * width + width - 1]
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var n = 0
        while (n < limit && a[n] == b[n]) n++
        return n
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

    /**
     * The first index at or after [from] whose key does not start with [prefix]. Keys are sorted,
     * so those with the prefix form one contiguous run from [from]; a binary search finds its end
     * without touching every key in a large run (a one-letter prefix covers thousands).
     */
    private fun upperBoundForPrefix(prefix: String, from: Int): Int {
        var lo = from
        var hi = normalizedKeys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (normalizedKeys[mid].startsWith(prefix)) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * The first index from [from] whose key does not start with [key]'s first [prefixLength]
     * characters: the end of the run [neighbours] prunes. Pruned runs are mostly short (a third or
     * fourth letter's worth of keys), so this gallops forward from [from] and binary-searches only
     * the last step, rather than bisecting the whole rest of the list from scratch; and it
     * compares in place, with no prefix string built per prune.
     */
    private fun endOfPrefixRun(key: String, prefixLength: Int, from: Int): Int {
        fun inRun(index: Int): Boolean = normalizedKeys[index].regionMatches(0, key, 0, prefixLength)
        val size = normalizedKeys.size
        var lo = from
        var step = 1
        // Invariant: every index below lo is in the run.
        while (lo < size && inRun(lo)) {
            val probe = lo + step
            if (probe >= size || !inRun(probe)) {
                var hi = minOf(probe, size)
                lo++
                while (lo < hi) {
                    val mid = (lo + hi) ushr 1
                    if (inRun(mid)) lo = mid + 1 else hi = mid
                }
                return lo
            }
            lo = probe + 1
            step = step shl 1
        }
        return lo
    }

    companion object {
        /** The order every ranked answer comes back in: highest raw frequency first, spelling as the tie-break. */
        private val BEST_FIRST: Comparator<WordFrequency> = compareByDescending<WordFrequency> { it.frequency }.thenBy { it.word }

        /** Builds an index from a decoded `.pbd` document. */
        fun from(document: PbdDocument): DictionaryIndex = build(document.language, document.entries)

        /**
         * Parses a `.pbd` byte stream straight into an index, or returns null when the bytes are
         * absent, truncated, or otherwise fail [PbdReader]'s checks (bad magic, unsupported
         * version, checksum mismatch, ...). [PbdReader.read] itself never throws for a malformed
         * file; this is the loading contract the running keyboard depends on to make a missing or
         * corrupt dictionary asset degrade to "no suggestions" rather than a crash. spec:
         * autocorrect-suggestions.md §2 point 3 ("the primary dictionary ... has finished
         * loading. If it has not, the load is scheduled in the background").
         */
        fun fromPbdBytes(bytes: ByteArray): DictionaryIndex? = when (val result = PbdReader.read(bytes)) {
            is PbdReadResult.Loaded -> from(result.document)
            is PbdReadResult.Failed -> null
        }

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
