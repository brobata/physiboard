package brobata.physiboard.device.privileged.setup

import brobata.physiboard.device.privileged.InMemoryDiagnosticsStore
import brobata.physiboard.device.privileged.PrivilegedExport
import brobata.physiboard.device.privileged.PrivilegedExportFacts
import brobata.physiboard.device.privileged.PrivilegedStep
import brobata.physiboard.device.privileged.StepOutcome
import brobata.physiboard.device.titan.BacklightWrite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: broker-privileged-toolbox.md SS7, SS8, SS9; device-backlight-ring.md SS3.1, SS5.9: the exact shell text and the export section. */
class ShellLinesTest {

    private val identity = AppIdentity("brobata.physiboard", "brobata.physiboard/brobata.physiboard.device.privileged.ring.NotificationRingListener")

    @Test
    fun `the vendor transactions are the spec's lines verbatim`() {
        assertEquals("service call agui_functional_service 2 s16 \"keyboard_brightness_timeout\" s16 \"-1\"", ShellLines.vendorTimeout(-1))
        assertEquals("service call agui_functional_service 2 s16 \"keyboard_brightness_timeout\" s16 \"30000\"", ShellLines.vendorTimeout(30_000))
        assertEquals("service call agui_functional_service 1 s16 \"keyboard_brightness_timeout\"", ShellLines.vendorTimeoutRead)
    }

    @Test
    fun `a titan decision renders to the matching line`() {
        assertEquals(ShellLines.vendorTimeout(-1), ShellLines.render(BacklightWrite.VendorTimeoutMs(-1)))
        assertEquals("settings put global agui_keyboard_background_light 0", ShellLines.render(BacklightWrite.MasterSwitch(0)))
    }

    @Test
    fun `the grants name the running package and the real listener component`() {
        assertEquals("appops set brobata.physiboard SYSTEM_ALERT_WINDOW allow", ShellLines.overlayGrant(identity))
        assertEquals("pm grant brobata.physiboard android.permission.WRITE_SECURE_SETTINGS", ShellLines.secureSettingsGrant(identity))
        assertEquals(
            "cmd notification allow_listener brobata.physiboard/brobata.physiboard.device.privileged.ring.NotificationRingListener; " +
                "appops set brobata.physiboard USE_FULL_SCREEN_INTENT allow; appops set brobata.physiboard POST_NOTIFICATION allow",
            ShellLines.ringGrants(identity),
        )
    }

    @Test
    fun `the export prints the spec's rows in order, with never-read and n-a placeholders`() {
        val diagnostics = InMemoryDiagnosticsStore()
        val facts = PrivilegedExportFacts(
            brokerPaired = true, wirelessDebuggingEnabled = false, brokerBlocker = "wireless_debugging_off",
            backlightEnabled = true, backlightAppliedFlag = true, overlayPermissionGranted = true,
            notificationListenerGranted = false, notificationRingEnabled = true, screenTrackpadEnabled = true,
            trackpadProvider = "overlay", imeEnabled = true, imeSelected = true,
        )
        val empty = PrivilegedExport.lines(facts, diagnostics)
        assertEquals("broker_paired=true", empty[0])
        assertEquals("broker_blocker=wireless_debugging_off", empty[2])
        assertEquals("backlight_device_value=never read", empty[5])
        assertEquals("backlight_device_value_at=n/a", empty[6])
        assertEquals("last_outcomes=(no privileged step has run)", empty.last())

        diagnostics.recordStep(PrivilegedStep.BACKLIGHT, StepOutcome(ok = false, reason = "wireless_debugging_off", atMs = 12))
        val recorded = PrivilegedExport.lines(facts.copy(brokerBlocker = null), diagnostics) { "t$it" }
        assertEquals("broker_blocker=none", recorded[2])
        assertTrue(recorded.contains("last_backlight=failed reason='wireless_debugging_off' at=t12"))
    }
}
