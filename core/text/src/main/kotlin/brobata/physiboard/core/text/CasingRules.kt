package brobata.physiboard.core.text

/**
 * The casing a suggestion or automatic correction gets recomposed with, following the word as
 * typed rather than the dictionary's own spelling of it. spec: autocorrect-suggestions.md SS5
 * (tapping a suggestion) and SS9 ("recased like a tapped suggestion ... without the
 * auto-capitalize override").
 */
object CasingRules {

    private enum class TypedShape { ALL_UPPER, LEADING_CAPITAL, LOWERCASE }

    /**
     * The base recasing rule shared by every caller: all letters uppercase (more than one letter)
     * forces the candidate fully uppercase regardless of the candidate's own casing; otherwise a
     * candidate with an internal capital (`McCartney`, `iPhone`) or exactly one uppercase letter
     * anywhere keeps its own casing untouched; otherwise a leading capital in [typed] capitalizes
     * the candidate's first letter (skipping any leading non-letter, so `l'amico` capitalizes to
     * `L'amico`), and anything else lowercases the candidate.
     */
    fun forTypedWord(typed: String, candidate: String): String {
        val shape = shapeOf(typed)
        if (shape == TypedShape.ALL_UPPER) return candidate.uppercase()
        if (hasInternalCapital(candidate) || countUppercaseLetters(candidate) == 1) return candidate
        return when (shape) {
            TypedShape.LEADING_CAPITAL -> capitalizeFirstLetter(candidate)
            else -> candidate.lowercase()
        }
    }

    /**
     * [forTypedWord] plus the strip-tap override: when the cursor sits where auto-capitalization
     * would arm and "Capitalize at text start" is on, the first letter is capitalized regardless
     * of what was typed. spec: SS5.
     */
    fun forTappedSuggestion(typed: String, candidate: String, autoCapitalizeOverride: Boolean): String {
        val base = forTypedWord(typed, candidate)
        return if (autoCapitalizeOverride) capitalizeFirstLetter(base) else base
    }

    private fun shapeOf(typed: String): TypedShape {
        val letters = typed.filter { it.isLetter() }
        return when {
            letters.isEmpty() -> TypedShape.LOWERCASE
            letters.length > 1 && letters.all { it.isUpperCase() } -> TypedShape.ALL_UPPER
            letters.first().isUpperCase() -> TypedShape.LEADING_CAPITAL
            else -> TypedShape.LOWERCASE
        }
    }

    private fun countUppercaseLetters(s: String): Int = s.count { it.isUpperCase() }

    private fun hasInternalCapital(s: String): Boolean {
        val firstLetterIndex = s.indexOfFirst { it.isLetter() }
        if (firstLetterIndex < 0) return false
        return s.withIndex().any { (i, c) -> i != firstLetterIndex && c.isUpperCase() }
    }

    /** Capitalizes the first letter of [text], skipping any leading non-letter (an apostrophe). */
    fun capitalizeFirstLetter(text: String): String {
        val index = text.indexOfFirst { it.isLetter() }
        if (index < 0) return text
        return text.substring(0, index) + text[index].uppercase() + text.substring(index + 1)
    }
}
