package brobata.physiboard.core.dict

/**
 * Optimal-string-alignment Damerau-Levenshtein distance: insertion, deletion, substitution and
 * one adjacent transposition each cost 1 (this is OSA, not full Damerau-Levenshtein: a
 * substring may be transposed at most once, never re-edited afterwards). spec:
 * autocorrect-suggestions.md §3.4 ("Distance is optimal-string-alignment Damerau-Levenshtein").
 */
object EditDistance {

    /**
     * Computes the OSA distance between [a] and [b]. When the two strings' lengths already
     * differ by more than [maxDistance], `maxDistance + 1` is returned without doing the full
     * dynamic-programming pass, since the caller only needs to know the true distance exceeds
     * the bound, not what it exactly is.
     */
    fun osaDistance(a: String, b: String, maxDistance: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        val lenA = a.length
        val lenB = b.length
        if (kotlin.math.abs(lenA - lenB) > maxDistance) {
            return if (maxDistance == Int.MAX_VALUE) maxDistance else maxDistance + 1
        }
        if (lenA == 0) return lenB
        if (lenB == 0) return lenA

        // Three rolling rows of the OSA table: prevPrev is row i-2 (needed for the
        // transposition look-back), prev is row i-1, curr is the row being filled in.
        var prevPrev = IntArray(lenB + 1)
        var prev = IntArray(lenB + 1) { it }
        var curr = IntArray(lenB + 1)

        for (i in 1..lenA) {
            curr[0] = i
            for (j in 1..lenB) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    curr[j - 1] + 1, // insertion
                    prev[j] + 1, // deletion
                    prev[j - 1] + cost, // substitution (or match)
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, prevPrev[j - 2] + 1) // adjacent transposition
                }
                curr[j] = value
            }
            val recycled = prevPrev
            prevPrev = prev
            prev = curr
            curr = recycled
        }
        return prev[lenB]
    }
}
