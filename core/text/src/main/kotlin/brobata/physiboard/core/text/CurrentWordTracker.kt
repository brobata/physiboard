package brobata.physiboard.core.text

/**
 * The keyboard's live model of the word being typed, built from the characters it committed
 * rather than from a composing region: ordinary typing on the Titan never composes (text-
 * input.md SS4), so the suggestion strip's notion of "the current word" has to be tracked
 * separately. spec: autocorrect-suggestions.md SS1.1 (word-character rule, the 48-character cap,
 * the apostrophe rule, Backspace, multi-tap replace) and SS1.2 (re-syncing from the field).
 *
 * Every method returns a new tracker; this type carries no mutable state and performs no I/O.
 */
@ConsistentCopyVisibility
data class CurrentWordTracker private constructor(val word: String, private val maxLength: Int) {

    /** Whether [ch] would extend [word] if it were committed next. spec: SS1.1. */
    private fun joins(ch: Char): Boolean =
        ch.isLetterOrDigit() || (WordChars.isApostrophe(ch) && word.isNotEmpty() && word.last().isLetterOrDigit())

    /**
     * A plain character was committed to the field. Extends the word when [ch] joins it; any other
     * character (space, punctuation, symbol, emoji) resets to empty. spec: SS1.1.
     */
    fun onCharacterCommitted(ch: Char): CurrentWordTracker =
        if (joins(ch)) CurrentWordTracker(appendCapped(word, WordChars.straighten(ch)), maxLength) else empty(maxLength)

    /**
     * A multi-tap cycle replaced the last committed character with [ch] in one batch edit (a
     * committed backspace-then-character). spec: SS1.1 ("the tracker removes one character and
     * then appends the new one").
     */
    fun onCharacterReplaced(ch: Char): CurrentWordTracker {
        val withoutLast = if (word.isEmpty()) word else word.substring(0, word.length - 1)
        val probe = CurrentWordTracker(withoutLast, maxLength)
        return if (probe.joins(ch)) {
            CurrentWordTracker(appendCapped(withoutLast, WordChars.straighten(ch)), maxLength)
        } else {
            empty(maxLength)
        }
    }

    /** Backspace removed the last committed character; the word empties out one character at a time. spec: SS1.1. */
    fun onBackspace(): CurrentWordTracker =
        if (word.isEmpty()) this else CurrentWordTracker(word.substring(0, word.length - 1), maxLength)

    /** Resets to an empty word: a boundary character, a context change, or the cursor landing on empty space. */
    fun reset(): CurrentWordTracker = empty(maxLength)

    /**
     * Re-derives the word from the field's own text immediately before the cursor, keeping at most
     * the last [maxLength] characters. spec: SS1.2 (cursor moves, field entry, boundary key
     * pressed all re-read the word this way).
     */
    fun syncedFrom(textBeforeCursor: String): CurrentWordTracker {
        val raw = WordChars.wordEndingAt(textBeforeCursor, textBeforeCursor.length)
        val straightened = buildString(raw.length) { for (c in raw) append(WordChars.straighten(c)) }
        return CurrentWordTracker(straightened.takeLast(maxLength), maxLength)
    }

    private fun appendCapped(base: String, ch: Char): String =
        if (base.length >= maxLength) base else base + ch

    companion object {
        /** spec: autocorrect-suggestions.md SS1.1, "The current word holds at most 48 characters." */
        const val DEFAULT_MAX_LENGTH: Int = 48

        fun empty(maxLength: Int = DEFAULT_MAX_LENGTH): CurrentWordTracker = CurrentWordTracker("", maxLength)
    }
}
