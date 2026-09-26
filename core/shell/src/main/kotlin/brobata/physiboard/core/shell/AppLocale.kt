package brobata.physiboard.core.shell

import java.util.Locale

/**
 * The app's own interface language (dictionaries-languages.md SS11, "The app's own interface
 * language"): resolving the stored `app_language_tag` to the BCP-47 tag the app should apply, and
 * building each option's label for the "App Language" screen (app-shell.md SS15 card 3, SS22.3;
 * settings-catalog.md SS9.2). Pure and Android-free so the resolution rule and the dual-name
 * labelling rule are both JVM-testable; the actual override (AppCompatDelegate) lives in `:app`.
 */
object AppLocale {

    /**
     * The ten shipped locales in the spec's fixed order. "System default" is not a tag here; it is
     * the case where [resolve] returns null.
     */
    val SUPPORTED_TAGS: List<String> = listOf("en", "it", "de", "es", "fr", "pl", "ru", "uk", "vi", "hy")

    /**
     * `app_language_tag`: blank, or anything not one of [SUPPORTED_TAGS], resolves to null, meaning
     * "do not override, follow the system language". A known tag is returned unchanged.
     */
    fun resolve(storedTag: String): String? {
        val trimmed = storedTag.trim()
        return trimmed.takeIf { it.isNotEmpty() && it in SUPPORTED_TAGS }
    }

    /** The language's name in itself (`Deutsch`, `Espanol`), used alone as the About row's current-choice description. */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /**
     * SS11's dual-label rule for one row of the "App Language" list: the language's name in
     * itself, and, only when it differs from its name in [uiLanguageTag] (the language the app
     * interface is currently shown in), "<native> - <ui name>" (`Deutsch - German`).
     */
    fun optionLabel(tag: String, uiLanguageTag: String): String {
        val native = nativeName(tag)
        val uiLocale = Locale.forLanguageTag(uiLanguageTag.ifBlank { "en" })
        val inUiLanguage = Locale.forLanguageTag(tag).getDisplayName(uiLocale).replaceFirstChar { it.titlecase(uiLocale) }
        return if (native.equals(inUiLanguage, ignoreCase = true)) native else "$native - $inUiLanguage"
    }
}
