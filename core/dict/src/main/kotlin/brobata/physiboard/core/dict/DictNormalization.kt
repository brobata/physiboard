package brobata.physiboard.core.dict

import java.text.Normalizer

/**
 * The normalization a word's spelling is folded through to produce its dictionary lookup key,
 * so that `e`, `è`, `é` and `l'oeil`/`loeil` all share one bucket. Dictionary entries keep
 * their original spelling; only the key used to find them is normalized. spec:
 * dictionaries-languages.md §2.3, steps 1-5.
 *
 * // SPEC GAP: step 2 is specified as "lowercase in the dictionary's language", implying a
 * // locale-specific case fold (Turkish dotless i is the classic example). None of the
 * // languages this module has to serve first need it, so this uses the ordinary
 * // locale-invariant `lowercase()`; a language that does need it is a later addition here,
 * // not a reason to block on it now.
 */
object DictNormalization {

    private val CURLY_APOSTROPHES = charArrayOf('’', '‘', 'ʼ')

    /**
     * Folds [word] down to its dictionary lookup key: straight apostrophes, lowercase,
     * ligature folding (`œ`/`Œ` -> `oe`, `æ`/`Æ` -> `ae`, `ĳ`/`Ĳ` -> `ij`, `ß` -> `ss`), accent
     * removal, then every character that is not a letter (including the apostrophes just
     * straightened) is dropped. spec: dictionaries-languages.md §2.3 steps 1-5.
     */
    fun normalizedKey(word: String): String {
        val straightened = straightenApostrophes(word)
        val lowered = straightened.lowercase()
        val folded = foldLigatures(lowered)
        val unaccented = stripAccents(folded)
        return buildString(unaccented.length) {
            for (ch in unaccented) if (ch.isLetter()) append(ch)
        }
    }

    /**
     * Removes combining marks after Unicode NFD decomposition, so `è` becomes `e`. Exposed on
     * its own because the accent-stripped fuzzy lookup (autocorrect-suggestions.md §3.4) needs
     * exactly this step applied to an already-normalized word, not the full key derivation.
     */
    fun stripAccents(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return buildString(decomposed.length) {
            for (ch in decomposed) {
                val type = Character.getType(ch)
                val isCombiningMark = type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.COMBINING_SPACING_MARK.toInt() ||
                    type == Character.ENCLOSING_MARK.toInt()
                if (!isCombiningMark) append(ch)
            }
        }
    }

    /** Exposed (not `private`) so [NgramPrefix] can reuse the same first two folding steps. */
    internal fun straightenApostrophes(word: String): String {
        if (CURLY_APOSTROPHES.none { it in word }) return word
        return buildString(word.length) {
            for (ch in word) append(if (ch in CURLY_APOSTROPHES) '\'' else ch)
        }
    }

    /** Exposed (not `private`) so [NgramPrefix] can reuse the same ligature-folding step. */
    internal fun foldLigatures(word: String): String = word
        .replace("œ", "oe").replace("Œ", "oe")
        .replace("æ", "ae").replace("Æ", "ae")
        .replace("ĳ", "ij").replace("Ĳ", "ij")
        .replace("ß", "ss")
}
