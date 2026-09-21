package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** spec: device-backlight-ring.md SS3.1, SS5.8, SS10 test cases T6-T14. */
class KeyboardBacklightTest {

    @Test
    fun `T6 - suppress with the switch on captures it and writes 0`() {
        val decision = KeyboardBacklight.decideSuppression(
            ringEnabled = true, keyboardDarkEnabled = true, hasWriteSecureSettingsPermission = true,
            alreadySuppressed = false, currentSwitchValue = 1, nowMs = 0,
        )
        val suppress = assertIs<BacklightSuppressionDecision.Suppress>(decision)
        assertEquals(0, suppress.write.value)
        assertEquals(1, suppress.record.priorSwitchValue)
    }

    @Test
    fun `T7 - restoring an outstanding record writes the captured value back`() {
        val record = BacklightSuppressionRecord(priorSwitchValue = 1, capturedAtMs = 0)
        val decision = KeyboardBacklight.decideRestore(record, hasWriteSecureSettingsPermission = true)
        val restore = assertIs<BacklightRestoreDecision.Restore>(decision)
        assertEquals(1, restore.write.value)
    }

    @Test
    fun `T8 - a second suppress while one is outstanding is skipped, and the eventual restore uses the first record`() {
        val first = KeyboardBacklight.decideSuppression(
            ringEnabled = true, keyboardDarkEnabled = true, hasWriteSecureSettingsPermission = true,
            alreadySuppressed = false, currentSwitchValue = 1, nowMs = 0,
        )
        val record = assertIs<BacklightSuppressionDecision.Suppress>(first).record

        val second = KeyboardBacklight.decideSuppression(
            ringEnabled = true, keyboardDarkEnabled = true, hasWriteSecureSettingsPermission = true,
            alreadySuppressed = true, currentSwitchValue = 0, nowMs = 10,
        )
        assertEquals(BacklightSuppressionDecision.Skip, second)

        val restore = assertIs<BacklightRestoreDecision.Restore>(KeyboardBacklight.decideRestore(record, hasWriteSecureSettingsPermission = true))
        assertEquals(1, restore.write.value)
    }

    @Test
    fun `T9 - the switch already off records nothing, and restoring finds nothing`() {
        val decision = KeyboardBacklight.decideSuppression(
            ringEnabled = true, keyboardDarkEnabled = true, hasWriteSecureSettingsPermission = true,
            alreadySuppressed = false, currentSwitchValue = 0, nowMs = 0,
        )
        assertEquals(BacklightSuppressionDecision.Skip, decision)
        assertEquals(BacklightRestoreDecision.NoRecord, KeyboardBacklight.decideRestore(null, hasWriteSecureSettingsPermission = true))
    }

    @Test
    fun `T10 - keyboard-dark disabled skips suppression`() {
        val decision = KeyboardBacklight.decideSuppression(
            ringEnabled = true, keyboardDarkEnabled = false, hasWriteSecureSettingsPermission = true,
            alreadySuppressed = false, currentSwitchValue = 1, nowMs = 0,
        )
        assertEquals(BacklightSuppressionDecision.Skip, decision)
    }

    @Test
    fun `T11 - the ring disabled skips suppression`() {
        val decision = KeyboardBacklight.decideSuppression(
            ringEnabled = false, keyboardDarkEnabled = true, hasWriteSecureSettingsPermission = true,
            alreadySuppressed = false, currentSwitchValue = 1, nowMs = 0,
        )
        assertEquals(BacklightSuppressionDecision.Skip, decision)
    }

    @Test
    fun `T12 - restoring twice with nothing recorded the second time leaves the user's own choice alone`() {
        val suppress = assertIs<BacklightSuppressionDecision.Suppress>(
            KeyboardBacklight.decideSuppression(
                ringEnabled = true, keyboardDarkEnabled = true, hasWriteSecureSettingsPermission = true,
                alreadySuppressed = false, currentSwitchValue = 1, nowMs = 0,
            ),
        )
        assertIs<BacklightRestoreDecision.Restore>(KeyboardBacklight.decideRestore(suppress.record, hasWriteSecureSettingsPermission = true))
        // The caller clears its record after that restore succeeds; a later restore with no record is a no-op.
        assertEquals(BacklightRestoreDecision.NoRecord, KeyboardBacklight.decideRestore(null, hasWriteSecureSettingsPermission = true))
    }

    @Test
    fun `T13 - missing permission skips suppression entirely`() {
        val decision = KeyboardBacklight.decideSuppression(
            ringEnabled = true, keyboardDarkEnabled = true, hasWriteSecureSettingsPermission = false,
            alreadySuppressed = false, currentSwitchValue = 1, nowMs = 0,
        )
        assertEquals(BacklightSuppressionDecision.Skip, decision)
    }

    @Test
    fun `T14 - a permission lost before restore keeps the record instead of dropping it`() {
        val suppress = assertIs<BacklightSuppressionDecision.Suppress>(
            KeyboardBacklight.decideSuppression(
                ringEnabled = true, keyboardDarkEnabled = true, hasWriteSecureSettingsPermission = true,
                alreadySuppressed = false, currentSwitchValue = 1, nowMs = 0,
            ),
        )
        val decision = KeyboardBacklight.decideRestore(suppress.record, hasWriteSecureSettingsPermission = false)
        assertEquals(BacklightRestoreDecision.KeepRecordPermissionMissing, decision)
    }

    // Smart backlight timeout write and staleness, not individually T-numbered. spec: SS3.1, SS3.4.

    @Test
    fun `smart backlight on writes the always-on sentinel, off writes stock`() {
        assertEquals(BacklightWrite.VendorTimeoutMs(-1), KeyboardBacklight.timeoutWriteFor(smartBacklightEnabled = true))
        assertEquals(BacklightWrite.VendorTimeoutMs(30_000), KeyboardBacklight.timeoutWriteFor(smartBacklightEnabled = false))
    }

    @Test
    fun `a device value other than the sentinel while enabled is stale`() {
        assertEquals(true, KeyboardBacklight.isTimeoutStale(smartBacklightEnabled = true, deviceReadValue = "30000"))
        assertEquals(false, KeyboardBacklight.isTimeoutStale(smartBacklightEnabled = true, deviceReadValue = "-1"))
        assertEquals(false, KeyboardBacklight.isTimeoutStale(smartBacklightEnabled = false, deviceReadValue = "30000"))
    }

    @Test
    fun `a suppression the ring never claimed becomes orphaned after 20 seconds`() {
        assertEquals(false, KeyboardBacklight.isOrphaned(suppressedAtMs = 0, ringTookOwnership = false, nowMs = 19_999))
        assertEquals(true, KeyboardBacklight.isOrphaned(suppressedAtMs = 0, ringTookOwnership = false, nowMs = 20_000))
        assertEquals(false, KeyboardBacklight.isOrphaned(suppressedAtMs = 0, ringTookOwnership = true, nowMs = 20_000))
    }
}
