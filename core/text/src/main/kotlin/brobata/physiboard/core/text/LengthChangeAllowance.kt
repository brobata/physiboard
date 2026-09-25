package brobata.physiboard.core.text

/**
 * spec: autocorrect-suggestions.md SS9 step 8, "Per-language length allowance: English allows a
 * length change of up to 2; every other language allows 0." Without it the safe-shape gate
 * refused every dropped or extra letter, so "postr" stayed "postr" on the maintainer's phone
 * (2026-09-25) while the strip offered "poster" first.
 */
object LengthChangeAllowance {
    const val ENGLISH = 2
    const val OTHER = 0

    /** [languageTag] is a BCP 47 tag or bare code; only the language part matters ("en", "en-US", "en_GB"). */
    fun forLanguage(languageTag: String): Int =
        if (languageTag.lowercase().split('-', '_').firstOrNull() == "en") ENGLISH else OTHER
}
