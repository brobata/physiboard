package brobata.physiboard.core.actions.emoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS4.3, the modes of the emoji page. */
class PickerModesTest {

    @Test
    fun `the emoji page opens on emoji every time, whatever the last visit used`() {
        // A visit that ended in kaomoji or symbols leaves nothing behind: an open with no request is emoji.
        for (kaomoji in listOf(false, true)) {
            assertEquals(PickerMode.EMOJI, PickerModes.openingMode(null, kaomoji))
            assertEquals(PickerMode.EMOJI, PickerModes.openingMode(PickerMode.EMOJI, kaomoji))
        }
    }

    @Test
    fun `kaomoji exist only when switched on`() {
        assertEquals(listOf(PickerMode.EMOJI), PickerModes.cycle(kaomojiEnabled = false))
        assertEquals(PickerMode.EMOJI, PickerModes.openingMode(PickerMode.KAOMOJI, kaomojiEnabled = false))
        assertEquals(PickerMode.EMOJI, PickerModes.next(PickerMode.EMOJI, kaomojiEnabled = false))
        assertFalse(PickerModes.showsModeButton(PickerMode.EMOJI, kaomojiEnabled = false))

        assertEquals(PickerMode.KAOMOJI, PickerModes.openingMode(PickerMode.KAOMOJI, kaomojiEnabled = true))
        assertEquals(PickerMode.KAOMOJI, PickerModes.next(PickerMode.EMOJI, kaomojiEnabled = true))
        assertEquals(PickerMode.EMOJI, PickerModes.next(PickerMode.KAOMOJI, kaomojiEnabled = true))
    }

    @Test
    fun `Unicode symbols open on request but are never in the mode button's cycle`() {
        assertEquals(PickerMode.SYMBOLS, PickerModes.openingMode(PickerMode.SYMBOLS, kaomojiEnabled = false))
        assertFalse(PickerMode.SYMBOLS in PickerModes.cycle(kaomojiEnabled = true))
        assertTrue(PickerModes.showsModeButton(PickerMode.SYMBOLS, kaomojiEnabled = false))
        assertEquals(PickerMode.EMOJI, PickerModes.next(PickerMode.SYMBOLS, kaomojiEnabled = false))
    }
}
