package brobata.physiboard.device.titan

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.ModifierKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * spec: keys-and-modifiers.md SS1.1, SS3.1-3.2 (Fn survives the vendor remap by scancode, D2, D4,
 * D5), SS4 (Sym is keycode 63, D8), SS7.1 (swipe-to-delete keycodes, D10). Literal Android keycode
 * and action values are used throughout, exactly as `:ime` would pass them off the real event.
 */
class KeyNormalizerTest {

    // Fn recognition: D2, D4, D5 -----------------------------------------------------------------

    @Test
    fun `a held Fn arrives as keycode CTRL_LEFT with scancode 251 and normalizes to Fn`() {
        // keycode 113 = CTRL_LEFT, scancode 251 = Fn (FUNC3), repeat 1: the vendor's first delivered repeat.
        val stroke = KeyNormalizer.normalize(
            keyCode = 113, scanCode = 251, action = 0, repeatCount = 1, metaState = 0x1000, deviceId = 1, eventTimeMs = 400,
        )
        assertEquals(KeyId.Modifier(ModifierKey.FN), stroke?.key)
        assertEquals(KeyEdge.DOWN, stroke?.edge)
        assertEquals(1, stroke?.repeatCount)
    }

    @Test
    fun `KEYCODE_FUNCTION alone also normalizes to Fn`() {
        // keycode 119 = KEYCODE_FUNCTION, an unrelated scancode: SS3.2's second recognition path.
        val stroke = KeyNormalizer.normalize(keyCode = 119, scanCode = 999, action = 0, repeatCount = 0, metaState = 0, deviceId = 1, eventTimeMs = 0)
        assertEquals(KeyId.Modifier(ModifierKey.FN), stroke?.key)
    }

    @Test
    fun `a real Ctrl keycode with no Fn scancode normalizes to Ctrl, for an external keyboard`() {
        val stroke = KeyNormalizer.normalize(keyCode = 113, scanCode = 29, action = 0, repeatCount = 0, metaState = 0, deviceId = 2, eventTimeMs = 0)
        assertEquals(KeyId.Modifier(ModifierKey.CTRL), stroke?.key)
    }

    // Sym recognition: D8 -------------------------------------------------------------------------

    @Test
    fun `keycode 63 normalizes to Sym regardless of scancode`() {
        val stroke = KeyNormalizer.normalize(keyCode = 63, scanCode = 253, action = 0, repeatCount = 0, metaState = 0, deviceId = 1, eventTimeMs = 0)
        assertEquals(KeyId.Modifier(ModifierKey.SYM), stroke?.key)
    }

    // Letters and digits ---------------------------------------------------------------------------

    @Test
    fun `every letter keycode 29 to 54 round-trips to its QWERTY letter`() {
        for (offset in 0..25) {
            val stroke = KeyNormalizer.normalize(keyCode = 29 + offset, scanCode = 0, action = 0, repeatCount = 0, metaState = 0, deviceId = 1, eventTimeMs = 0)
            assertEquals(KeyId.Letter('A' + offset), stroke?.key)
        }
    }

    @Test
    fun `every digit keycode 7 to 16 round-trips to its digit`() {
        for (offset in 0..9) {
            val stroke = KeyNormalizer.normalize(keyCode = 7 + offset, scanCode = 0, action = 0, repeatCount = 0, metaState = 0, deviceId = 1, eventTimeMs = 0)
            assertEquals(KeyId.Digit('0' + offset), stroke?.key)
        }
    }

    // Swipe-to-delete: D10 -------------------------------------------------------------------------

    @Test
    fun `both swipe-to-delete keycodes normalize to the one SWIPE_TO_DELETE identity`() {
        val first = KeyNormalizer.normalize(keyCode = 322, scanCode = 0, action = 0, repeatCount = 0, metaState = 0, deviceId = 1, eventTimeMs = 0)
        val second = KeyNormalizer.normalize(keyCode = 404, scanCode = 0, action = 0, repeatCount = 0, metaState = 0, deviceId = 1, eventTimeMs = 0)
        assertEquals(KeyId.Control(ControlKey.SWIPE_TO_DELETE), first?.key)
        assertEquals(KeyId.Control(ControlKey.SWIPE_TO_DELETE), second?.key)
    }

    // Meta flags -------------------------------------------------------------------------------------

    @Test
    fun `meta state bits decode into the ModifierFlags the stroke carries`() {
        // shift 0x1, ctrl 0x1000, alt 0x2, sym 0x4 all set.
        val stroke = KeyNormalizer.normalize(keyCode = 29, scanCode = 0, action = 0, repeatCount = 0, metaState = 0x1 or 0x2 or 0x4 or 0x1000, deviceId = 1, eventTimeMs = 0)
        val meta = requireNotNull(stroke).meta
        assertEquals(true, meta.shift)
        assertEquals(true, meta.ctrl)
        assertEquals(true, meta.alt)
        assertEquals(true, meta.sym)
    }

    // Unrecognised events -------------------------------------------------------------------------

    @Test
    fun `an action other than down or up produces no stroke`() {
        val stroke = KeyNormalizer.normalize(keyCode = 29, scanCode = 0, action = 2, repeatCount = 0, metaState = 0, deviceId = 1, eventTimeMs = 0)
        assertNull(stroke)
    }

    @Test
    fun `a keycode with no KeyId mapping produces no stroke`() {
        val stroke = KeyNormalizer.normalize(keyCode = 999_999, scanCode = 0, action = 0, repeatCount = 0, metaState = 0, deviceId = 1, eventTimeMs = 0)
        assertNull(stroke)
    }

    @Test
    fun `deviceId, event time and repeat count pass through unchanged`() {
        val stroke = requireNotNull(
            KeyNormalizer.normalize(keyCode = 62, scanCode = 57, action = 1, repeatCount = 3, metaState = 0, deviceId = 7, eventTimeMs = 12345),
        )
        assertEquals(ControlKey.SPACE, (stroke.key as KeyId.Control).key)
        assertEquals(KeyEdge.UP, stroke.edge)
        assertEquals(3, stroke.repeatCount)
        assertEquals(12345L, stroke.timeMs)
        assertEquals(7, stroke.deviceId)
    }
}
