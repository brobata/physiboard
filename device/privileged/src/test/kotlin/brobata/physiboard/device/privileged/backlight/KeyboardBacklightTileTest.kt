package brobata.physiboard.device.privileged.backlight

import brobata.physiboard.core.settings.DeviceCaptures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: device-backlight-ring.md SS2.2; test cases T32, T33. */
class KeyboardBacklightTileTest {

    @Test
    fun `T32 - a tap with a ring record outstanding captures the ring's prior as the original and clears the ring's record`() {
        val captures = DeviceCaptures(ringBacklightPrevCaptured = true, ringBacklightPrev = 1)
        val tap = KeyboardBacklightTile.decideTap(currentValue = 0, captures = captures)
        assertTrue(tap.captures.qsBacklightPrevCaptured)
        assertEquals(1, tap.captures.qsBacklightPrev)
        assertFalse(tap.captures.ringBacklightPrevCaptured)
        assertNull(tap.captures.ringBacklightPrev)
        assertEquals(1, tap.writeValue)
    }

    @Test
    fun `T33 - two taps with the switch on capture 1 once and toggle both ways`() {
        val first = KeyboardBacklightTile.decideTap(currentValue = 1, captures = DeviceCaptures())
        assertTrue(first.captures.qsBacklightPrevCaptured)
        assertEquals(1, first.captures.qsBacklightPrev)
        assertEquals(0, first.writeValue)
        val second = KeyboardBacklightTile.decideTap(currentValue = 0, captures = first.captures)
        assertEquals(first.captures, second.captures, "repeated taps never overwrite the capture")
        assertEquals(1, second.writeValue)
    }

    @Test
    fun `an unset switch is captured as unset and reads as active`() {
        val tap = KeyboardBacklightTile.decideTap(currentValue = null, captures = DeviceCaptures())
        assertTrue(tap.captures.qsBacklightPrevCaptured)
        assertNull(tap.captures.qsBacklightPrev)
        assertEquals(1, tap.writeValue, "spec SS2.2 step 3c: 0 or unset becomes 1")
        assertTrue(KeyboardBacklightTile.isActive(null))
        assertTrue(KeyboardBacklightTile.isActive(1))
        assertFalse(KeyboardBacklightTile.isActive(0))
    }

    @Test
    fun `the subtitle names the missing grant, and the toast names the running package`() {
        assertEquals("Needs ADB grant", KeyboardBacklightTile.subtitle(hasWriteSecureSettings = false))
        assertEquals("", KeyboardBacklightTile.subtitle(hasWriteSecureSettings = true))
        assertTrue(KeyboardBacklightTile.grantToast("brobata.physiboard.dev3").endsWith("adb shell pm grant brobata.physiboard.dev3 android.permission.WRITE_SECURE_SETTINGS"))
    }
}
