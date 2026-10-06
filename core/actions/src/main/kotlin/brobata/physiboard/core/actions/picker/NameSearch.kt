package brobata.physiboard.core.actions.picker

import brobata.physiboard.core.actions.emoji.EmojiTermNormalizer

/**
 * How the kaomoji and the Unicode symbol searches rank a name against a query. spec:
 * expansion-clipboard-pickers-launcher.md SS4.8. Both sides are normalised as the emoji search
 * normalises (SS4.2: lowercase, marks removed, letters and digits only, words split on spaces,
 * `-` and `_`), so "N-ARY SUMMATION" is the words "n ary summation".
 *
 * | Match | Score |
 * |---|---|
 * | the term equals the query | 1000 |
 * | the term starts with the query | 800 |
 * | every query word is a word of the term or the start of one, in any order | 600, +20 for each query word that is a whole word |
 * | the term contains the query (2+ characters) | 300 |
 *
 * The term's word count is subtracted, so among equal matches the shorter name comes first
 * ("rightwards arrow" before "rightwards double arrow").
 */
object NameSearch {
    const val SCORE_EQUALS: Int = 1000
    const val SCORE_PREFIX: Int = 800
    const val SCORE_ALL_WORDS: Int = 600
    const val SCORE_WHOLE_WORD_BONUS: Int = 20
    const val SCORE_CONTAINS: Int = 300

    /** A normalised query, split once. */
    class Query(raw: String) {
        val raw: String = raw.trim()
        val text: String = EmojiTermNormalizer.normalize(this.raw)
        val words: List<String> = if (text.isEmpty()) emptyList() else text.split(' ')
        val isEmpty: Boolean get() = text.isEmpty()
    }

    /** One already-normalised term (a name or a tag) and its words. */
    class Term(val text: String) {
        val words: List<String> = if (text.isEmpty()) emptyList() else text.split(' ')

        companion object {
            fun of(raw: String): Term = Term(EmojiTermNormalizer.normalize(raw))
        }
    }

    /** The table's score of [term] for [query], or null when it does not match at all. */
    fun score(query: Query, term: Term): Int? {
        if (query.isEmpty || term.text.isEmpty()) return null
        val base = when {
            term.text == query.text -> SCORE_EQUALS
            term.text.startsWith(query.text) -> SCORE_PREFIX
            else -> allWordsScore(query, term) ?: if (query.text.length >= 2 && term.text.contains(query.text)) SCORE_CONTAINS else return null
        }
        return base - term.words.size.coerceAtMost(MAX_WORD_PENALTY)
    }

    private fun allWordsScore(query: Query, term: Term): Int? {
        var whole = 0
        for (q in query.words) {
            var best = 0
            for (w in term.words) {
                if (w == q) {
                    best = 2
                    break
                }
                if (w.startsWith(q)) best = 1
            }
            if (best == 0) return null
            if (best == 2) whole++
        }
        return SCORE_ALL_WORDS + SCORE_WHOLE_WORD_BONUS * whole
    }

    private const val MAX_WORD_PENALTY: Int = 50
}
