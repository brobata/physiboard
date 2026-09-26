package brobata.physiboard.core.subtype

import brobata.physiboard.core.keys.LayoutDescription
import brobata.physiboard.core.settings.LanguagePrefs

/**
 * Builds the list of [InputStyle]s a user can switch between and answers what one switch changes.
 * spec: dictionaries-languages.md SS8 (input styles), SS9 (switching). Android's own subtype
 * machinery (SS8.3's registration, SS9.3 steps 1-2's "Android's list of enabled subtypes") is out
 * of scope: this module has no `android.*` import and 3.0 registers no additional subtypes, so
 * the list and the cycle below are this project's own, built straight from [LanguagePrefs], not a
 * query against the platform.
 */
object InputStyleCatalog {

    /**
     * spec SS8.2's row list, scoped to this project: every entry of [shipped] not named in
     * `hidden_system_input_styles`, then every `custom_input_styles` entry not already present
     * (first occurrence of a `locale:layout` key wins, matching SS8.2's "deduplicated by
     * locale:layout"). A malformed custom entry (fewer than the two required `:`-separated
     * parts, or either part blank) is skipped, matching SS8.3 step 1's "skip it when the locale
     * does not parse".
     */
    fun availableStyles(shipped: List<ShippedLayout>, prefs: LanguagePrefs): List<InputStyle> {
        val hidden = prefs.hiddenSystemInputStyles.map(::normalizedKey).toSet()
        val shippedStyles = shipped
            .map { InputStyle(locale = it.defaultLocale, layoutId = it.layoutId, shipped = true) }
            .filter { normalizedKey(it.key) !in hidden }
        val seen = shippedStyles.mapTo(mutableSetOf()) { normalizedKey(it.key) }
        val customStyles = prefs.inputStyles.mapNotNull(::parseCustomEntry).filter { seen.add(normalizedKey(it.key)) }
        return shippedStyles + customStyles
    }

    /** spec SS8.2's preference format: `locale:layout` or `locale:layout:extra`; the optional third part names a launcher-shortcut extra this module has no use for. */
    private fun parseCustomEntry(raw: String): InputStyle? {
        val parts = raw.split(':').map { it.trim() }
        if (parts.size < 2) return null
        val locale = parts[0]
        val layoutId = parts[1]
        if (locale.isEmpty() || layoutId.isEmpty()) return null
        return InputStyle(locale = locale, layoutId = layoutId, shipped = false)
    }

    private fun normalizedKey(key: String): String = key.replace('_', '-').lowercase()

    /**
     * spec SS9.3 step 4, scoped to this project's own list rather than Android's: the row after
     * the one keyed [currentKey], wrapping to the first; the first row when [currentKey] matches
     * none of [styles] (spec: "when the current one is not in the list, go to the first"). Null
     * only when [styles] is empty, which cannot happen once [availableStyles] has run against a
     * non-empty [shipped] list.
     */
    fun next(styles: List<InputStyle>, currentKey: String): InputStyle? {
        if (styles.isEmpty()) return null
        val index = styles.indexOfFirst { normalizedKey(it.key) == normalizedKey(currentKey) }
        val nextIndex = if (index < 0) 0 else (index + 1) % styles.size
        return styles[nextIndex]
    }

    /** The row [currentKey] names, or the first row when it names none of [styles] (a deleted custom style, or a fresh session with no key chosen yet). Null only when [styles] is empty. */
    fun current(styles: List<InputStyle>, currentKey: String): InputStyle? =
        styles.firstOrNull { normalizedKey(it.key) == normalizedKey(currentKey) } ?: styles.firstOrNull()

    /**
     * spec keys-and-modifiers.md SS7.5's 2026-09-24 amendment ("with a single layout there is
     * nothing to switch"): true whenever a second row exists to land on. `:ime` feeds this
     * straight to [brobata.physiboard.core.keys.LayerResolver.Context.canSwitchLayout] (by way of
     * `KeyboardPipeline.anotherSubtypeAvailable`).
     */
    fun anotherStyleAvailable(styles: List<InputStyle>): Boolean = styles.size > 1

    /**
     * spec dictionaries-languages.md SS10's "automatic... resolves the layout from the subtype or
     * mapping", read here for `keyboard_layout_auto_by_locale` once, at startup: with
     * [autoByLocale] off the catalog's own first (shipped) row is kept; with it on, the first row
     * whose language matches [systemLocale]'s, or the first row when none does. `:ime` now also
     * has a subtype-changed callback (SS9.4, `KeyboardSession.onCurrentInputMethodSubtypeChanged`)
     * that keeps the running style in sync with Android's own choice after startup; this function
     * only decides the one-time starting point.
     */
    fun startupStyle(styles: List<InputStyle>, systemLocale: String, autoByLocale: Boolean): InputStyle? {
        if (styles.isEmpty()) return null
        if (!autoByLocale) return styles.first()
        val language = normalizedLanguage(systemLocale)
        return styles.firstOrNull { normalizedLanguage(it.locale) == language } ?: styles.first()
    }

    private fun normalizedLanguage(locale: String): String = locale.replace('_', '-').substringBefore('-').lowercase()

    /**
     * Resolves [style]'s own layout id to the [LayoutDescription] it should type with, falling
     * back to [shipped]'s first entry when [style] names a layout this device does not ship.
     * Null only when [shipped] itself is empty. `:device:titan`'s `TitanLayouts.bundled()` now
     * gives [shipped] all of layers-sym-alt.md SS9.2's eighteen names, so a custom style naming
     * any bundled layout id types with that layout's own key map, not a fallback.
     */
    fun layoutFor(style: InputStyle, shipped: List<ShippedLayout>): LayoutDescription? =
        (shipped.firstOrNull { it.layoutId == style.layoutId } ?: shipped.firstOrNull())?.layout

    /**
     * spec SS9.3 step 6's switch toast text: `<layout id> | <PRIMARY> | <EXTRA1>, <EXTRA2>`, the
     * extra segment only when [extraLanguages] is non-empty. [extraLanguages] are bare language
     * codes the caller already resolved (`:core:dict`'s `LanguageCode.value`); this function only
     * formats them, matching the house rule that `:core:subtype` never depends on `:core:dict`.
     *
     * SPEC GAP: SS9.3 names the layout's own display name ("the part before ' | '" of its
     * metadata); no layout-metadata catalog exists in this module or its caller, so
     * [InputStyle.layoutId] is shown in its place.
     */
    fun switchToastText(style: InputStyle, extraLanguages: List<String> = emptyList()): String {
        val segments = mutableListOf(style.layoutId, style.primaryLanguage.uppercase())
        if (extraLanguages.isNotEmpty()) segments += extraLanguages.joinToString(", ") { it.uppercase() }
        return segments.joinToString(" | ")
    }
}
