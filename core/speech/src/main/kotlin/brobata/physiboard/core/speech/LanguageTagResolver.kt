package brobata.physiboard.core.speech

/**
 * Picks the BCP-47 tag one recognizer request asks for. spec: dictation.md SS5.1: subtype locale
 * first (normalised to a tag), then the device's first configured locale, then a fallback for
 * when neither answers. 2.0.7 used `it-IT` here (dictionaries-languages.md SS12: "the upstream
 * default locale was Italian and survived the fork"), but that document's own SS17 Keep/Drop
 * verdict for 3.0 is explicit: "Italian fallback when there is no subtype: Replace with English,
 * upstream leftover."
 */
object LanguageTagResolver {
    private const val FALLBACK_LANGUAGE_TAG = "en-US"

    fun resolve(subtypeLanguageTag: String?, deviceLocaleTag: String?): String {
        languageTagOf(subtypeLanguageTag)?.let { return it }
        val device = deviceLocaleTag?.trim().orEmpty()
        if (device.isNotEmpty()) return device
        return FALLBACK_LANGUAGE_TAG
    }

    /** Normalises [candidate] (`_` to `-`, trimmed) and returns it only when it has a language subtag. */
    private fun languageTagOf(candidate: String?): String? {
        val normalized = candidate?.trim()?.replace('_', '-') ?: return null
        if (normalized.isEmpty()) return null
        val language = normalized.substringBefore('-')
        return if (language.isNotEmpty() && language.all { it.isLetter() }) normalized else null
    }
}
