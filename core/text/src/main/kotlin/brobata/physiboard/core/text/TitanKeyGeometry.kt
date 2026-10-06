package brobata.physiboard.core.text

import kotlin.math.sqrt

/**
 * Where the letter keys sit on the Titan 2 Elite, for the per-edit costs of the noisy-channel
 * correction ([ChannelCost]). spec: autocorrect-suggestions.md §16 W2 ("deriving adjacency from
 * the layout row strings `qwertyuiop`, `asdfghjkl`, `zxcvbnm` rather than the hand-coded grid")
 * and §14 D3 (the three letter rows). The rows are staggered half a key each, the way the Titan's
 * keys actually sit, so `s` is as close to `w` and `e` as to `a` and `d`; the hand-coded grid in
 * [QwertyGrid] (straight columns, a gap for the space bar) stays for the proximity filter the
 * older correction path still applies, since that filter's threshold was tuned against it.
 *
 * A character off the grid (a digit, an apostrophe, an accented letter) has no position; a cost
 * that asks about it falls back to the "far key" price, which is the honest answer for a slip
 * the geometry cannot explain.
 *
 * QWERTY only: the rows are fixed letter strings, not read from the active layout. On a QWERTZ or
 * AZERTY layout the letters the layout moves (`y`/`z`; `a`/`q`, `z`/`w`, `m`) are priced where
 * QWERTY puts them, so a slip onto the key physically beside them costs a far-key substitution
 * and one onto a key that is beside them only on QWERTY costs an adjacent one. Every other letter
 * is priced right. Only English has a word-pair table today, so this only reaches English typed
 * on those layouts; the path without a table remaps its own grid per layout (autocorrect-
 * suggestions.md §3.5). docs/plans/autocorrect-context.md records it as a known limit.
 */
object TitanKeyGeometry {
    val rows: List<String> = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val stagger = doubleArrayOf(0.0, 0.5, 1.0)

    private val xs = DoubleArray(26) { Double.NaN }
    private val ys = DoubleArray(26) { Double.NaN }

    init {
        for ((row, letters) in rows.withIndex()) {
            for ((column, letter) in letters.withIndex()) {
                xs[letter - 'a'] = column + stagger[row]
                ys[letter - 'a'] = row.toDouble()
            }
        }
    }

    /** Two keys that touch, straight or diagonally (a half-key stagger makes a diagonal neighbour about 1.12 keys away). */
    const val ADJACENT: Double = 1.15

    /** Euclidean distance in key widths, or null when either character is not a letter on the grid. */
    fun distance(a: Char, b: Char): Double? {
        val ia = index(a) ?: return null
        val ib = index(b) ?: return null
        val dx = xs[ia] - xs[ib]
        val dy = ys[ia] - ys[ib]
        return sqrt(dx * dx + dy * dy)
    }

    /** Whether [a] and [b] are distinct keys that touch on the grid. */
    fun areAdjacent(a: Char, b: Char): Boolean {
        val d = distance(a, b) ?: return false
        return d > 0.0 && d <= ADJACENT
    }

    /** Every letter that touches [letter] on the grid, in row order; empty for a character off the grid. */
    fun neighbours(letter: Char): List<Char> {
        if (index(letter) == null) return emptyList()
        val found = ArrayList<Char>(8)
        for (row in rows) for (other in row) if (areAdjacent(letter, other)) found.add(other)
        return found
    }

    private fun index(c: Char): Int? {
        val lower = c.lowercaseChar()
        if (lower !in 'a'..'z') return null
        val i = lower - 'a'
        return if (xs[i].isNaN()) null else i
    }
}
