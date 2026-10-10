package brobata.physiboard.core.actions.launcher

import brobata.physiboard.core.actions.commands.BuiltInCommands
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS6.2 D, "Tap or hold", T90 to T95. */
class LauncherPressTest {

    private val q = KeyId.Letter('Q')
    private val qCode = AssignableKeys.keycodeOf(q)!!
    private val space = KeyId.Control(ControlKey.SPACE)
    private val app = ShortcutEntry.of(BuiltInCommands.app("com.whatsapp", "WhatsApp"))
    private val threshold = 500L

    private fun held(entry: ShortcutEntry = app, key: KeyId = q, at: Long = 1_000): LauncherPress {
        val step = LauncherPressTiming.begin(key, LauncherKeyDecision.Run(AssignableKeys.keycodeOf(key)!!, entry), at, threshold)
        assertNull(step.decision, "an assigned key does nothing on its down")
        return step.press!!
    }

    @Test
    fun `T90 a tap runs the assignment on the release, with nothing waiting on a timer`() {
        val press = held()
        assertEquals(1_500L, press.deadlineMs)
        val release = LauncherPressTiming.onRelease(press, 1_120)
        assertEquals(LauncherKeyDecision.Run(qCode, app), release.decision)
        assertNull(release.press, "the release ends the press")
    }

    @Test
    fun `T91 held to the threshold the sheet opens for the key, and the release then does nothing`() {
        val press = held()
        assertNull(LauncherPressTiming.onTick(press, 1_499).decision, "one millisecond short is still a tap")
        val tick = LauncherPressTiming.onTick(press, 1_500)
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(qCode, byHold = true), tick.decision)
        val settled = tick.press!!
        assertTrue(settled.settled)
        val release = LauncherPressTiming.onRelease(settled, 1_900)
        assertNull(release.decision, "a hold never launches as well")
        assertNull(release.press)
    }

    @Test
    fun `T92 auto-repeats never launch and open the sheet at most once`() {
        var press = held()
        // The system repeats from about 400 ms, every 50 ms.
        for (at in listOf(1_400L, 1_450L)) {
            val step = LauncherPressTiming.onTick(press, at)
            assertNull(step.decision)
            press = step.press!!
        }
        val fired = LauncherPressTiming.onTick(press, 1_500)
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(qCode, byHold = true), fired.decision)
        press = fired.press!!
        for (at in listOf(1_550L, 1_600L, 1_650L)) {
            val step = LauncherPressTiming.onTick(press, at)
            assertNull(step.decision, "the sheet opens once")
            press = step.press!!
        }
        assertNull(LauncherPressTiming.onRelease(press, 1_700).decision)
    }

    @Test
    fun `T93 a release after the threshold whose timer had not run yet is still a hold`() {
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(qCode, byHold = true), LauncherPressTiming.onRelease(held(), 1_600).decision)
    }

    @Test
    fun `T94 an unassigned key opens the sheet on its down, as before, and its release does nothing more`() {
        val step = LauncherPressTiming.begin(q, LauncherKeyDecision.OpenAssignmentSheet(qCode), 1_000, threshold)
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(qCode), step.decision)
        val press = step.press!!
        assertTrue(press.settled, "nothing is left for a timer to do")
        assertNull(LauncherPressTiming.onTick(press, 2_000).decision)
        assertNull(LauncherPressTiming.onRelease(press, 2_000).decision)
        assertNull(LauncherPressTiming.begin(q, LauncherKeyDecision.FallThrough, 1_000, threshold).press, "not this feature's key")
    }

    @Test
    fun `T95 the quick launcher key - a tap opens the launcher, a hold opens its sheet`() {
        val spaceCode = AssignableKeys.KEYCODE_SPACE
        val tap = LauncherPressTiming.onRelease(held(ShortcutEntry.QUICK_LAUNCHER, space), 1_100).decision
        assertTrue(tap is LauncherKeyDecision.Run && tap.entry.isQuickLauncher)
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(spaceCode, byHold = true), LauncherPressTiming.onTick(held(ShortcutEntry.QUICK_LAUNCHER, space), 1_500).decision)
    }

    @Test
    fun `a press abandoned by a field change neither launches nor opens the sheet`() {
        val abandoned = LauncherPressTiming.abandon(held())
        assertNull(LauncherPressTiming.onTick(abandoned, 5_000).decision)
        assertNull(LauncherPressTiming.onRelease(abandoned, 1_100).decision)
    }

    @Test
    fun `the sheet names what the key does now`() {
        assertEquals("Now: WhatsApp", AssignmentSheet.currentLabel(app))
        assertEquals("Now: PhysiBoard QuickLauncher", AssignmentSheet.currentLabel(ShortcutEntry.QUICK_LAUNCHER))
        assertEquals("Now: com.example", AssignmentSheet.currentLabel(ShortcutEntry(type = ShortcutEntry.TYPE_APP, packageName = "com.example")))
    }
}
