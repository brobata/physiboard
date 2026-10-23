package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * spec: device-backlight-ring.md SS2.3; test cases T80 to T82. The 2026-10-22 report: on the
 * charger, the screen timed out into Android's screen saver, which put the keyboard light out;
 * a touch ended the screen saver with the screen still on, and the keyboard stayed dark.
 */
class AndroidScreenSaverTest {

    @Test
    fun `T80 - the screen saver runs while charging only with both rows set`() {
        assertTrue(AndroidScreenSaver.runsWhileCharging(enabled = true, activateOnSleep = true))
        assertFalse(AndroidScreenSaver.runsWhileCharging(enabled = true, activateOnSleep = false))
        assertFalse(AndroidScreenSaver.runsWhileCharging(enabled = false, activateOnSleep = true))
        assertFalse(AndroidScreenSaver.runsWhileCharging(enabled = false, activateOnSleep = false))
    }

    @Test
    fun `T81 - the maintainer's phone - screen saver while charging with Smart backlight on breaks the keyboard light`() {
        val conflicts = AndroidScreenSaver.conflicts(runsWhileCharging = true, smartBacklightEnabled = true, chargingClockOn = false)
        assertEquals(setOf(ScreenSaverConflict.KEYBOARD_LIGHT), conflicts)
    }

    @Test
    fun `T81 - with the charging clock on as well, both promises are broken, keyboard first`() {
        val conflicts = AndroidScreenSaver.conflicts(runsWhileCharging = true, smartBacklightEnabled = true, chargingClockOn = true)
        assertEquals(listOf(ScreenSaverConflict.KEYBOARD_LIGHT, ScreenSaverConflict.CHARGING_CLOCK), conflicts.toList())
        assertEquals(
            setOf(ScreenSaverConflict.CHARGING_CLOCK),
            AndroidScreenSaver.conflicts(runsWhileCharging = true, smartBacklightEnabled = false, chargingClockOn = true),
        )
    }

    @Test
    fun `T82 - no conflict when the screen saver stays off while charging, or when neither feature is on`() {
        assertTrue(AndroidScreenSaver.conflicts(runsWhileCharging = false, smartBacklightEnabled = true, chargingClockOn = true).isEmpty())
        assertTrue(AndroidScreenSaver.conflicts(runsWhileCharging = true, smartBacklightEnabled = false, chargingClockOn = false).isEmpty())
    }
}
