package brobata.physiboard.device.privileged.setup

import brobata.physiboard.device.privileged.FakeShell
import brobata.physiboard.device.privileged.InMemoryDeviceStateStore
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.updateCaptures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FnCtrlRemapTest {
    private val settings = FakeSystemSettingsAccess()
    private val store = InMemoryDeviceStateStore()

    @Test
    fun `apply captures the phone's own values once, then writes 1 and 1`() {
        settings.put(VendorKeyRows.FN_ENABLE, 0)
        settings.put(VendorKeyRows.FN_FUNCTION, 0)
        val shell = FakeShell()
        val remap = FnCtrlRemap(shell, settings, store)

        val outcome = remap.apply()

        assertEquals(listOf(ShellLines.joined(ShellLines.systemPut(VendorKeyRows.FN_ENABLE, "1"), ShellLines.systemPut(VendorKeyRows.FN_FUNCTION, "1"))), shell.lines)
        val captures = store.snapshot().captures
        assertTrue(captures.fnCtrlPrevCaptured)
        assertEquals(0, captures.fnCtrlPrevEnable)
        assertEquals(0, captures.fnCtrlPrevFunction)
        // The fake settings store does not reflect the write itself, so the read-back confirmation fails honestly here.
        assertEquals(RevertOutcome.FAILED, outcome)
    }

    @Test
    fun `apply is a no-op success when already enabled`() {
        settings.put(VendorKeyRows.FN_ENABLE, 1)
        settings.put(VendorKeyRows.FN_FUNCTION, 1)
        val shell = FakeShell()
        val remap = FnCtrlRemap(shell, settings, store)

        assertEquals(RevertOutcome.SUCCESS, remap.apply())
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `apply reports needs permission when no key is stored`() {
        val remap = FnCtrlRemap(FakeShell.notPaired(), settings, store)
        assertEquals(RevertOutcome.NEEDS_PERMISSION, remap.apply())
    }

    @Test
    fun `apply confirms success once the read-back shows both keys as 1`() {
        settings.put(VendorKeyRows.FN_ENABLE, 0)
        settings.put(VendorKeyRows.FN_FUNCTION, 0)
        val line = ShellLines.joined(ShellLines.systemPut(VendorKeyRows.FN_ENABLE, "1"), ShellLines.systemPut(VendorKeyRows.FN_FUNCTION, "1"))
        val shell = FakeShell().apply {
            responses[line] = ShellResult.Ok("")
            onLine = { settings.put(VendorKeyRows.FN_ENABLE, 1); settings.put(VendorKeyRows.FN_FUNCTION, 1) }
        }
        assertEquals(RevertOutcome.SUCCESS, FnCtrlRemap(shell, settings, store).apply())
    }

    @Test
    fun `T47 reset writes captured enable 1 function unset back as 1 and 0`() {
        store.updateCaptures { it.copy(fnCtrlPrevCaptured = true, fnCtrlPrevEnable = 1, fnCtrlPrevFunction = null) }
        val shell = FakeShell()
        FnCtrlRemap(shell, settings, store).resetToDefault()
        assertEquals(listOf(ShellLines.joined(ShellLines.systemPut(VendorKeyRows.FN_ENABLE, "1"), ShellLines.systemPut(VendorKeyRows.FN_FUNCTION, "0"))), shell.lines)
    }

    @Test
    fun `T48 reset never captured writes 0 and 0`() {
        val shell = FakeShell()
        FnCtrlRemap(shell, settings, store).resetToDefault()
        assertEquals(listOf(ShellLines.joined(ShellLines.systemPut(VendorKeyRows.FN_ENABLE, "0"), ShellLines.systemPut(VendorKeyRows.FN_FUNCTION, "0"))), shell.lines)
    }
}

class SideKeyAssistantRemapTest {
    private val identity = AppIdentity("brobata.physiboard", "brobata.physiboard/.Ring")
    private val settings = FakeSystemSettingsAccess()
    private val store = InMemoryDeviceStateStore()

    @Test
    fun `bind captures a well-formed original once and writes the three vendor rows`() {
        settings.put(VendorKeyRows.SIDE_KEY_PACKAGE, "com.google.android.googlequicksearchbox")
        settings.put(VendorKeyRows.SIDE_KEY_ACTIVITY, "com.google.android.apps.bard.MainActivity")
        val line = ShellLines.joined(
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_PACKAGE, identity.packageName),
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_ACTIVITY, "brobata.physiboard.AssistantTriggerActivity"),
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_SHORTCUT_ENABLE, "1"),
        )
        val shell = FakeShell().apply {
            responses[line] = ShellResult.Ok("")
            onLine = { settings.put(VendorKeyRows.SIDE_KEY_PACKAGE, identity.packageName) }
        }
        val remap = SideKeyAssistantRemap(shell, settings, store, identity)

        assertEquals(RevertOutcome.SUCCESS, remap.bind("brobata.physiboard.AssistantTriggerActivity"))
        val captures = store.snapshot().captures
        assertTrue(captures.sideKeyOriginalCaptured)
        assertEquals("com.google.android.googlequicksearchbox", captures.sideKeyOriginalPackage)
    }

    @Test
    fun `bind never captures a malformed original`() {
        settings.put(VendorKeyRows.SIDE_KEY_PACKAGE, "com.x; rm")
        settings.put(VendorKeyRows.SIDE_KEY_ACTIVITY, "com.x.Y")
        SideKeyAssistantRemap(FakeShell.notPaired(), settings, store, identity).bind("brobata.physiboard.AssistantTriggerActivity")
        assertFalse(store.snapshot().captures.sideKeyOriginalCaptured)
    }

    @Test
    fun `unbind with nothing captured is a no-op success`() {
        assertEquals(RevertOutcome.SUCCESS, SideKeyAssistantRemap(FakeShell(), settings, store, identity).unbind())
    }
}
