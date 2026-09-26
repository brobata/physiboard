package brobata.physiboard.core.pointer.keyboardswipe

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: trackpad-caret-nav.md SS3.6, its two-row table. */
class FirmwareSwipeKeycodeTest {

    @Test
    fun `a keycode that is not one of the two firmware codes is not recognized`() {
        assertEquals(
            FirmwareSwipeResult.NOT_RECOGNIZED,
            FirmwareSwipeKeycode.decide(1, isFirmwareSwipeKeycode = false, swipeToDelete = true, provider = SwipeToDeleteProvider.TITAN2_KEYCODE),
        )
    }

    @Test
    fun `swipe to delete on with the titan2_keycode provider deletes the last word`() {
        assertEquals(
            FirmwareSwipeResult.DELETE_LAST_WORD,
            FirmwareSwipeKeycode.decide(322, isFirmwareSwipeKeycode = true, swipeToDelete = true, provider = SwipeToDeleteProvider.TITAN2_KEYCODE),
        )
    }

    @Test
    fun `on a fresh install the keycode is swallowed silently`() {
        assertEquals(
            FirmwareSwipeResult.CONSUMED_IGNORED,
            FirmwareSwipeKeycode.decide(404, isFirmwareSwipeKeycode = true, swipeToDelete = false, provider = SwipeToDeleteProvider.NATIVE_IME),
        )
    }

    @Test
    fun `swipe to delete on but the native_ime provider still ignores the firmware keycode`() {
        assertEquals(
            FirmwareSwipeResult.CONSUMED_IGNORED,
            FirmwareSwipeKeycode.decide(322, isFirmwareSwipeKeycode = true, swipeToDelete = true, provider = SwipeToDeleteProvider.NATIVE_IME),
        )
    }
}
