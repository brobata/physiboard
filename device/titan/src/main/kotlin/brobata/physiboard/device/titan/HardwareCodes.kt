package brobata.physiboard.device.titan

/**
 * The Titan 2 Elite's built-in keyboard scancode table, `TitanKey` at `/dev/input/event5`.
 *
 * spec: keys-and-modifiers.md D2, D8; device-backlight-ring.md D8. PhysiBoard keys off the
 * Android keycode for everything except recognising Fn (keys-and-modifiers.md SS1.1, SS3.2), so
 * only [FN] is read by [KeyNormalizer]; the rest of this table exists so a later module (the Key
 * mapping inventory screen, keys-and-modifiers.md SS17) can show the hardware identity of every
 * key without this module's clean-room facts leaking anywhere else.
 */
object ScanCodes {
    /** FUNC3. The only scancode this module's normalizer reads. D2, D4, D5. */
    const val FN = 251

    /** AGUI_SYM. Sym is recognised by keycode 63, not this scancode (SS3.2); kept for the inventory. D8. */
    const val SYM = 253

    const val BACKSPACE = 14
    const val ALT_LEFT = 56
    const val ENTER = 28
    const val SHIFT_LEFT = 42
    const val SHIFT_RIGHT = 54
    const val BACK = 158
    const val HOME = 102
    const val SPACE = 57
    const val APP_SWITCH = 580

    /** The orange side key (func1). Never reaches an input method; the vendor launches its own binding directly. SS17, D11. */
    const val ORANGE_SIDE_KEY = 249

    /** gpio-keys. Never filtered by any PhysiBoard feature. SS17, D13. */
    const val VOLUME_UP = 115

    /** gpio-keys. Never filtered by any PhysiBoard feature. SS17, D13. */
    const val VOLUME_DOWN = 114

    /** ff_key. Never filtered by any PhysiBoard feature. SS17, D13. */
    const val POWER = 116

    private val topRow = (16..25).zip("QWERTYUIOP".toList())
    private val homeRow = (30..38).zip("ASDFGHJKL".toList())
    private val bottomRow = (44..50).zip("ZXCVBNM".toList())

    /** Every letter key's hardware scancode. Reference only: letters are identified by keycode. D2. */
    val letterByScanCode: Map<Int, Char> = (topRow + homeRow + bottomRow).toMap()
}

/**
 * Non-standard Android keycodes the Titan 2's vendor firmware synthesizes for its own gestures,
 * not part of the platform's stable keycode space.
 *
 * spec: keys-and-modifiers.md D10 ("The Titan 2 delivers keycodes 322 and 404 for the keyboard's
 * swipe-to-delete gesture").
 */
object VendorKeyCodes {
    const val SWIPE_TO_DELETE_PRIMARY = 322
    const val SWIPE_TO_DELETE_SECONDARY = 404
}
