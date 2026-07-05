package it.palsoftware.pastiera.core

/**
 * Centralized punctuation sets. Apostrophes are intentionally excluded from boundary handling.
 */
object Punctuation {
    const val NARROW_NO_BREAK_SPACE: Char = '\u202F'

    // Boundary punctuation (no apostrophes).
    const val BOUNDARY: String = ".,;:!?()[]{}\\/\""

    // Auto-space punctuation (no apostrophes). All candidates are opt-in by
    // default so ASCII smileys and quotes stay easy to type.
    const val AUTO_SPACE_CANDIDATES: String = ".,;:!?\\/\""
    const val DEFAULT_AUTO_SPACE: String = ""
    const val AUTO_SPACE: String = DEFAULT_AUTO_SPACE

    const val FRENCH_SPACED_PUNCTUATION: String = "?!;:"
    const val COMMA_SPACE_PUNCTUATION: Char = ','

    // Normalize curly/variant apostrophes to straight.
    fun normalizeApostrophe(c: Char): Char = when (c) {
        '’', '‘', 'ʼ' -> '\''
        else -> c
    }

    fun isWordBoundary(ch: Char, prev: Char? = null, next: Char? = null): Boolean {
        val normalized = normalizeApostrophe(ch)
        if (normalized.isWhitespace()) return true
        if (normalized in BOUNDARY) return true
        if (normalized == '\'') {
            val prevIsWord = prev?.let { normalizeApostrophe(it) }?.isLetterOrDigit() == true
            return !prevIsWord
        }
        return !normalized.isLetterOrDigit()
    }

    fun commitFrenchSpacedPunctuation(
        inputConnection: android.view.inputmethod.InputConnection,
        punctuation: Char
    ): Boolean {
        if (punctuation !in FRENCH_SPACED_PUNCTUATION) return false

        val before = inputConnection.getTextBeforeCursor(16, 0)?.toString().orEmpty()
        val existingSpaceCount = before.takeLastWhile { it == ' ' || it == '\u00A0' || it == NARROW_NO_BREAK_SPACE }.length
        val prefix = before.dropLast(existingSpaceCount)
        if (prefix.isEmpty() || prefix.last().isWhitespace()) {
            return false
        }

        if (existingSpaceCount > 0) {
            inputConnection.deleteSurroundingText(existingSpaceCount, 0)
        }
        inputConnection.commitText("$NARROW_NO_BREAK_SPACE$punctuation", 1)
        AutoSpaceTracker.clear()
        return true
    }

    fun commitCommaSpace(
        inputConnection: android.view.inputmethod.InputConnection,
        punctuation: Char
    ): Boolean {
        if (punctuation != COMMA_SPACE_PUNCTUATION) return false

        val before = inputConnection.getTextBeforeCursor(16, 0)?.toString().orEmpty()
        if (before.endsWith("$punctuation ")) {
            AutoSpaceTracker.markAutoSpace()
            return true
        }
        if (before.endsWith(punctuation)) {
            val beforeComma = before.dropLast(1)
            val preCommaSpaceCount = beforeComma.takeLastWhile { it == ' ' }.length
            if (preCommaSpaceCount > 0) {
                inputConnection.deleteSurroundingText(preCommaSpaceCount + 1, 0)
                inputConnection.commitText("$punctuation ", 1)
            } else {
                inputConnection.commitText(" ", 1)
            }
            AutoSpaceTracker.markAutoSpace()
            return true
        }
        val existingSpaceCount = before.takeLastWhile { it == ' ' }.length
        if (existingSpaceCount > 0) {
            inputConnection.deleteSurroundingText(existingSpaceCount, 0)
        }
        inputConnection.commitText("$punctuation ", 1)
        AutoSpaceTracker.markAutoSpace()
        return true
    }
}
