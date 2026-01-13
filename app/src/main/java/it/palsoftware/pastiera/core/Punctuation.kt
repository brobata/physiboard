package it.palsoftware.pastiera.core

/**
 * Centralized punctuation sets. Apostrophes are intentionally excluded from boundary handling.
 */
object Punctuation {
    // Boundary punctuation (no apostrophes).
    const val BOUNDARY: String = ".,;:!?()[]{}\\/\""

    // Auto-space punctuation (no apostrophes).
    const val AUTO_SPACE: String = ".,;:!?\\/\""

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
}
