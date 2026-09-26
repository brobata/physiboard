package brobata.physiboard.core.actions.launcher

import brobata.physiboard.core.actions.commands.BuiltInCommands
import brobata.physiboard.core.actions.commands.CommandCatalog
import brobata.physiboard.core.actions.commands.CommandIds
import brobata.physiboard.core.actions.commands.CommandSource
import brobata.physiboard.core.actions.commands.LaunchSpec
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS12, T36 to T39, and the SS6 / SS11 rows. */
class LauncherShortcutsTest {

    private val space = AssignableKeys.KEYCODE_SPACE
    private val enter = AssignableKeys.KEYCODE_ENTER
    private val q = AssignableKeys.keycodeOf(KeyId.Letter('Q'))!!
    private val app = ShortcutEntry.of(BuiltInCommands.app("com.whatsapp", "WhatsApp"))

    @Test
    fun `T36 only one key holds the quick launcher`() {
        val s = LauncherShortcuts().assign(space, ShortcutEntry.QUICK_LAUNCHER).assign(enter, ShortcutEntry.QUICK_LAUNCHER)
        assertEquals(enter, s.quickLauncherKeycode)
        assertNull(s[space])
    }

    @Test
    fun `T37 fresh preferences give Space the quick launcher on first read`() {
        val outcome = LauncherShortcuts().applyDefault(defaultAlreadyAssigned = false)
        assertEquals(space, outcome.shortcuts.quickLauncherKeycode)
        assertTrue(outcome.defaultAssigned)
        assertFalse(outcome.blockedBySpace)
    }

    @Test
    fun `T38 Space already holding an app blocks the default and reports it`() {
        val outcome = LauncherShortcuts().assign(space, app).applyDefault(defaultAlreadyAssigned = false)
        assertEquals(app, outcome.shortcuts[space])
        assertTrue(outcome.blockedBySpace)
        assertFalse(outcome.defaultAssigned)
    }

    @Test
    fun `removing the quick launcher from every key never re-adds it`() {
        val outcome = LauncherShortcuts().applyDefault(false).shortcuts.remove(space).applyDefault(defaultAlreadyAssigned = true)
        assertNull(outcome.shortcuts.quickLauncherKeycode)
    }

    @Test
    fun `T39 swapping Q and Space exchanges the entries in both directions`() {
        val s = LauncherShortcuts().assign(q, app).assign(space, ShortcutEntry.QUICK_LAUNCHER).swap(q, space)
        assertEquals(q, s.quickLauncherKeycode)
        assertEquals(app, s[space])
        assertEquals(s, s.swap(q, space).swap(q, space))
        assertEquals(s, s.swap(100, 101), "dropping on nothing does nothing")
    }

    @Test
    fun `the JSON document round-trips and legacy entries derive a launch spec`() {
        val s = LauncherShortcuts().assign(q, app).assign(space, ShortcutEntry.QUICK_LAUNCHER)
        val parsed = LauncherShortcuts.parse(LauncherShortcuts.encode(s))
        assertEquals(s, parsed)
        val legacy = LauncherShortcuts.parse("""{"62":{"type":"quick_launcher"},"29":{"type":"app","packageName":"a.b","appName":"AB"},"x":{"type":"app"},"30":5,"31":{"nope":true}}""")
        assertEquals(LaunchSpec.InternalAction("open_quick_launcher"), legacy[62]!!.launch)
        assertEquals(LaunchSpec.AppPackage("a.b"), legacy[29]!!.launch)
        assertEquals(setOf(62, 29), legacy.entries.keys)
        assertEquals(LauncherShortcuts(), LauncherShortcuts.parse("not json"))
    }

    @Test
    fun `a backup with a shortcut on a non-letter key is stored but never fires`() {
        val s = LauncherShortcuts.parse("""{"131":{"type":"app","packageName":"a.b"}}""")
        assertEquals(1, s.entries.size)
        assertNull(AssignableKeys.keyOf(131))
        assertEquals("Key 131", AssignableKeys.label(131))
        assertEquals("␣", AssignableKeys.label(space))
    }

    @Test
    fun `uninstalled apps are pruned when the assignments screen opens`() {
        val s = LauncherShortcuts().assign(q, app).assign(space, ShortcutEntry.QUICK_LAUNCHER).pruneUninstalled { false }
        assertEquals(setOf(space), s.entries.keys)
    }

    @Test
    fun `T50 a real uninstall removes every shortcut targeting the removed package`() {
        val s = LauncherShortcuts().assign(q, app).assign(space, ShortcutEntry.QUICK_LAUNCHER).removeTargeting("com.whatsapp")
        assertEquals(setOf(space), s.entries.keys)
    }

    @Test
    fun `T51 an unrelated package name removes nothing`() {
        val s = LauncherShortcuts().assign(q, app).assign(space, ShortcutEntry.QUICK_LAUNCHER)
        assertEquals(s, s.removeTargeting("com.example.other"))
    }

    @Test
    fun `resolution prefers the live catalogue, then the stored launch spec, and quick launcher entries always open the sheet`() {
        val live = BuiltInCommands.app("com.whatsapp", "WhatsApp Renamed")
        val catalog = CommandCatalog(listOf(live))
        assertEquals(ShortcutRun.RunCommand(live), ShortcutRun.resolve(app, catalog::find))
        val stale = ShortcutRun.resolve(app, CommandCatalog.EMPTY::find)
        assertIs<ShortcutRun.RunLaunchSpec>(stale)
        assertEquals(LaunchSpec.AppPackage("com.whatsapp"), stale.launch)
        assertEquals(ShortcutRun.OpenQuickLauncher, ShortcutRun.resolve(ShortcutEntry.QUICK_LAUNCHER, CommandCatalog.EMPTY::find))
        assertEquals(ShortcutRun.Nothing, ShortcutRun.resolve(ShortcutEntry(type = "shortcut"), CommandCatalog.EMPTY::find))
        val legacyApp = ShortcutEntry(type = ShortcutEntry.TYPE_APP, packageName = "a.b", launch = LaunchSpec.AppPackage("a.b"))
        assertEquals("a.b", (ShortcutRun.resolve(legacyApp, CommandCatalog.EMPTY::find) as ShortcutRun.RunLaunchSpec).fallbackPackage)
    }

    @Test
    fun `the entry written from a command carries every field`() {
        assertEquals(ShortcutEntry.TYPE_COMMAND, app.type)
        assertEquals(CommandIds.appCommandId("com.whatsapp"), app.commandId)
        assertEquals(CommandSource.APPS, app.source)
        assertEquals("WhatsApp", app.title)
        assertEquals("com.whatsapp", app.displayPackage)
    }

    // ---- SS6.2, the three paths ----

    private val settings = LauncherKeySettings()
    private val shortcuts = LauncherShortcuts().assign(space, ShortcutEntry.QUICK_LAUNCHER)

    @Test
    fun `C - in a text field, Sym plus the assigned key runs and an unassigned key falls through`() {
        val run = LauncherKeyRouter.inTextField(KeyId.Control(ControlKey.SPACE), shortcuts, settings, symHeldOrPending = true, isInitialPress = true, ctrlLatchActive = false)
        assertIs<LauncherKeyDecision.Run>(run)
        assertEquals(LauncherKeyDecision.FallThrough, LauncherKeyRouter.inTextField(KeyId.Letter('Q'), shortcuts, settings, true, true, false))
        assertEquals(LauncherKeyDecision.FallThrough, LauncherKeyRouter.inTextField(KeyId.Control(ControlKey.SPACE), shortcuts, settings, false, true, false))
        assertEquals(LauncherKeyDecision.FallThrough, LauncherKeyRouter.inTextField(KeyId.Control(ControlKey.SPACE), shortcuts, settings.copy(symShortcutsEnabled = false), true, true, false))
        assertEquals(LauncherKeyDecision.FallThrough, LauncherKeyRouter.inTextField(KeyId.Control(ControlKey.SPACE), shortcuts, settings, true, isInitialPress = false, ctrlLatchActive = false))
    }

    @Test
    fun `A - a bare assigned key on the home screen does nothing with the home switch off`() {
        assertEquals(LauncherKeyDecision.FallThrough, LauncherKeyRouter.outsideTextField(KeyId.Control(ControlKey.SPACE), shortcuts, settings, false, foregroundIsHome = true, fromArmedMode = false))
        val on = settings.copy(homeScreenShortcutsEnabled = true)
        assertIs<LauncherKeyDecision.Run>(LauncherKeyRouter.outsideTextField(KeyId.Control(ControlKey.SPACE), shortcuts, on, false, true, false))
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(q), LauncherKeyRouter.outsideTextField(KeyId.Letter('Q'), shortcuts, on, false, true, false))
        assertEquals(LauncherKeyDecision.FallThrough, LauncherKeyRouter.outsideTextField(KeyId.Letter('Q'), shortcuts, on, false, foregroundIsHome = false, fromArmedMode = false))
        assertEquals(LauncherKeyDecision.FallThrough, LauncherKeyRouter.outsideTextField(KeyId.Letter('Q'), shortcuts, on, ctrlLatchActive = true, foregroundIsHome = true, fromArmedMode = false))
    }

    @Test
    fun `B - Sym arms the mode, a second Sym disarms it, an assigned key fires as case A`() {
        val (armed, effect) = PowerShortcutMode.onSymDown(PowerShortcutState.IDLE, 1000, enabled = true, navModeActive = true)
        assertTrue(armed.isArmed)
        assertTrue(effect.consumed && effect.scheduleToast && effect.suspendNavMode)
        val (disarmed, second) = PowerShortcutMode.onSymDown(armed, 1200, true, false)
        assertFalse(disarmed.isArmed)
        assertTrue(second.consumed && second.restoreNavMode)
        assertFalse(PowerShortcutMode.shouldShowToast(disarmed, 1000), "no toast if disarmed before 500 ms")

        val (afterOther, otherEffect) = PowerShortcutMode.onKeyDown(armed, KeyId.Digit('1'), 1300)
        assertTrue(afterOther.isArmed, "keys outside the 29 leave the mode armed")
        assertFalse(otherEffect.consumed)
        val (afterKey, keyEffect) = PowerShortcutMode.onKeyDown(armed, KeyId.Letter('Q'), 1300)
        assertFalse(afterKey.isArmed)
        assertEquals(KeyId.Letter('Q'), keyEffect.fireKey)
        assertTrue(keyEffect.restoreNavMode)
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(q), LauncherKeyRouter.outsideTextField(KeyId.Letter('Q'), shortcuts, settings, false, foregroundIsHome = false, fromArmedMode = true))
    }

    @Test
    fun `B - a physically held Sym fires the assigned key directly outside a text field, even unarmed`() {
        val decision = LauncherKeyRouter.outsideTextField(
            KeyId.Control(ControlKey.SPACE), shortcuts, settings, ctrlLatchActive = false,
            foregroundIsHome = false, fromArmedMode = false, symPhysicallyHeld = true,
        )
        assertIs<LauncherKeyDecision.Run>(decision)
        // Ctrl latch still blocks it, and it needs power_shortcuts_enabled, not the home switch.
        assertEquals(
            LauncherKeyDecision.FallThrough,
            LauncherKeyRouter.outsideTextField(
                KeyId.Control(ControlKey.SPACE), shortcuts, settings, ctrlLatchActive = true,
                foregroundIsHome = false, fromArmedMode = false, symPhysicallyHeld = true,
            ),
        )
        assertEquals(
            LauncherKeyDecision.FallThrough,
            LauncherKeyRouter.outsideTextField(
                KeyId.Control(ControlKey.SPACE), shortcuts, settings.copy(symShortcutsEnabled = false), ctrlLatchActive = false,
                foregroundIsHome = false, fromArmedMode = false, symPhysicallyHeld = true,
            ),
        )
    }

    @Test
    fun `B - the mode disarms by itself after 5000 ms and does nothing when power shortcuts are off`() {
        val (armed, _) = PowerShortcutMode.onSymDown(PowerShortcutState.IDLE, 0, true, false)
        val (still, _) = PowerShortcutMode.onTimeout(armed, 4999)
        assertTrue(still.isArmed)
        val (gone, _) = PowerShortcutMode.onTimeout(armed, 5000)
        assertFalse(gone.isArmed)
        val (off, effect) = PowerShortcutMode.onSymDown(PowerShortcutState.IDLE, 0, enabled = false, navModeActive = false)
        assertFalse(off.isArmed)
        assertFalse(effect.consumed)
    }
}
