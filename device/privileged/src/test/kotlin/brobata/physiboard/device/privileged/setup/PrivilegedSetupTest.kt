package brobata.physiboard.device.privileged.setup

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.device.privileged.FakeClock
import brobata.physiboard.device.privileged.FakeShell
import brobata.physiboard.device.privileged.InMemoryDeviceStateStore
import brobata.physiboard.device.privileged.InMemoryDiagnosticsStore
import brobata.physiboard.device.privileged.PrivilegedStep
import brobata.physiboard.device.privileged.StepReasons
import brobata.physiboard.device.privileged.backlight.KeyboardBacklightController
import brobata.physiboard.device.privileged.broker.ShellResult
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** spec: broker-privileged-toolbox.md SS7, SS8; SS21 ("Setup pass runs with no key or debugging off"). */
class PrivilegedSetupTest {

    private val identity = AppIdentity("brobata.physiboard.dev3", "brobata.physiboard.dev3/brobata.physiboard.device.privileged.ring.NotificationRingListener")
    private val shell = FakeShell()
    private val permissions = FakePermissionProbe()
    private val store = InMemoryDeviceStateStore(Settings())
    private val diagnostics = InMemoryDiagnosticsStore()
    private val clock = FakeClock(nowMs = 7_000)
    private val executor = Executors.newSingleThreadExecutor()
    private val backlight = KeyboardBacklightController(shell, store, diagnostics, clock, executor)
    private val setup = PrivilegedSetup(shell, permissions, store, diagnostics, backlight, identity, clock)

    private val alwaysOn = ShellLines.vendorTimeout(-1)

    @Test
    fun `with no key every step is recorded failed as not_paired and nothing is attempted`() {
        shell.blocker = brobata.physiboard.device.privileged.broker.BrokerBlocker.NOT_PAIRED
        val report = setup.run(SetupReasons.IME_START)
        assertEquals(PrivilegedStep.entries.toSet(), report.outcomes.keys)
        PrivilegedStep.entries.forEach { step ->
            val outcome = assertNotNull(diagnostics.step(step), "$step recorded")
            assertFalse(outcome.ok)
            assertEquals(StepReasons.NOT_PAIRED, outcome.reason)
            assertEquals(7_000, outcome.atMs)
        }
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `with debugging off every step is recorded failed as wireless_debugging_off`() {
        shell.blocker = brobata.physiboard.device.privileged.broker.BrokerBlocker.WIRELESS_DEBUGGING_OFF
        setup.run(SetupReasons.BACKLIGHT_SCREEN)
        PrivilegedStep.entries.forEach { assertEquals(StepReasons.WIRELESS_DEBUGGING_OFF, diagnostics.step(it)?.reason) }
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `step 1 writes the always-on timeout, reads it back, and records ok`() {
        shell.responses[ShellLines.vendorTimeoutRead] = ShellResult.Ok("Result: Parcel(00000000 00000002 0031002d 00000000 '........-.1.....')")
        val report = setup.run(SetupReasons.PAIRING_SUCCEEDED)
        assertEquals(alwaysOn, shell.lines[0])
        assertEquals(ShellLines.vendorTimeoutRead, shell.lines[1])
        assertTrue(report.outcomes.getValue(PrivilegedStep.BACKLIGHT).ok)
        assertTrue(store.snapshot().captures.smartBacklightApplied)
        assertEquals("-1", diagnostics.backlightDeviceValue()?.value)
    }

    @Test
    fun `step 1 is skipped when the smart backlight is off, and says so`() {
        store.update { it.copy(device = it.device.copy(smartBacklightEnabled = false)) }
        setup.run(SetupReasons.IME_START)
        assertFalse(shell.lines.contains(alwaysOn))
        assertEquals(PrivilegedSetup.SKIPPED_DISABLED, diagnostics.step(PrivilegedStep.BACKLIGHT)?.reason)
        assertTrue(diagnostics.step(PrivilegedStep.BACKLIGHT)!!.ok)
    }

    @Test
    fun `step 1 records the broker's error when the write fails`() {
        shell.failWith(alwaysOn, "SocketTimeoutException: connect timed out")
        val report = setup.run(SetupReasons.IME_START)
        val outcome = report.outcomes.getValue(PrivilegedStep.BACKLIGHT)
        assertFalse(outcome.ok)
        assertEquals("SocketTimeoutException: connect timed out", outcome.reason)
        assertFalse(store.snapshot().captures.smartBacklightApplied)
    }

    @Test
    fun `step 2 is a no-op when the overlay is already granted`() {
        permissions.overlays = true
        setup.run(SetupReasons.IME_START)
        assertFalse(shell.lines.contains(ShellLines.overlayGrant(identity)))
        assertEquals(StepReasons.OK, diagnostics.step(PrivilegedStep.OVERLAY_GRANT)?.reason)
    }

    @Test
    fun `step 2 grants the overlay for the running package, re-checks, and switches the trackpad on the first time`() {
        shell.onLine = { line -> if (line == ShellLines.overlayGrant(identity)) permissions.overlays = true }
        assertFalse(store.snapshot().trackpad.enabled)
        setup.run(SetupReasons.PAIRING_SUCCEEDED)
        assertTrue(shell.lines.contains("appops set brobata.physiboard.dev3 SYSTEM_ALERT_WINDOW allow"))
        assertTrue(diagnostics.step(PrivilegedStep.OVERLAY_GRANT)!!.ok)
        assertTrue(store.snapshot().trackpad.enabled, "the pairing is the only step the user takes (SS7 step 2)")
    }

    @Test
    fun `step 2 records a failure when the re-check still says no, even though the shell said ok`() {
        setup.run(SetupReasons.IME_START)
        val outcome = diagnostics.step(PrivilegedStep.OVERLAY_GRANT)!!
        assertFalse(outcome.ok)
        assertEquals(StepReasons.SHELL_FAILED, outcome.reason)
    }

    @Test
    fun `step 3 sends the three ring grants as one line naming the real listener component, judged by re-check`() {
        shell.onLine = { line ->
            if (line == ShellLines.ringGrants(identity)) {
                permissions.listener = true
                permissions.fullScreenIntent = true
                permissions.notifications = true
            }
        }
        setup.run(SetupReasons.IME_START)
        assertTrue(
            shell.lines.contains(
                "cmd notification allow_listener brobata.physiboard.dev3/brobata.physiboard.device.privileged.ring.NotificationRingListener; " +
                    "appops set brobata.physiboard.dev3 USE_FULL_SCREEN_INTENT allow; appops set brobata.physiboard.dev3 POST_NOTIFICATION allow",
            ),
        )
        assertTrue(diagnostics.step(PrivilegedStep.NOTIFICATION_RING)!!.ok)
    }

    @Test
    fun `step 3 and step 4 are skipped when the ring is off`() {
        store.update { it.copy(device = it.device.copy(ringEnabled = false)) }
        setup.run(SetupReasons.IME_START)
        assertFalse(shell.lines.any { it.startsWith("cmd notification") || it.startsWith("pm grant") })
        assertEquals(PrivilegedSetup.SKIPPED_DISABLED, diagnostics.step(PrivilegedStep.NOTIFICATION_RING)?.reason)
        assertEquals(PrivilegedSetup.SKIPPED_DISABLED, diagnostics.step(PrivilegedStep.RING_BACKLIGHT)?.reason)
    }

    @Test
    fun `step 3 is a no-op when all three grants are already in place`() {
        permissions.listener = true
        permissions.fullScreenIntent = true
        permissions.notifications = true
        setup.run(SetupReasons.IME_START)
        assertFalse(shell.lines.any { it.startsWith("cmd notification") })
        assertEquals(StepReasons.OK, diagnostics.step(PrivilegedStep.NOTIFICATION_RING)?.reason)
    }

    @Test
    fun `step 4 grants WRITE_SECURE_SETTINGS and judges by the permission, not the shell`() {
        shell.onLine = { line -> if (line == ShellLines.secureSettingsGrant(identity)) permissions.writeSecureSettings = true }
        setup.run(SetupReasons.IME_START)
        assertTrue(shell.lines.contains("pm grant brobata.physiboard.dev3 android.permission.WRITE_SECURE_SETTINGS"))
        assertTrue(diagnostics.step(PrivilegedStep.RING_BACKLIGHT)!!.ok)
    }

    @Test
    fun `step 4 records a failure when pm grant printed nothing and the permission is still missing`() {
        setup.run(SetupReasons.IME_START)
        val outcome = diagnostics.step(PrivilegedStep.RING_BACKLIGHT)!!
        assertFalse(outcome.ok)
        assertEquals(StepReasons.SHELL_FAILED, outcome.reason)
    }

    @Test
    fun `the pass is idempotent - a second run repeats the writes and records again`() {
        setup.run(SetupReasons.IME_START)
        val before = shell.lines.size
        clock.nowMs = 9_000
        setup.run(SetupReasons.IME_START)
        assertEquals(before * 2, shell.lines.size)
        assertEquals(9_000, diagnostics.step(PrivilegedStep.BACKLIGHT)?.atMs)
    }

    @Test
    fun `a step that throws is recorded with the error and never stops the pass`() {
        val throwingPermissions = object : PermissionProbe by permissions {
            override fun canDrawOverlays(): Boolean = throw IllegalStateException("dead system")
        }
        val pass = PrivilegedSetup(shell, throwingPermissions, store, diagnostics, backlight, identity, clock)
        val report = pass.run(SetupReasons.IME_START)
        assertEquals("IllegalStateException: dead system", report.outcomes.getValue(PrivilegedStep.OVERLAY_GRANT).reason)
        assertNotNull(report.outcomes[PrivilegedStep.RING_BACKLIGHT])
    }
}
