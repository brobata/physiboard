package brobata.physiboard.core.subtype

/**
 * One row of layers-sym-alt.md SS9.2's bundled layout table, its display name and note, for the
 * pickers over [BundledLayoutIds.ALL] (SS9.6's Keyboard Layout screen and, in place of the free-text
 * layout field it replaces, the Input Styles screen's own layout picker; SS9.6's "Standard" note is
 * [note] for `qwerty`).
 */
data class BundledLayoutInfo(val id: String, val displayName: String, val note: String)

/** spec: layers-sym-alt.md SS9.2's table, in the table's own order. */
object BundledLayoutCatalog {
    val ALL: List<BundledLayoutInfo> = listOf(
        BundledLayoutInfo("qwerty", "QWERTY", "identity; Standard QWERTY layout (no conversion)"),
        BundledLayoutInfo("qwertz", "QWERTZ | Deutsch", "Y and Z swapped"),
        BundledLayoutInfo("azerty", "AZERTY", "French positions"),
        BundledLayoutInfo("german_multitap_qwertz", "QWERTZ Multi-Tap", "umlauts and ß by repeated taps"),
        BundledLayoutInfo("turkish_multitap", "Turkish (multi-tap)", ""),
        BundledLayoutInfo("norwegian_multitap_qwerty", "Norwegian (multi-tap)", ""),
        BundledLayoutInfo("arabic", "Arabic", ""),
        BundledLayoutInfo("armenian_phonetic", "Armenian phonetic (multi-tap)", ""),
        BundledLayoutInfo("bulgarian_phonetic", "Bulgarian Phonetic", ""),
        BundledLayoutInfo("bulgarian_phonetic_traditional", "Bulgarian Phonetic (Traditional)", ""),
        BundledLayoutInfo("Cyrillic_Translite", "Cyrillic Translite", ""),
        BundledLayoutInfo("greek", "Greek", ""),
        BundledLayoutInfo("russian_jcuken", "Russian (JCUKEN compact)", ""),
        BundledLayoutInfo("russian_standard", "Russian (ЙЦУКЕН)", ""),
        BundledLayoutInfo("russian_translit", "Russian (multi-tap)", ""),
        BundledLayoutInfo("serbian_cyrillic", "Serbian Cyrillic", ""),
        BundledLayoutInfo("ukrainian", "Ukrainian", ""),
        BundledLayoutInfo("vietnamese_telex_qwerty", "Tiếng Việt (TELEX, QWERTY)", "the live Telex composer is out of scope for 3.0; this ships the key map only"),
    )

    private val byId: Map<String, BundledLayoutInfo> = ALL.associateBy { it.id }

    /** The catalogue row for [layoutId], or a row that just echoes the id back for a custom (non-bundled) layout name. */
    fun infoFor(layoutId: String): BundledLayoutInfo = byId[layoutId] ?: BundledLayoutInfo(layoutId, layoutId, "")
}
