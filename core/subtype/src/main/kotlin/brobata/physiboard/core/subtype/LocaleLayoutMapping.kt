package brobata.physiboard.core.subtype

/**
 * spec dictionaries-languages.md SS10: resolves the layout id for a locale string, the user's own
 * override (`files/locale_layout_mapping.json`, written by the Input Languages screen) taking
 * precedence over the bundled asset (`common/locale_layout_mapping.json`), with `qwerty` as the
 * last resort. Pure (a plain map lookup over already-decoded JSON), so `:app` (which writes the
 * override) and `:ime` (which reads both to resolve a subtype's layout) share the exact rule the
 * spec's own T20-T22 test cases encode, and can run it without touching a file at all.
 */
object LocaleLayoutMapping {

    /** SS10: "then `qwerty`" -- the last resort when neither the override nor the asset names a layout. */
    const val FALLBACK_LAYOUT_ID: String = "qwerty"

    /**
     * spec SS10's bundled `common/locale_layout_mapping.json` table, kept here (not just as an
     * asset) so [resolve] is testable with no file I/O at all; `:app`/`:ime` may still ship the
     * same table as an asset for a host that reads it as JSON.
     */
    val BUNDLED_ASSET: Map<String, String> = mapOf(
        "tr" to "turkish_multitap", "tr_TR" to "turkish_multitap", "tr_CY" to "turkish_multitap",
        "en_US" to "qwerty", "it_IT" to "qwerty", "pl_PL" to "qwerty", "es_ES" to "qwerty", "pt_PT" to "qwerty",
        "da" to "qwerty", "da_DK" to "qwerty",
        "fr_FR" to "azerty",
        "de" to "qwertz", "de_DE" to "qwertz", "de_AT" to "qwertz", "de_CH" to "qwertz", "de_LU" to "qwertz",
        "no" to "norwegian_multitap_qwerty", "no_NO" to "norwegian_multitap_qwerty",
        "nb" to "norwegian_multitap_qwerty", "nb_NO" to "norwegian_multitap_qwerty",
        "nn" to "norwegian_multitap_qwerty", "nn_NO" to "norwegian_multitap_qwerty",
        "vi_VN" to "vietnamese_telex_qwerty",
        "ru_RU" to "russian_translit",
        "sr_RS" to "serbian_cyrillic",
        "uk_UA" to "ukrainian",
    )

    /**
     * SS10's "Lookup for a locale string `L` with language `l`": the override's `L` then its `l`
     * (non-empty values only), then the asset's `L`, then the asset's `l`, then
     * [FALLBACK_LAYOUT_ID]. Both the lookup key and every map key are compared with underscores
     * and hyphens treated as equal and case-insensitively, so `de-AT` resolves through `de_AT` or
     * `de-AT` alike (T20).
     */
    fun resolve(locale: String, asset: Map<String, String> = BUNDLED_ASSET, override: Map<String, String> = emptyMap()): String {
        val normLocale = normalize(locale)
        val normLanguage = normLocale.substringBefore('-')
        val normOverride = override.mapKeys { normalize(it.key) }
        val normAsset = asset.mapKeys { normalize(it.key) }
        return normOverride[normLocale]?.takeIf { it.isNotBlank() }
            ?: normOverride[normLanguage]?.takeIf { it.isNotBlank() }
            ?: normAsset[normLocale]?.takeIf { it.isNotBlank() }
            ?: normAsset[normLanguage]?.takeIf { it.isNotBlank() }
            ?: FALLBACK_LAYOUT_ID
    }

    private fun normalize(s: String): String = s.replace('_', '-').lowercase()
}
