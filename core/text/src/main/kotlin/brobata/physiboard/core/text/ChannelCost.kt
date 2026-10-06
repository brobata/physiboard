package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictNormalization

/**
 * How costly it is to have typed [typed] when [intended] was meant: the "channel" half of the
 * noisy-channel correction ([ContextCorrection]). spec: autocorrect-suggestions.md §16 W2, whose
 * table of per-edit costs this starts from, tuned against the sentence harness (§12):
 *
 * | Slip | Cost |
 * |---|---|
 * | substitution, keys touching | [Weights.adjacentSubstitution] |
 * | substitution, one key between | [Weights.nearSubstitution] |
 * | substitution, one vowel for another | [Weights.vowelSubstitution] (spelling, not fingers: `definately`) |
 * | substitution, anything farther | [Weights.farSubstitution] |
 * | two neighbouring letters swapped | [Weights.transposition] |
 * | a letter left out | [Weights.droppedLetter], [Weights.droppedDouble] when it was half of a double |
 * | a letter typed twice | [Weights.doubledLetter] |
 * | an extra key beside one pressed on purpose | [Weights.adjacentInsertion] |
 * | any other extra letter | [Weights.otherInsertion] |
 * | the first letter differs (not by swapping the first two) | + [Weights.firstLetter] |
 *
 * Distances come from the Titan's own staggered rows ([TitanKeyGeometry]). The comparison is on
 * lowercase letters with accents and apostrophes set aside; an apostrophe the dictionary spelling
 * has and the typed word lacks costs [Weights.apostropheAdded] (the Titan's apostrophe needs a
 * modifier, so it is the commonest thing left out), the reverse [Weights.apostropheRemoved];
 * accents likewise ([Weights.accentAdded], [Weights.accentRemoved]).
 *
 * The alignment is the optimal-string-alignment recurrence the fuzzy index uses, with these
 * weights in place of 1 for every edit, so the cheapest explanation of the slip is the one priced.
 */
object ChannelCost {

    data class Weights(
        val adjacentSubstitution: Double = 0.35,
        val nearSubstitution: Double = 0.8,
        val vowelSubstitution: Double = 0.9,
        val farSubstitution: Double = 1.6,
        val transposition: Double = 0.4,
        val droppedLetter: Double = 0.5,
        val droppedDouble: Double = 0.3,
        val doubledLetter: Double = 0.3,
        val adjacentInsertion: Double = 0.45,
        val otherInsertion: Double = 0.9,
        val firstLetter: Double = 0.8,
        val apostropheAdded: Double = 0.15,
        val apostropheRemoved: Double = 0.6,
        val accentAdded: Double = 0.15,
        val accentRemoved: Double = 0.6,
    )

    val DEFAULT: Weights = Weights()

    /** Keys at most this far apart (in key widths) are "one key between": two apart on a row is 2.0, a diagonal two away about 2.06. */
    private const val NEAR: Double = 2.25

    private const val VOWELS = "aeiouy"

    /** The cost of the slip from [intended] to [typed]; 0.0 when they are the same letters. */
    fun cost(typed: String, intended: String, weights: Weights = DEFAULT): Double {
        val t = letters(typed)
        val c = letters(intended)
        var total = alignment(t, c, weights)
        // A changed first letter is the costliest kind of slip to believe (`ding` is not `fing`),
        // but swapping the first two letters still typed the first one, just a beat late.
        val firstTwoSwapped = t.length >= 2 && c.length >= 2 && t[0] == c[1] && t[1] == c[0]
        if (t.isNotEmpty() && c.isNotEmpty() && t[0] != c[0] && !firstTwoSwapped) total += weights.firstLetter
        val typedApostrophes = typed.count { WordChars.isApostrophe(it) }
        val intendedApostrophes = intended.count { WordChars.isApostrophe(it) }
        if (intendedApostrophes > typedApostrophes) total += weights.apostropheAdded * (intendedApostrophes - typedApostrophes)
        if (typedApostrophes > intendedApostrophes) total += weights.apostropheRemoved * (typedApostrophes - intendedApostrophes)
        val typedAccents = accents(typed)
        val intendedAccents = accents(intended)
        if (intendedAccents > typedAccents) total += weights.accentAdded * (intendedAccents - typedAccents)
        if (typedAccents > intendedAccents) total += weights.accentRemoved * (typedAccents - intendedAccents)
        return total
    }

    private fun accents(word: String): Int = word.count { it.isLetter() && it.code >= 0x80 && !WordChars.isApostrophe(it) }

    /** Lowercase letters only, accents removed: the alphabet the geometry knows. */
    private fun letters(word: String): String {
        val plain = DictNormalization.stripAccents(word.lowercase())
        return buildString(plain.length) { for (ch in plain) if (ch.isLetter()) append(ch) }
    }

    /** `d[i][j]`: the cheapest way the first `j` intended letters came out as the first `i` typed ones. */
    private fun alignment(t: String, c: String, w: Weights): Double {
        val rows = t.length + 1
        val cols = c.length + 1
        val d = DoubleArray(rows * cols)
        for (j in 1 until cols) d[j] = d[j - 1] + dropCost(c, j - 1, w)
        for (i in 1 until rows) {
            d[i * cols] = d[(i - 1) * cols] + insertCost(t, i - 1, w)
            for (j in 1 until cols) {
                val typedCh = t[i - 1]
                val intendedCh = c[j - 1]
                var best = d[(i - 1) * cols + j - 1] + if (typedCh == intendedCh) 0.0 else substitutionCost(intendedCh, typedCh, w)
                best = minOf(best, d[i * cols + j - 1] + dropCost(c, j - 1, w))
                best = minOf(best, d[(i - 1) * cols + j] + insertCost(t, i - 1, w))
                if (i >= 2 && j >= 2 && typedCh == c[j - 2] && t[i - 2] == intendedCh && typedCh != intendedCh) {
                    best = minOf(best, d[(i - 2) * cols + j - 2] + w.transposition)
                }
                d[i * cols + j] = best
            }
        }
        return d[rows * cols - 1]
    }

    private fun substitutionCost(intended: Char, typed: Char, w: Weights): Double {
        val distance = TitanKeyGeometry.distance(intended, typed)
        val byGeometry = when {
            distance == null -> w.farSubstitution
            distance <= TitanKeyGeometry.ADJACENT -> w.adjacentSubstitution
            distance <= NEAR -> w.nearSubstitution
            else -> w.farSubstitution
        }
        return if (intended in VOWELS && typed in VOWELS) minOf(byGeometry, w.vowelSubstitution) else byGeometry
    }

    /** The intended letter at [index] was never typed. */
    private fun dropCost(c: String, index: Int, w: Weights): Double {
        val ch = c[index]
        val partOfDouble = c.getOrNull(index - 1) == ch || c.getOrNull(index + 1) == ch
        return if (partOfDouble) w.droppedDouble else w.droppedLetter
    }

    /** The typed letter at [index] was not meant at all. */
    private fun insertCost(t: String, index: Int, w: Weights): Double {
        val ch = t[index]
        val before = t.getOrNull(index - 1)
        val after = t.getOrNull(index + 1)
        if (before == ch || after == ch) return w.doubledLetter
        val besideAKey = (before != null && TitanKeyGeometry.areAdjacent(ch, before)) || (after != null && TitanKeyGeometry.areAdjacent(ch, after))
        return if (besideAKey) w.adjacentInsertion else w.otherInsertion
    }
}
