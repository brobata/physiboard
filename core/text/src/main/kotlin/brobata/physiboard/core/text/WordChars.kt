package brobata.physiboard.core.text

/**
 * The one word-character and boundary-punctuation rule shared by every feature in this module:
 * composition tracking, the suggestion tracker, selection helpers and the substitution hand-off.
 * spec: text-input.md SS1 ("Boundary punctuation", "Apostrophe") and autocorrect-suggestions.md
 * SS1.1 (word-character rule) describe the same fourteen boundary characters and the same
 * context-sensitive apostrophe; this object is the single source both specs describe.
 */
object WordChars {

    /** The fixed set of fourteen characters that end a word on their own. spec: text-input.md SS1. */
    const val BOUNDARY_PUNCTUATION: String = ".,;:!?()[]{}\\/\""

    private val CURLY_APOSTROPHES = charArrayOf('’', '‘', 'ʼ')

    /** Whether [ch] is the straight apostrophe or one of its three curly variants. spec: text-input.md SS1. */
    fun isApostrophe(ch: Char): Boolean = ch == '\'' || ch in CURLY_APOSTROPHES

    /** Folds any apostrophe variant down to the straight one everywhere this subsystem stores text. */
    fun straighten(ch: Char): Char = if (isApostrophe(ch)) '\'' else ch

    /**
     * [straighten] applied to every character of [text]. Used wherever a live editor read is
     * compared against this subsystem's own record ([CurrentWordTracker], [DriftCheck]), since the
     * record folds apostrophe variants but a real commit does not (it types back exactly the key
     * the user pressed); comparing both in this folded form keeps an apostrophe style difference
     * from ever looking like drift.
     */
    fun straightenAll(text: String): String = buildString(text.length) { for (c in text) append(straighten(c)) }

    /**
     * Whether `text[index]` joins a word, given the characters to its left in [text]. A letter or
     * digit in any script always joins; an apostrophe joins only when the character immediately
     * before it is itself a letter or digit, so "l'amico" is one word but a leading apostrophe
     * never starts one. spec: autocorrect-suggestions.md SS1.1.
     */
    fun isWordChar(text: String, index: Int): Boolean {
        val ch = text[index]
        if (ch.isLetterOrDigit()) return true
        if (isApostrophe(ch)) {
            val prev = text.getOrNull(index - 1)
            return prev != null && prev.isLetterOrDigit()
        }
        return false
    }

    /** Whether [ch] is boundary punctuation, whitespace, or any other non-word character on its own. */
    fun isBoundaryChar(ch: Char): Boolean =
        ch.isWhitespace() || ch in BOUNDARY_PUNCTUATION || (!ch.isLetterOrDigit() && !isApostrophe(ch))

    /**
     * The run of word characters immediately before `text[endIndexExclusive]`, at most [maxLength]
     * characters. Used both to sync the current-word tracker from the field (autocorrect-
     * suggestions.md SS1.2) and to find the word span under the cursor when accepting a suggestion
     * (text-input.md SS6.11).
     */
    fun wordEndingAt(text: String, endIndexExclusive: Int, maxLength: Int = Int.MAX_VALUE): String {
        var start = endIndexExclusive
        var length = 0
        while (start > 0 && length < maxLength && isWordChar(text, start - 1)) {
            start--
            length++
        }
        return text.substring(start, endIndexExclusive)
    }

    /** The run of word characters starting at `text[startIndex]`, at most [maxLength] characters. spec: text-input.md SS6.11. */
    fun wordStartingAt(text: String, startIndex: Int, maxLength: Int = Int.MAX_VALUE): String {
        var end = startIndex
        var length = 0
        while (end < text.length && length < maxLength && isWordChar(text, end)) {
            end++
            length++
        }
        return text.substring(startIndex, end)
    }

    /** Whether the text before the cursor, ignoring trailing whitespace, ends in a sentence-ending mark. spec: text-input.md SS1. */
    fun endsSentence(textBeforeCursor: String): Boolean {
        val trimmed = textBeforeCursor.trimEnd { it.isWhitespace() }
        if (trimmed.isEmpty()) return false
        return when (trimmed.last()) {
            '!', '?' -> true
            '.' -> trimmed.length < 2 || trimmed[trimmed.length - 2] != '.'
            else -> false
        }
    }

    /** Whether the text before the cursor is a sentence end followed by at least one whitespace character. spec: text-input.md SS1, SS9.2. */
    fun endsSentenceFollowedByWhitespace(textBeforeCursor: String): Boolean {
        val trimmed = textBeforeCursor.trimEnd { it.isWhitespace() }
        if (trimmed.length == textBeforeCursor.length) return false
        return endsSentence(trimmed)
    }
}
