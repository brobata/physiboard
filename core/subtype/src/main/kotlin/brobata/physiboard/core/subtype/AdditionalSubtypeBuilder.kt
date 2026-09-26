package brobata.physiboard.core.subtype

/**
 * dictionaries-languages.md SS8.1's twelve base subtypes, in the spec's own order, each paired
 * with its SS10-mapped layout so [AdditionalSubtypeBuilder] can tell a redundant custom entry
 * (`en_US:qwerty`) from a real one (`en_US:vietnamese_telex_qwerty`).
 */
data class BaseSubtype(val locale: String, val mappedLayoutId: String)

object BaseSubtypes {
    val ALL: List<BaseSubtype> = listOf(
        BaseSubtype("en_US", "qwerty"),
        BaseSubtype("it_IT", "qwerty"),
        BaseSubtype("fr_FR", "azerty"),
        BaseSubtype("de_DE", "qwertz"),
        BaseSubtype("pl_PL", "qwerty"),
        BaseSubtype("da_DK", "qwerty"),
        BaseSubtype("no_NO", "norwegian_multitap_qwerty"),
        BaseSubtype("es_ES", "qwerty"),
        BaseSubtype("pt_PT", "qwerty"),
        BaseSubtype("ru_RU", "russian_translit"),
        BaseSubtype("sr_RS", "serbian_cyrillic"),
        BaseSubtype("uk_UA", "ukrainian"),
    )
}

/**
 * layers-sym-alt.md SS9.2's full bundled layout catalog: `:device:titan`'s `TitanLayouts.bundled()`
 * now ships all eighteen names as real [brobata.physiboard.core.subtype.ShippedLayout]s, so this is
 * the whole "available layouts" set SS8.3 step 1 checks a custom input style's layout against.
 */
object BundledLayoutIds {
    val ALL: Set<String> = setOf(
        "qwerty", "qwertz", "azerty", "german_multitap_qwertz", "turkish_multitap",
        "norwegian_multitap_qwerty", "arabic", "armenian_phonetic", "bulgarian_phonetic",
        "bulgarian_phonetic_traditional", "Cyrillic_Translite", "greek", "russian_jcuken",
        "russian_standard", "russian_translit", "serbian_cyrillic", "ukrainian",
        "vietnamese_telex_qwerty",
    )
}

/** One additional subtype to hand Android. spec SS8.3 step 3; `:ime` turns this into a real `InputMethodSubtype`. */
data class AdditionalSubtypeSpec(
    val locale: String,
    val layoutId: String,
    val extraValue: String,
    val id: Int,
)

/**
 * Builds the additional-subtype list SS8.3 registers with Android from `custom_input_styles`,
 * pure (no android.* import) so `:ime` only has to turn each [AdditionalSubtypeSpec] into a real
 * `InputMethodSubtype` with `InputMethodSubtypeBuilder`. spec: dictionaries-languages.md SS8.3.
 */
object AdditionalSubtypeBuilder {

    private val LOCALE_PATTERN = Regex("^[a-zA-Z]{2,3}([_-][a-zA-Z]{2,3})?$")

    /**
     * SS8.3's per-entry algorithm, in order: skip an entry whose locale does not parse or whose
     * layout is not [availableLayoutIds] (step 1); skip one that would duplicate a base subtype
     * (step 2); otherwise build its [AdditionalSubtypeSpec] (step 3). A duplicate `locale:layout`
     * key among the survivors keeps only the first, matching how `custom_input_styles` is meant to
     * already be free of duplicates (SS8.2's own "already exists" refusal).
     */
    fun build(inputStyles: List<String>, availableLayoutIds: Set<String> = BundledLayoutIds.ALL): List<AdditionalSubtypeSpec> {
        val baseLayoutByLocale = BaseSubtypes.ALL.associate { normalizedKey(it.locale) to it.mappedLayoutId }
        val seenKeys = mutableSetOf<String>()
        val specs = mutableListOf<AdditionalSubtypeSpec>()
        for (raw in inputStyles) {
            val parts = raw.split(':').map { it.trim() }
            if (parts.size < 2) continue
            val locale = parts[0]
            val layoutId = parts[1]
            val extra = parts.getOrNull(2)
            if (locale.isEmpty() || layoutId.isEmpty()) continue
            if (!LOCALE_PATTERN.matches(locale)) continue
            if (layoutId !in availableLayoutIds) continue
            val key = normalizedKey("$locale:$layoutId")
            if (!seenKeys.add(key)) continue
            val baseLayout = baseLayoutByLocale[normalizedKey(locale)]
            if (baseLayout != null && baseLayout.equals(layoutId, ignoreCase = true)) continue
            specs += AdditionalSubtypeSpec(
                locale = locale,
                layoutId = layoutId,
                extraValue = extraValue(layoutId, extra),
                id = stableId(locale, layoutId),
            )
        }
        return specs
    }

    /** SS8.3 step 3: `KeyboardLayoutSet=<layout>,AsciiCapable,EmojiCapable,isAdditionalSubtype[,<extra>]`. */
    private fun extraValue(layoutId: String, extra: String?): String = buildString {
        append("KeyboardLayoutSet=").append(layoutId)
        append(",AsciiCapable,EmojiCapable,isAdditionalSubtype")
        if (!extra.isNullOrBlank()) append(',').append(extra)
    }

    /** SS8.3 step 3: "a stable id derived from the text `pastiera-subtype-v2|<locale>|<layout>` (never 0)." */
    fun stableId(locale: String, layoutId: String): Int {
        val hash = "pastiera-subtype-v2|$locale|$layoutId".hashCode()
        return if (hash == 0) 1 else hash
    }

    private fun normalizedKey(key: String): String = key.replace('_', '-').lowercase()
}
