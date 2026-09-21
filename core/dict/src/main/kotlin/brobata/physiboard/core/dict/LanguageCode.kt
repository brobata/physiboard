package brobata.physiboard.core.dict

/**
 * A dictionary's language identity: the two-letter lowercase ISO 639-1 code that names a
 * `<lang>_base` dictionary and, in this module, a `.pbd` file's header. Region is never part
 * of it: `en_US` and `en_GB` both resolve to the same [LanguageCode] `en`, because a keyboard
 * process keeps one dictionary per language code, not per locale.
 *
 * spec: dictionaries-languages.md "Vocabulary" (language code, locale string, language tag)
 * and §8.7 ("The primary language is the language part of the subtype... region ignored").
 */
@JvmInline
value class LanguageCode private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        private val CODE_PATTERN = Regex("^[a-z]{2}$")

        /** Parses a bare code such as `en` or `DE`; null when it is not exactly two letters. */
        fun of(raw: String): LanguageCode? {
            val lower = raw.lowercase()
            return if (CODE_PATTERN.matches(lower)) LanguageCode(lower) else null
        }

        /**
         * Parses a locale string or language tag (`en_US`, `en-US`, `en`, `EN`) down to its
         * language part, ignoring any region. spec: dictionaries-languages.md §8.7, §12.
         */
        fun fromLocale(localeOrTag: String): LanguageCode? {
            val languagePart = localeOrTag.substringBefore('_').substringBefore('-')
            return of(languagePart)
        }
    }
}

/**
 * The set of dictionaries consulted for one input style: the primary language plus its
 * configured extra suggestion languages, deduplicated, with the primary itself and the
 * substitution-only tag dropped from the extras. spec: dictionaries-languages.md §8.4 (extra
 * suggestion languages) and §15 ("`x-pastiera` in an extra-language list: Ignored, it names
 * the substitution rule set, not a dictionary").
 *
 * This type answers only "which languages", never "does that language have a file on disk":
 * resolving a language to an installed dictionary is a later module's job (file system layout,
 * downloads and hosting are out of `:core:dict`'s scope).
 */
data class ActiveLanguages(val primary: LanguageCode, val extra: List<LanguageCode>) {

    /** Every language to have a dictionary loaded for, primary first. */
    fun all(): List<LanguageCode> = buildList {
        add(primary)
        addAll(extra)
    }

    companion object {
        private const val SUBSTITUTION_ONLY_TAG = "x-pastiera"

        /**
         * Builds the active set from a primary language and the raw extra-language tags
         * configured for an input style (locale strings or language tags, as
         * `input_style_suggestion_locales` stores them). Invalid tags, the substitution-only
         * tag, the primary language itself and duplicates are all dropped; order among the
         * survivors is preserved.
         */
        fun of(primary: LanguageCode, requestedExtra: List<String>): ActiveLanguages {
            val extra = mutableListOf<LanguageCode>()
            for (raw in requestedExtra) {
                val trimmed = raw.trim()
                if (trimmed.equals(SUBSTITUTION_ONLY_TAG, ignoreCase = true)) continue
                val code = LanguageCode.fromLocale(trimmed) ?: continue
                if (code == primary) continue
                if (code !in extra) extra.add(code)
            }
            return ActiveLanguages(primary, extra)
        }
    }
}
