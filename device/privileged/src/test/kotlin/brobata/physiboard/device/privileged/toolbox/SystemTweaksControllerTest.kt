package brobata.physiboard.device.privileged.toolbox

import brobata.physiboard.core.toolbox.AnimationSpeed
import brobata.physiboard.core.toolbox.SystemTweaks
import brobata.physiboard.device.privileged.FakeShell
import brobata.physiboard.device.privileged.broker.ShellResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemTweaksControllerTest {
    @Test
    fun `T26 reads the three lines into fast history off one-handed on`() {
        val shell = FakeShell().apply { responses[SystemTweaks.READ_LINE] = ShellResult.Ok("0.5\nnull\n1") }
        val outcome = SystemTweaksController(shell).read() as TweaksReadOutcome.Loaded
        assertEquals(AnimationSpeed.FAST, outcome.reading.animation)
        assertEquals(false, outcome.reading.notificationHistoryOn)
        assertEquals(true, outcome.reading.oneHandedOn)
    }

    @Test
    fun `T28 empty output is unreadable`() {
        val shell = FakeShell().apply { responses[SystemTweaks.READ_LINE] = ShellResult.Ok("") }
        assertEquals(TweaksReadOutcome.Unreadable, SystemTweaksController(shell).read())
    }

    @Test
    fun `read reports not paired without sending a line`() {
        val shell = FakeShell.notPaired()
        assertEquals(TweaksReadOutcome.NotPaired, SystemTweaksController(shell).read())
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `T29 turning notification history off sends a delete not a write of 0`() {
        val shell = FakeShell()
        SystemTweaksController(shell).setNotificationHistory(false)
        assertEquals(listOf("settings delete secure ${SystemTweaks.NOTIFICATION_HISTORY_KEY}"), shell.lines)
    }

    @Test
    fun `T30 reset all sends the one combined line`() {
        val shell = FakeShell()
        assertTrue(SystemTweaksController(shell).resetAll())
        assertEquals(listOf(SystemTweaks.resetAllLine), shell.lines)
    }
}
