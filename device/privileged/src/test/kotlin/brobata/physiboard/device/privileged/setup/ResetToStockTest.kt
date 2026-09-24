package brobata.physiboard.device.privileged.setup

import brobata.physiboard.core.settings.DeviceCaptures
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.device.privileged.FakeClock
import brobata.physiboard.device.privileged.FakeShell
import brobata.physiboard.device.privileged.InMemoryDeviceStateStore
import brobata.physiboard.device.privileged.InMemoryDiagnosticsStore
import brobata.physiboard.device.privileged.backlight.FakeMasterSwitch
import brobata.physiboard.device.privileged.backlight.KeyboardBacklightController
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: broker-privileged-toolbox.md SS10, test cases T44 to T49; device-backlight-ring.md SS8, T34 to T36. */
class ResetToStockTest {

    private val identity = AppIdentity("brobata.physiboard", "brobata.physiboard/brobata.physiboard.device.privileged.ring.NotificationRingListener")
    private val shell = FakeShell()
    private val permissions = FakePermissionProbe()
    private val masterSwitch = FakeMasterSwitch(value = 0)
    private val store = InMemoryDeviceStateStore(Settings())
    private val diagnostics = InMemoryDiagnosticsStore()
    private val backlight = KeyboardBacklightController(shell, store, diagnostics, FakeClock(), Executors.newSingleThreadExecutor())
    private val reset = ResetToStock(shell, permissions, masterSwitch, store, backlight, identity)

    private fun captures(transform: (DeviceCaptures) -> DeviceCaptures) = store.update { it.copy(captures = transform(it.captures)) }

    // Messages. ------------------------------------------------------------------------------

    @Test
    fun `T44 - all SUCCESS gives the restored message`() {
        assertEquals(ResetMessages.ALL_RESTORED, ResetMessages.forOutcomes(List(5) { RevertOutcome.SUCCESS }))
    }

    @Test
    fun `T45 - one NEEDS_PERMISSION among successes gives the needs-permission message`() {
        assertEquals(ResetMessages.NEEDS_PERMISSION, ResetMessages.forOutcomes(listOf(RevertOutcome.SUCCESS, RevertOutcome.NEEDS_PERMISSION, RevertOutcome.SUCCESS)))
    }

    @Test
    fun `T46 - one FAILED and no NEEDS_PERMISSION gives the partial message`() {
        assertEquals(ResetMessages.PARTIAL, ResetMessages.forOutcomes(listOf(RevertOutcome.SUCCESS, RevertOutcome.FAILED)))
        assertEquals(ResetMessages.NEEDS_PERMISSION, ResetMessages.forOutcomes(listOf(RevertOutcome.FAILED, RevertOutcome.NEEDS_PERMISSION)), "needs-permission outranks partial")
    }

    // Pure values. ---------------------------------------------------------------------------

    @Test
    fun `T47 - Fn revert with captured enable 1 and function unset writes 1 and 0`() {
        assertEquals(1 to 0, RevertValues.fnCtrlTargets(DeviceCaptures(fnCtrlPrevCaptured = true, fnCtrlPrevEnable = 1, fnCtrlPrevFunction = null)))
    }

    @Test
    fun `T48 - Fn revert never captured writes 0 and 0`() {
        assertEquals(0 to 0, RevertValues.fnCtrlTargets(DeviceCaptures()))
    }

    @Test
    fun `T49 - side-key safe value`() {
        assertTrue(SideKeyValue.isSafe("com.google.android.apps.bard.MainActivity"))
        assertTrue(SideKeyValue.isSafe("a\$b"))
        assertFalse(SideKeyValue.isSafe("x; rm"))
        assertFalse(SideKeyValue.isSafe("a".repeat(257)))
        assertTrue(SideKeyValue.isSafe("a".repeat(256)))
    }

    // Step 1: Fn to Ctrl. --------------------------------------------------------------------

    @Test
    fun `Fn revert writes both rows in one broker line and clears the capture only on success`() {
        captures { it.copy(fnCtrlPrevCaptured = true, fnCtrlPrevEnable = 1, fnCtrlPrevFunction = null) }
        val report = reset.run()
        assertEquals(RevertOutcome.SUCCESS, report.outcomes[RevertStep.FN_CTRL])
        assertTrue(shell.lines.contains("settings put system fn_programmable_key_enable 1; settings put system fn_programmable_key_function 0"))
        assertFalse(store.snapshot().captures.fnCtrlPrevCaptured)
    }

    @Test
    fun `Fn revert with no key is NEEDS_PERMISSION and keeps the capture`() {
        shell.blocker = BrokerBlocker.NOT_PAIRED
        captures { it.copy(fnCtrlPrevCaptured = true, fnCtrlPrevEnable = 1, fnCtrlPrevFunction = 1) }
        val report = reset.run()
        assertEquals(RevertOutcome.NEEDS_PERMISSION, report.outcomes[RevertStep.FN_CTRL])
        assertTrue(store.snapshot().captures.fnCtrlPrevCaptured)
    }

    @Test
    fun `Fn revert whose line fails is FAILED and keeps the capture`() {
        captures { it.copy(fnCtrlPrevCaptured = true, fnCtrlPrevEnable = 1, fnCtrlPrevFunction = 1) }
        shell.failWith("settings put system fn_programmable_key_enable 1; settings put system fn_programmable_key_function 1")
        assertEquals(RevertOutcome.FAILED, reset.run().outcomes[RevertStep.FN_CTRL])
        assertTrue(store.snapshot().captures.fnCtrlPrevCaptured)
    }

    // Step 2: the backlight. -----------------------------------------------------------------

    @Test
    fun `T36 - backlight revert with enabled false and applied false succeeds with no broker call`() {
        store.update { it.copy(device = it.device.copy(smartBacklightEnabled = false)) }
        val report = reset.run()
        assertEquals(RevertOutcome.SUCCESS, report.outcomes[RevertStep.BACKLIGHT])
        assertFalse(shell.lines.any { it.startsWith("service call") })
    }

    @Test
    fun `backlight revert sends the stock timeout, awaits it, and clears both flags`() {
        captures { it.copy(smartBacklightApplied = true) }
        val report = reset.run()
        assertEquals(RevertOutcome.SUCCESS, report.outcomes[RevertStep.BACKLIGHT])
        assertTrue(shell.lines.contains(ShellLines.vendorTimeout(30_000)))
        assertFalse(store.snapshot().device.smartBacklightEnabled)
        assertFalse(store.snapshot().captures.smartBacklightApplied)
    }

    @Test
    fun `backlight revert reports the truth when the awaited write fails, and still clears the flags`() {
        shell.failWith(ShellLines.vendorTimeout(30_000))
        val report = reset.run()
        assertEquals(RevertOutcome.FAILED, report.outcomes[RevertStep.BACKLIGHT])
        assertFalse(store.snapshot().device.smartBacklightEnabled, "nothing re-arms it")
    }

    @Test
    fun `backlight revert with no key is NEEDS_PERMISSION`() {
        shell.blocker = BrokerBlocker.NOT_PAIRED
        assertEquals(RevertOutcome.NEEDS_PERMISSION, reset.run().outcomes[RevertStep.BACKLIGHT])
    }

    // Step 3: the tile. ----------------------------------------------------------------------

    @Test
    fun `T35 - captured 1 with the permission held writes 1 in-process and clears the capture`() {
        permissions.writeSecureSettings = true
        captures { it.copy(qsBacklightPrevCaptured = true, qsBacklightPrev = 1) }
        val report = reset.run()
        assertEquals(RevertOutcome.SUCCESS, report.outcomes[RevertStep.QS_BACKLIGHT])
        assertEquals(listOf(1), masterSwitch.writes)
        assertFalse(store.snapshot().captures.qsBacklightPrevCaptured)
        assertNull(store.snapshot().captures.qsBacklightPrev)
    }

    @Test
    fun `T34 (3-0 form) - never captured puts the switch back to unset through the broker`() {
        permissions.writeSecureSettings = true
        val report = reset.run()
        assertEquals(RevertOutcome.SUCCESS, report.outcomes[RevertStep.QS_BACKLIGHT])
        assertTrue(shell.lines.contains("settings delete global agui_keyboard_background_light"))
        assertTrue(masterSwitch.writes.isEmpty())
    }

    @Test
    fun `captured value without the permission goes through the broker`() {
        captures { it.copy(qsBacklightPrevCaptured = true, qsBacklightPrev = 0) }
        assertEquals(RevertOutcome.SUCCESS, reset.run().outcomes[RevertStep.QS_BACKLIGHT])
        assertTrue(shell.lines.contains("settings put global agui_keyboard_background_light 0"))
    }

    @Test
    fun `tile revert with neither route is NEEDS_PERMISSION`() {
        shell.blocker = BrokerBlocker.NOT_PAIRED
        captures { it.copy(qsBacklightPrevCaptured = true, qsBacklightPrev = 1) }
        assertEquals(RevertOutcome.NEEDS_PERMISSION, reset.run().outcomes[RevertStep.QS_BACKLIGHT])
        assertTrue(store.snapshot().captures.qsBacklightPrevCaptured)
    }

    // Step 4: the orange side key. -----------------------------------------------------------

    @Test
    fun `side key revert sets the assistant off and restores the captured pair in one line`() {
        store.update { it.copy(dictation = it.dictation.copy(sideKeyAssistant = true)) }
        captures { it.copy(sideKeyOriginalCaptured = true, sideKeyOriginalPackage = "com.google.android.apps.bard", sideKeyOriginalActivity = "com.google.android.apps.bard.MainActivity") }
        val report = reset.run()
        assertEquals(RevertOutcome.SUCCESS, report.outcomes[RevertStep.SIDE_KEY])
        assertFalse(store.snapshot().dictation.sideKeyAssistant)
        assertTrue(shell.lines.contains("settings put system func1_long_press_package com.google.android.apps.bard; settings put system func1_long_press_activity com.google.android.apps.bard.MainActivity"))
        assertFalse(store.snapshot().captures.sideKeyOriginalCaptured)
    }

    @Test
    fun `side key revert with nothing captured succeeds without writing`() {
        assertEquals(RevertOutcome.SUCCESS, reset.run().outcomes[RevertStep.SIDE_KEY])
        assertFalse(shell.lines.any { it.contains("func1") })
    }

    @Test
    fun `side key revert drops a malformed capture instead of restoring it`() {
        captures { it.copy(sideKeyOriginalCaptured = true, sideKeyOriginalPackage = "x; rm -rf /", sideKeyOriginalActivity = "Main") }
        assertEquals(RevertOutcome.SUCCESS, reset.run().outcomes[RevertStep.SIDE_KEY])
        assertFalse(shell.lines.any { it.contains("rm -rf") })
        assertFalse(store.snapshot().captures.sideKeyOriginalCaptured)
    }

    // Step 5: the ring. ----------------------------------------------------------------------

    @Test
    fun `ring revert with nothing granted turns the ring off and succeeds without the broker`() {
        assertEquals(RevertOutcome.SUCCESS, reset.run().outcomes[RevertStep.NOTIFICATION_RING])
        assertFalse(store.snapshot().device.ringEnabled)
        assertFalse(shell.lines.any { it.startsWith("cmd notification") })
    }

    @Test
    fun `ring revert with the listener granted sends the disallow line and succeeds only once access is gone`() {
        permissions.listener = true
        permissions.fullScreenIntent = true
        shell.onLine = { line -> if (line == ShellLines.ringRevoke(identity)) permissions.listener = false }
        assertEquals(RevertOutcome.SUCCESS, reset.run().outcomes[RevertStep.NOTIFICATION_RING])
        assertTrue(
            shell.lines.contains(
                "cmd notification disallow_listener brobata.physiboard/brobata.physiboard.device.privileged.ring.NotificationRingListener; " +
                    "appops set brobata.physiboard USE_FULL_SCREEN_INTENT default",
            ),
        )
    }

    @Test
    fun `ring revert whose listener survives the line is FAILED`() {
        permissions.listener = true
        assertEquals(RevertOutcome.FAILED, reset.run().outcomes[RevertStep.NOTIFICATION_RING])
    }

    @Test
    fun `ring revert with grants but no key is NEEDS_PERMISSION`() {
        shell.blocker = BrokerBlocker.NOT_PAIRED
        permissions.fullScreenIntent = true
        assertEquals(RevertOutcome.NEEDS_PERMISSION, reset.run().outcomes[RevertStep.NOTIFICATION_RING])
    }

    // Independence. --------------------------------------------------------------------------

    @Test
    fun `one revert failing never skips the others`() {
        captures { it.copy(fnCtrlPrevCaptured = true, fnCtrlPrevEnable = 1, fnCtrlPrevFunction = 1, smartBacklightApplied = true) }
        shell.failWith("settings put system fn_programmable_key_enable 1; settings put system fn_programmable_key_function 1")
        val report = reset.run()
        assertEquals(RevertOutcome.FAILED, report.outcomes[RevertStep.FN_CTRL])
        assertEquals(RevertOutcome.SUCCESS, report.outcomes[RevertStep.BACKLIGHT])
        assertEquals(5, report.outcomes.size)
        assertEquals(ResetMessages.PARTIAL, report.message)
    }
}
