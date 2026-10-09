package brobata.physiboard.device.privileged.setup

import brobata.physiboard.core.toolbox.AccessibilityServiceList
import brobata.physiboard.device.privileged.FakeShell
import brobata.physiboard.device.privileged.broker.ShellResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: broker-privileged-toolbox.md SS9, "Turn on with pairing" for the accessibility service. */
class AccessibilityServiceSwitchTest {

    private val pkg = "brobata.physiboard.dev3"
    private val ours = AccessibilityServiceList.component(pkg)
    private val other = "cz.mobilesoft.appblock/cz.mobilesoft.appblock.service.LockAccessibilityService"
    private val shell = FakeShell()
    private val permissions = FakePermissionProbe()
    private val switch = AccessibilityServiceSwitch(shell, permissions, pkg)

    @Test
    fun `appends ours to the other services and reports on once the re-read agrees`() {
        shell.responses[AccessibilityServiceList.READ_LINE] = ShellResult.Ok("$other\n1\n")
        shell.onLine = { line -> if (line.startsWith("settings put")) permissions.accessibilityService = true }
        assertEquals(AccessibilityTurnOnOutcome.TURNED_ON, switch.turnOn())
        assertTrue(shell.lines.contains("settings put secure enabled_accessibility_services '$other:$ours'; settings put secure accessibility_enabled 1"))
    }

    @Test
    fun `a write that did not take is a failure`() {
        shell.responses[AccessibilityServiceList.READ_LINE] = ShellResult.Ok("null\n0\n")
        assertEquals(AccessibilityTurnOnOutcome.FAILED, switch.turnOn())
    }

    @Test
    fun `already on, or not paired, writes nothing`() {
        permissions.accessibilityService = true
        assertEquals(AccessibilityTurnOnOutcome.ALREADY_ON, switch.turnOn())
        permissions.accessibilityService = false
        val unpaired = FakeShell.notPaired()
        assertEquals(AccessibilityTurnOnOutcome.NOT_PAIRED, AccessibilityServiceSwitch(unpaired, permissions, pkg).turnOn())
        assertTrue(shell.lines.isEmpty() && unpaired.lines.isEmpty())
    }

    @Test
    fun `an unreadable list or an unsafe entry refuses`() {
        shell.responses[AccessibilityServiceList.READ_LINE] = ShellResult.Failed("closed")
        assertEquals(AccessibilityTurnOnOutcome.FAILED, switch.turnOn())
        shell.responses[AccessibilityServiceList.READ_LINE] = ShellResult.Ok("a/b;reboot\n1\n")
        assertEquals(AccessibilityTurnOnOutcome.LIST_NOT_SAFE, switch.turnOn())
        assertEquals(listOf(AccessibilityServiceList.READ_LINE, AccessibilityServiceList.READ_LINE), shell.lines)
    }
}
