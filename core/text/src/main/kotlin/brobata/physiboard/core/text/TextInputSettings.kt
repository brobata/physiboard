package brobata.physiboard.core.text

import brobata.physiboard.core.keys.AltBackspaceAction

/** Which dash character "Hyphen to dash" inserts. spec: text-input.md SS6.8, `spaced_hyphen_dash_style`. */
enum class DashStyle(val char: Char) { EN_DASH('–'), EM_DASH('—') }

/** The five paired-quote styles "Smart quotes" can produce. spec: text-input.md SS6.10, `smart_quotes_style`. */
enum class SmartQuoteStyle(val open: String, val close: String) {
    GERMAN_GUILLEMETS("»", "«"),
    FRENCH_GUILLEMETS("«", "»"),
    FRENCH_GUILLEMETS_NARROW_SPACED("« ", " »"),
    GERMAN_LOW_HIGH("„", "“"),
    ENGLISH_CURLY("“", "”"),
}

/**
 * Auto-capitalization's user-facing settings. spec: text-input.md SS15,
 * `auto_capitalize_first_letter`, `auto_capitalize_after_period`, `auto_capitalize_restricted_fields`.
 */
data class AutoCapSettings(
    val capitalizeAtTextStart: Boolean = true,
    val capitalizeAfterSentenceEnd: Boolean = true,
    val capitalizeRestrictedFields: Boolean = false,
)

/**
 * Spacing and punctuation settings. spec: text-input.md SS15. [removeBeforeList] and
 * [beforeNextTextList] are subsets of the eleven auto-space candidates `. , ; : ! ? \ / " ) ] }`,
 * always in that canonical order (SS1); an invalid or duplicated character is the caller's
 * mistake to avoid; this module treats membership by content, not position.
 */
data class SpacingSettings(
    val doubleSpaceToPeriod: Boolean = true,
    val removeBeforeList: String = "",
    val beforeNextTextList: String = "",
    val commaSpace: Boolean = false,
    val spacedHyphenToDash: Boolean = false,
    val dashStyle: DashStyle = DashStyle.EN_DASH,
    val smartQuotes: Boolean = false,
    val smartQuoteStyle: SmartQuoteStyle = SmartQuoteStyle.GERMAN_GUILLEMETS,
    val frenchPunctuationSpacing: Boolean = false,
    val frenchPunctuationOnlyFrench: Boolean = false,
) {
    companion object {
        /** spec: text-input.md SS1, "Auto-space candidates". */
        const val AUTO_SPACE_CANDIDATES: String = ".,;:!?\\/\")]}"

        /** spec: text-input.md SS6.6, the characters that cancel a deferred-space debt instead of collecting it. */
        const val NO_SPACE_BEFORE: String = ".,;:!?/\\)]}»›"
    }
}

/**
 * Backspace behavior settings. spec: text-input.md SS15, `shift_backspace_delete`,
 * `alt_backspace_delete`, `backspace_at_start_delete`.
 */
data class BackspaceSettings(
    val shiftBackspaceDeletesForward: Boolean = false,
    val altBackspace: AltBackspaceAction = AltBackspaceAction.DELETE_CHARACTER,
    val backspaceAtStartDeletesForward: Boolean = false,
)

/**
 * Suggestion and autocorrect settings. spec: autocorrect-suggestions.md SS13. Names mirror the
 * preference keys for traceability even though this module never touches Android preferences.
 */
data class AutocorrectSettings(
    val suggestionsEnabled: Boolean = true,
    val autoCorrectEnabled: Boolean = true,
    val autoReplaceOnSpaceEnter: Boolean = false,
    val maxAutoReplaceDistance: Int = 1,
    val useKeyboardProximity: Boolean = false,
    val useEditTypeRanking: Boolean = false,
    val accentMatchingEnabled: Boolean = true,
    /** `fix_word_mixups`: the previous-word mix-up fix (its/it's, their/there, ...). spec: autocorrect-suggestions.md SS10. On by default from 3.2. */
    val fixWordMixups: Boolean = true,
) {
    init {
        require(maxAutoReplaceDistance in 0..3) { "maxAutoReplaceDistance must be 0..3, was $maxAutoReplaceDistance" }
    }
}
