package brobata.physiboard.core.subtype

import brobata.physiboard.core.keys.LayoutDescription

/**
 * One physical layout this build can actually type with, named the way `keyboard_layout` and
 * `custom_input_styles` name a layout, and the locale it defaults to when nothing more specific
 * picks one. spec: dictionaries-languages.md SS8.1 (a base subtype pairs a locale with a mapped
 * layout), SS10 (the locale-to-layout mapping this project does not reproduce in full).
 *
 * `:ime` builds the (today one-entry) list of these from whichever `:device:*` module it links,
 * e.g. `TitanLayouts.titan2EliteQwerty()` under id `"qwerty"`; this module never constructs a
 * [LayoutDescription] itself, only a device module knows what one looks like.
 */
data class ShippedLayout(
    val layoutId: String,
    val defaultLocale: String,
    val layout: LayoutDescription,
)
