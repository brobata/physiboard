package brobata.physiboard.device.privileged.backlight

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.device.privileged.FakeClock
import brobata.physiboard.device.privileged.FakeShell
import brobata.physiboard.device.privileged.InMemoryDeviceStateStore
import brobata.physiboard.device.privileged.InMemoryDiagnosticsStore
import brobata.physiboard.device.privileged.PrivilegedStep
import brobata.physiboard.device.privileged.StepReasons
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.setup.ShellLines
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: device-backlight-ring.md SS3.1, SS3.2, SS3.4; test cases T37 to T40. */
class KeyboardBacklightControllerTest {

    private val shell = FakeShell()
    private val store = InMemoryDeviceStateStore(Settings())
    private val diagnostics = InMemoryDiagnosticsStore()
    private val clock = FakeClock(nowMs = 5_000)
    private val controller = KeyboardBacklightController(shell, store, diagnostics, clock, Executors.newSingleThreadExecutor())

    @Test
    fun `T37 - gate with no key records not_paired and makes no shell call`() {
        shell.blocker = brobata.physiboard.device.privileged.broker.BrokerBlocker.NOT_PAIRED
        val outcome = controller.applyNow(smartBacklightEnabled = true)
        assertFalse(outcome.ok)
        assertEquals(StepReasons.NOT_PAIRED, outcome.reason)
        assertEquals(outcome, diagnostics.step(PrivilegedStep.BACKLIGHT))
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `T38 - gate with key stored and adb_wifi_enabled 0 records wireless_debugging_off and makes no shell call`() {
        shell.blocker = brobata.physiboard.device.privileged.broker.BrokerBlocker.WIRELESS_DEBUGGING_OFF
        controller.applyAsync(smartBacklightEnabled = true)
        assertEquals(StepReasons.WIRELESS_DEBUGGING_OFF, diagnostics.step(PrivilegedStep.BACKLIGHT)?.reason)
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `T39 - a successful always-on write sets the applied latch, stores the read-back, and records ok`() {
        shell.responses[ShellLines.vendorTimeoutRead] = ShellResult.Ok("Result: Parcel(00000000 00000002 0031002d 00000000 '........-.1.....')")
        val outcome = controller.applyNow(smartBacklightEnabled = true)
        assertEquals(
            listOf("service call agui_functional_service 2 s16 \"keyboard_brightness_timeout\" s16 \"-1\"", ShellLines.vendorTimeoutRead),
            shell.lines,
        )
        assertTrue(outcome.ok)
        assertEquals(StepReasons.OK, outcome.reason)
        assertTrue(store.snapshot().captures.smartBacklightApplied)
        assertEquals("-1", diagnostics.backlightDeviceValue()?.value)
        assertEquals(5_000, diagnostics.backlightDeviceValue()?.atMs)
        assertFalse(controller.isDeviceValueStale(smartBacklightEnabled = true))
    }

    @Test
    fun `T40 - a successful stock write clears the applied latch`() {
        store.update { it.copy(captures = it.captures.copy(smartBacklightApplied = true)) }
        shell.responses[ShellLines.vendorTimeoutRead] = ShellResult.Ok("Result: Parcel(00000000 00000005 00300033 00300030 00000030 '..')")
        val outcome = controller.applyNow(smartBacklightEnabled = false)
        assertTrue(outcome.ok)
        assertEquals("service call agui_functional_service 2 s16 \"keyboard_brightness_timeout\" s16 \"30000\"", shell.lines[0])
        assertFalse(store.snapshot().captures.smartBacklightApplied)
        assertEquals("30000", diagnostics.backlightDeviceValue()?.value)
    }

    @Test
    fun `a failed write records the broker's error and leaves the latch alone`() {
        shell.failWith(ShellLines.vendorTimeout(-1), "No adb-tls-connect service found. Is wireless debugging on?")
        val outcome = controller.applyNow(smartBacklightEnabled = true)
        assertFalse(outcome.ok)
        assertEquals("No adb-tls-connect service found. Is wireless debugging on?", outcome.reason)
        assertFalse(store.snapshot().captures.smartBacklightApplied)
    }

    @Test
    fun `a failed write with no error text records shell_failed`() {
        shell.responses[ShellLines.vendorTimeout(-1)] = ShellResult.Failed("")
        assertEquals(StepReasons.SHELL_FAILED, controller.applyNow(smartBacklightEnabled = true).reason)
    }

    @Test
    fun `an unreadable read-back is stored as absent, and the phone holding another value reads as stale`() {
        shell.responses[ShellLines.vendorTimeoutRead] = ShellResult.Ok("Result: oops")
        assertNull(controller.readDeviceTimeout())
        assertNull(diagnostics.backlightDeviceValue()?.value)
        assertFalse(controller.isDeviceValueStale(smartBacklightEnabled = true), "unreadable is not 'holding another value'")
        shell.responses[ShellLines.vendorTimeoutRead] = ShellResult.Ok("Result: Parcel(00000000 00000005 00300033 00300030 00000030 '..')")
        assertEquals("30000", controller.readDeviceTimeout())
        assertTrue(controller.isDeviceValueStale(smartBacklightEnabled = true), "a system update reset it: 'Apply again' (SS3.4)")
    }

    @Test
    fun `writes queue on one worker in order`() {
        controller.applyAsync(smartBacklightEnabled = true)
        controller.applyAsync(smartBacklightEnabled = false)
        controller.applyNow(smartBacklightEnabled = true)
        assertEquals(
            listOf(ShellLines.vendorTimeout(-1), ShellLines.vendorTimeoutRead, ShellLines.vendorTimeout(30_000), ShellLines.vendorTimeoutRead, ShellLines.vendorTimeout(-1), ShellLines.vendorTimeoutRead),
            shell.lines,
        )
    }
}
