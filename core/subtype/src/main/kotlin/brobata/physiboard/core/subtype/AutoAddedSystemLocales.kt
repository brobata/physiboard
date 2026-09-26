package brobata.physiboard.core.subtype

/**
 * dictionaries-languages.md SS8.5: which of the device's own languages get an automatic input
 * style because they have no dictionary anywhere. Pure; `:ime` supplies the device's locale list
 * and the language codes that actually have a dictionary in any tier, and applies the returned
 * [Result] to `custom_input_styles`/`auto_added_system_locales`.
 */
object AutoAddedSystemLocales {

    /**
     * SS8.5's variant table: "`en` covers `en_US en_GB en_AU en_CA en`, `no` covers
     * `no_NO nb_NO nn_NO nb nn no`, and so on for `it fr de pl da es pt ru`."
     */
    private val VARIANTS: Map<String, Set<String>> = mapOf(
        "en" to setOf("en_US", "en_GB", "en_AU", "en_CA", "en"),
        "no" to setOf("no_NO", "nb_NO", "nn_NO", "nb", "nn", "no"),
        "it" to setOf("it_IT", "it"),
        "fr" to setOf("fr_FR", "fr"),
        "de" to setOf("de_DE", "de_AT", "de_CH", "de_LU", "de"),
        "pl" to setOf("pl_PL", "pl"),
        "da" to setOf("da_DK", "da"),
        "es" to setOf("es_ES", "es"),
        "pt" to setOf("pt_PT", "pt"),
        "ru" to setOf("ru_RU", "ru"),
    )

    private val BASE_LOCALES: Set<String> = BaseSubtypes.ALL.map { normalize(it.locale) }.toSet()

    /** [inputStyles]: the new `custom_input_styles`; [autoAddedLocales]: the new `auto_added_system_locales`. */
    data class Result(val inputStyles: List<String>, val autoAddedLocales: Set<String>)

    /**
     * SS8.5: "Every system locale that has no dictionary..., is not a base locale, and is not
     * already in `custom_input_styles`, is appended... as `<locale>:<mapped layout>` and
     * remembered... When the device's language list changes..., the remembered locales that are
     * no longer system languages are removed... Locales the user added themselves are never
     * removed by this sweep."
     */
    fun reconcile(
        systemLocales: List<String>,
        languagesWithDictionary: Set<String>,
        inputStyles: List<String>,
        autoAddedLocales: Set<String>,
        layoutFor: (String) -> String,
    ): Result {
        val stillSystem = systemLocales.map(::normalize).toSet()
        val goneLocales = autoAddedLocales.filter { normalize(it) !in stillSystem }.toSet()

        var styles = inputStyles.filterNot { entry -> goneLocales.any { normalize(entry.substringBefore(':')) == normalize(it) } }
        var added = autoAddedLocales - goneLocales

        val currentKeys = styles.mapNotNull { it.substringBefore(':', "").ifEmpty { null } }.map(::normalize).toSet()
        for (locale in systemLocales) {
            if (hasDictionaryCoverage(locale, languagesWithDictionary)) continue
            if (normalize(locale) in BASE_LOCALES) continue
            if (normalize(locale) in currentKeys) continue
            styles = styles + "$locale:${layoutFor(locale)}"
            added = added + locale
        }

        return Result(styles, added)
    }

    /** True when [locale] is covered by one of [languagesWithDictionary]'s SS8.5 variant sets, or names that language directly. */
    private fun hasDictionaryCoverage(locale: String, languagesWithDictionary: Set<String>): Boolean {
        val normalized = normalize(locale).replace('-', '_')
        val language = normalized.substringBefore('_')
        return languagesWithDictionary.any { lang ->
            val variants = VARIANTS[lang]?.map { it.replace('-', '_') }?.toSet() ?: setOf(lang)
            normalized in variants || language == lang
        }
    }

    private fun normalize(s: String): String = s.replace('_', '-').lowercase()
}
