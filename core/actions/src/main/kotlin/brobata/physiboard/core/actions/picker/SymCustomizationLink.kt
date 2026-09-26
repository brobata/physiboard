package brobata.physiboard.core.actions.picker

/**
 * The intent-extra names the keyboard and the "Customize SYM Keyboard" settings screen agree on.
 * spec: layers-sym-alt.md SS5.8: the pencil on the Sym grid overlay and a long press on a grid key
 * both open this screen, and with [EXTRA_OPEN_SYM_PICKER] and [EXTRA_RETURN_AFTER_PICKER] both
 * true and a key code present "the picker opens immediately and the screen finishes as soon as
 * the picker closes". Plain string constants (no android import) so `:ime` (building the
 * [android.content.Intent]) and `:app` (reading it back) share one spelling without either module
 * depending on the other.
 */
object SymCustomizationLink {
    const val EXTRA_INITIAL_SYM_PAGE: String = "brobata.physiboard.extra.INITIAL_SYM_PAGE"
    const val EXTRA_INITIAL_SYM_KEY_CODE: String = "brobata.physiboard.extra.INITIAL_SYM_KEY_CODE"
    const val EXTRA_OPEN_SYM_PICKER: String = "brobata.physiboard.extra.OPEN_SYM_PICKER"
    const val EXTRA_RETURN_AFTER_PICKER: String = "brobata.physiboard.extra.RETURN_AFTER_PICKER"
}
