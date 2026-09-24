package brobata.physiboard.core.speech

/**
 * Picks the BCP-47 tag one recognizer request asks for. spec: dictation.md SS5.1: subtype locale
 * first (normalised to a tag), then the device's first configured locale, then `it-IT` as the
 * upstream project's own fallback.
 */
object LanguageTagResolver {
    private const val FALLBACK_LANGUAGE_TAG = "it-IT"

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
