package it.palsoftware.pastiera.core.suggestions

import java.text.Normalizer
import java.util.Locale

/**
 * Shared normalization helpers used by repository and suggestion ranking.
 *
 * Goals:
 * - normalize apostrophes consistently
 * - fold common compatibility letters/ligatures (e.g. œ -> oe)
 * - remove accents/combining marks
 * - keep only letters (optionally apostrophes for in-word elisions)
 */
object WordNormalization {
    private val combiningMarksRegex = "\\p{Mn}".toRegex()
    private val nonLettersRegex = "[^\\p{L}]".toRegex()
    private val nonLettersWithApostrophesRegex = "[^\\p{L}']".toRegex()

    fun normalizeApostrophes(input: String): String {
        return input
            .replace("’", "'")
            .replace("‘", "'")
            .replace("ʼ", "'")
    }

    /**
     * Fold a small, conservative set of compatibility letters to ASCII digraphs.
     * This keeps behavior predictable across languages without over-normalizing.
     */
    fun foldCompatibilityLetters(input: String): String {
        if (input.isEmpty()) return input

        var out: StringBuilder? = null
        input.forEachIndexed { index, ch ->
            val replacement = when (ch) {
                'œ', 'Œ' -> "oe"
                'æ', 'Æ' -> "ae"
                'ĳ', 'Ĳ' -> "ij"
                'ß' -> "ss"
                else -> null
            }

            if (replacement == null) {
                out?.append(ch)
            } else {
                if (out == null) {
                    out = StringBuilder(input.length + 4)
                    out?.append(input, 0, index)
                }
                out?.append(replacement)
            }
        }

        return out?.toString() ?: input
    }

    fun normalizeForDictionary(word: String, locale: Locale): String {
        val withAsciiCompat = foldCompatibilityLetters(normalizeApostrophes(word).lowercase(locale))
        val normalized = Normalizer.normalize(withAsciiCompat, Normalizer.Form.NFD)
        val withoutAccents = normalized.replace(combiningMarksRegex, "")
        return withoutAccents.replace(nonLettersRegex, "")
    }

    fun normalizeForSuggestion(word: String, locale: Locale): String {
        val withAsciiCompat = foldCompatibilityLetters(normalizeApostrophes(word).lowercase(locale))
        val normalized = Normalizer.normalize(withAsciiCompat, Normalizer.Form.NFD)
        val withoutAccents = normalized.replace(combiningMarksRegex, "")
        return withoutAccents.replace(nonLettersWithApostrophesRegex, "")
    }
}
