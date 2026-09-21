package brobata.physiboard.core.keys

/**
 * The identity of a physical key, independent of any platform's keycode space.
 *
 * spec: keys-and-modifiers.md SS1.1 ("PhysiBoard uses all of these... Everything else keys off
 * the keycode") and SS19 D2 (the Titan's scancode-to-key table); layers-sym-alt.md SS3.1 and
 * SS9.1 (mapping and layout files are keyed by physical key name, not by the character a layout
 * prints on the key).
 *
 * A device module (for example `:device:titan`) owns translating its own scancodes and
 * platform keycodes onto this set before a [KeyStroke] ever reaches this module. Letters and
 * digits are named for the physical QWERTY position they occupy, because every mapping file the
 * spec describes (base layout, device layer, Sym page, Ctrl/Fn Layer mapping) is keyed by
 * physical position: "ALT, SYM and Ctrl mappings remain based on physical key position"
 * (layers-sym-alt.md SS9.2).
 */
sealed class KeyId {

    /** One of the 26 physical letter keys, named for the QWERTY letter printed in that position. */
    data class Letter(val qwertyLetter: Char) : KeyId() {
        init {
            require(qwertyLetter in 'A'..'Z') { "letter key must be A-Z, was '$qwertyLetter'" }
        }

        override fun toString(): String = "Letter($qwertyLetter)"
    }

    /** One of the ten physical number-row keys. */
    data class Digit(val digit: Char) : KeyId() {
        init {
            require(digit in '0'..'9') { "digit key must be 0-9, was '$digit'" }
        }

        override fun toString(): String = "Digit($digit)"
    }

    /** A punctuation key named in layers-sym-alt.md SS3.1's recognised key-name list. */
    data class Punctuation(val key: PunctuationKey) : KeyId()

    /** Shift, Ctrl, Alt, Sym or Fn: the keys the modifier state machine owns. */
    data class Modifier(val key: ModifierKey) : KeyId()

    /** Every other named key the spec refers to by function rather than by character. */
    data class Control(val key: ControlKey) : KeyId()
}

/** spec: layers-sym-alt.md SS3.1. */
enum class PunctuationKey {
    GRAVE, MINUS, EQUALS, LEFT_BRACKET, RIGHT_BRACKET, BACKSLASH,
    SEMICOLON, APOSTROPHE, COMMA, PERIOD, SLASH,
}

/** spec: keys-and-modifiers.md SS2 (Shift, Ctrl, Alt), SS4 (Sym), SS3 (Fn). */
enum class ModifierKey { SHIFT, CTRL, ALT, SYM, FN }

/**
 * Named keys with no letter identity of their own.
 *
 * `SWIPE_TO_DELETE` stands for the Titan's two swipe-to-delete scancodes (keys-and-modifiers.md
 * D10, SS7.1): the device module normalises both hardware keycodes to this one identity, since
 * this module treats them identically regardless of which physical scancode fired.
 */
enum class ControlKey {
    SPACE, ENTER, BACKSPACE, TAB, BACK, ESCAPE,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER,
    MOVE_HOME, MOVE_END, PAGE_UP, PAGE_DOWN, FORWARD_DELETE,
    SWIPE_TO_DELETE,
    MEDIA_PLAY_PAUSE, MEDIA_PREVIOUS, MEDIA_NEXT,
    VOLUME_UP, VOLUME_DOWN, POWER, APP_SWITCH, HOME, RECENT,
}
