package brobata.physiboard.core.text

/**
 * What came before a word, read from the text before the cursor the way the word-pair table was
 * counted (`scripts/build_bigrams.py`), so the probabilities mean what they say. spec:
 * autocorrect-suggestions.md §16 W5 (the context prior) and §10 (the mix-up check reads the word
 * before the previous one).
 *
 * - The start of the text, a new line, and `.` `!` `?` `;` `:` start a sentence: the table's
 *   sentence-start context. The keyboard calls `;` and `:` soft boundaries for capitalisation, but
 *   the table was counted with them resetting, and the probabilities have to be read the way they
 *   were counted.
 * - Spaces, commas, quotes, brackets and dashes are passed over: the table counts "yes, its" as
 *   the pair (yes, its), exactly as the keyboard's own soft boundary would.
 * - Anything else (a digit, an emoji, a symbol) means the context is unknown, and so does a word
 *   cut off by the start of a window that was itself cut from a longer text.
 */
sealed class Preceding {
    /** The sentence starts here. */
    object SentenceStart : Preceding()

    /** Nothing usable precedes: the caller corrects without context. */
    object Unknown : Preceding()

    /**
     * A word, exactly as it stands in the text ([text], apostrophes unfolded), at
     * `[start, end)` of the text it was read from. [gap] is everything between its end and the
     * position the read started from.
     */
    data class Word(val text: String, val start: Int, val end: Int, val gap: String) : Preceding() {
        /** The spelling the word-pair table and the confusion sets use: lowercase, straight apostrophes. */
        val key: String get() = WordChars.straightenAll(text).lowercase()
    }
}

object SentenceContext {

    private const val SENTENCE_RESETS = ".!?;:\n\r"
    private const val PASSED_OVER = ",\"()[]{}-–—“”«»"

    /**
     * What precedes position [end] of [text]. [windowTruncated] says [text] was cut from a longer
     * text at its start, so reaching index 0 is not the start of anything.
     */
    fun before(text: String, end: Int, windowTruncated: Boolean): Preceding {
        var i = end
        while (i > 0) {
            val ch = text[i - 1]
            when {
                ch in SENTENCE_RESETS -> return Preceding.SentenceStart
                ch.isWhitespace() || ch in PASSED_OVER -> i--
                ch.isLetter() || (WordChars.isApostrophe(ch) && i >= 2 && text[i - 2].isLetter()) -> {
                    var start = i
                    while (start > 0 && WordChars.isWordChar(text, start - 1)) start--
                    // A run of word characters can still start with an apostrophe the rule above
                    // would not let join; the word itself starts at its first letter or digit.
                    while (start < i && !text[start].isLetterOrDigit()) start++
                    if (start == 0 && windowTruncated) return Preceding.Unknown
                    val word = text.substring(start, i)
                    if (word.any { it.isDigit() }) return Preceding.Unknown
                    return Preceding.Word(word, start, i, text.substring(i, end))
                }
                else -> return Preceding.Unknown
            }
        }
        return if (windowTruncated) Preceding.Unknown else Preceding.SentenceStart
    }
}
