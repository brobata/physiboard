package brobata.physiboard.core.subtype

/**
 * One row of the "Input Languages" list: a locale and layout id pair PhysiBoard can type with,
 * and whether it is the device's own shipped row or one the user added. spec:
 * dictionaries-languages.md SS8.2 ("Rows come from two sources... deduplicated by locale:layout").
 *
 * [key] is the `locale:layout` form both `custom_input_styles` (SS8.2) and
 * `input_style_suggestion_locales` (SS8.4) key their entries by; [primaryLanguage] is [locale]'s
 * bare language part (SS8.7, "the language part of the subtype... region ignored"), the form
 * `:core:dict`'s `LanguageCode` parses a dictionary by. This module never constructs a
 * `LanguageCode` itself (no dependency on `:core:dict`), so it stays a plain string here; the
 * caller converts it.
 */
data class InputStyle(
    val locale: String,
    val layoutId: String,
    val shipped: Boolean,
) {
    val key: String get() = "$locale:$layoutId"
    val primaryLanguage: String get() = locale.replace('_', '-').substringBefore('-').lowercase()
}
