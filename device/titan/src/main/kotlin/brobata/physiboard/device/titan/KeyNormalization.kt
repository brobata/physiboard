package brobata.physiboard.device.titan

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.keys.PunctuationKey

/**
 * The stable `android.view.KeyEvent` integer constants [KeyNormalizer] needs, reproduced here
 * (never imported) because neither this module nor `:core:keys` may carry an `android.*`
 * dependency. These are the platform's own public, unchanging API values, not a Titan fact; the
 * Titan-specific decisions live in [ScanCodes] and [KeyNormalizer].
 */
internal object AndroidKeyEvent {
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1

    const val META_SHIFT_ON = 0x1
    const val META_ALT_ON = 0x2
    const val META_SYM_ON = 0x4
    const val META_CTRL_ON = 0x1000

    const val KEYCODE_HOME = 3
    const val KEYCODE_BACK = 4
    const val KEYCODE_0 = 7 // 0-9 are the consecutive keycodes 7..16
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_POWER = 26
    const val KEYCODE_A = 29 // A-Z are the consecutive keycodes 29..54
    const val KEYCODE_COMMA = 55
    const val KEYCODE_PERIOD = 56
    const val KEYCODE_ALT_LEFT = 57
    const val KEYCODE_ALT_RIGHT = 58
    const val KEYCODE_SHIFT_LEFT = 59
    const val KEYCODE_SHIFT_RIGHT = 60
    const val KEYCODE_TAB = 61
    const val KEYCODE_SPACE = 62
    const val KEYCODE_SYM = 63
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67
    const val KEYCODE_GRAVE = 68
    const val KEYCODE_MINUS = 69
    const val KEYCODE_EQUALS = 70
    const val KEYCODE_LEFT_BRACKET = 71
    const val KEYCODE_RIGHT_BRACKET = 72
    const val KEYCODE_BACKSLASH = 73
    const val KEYCODE_SEMICOLON = 74
    const val KEYCODE_APOSTROPHE = 75
    const val KEYCODE_SLASH = 76
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_NEXT = 87
    const val KEYCODE_MEDIA_PREVIOUS = 88
    const val KEYCODE_PAGE_UP = 92
    const val KEYCODE_PAGE_DOWN = 93
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_FORWARD_DEL = 112
    const val KEYCODE_CTRL_LEFT = 113
    const val KEYCODE_CTRL_RIGHT = 114
    const val KEYCODE_FUNCTION = 119
    const val KEYCODE_MOVE_HOME = 122
    const val KEYCODE_MOVE_END = 123
    const val KEYCODE_APP_SWITCH = 187
}

/**
 * Normalises one raw Titan 2 Elite hardware key event into the [KeyStroke] the `:core:keys`
 * pipeline understands. This is the clean boundary the whole rebuild is organised around: the
 * `:ime` module pulls [keyCode], [scanCode] and the rest off the platform `KeyEvent` as plain
 * values and hands them here, so no `android.*` type ever needs to appear in a pure-Kotlin,
 * JVM-tested module.
 *
 * spec: keys-and-modifiers.md SS1.1 ("PhysiBoard uses all of these... It reads the scancode for
 * one thing only: recognising the Fn key. Everything else keys off the keycode"), SS3.1-3.2 (Fn
 * recognition survives the vendor's Fn-to-Ctrl remap, D2, D4, D5), SS4 (Sym is keycode 63, D8),
 * SS7.1 (the swipe-to-delete keycodes, D10).
 *
 * Returns null for an event this pipeline does not recognise: an action other than down/up, or a
 * keycode with no [KeyId]. The caller is expected to let the platform's default handling run for
 * a null result, exactly as an unmapped key falls through untouched in the spec (SS14).
 */
object KeyNormalizer {

    fun normalize(
        keyCode: Int,
        scanCode: Int,
        action: Int,
        repeatCount: Int,
        metaState: Int,
        deviceId: Int,
        eventTimeMs: Long,
    ): KeyStroke? {
        val edge = when (action) {
            AndroidKeyEvent.ACTION_DOWN -> KeyEdge.DOWN
            AndroidKeyEvent.ACTION_UP -> KeyEdge.UP
            else -> return null
        }
        val keyId = resolveKeyId(keyCode, scanCode) ?: return null

        val meta = ModifierFlags(
            shift = metaState and AndroidKeyEvent.META_SHIFT_ON != 0,
            ctrl = metaState and AndroidKeyEvent.META_CTRL_ON != 0,
            alt = metaState and AndroidKeyEvent.META_ALT_ON != 0,
            sym = metaState and AndroidKeyEvent.META_SYM_ON != 0,
        )

        return KeyStroke(
            key = keyId,
            edge = edge,
            repeatCount = repeatCount,
            timeMs = eventTimeMs,
            meta = meta,
            deviceId = deviceId,
        )
    }

    /**
     * spec: keys-and-modifiers.md SS3.2 ("A key event is treated as Fn-origin when its scancode
     * equals `fn_speech_scan_code` (default 251) or its keycode is KEYCODE_FUNCTION (119)"). This
     * runs before any keycode-based mapping so a held Fn (which arrives as keycode CTRL_LEFT with
     * scancode 251, D4, D5) is never mistaken for a real Ctrl key: the scancode match is what
     * survives the vendor's Fn-to-Ctrl remap regardless of which keycode it produces. `keep,
     * unconditional` per SS22 Keep/Drop: the scancode is hard-coded, not the settable
     * `fn_speech_scan_code` preference, because it is fixed by this device's key layout.
     */
    private fun isFnOrigin(keyCode: Int, scanCode: Int): Boolean =
        scanCode == ScanCodes.FN || keyCode == AndroidKeyEvent.KEYCODE_FUNCTION

    private fun resolveKeyId(keyCode: Int, scanCode: Int): KeyId? {
        if (isFnOrigin(keyCode, scanCode)) return KeyId.Modifier(ModifierKey.FN)

        return when (keyCode) {
            AndroidKeyEvent.KEYCODE_SYM -> KeyId.Modifier(ModifierKey.SYM) // D8
            AndroidKeyEvent.KEYCODE_SHIFT_LEFT, AndroidKeyEvent.KEYCODE_SHIFT_RIGHT -> KeyId.Modifier(ModifierKey.SHIFT)
            // D3: no Ctrl key exists in Titan hardware. A real CTRL_LEFT/RIGHT here is either an
            // external keyboard's own Ctrl, or the vendor's Fn-to-Ctrl remap already excluded above.
            AndroidKeyEvent.KEYCODE_CTRL_LEFT, AndroidKeyEvent.KEYCODE_CTRL_RIGHT -> KeyId.Modifier(ModifierKey.CTRL)
            AndroidKeyEvent.KEYCODE_ALT_LEFT, AndroidKeyEvent.KEYCODE_ALT_RIGHT -> KeyId.Modifier(ModifierKey.ALT)

            in AndroidKeyEvent.KEYCODE_A..(AndroidKeyEvent.KEYCODE_A + 25) ->
                KeyId.Letter('A' + (keyCode - AndroidKeyEvent.KEYCODE_A))
            in AndroidKeyEvent.KEYCODE_0..(AndroidKeyEvent.KEYCODE_0 + 9) ->
                KeyId.Digit('0' + (keyCode - AndroidKeyEvent.KEYCODE_0))

            AndroidKeyEvent.KEYCODE_GRAVE -> KeyId.Punctuation(PunctuationKey.GRAVE)
            AndroidKeyEvent.KEYCODE_MINUS -> KeyId.Punctuation(PunctuationKey.MINUS)
            AndroidKeyEvent.KEYCODE_EQUALS -> KeyId.Punctuation(PunctuationKey.EQUALS)
            AndroidKeyEvent.KEYCODE_LEFT_BRACKET -> KeyId.Punctuation(PunctuationKey.LEFT_BRACKET)
            AndroidKeyEvent.KEYCODE_RIGHT_BRACKET -> KeyId.Punctuation(PunctuationKey.RIGHT_BRACKET)
            AndroidKeyEvent.KEYCODE_BACKSLASH -> KeyId.Punctuation(PunctuationKey.BACKSLASH)
            AndroidKeyEvent.KEYCODE_SEMICOLON -> KeyId.Punctuation(PunctuationKey.SEMICOLON)
            AndroidKeyEvent.KEYCODE_APOSTROPHE -> KeyId.Punctuation(PunctuationKey.APOSTROPHE)
            AndroidKeyEvent.KEYCODE_COMMA -> KeyId.Punctuation(PunctuationKey.COMMA)
            AndroidKeyEvent.KEYCODE_PERIOD -> KeyId.Punctuation(PunctuationKey.PERIOD)
            AndroidKeyEvent.KEYCODE_SLASH -> KeyId.Punctuation(PunctuationKey.SLASH)

            AndroidKeyEvent.KEYCODE_SPACE -> KeyId.Control(ControlKey.SPACE)
            AndroidKeyEvent.KEYCODE_ENTER -> KeyId.Control(ControlKey.ENTER)
            AndroidKeyEvent.KEYCODE_DEL -> KeyId.Control(ControlKey.BACKSPACE)
            AndroidKeyEvent.KEYCODE_TAB -> KeyId.Control(ControlKey.TAB)
            AndroidKeyEvent.KEYCODE_BACK -> KeyId.Control(ControlKey.BACK)
            AndroidKeyEvent.KEYCODE_ESCAPE -> KeyId.Control(ControlKey.ESCAPE)
            AndroidKeyEvent.KEYCODE_DPAD_UP -> KeyId.Control(ControlKey.DPAD_UP)
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> KeyId.Control(ControlKey.DPAD_DOWN)
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> KeyId.Control(ControlKey.DPAD_LEFT)
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> KeyId.Control(ControlKey.DPAD_RIGHT)
            AndroidKeyEvent.KEYCODE_DPAD_CENTER -> KeyId.Control(ControlKey.DPAD_CENTER)
            AndroidKeyEvent.KEYCODE_MOVE_HOME -> KeyId.Control(ControlKey.MOVE_HOME)
            AndroidKeyEvent.KEYCODE_MOVE_END -> KeyId.Control(ControlKey.MOVE_END)
            AndroidKeyEvent.KEYCODE_PAGE_UP -> KeyId.Control(ControlKey.PAGE_UP)
            AndroidKeyEvent.KEYCODE_PAGE_DOWN -> KeyId.Control(ControlKey.PAGE_DOWN)
            AndroidKeyEvent.KEYCODE_FORWARD_DEL -> KeyId.Control(ControlKey.FORWARD_DELETE)
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> KeyId.Control(ControlKey.MEDIA_PLAY_PAUSE)
            AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> KeyId.Control(ControlKey.MEDIA_PREVIOUS)
            AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> KeyId.Control(ControlKey.MEDIA_NEXT)
            AndroidKeyEvent.KEYCODE_VOLUME_UP -> KeyId.Control(ControlKey.VOLUME_UP)
            AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> KeyId.Control(ControlKey.VOLUME_DOWN)
            AndroidKeyEvent.KEYCODE_POWER -> KeyId.Control(ControlKey.POWER)
            AndroidKeyEvent.KEYCODE_APP_SWITCH -> KeyId.Control(ControlKey.APP_SWITCH)
            AndroidKeyEvent.KEYCODE_HOME -> KeyId.Control(ControlKey.HOME)

            // D10: both Titan swipe-to-delete keycodes normalise to one identity; core:keys
            // decides what to do with them based on settings (SS7.1).
            VendorKeyCodes.SWIPE_TO_DELETE_PRIMARY, VendorKeyCodes.SWIPE_TO_DELETE_SECONDARY -> KeyId.Control(ControlKey.SWIPE_TO_DELETE)

            else -> null
        }
    }
}
