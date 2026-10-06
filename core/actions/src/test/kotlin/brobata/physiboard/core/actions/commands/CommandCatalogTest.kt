package brobata.physiboard.core.actions.commands

import brobata.physiboard.core.actions.launcher.AssignmentSheet
import brobata.physiboard.core.keys.KeyId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS8 (the catalogue, sources, icons) and SS6.4 (the sheet's content). */
class CommandCatalogTest {

    private fun catalog(resolves: Boolean = true) = CommandCatalog(
        BuiltInCommands.physiboard() +
            listOf(BuiltInCommands.app("com.whatsapp", "WhatsApp"), BuiltInCommands.app("org.telegram.messenger", "Telegram")) +
            BuiltInCommands.appActions({ resolves }, { "App" }) +
            BuiltInCommands.deviceControl(36, { resolves }, "brobata.physiboard") +
            BuiltInCommands.navigation(),
    )

    @Test
    fun `sources rank Apps, PhysiBoard, App actions, Device control, Navigation and keep their storage values`() {
        assertEquals(listOf("apps", "pastiera", "app_actions", "device_control", "nav_actions"), CommandSource.entries.map { it.storageValue })
        assertEquals(CommandSource.PHYSIBOARD, CommandSource.fromStorage("pastiera"))
        assertEquals(CommandSource.PHYSIBOARD, CommandSource.fromKind("PhysiBoardAction"))
    }

    @Test
    fun `the PhysiBoard source offers the quick launcher and settings to assigned keys and nav mode only, the assistant to all three`() {
        val c = catalog()
        assertEquals(setOf(CommandSurface.ASSIGNED_KEY, CommandSurface.NAV_MODE), c.find(CommandIds.QUICK_LAUNCHER)!!.surfaces)
        assertEquals(Command.ALL_SURFACES, c.find(CommandIds.VOICE_ASSISTANT)!!.surfaces)
        assertEquals(LaunchSpec.InternalAction(InternalActions.OPEN_QUICK_LAUNCHER), c.find(CommandIds.QUICK_LAUNCHER)!!.launch)
    }

    @Test
    fun `app actions and settings commands are listed only when they resolve`() {
        assertTrue(catalog(resolves = false).commands.none { it.source == CommandSource.APP_ACTIONS })
        assertTrue(catalog(resolves = false).commands.none { it.id.startsWith("settings.android") })
        val full = catalog()
        assertEquals(8, full.commands.count { it.source == CommandSource.APP_ACTIONS })
        assertNotNull(full.find("settings.android.internet_panel"))
        assertEquals("System panel", full.find("settings.android.internet_panel")!!.subtitle)
        assertTrue(BuiltInCommands.deviceControl(28, { true }, "x").none { it.id == "settings.android.internet_panel" }, "Android 10+ only")
        val own = full.find("settings.android.pastiera_notifications")!!.launch as LaunchSpec.IntentUri
        assertEquals(listOf("android.provider.extra.APP_PACKAGE=brobata.physiboard"), own.flags)
    }

    @Test
    fun `navigation commands exist only on the nav mode surface`() {
        val nav = catalog().commands.filter { it.source == CommandSource.NAVIGATION }
        assertEquals(12 + 16, nav.size)
        assertTrue(nav.all { it.surfaces == setOf(CommandSurface.NAV_MODE) })
        assertEquals(LaunchSpec.NavAction("keycode", "DPAD_UP"), catalog().find("nav.keycode.DPAD_UP")!!.launch)
        assertEquals("Action", catalog().find("nav.action.copy")!!.subtitle)
    }

    @Test
    fun `the quick launcher surface is filtered by source visibility and assigned keys see every source`() {
        val c = catalog()
        val ql = c.forQuickLauncher(SourceVisibility())
        assertTrue(ql.all { it.source == CommandSource.APPS || it.source == CommandSource.PHYSIBOARD })
        assertTrue(ql.none { it.id == CommandIds.QUICK_LAUNCHER }, "the quick launcher does not list itself")
        val sheet = AssignmentSheet.candidates(c)
        assertTrue(sheet.any { it.source == CommandSource.DEVICE_CONTROL })
        assertTrue(sheet.none { it.source == CommandSource.NAVIGATION })
    }

    @Test
    fun `icons follow the first-match rules`() {
        val c = catalog()
        assertEquals(CommandIcon.MAGNIFIER, c.find(CommandIds.QUICK_LAUNCHER)!!.icon)
        assertEquals(CommandIcon.GEAR, c.find(CommandIds.MAIN)!!.icon)
        assertEquals(CommandIcon.SKIP_NEXT, c.find(CommandIds.MEDIA_NEXT)!!.icon)
        assertEquals(CommandIcon.SUN, c.find(CommandIds.BRIGHTNESS_UP)!!.icon)
        assertEquals(CommandIcon.APPS_GRID, c.find("settings.android.apps")!!.icon)
        assertEquals(CommandIcon.KEYBOARD, c.find("settings.android.input_method")!!.icon)
        assertEquals(CommandIcon.BELL, c.find("settings.android.pastiera_notifications")!!.icon)
        assertEquals(CommandIcon.EVENT, c.find("niagara.agenda")!!.icon)
        assertEquals(CommandIcon.MICROPHONE, c.find("homeassistant.assist")!!.icon)
        assertEquals(CommandIcon.HOME, c.find("homeassistant.navigate")!!.icon)
        assertEquals(CommandIcon.ARROW_UP, c.find("nav.keycode.DPAD_UP")!!.icon)
        assertEquals(CommandIcon.COPY, c.find("nav.action.copy")!!.icon)
        assertEquals(CommandIcon.GEAR, c.find(CommandIds.DEVICE_HOME)!!.icon)
        assertEquals(CommandIcon.COMMAND_KEY, c.find(CommandIds.VOICE_ASSISTANT)!!.icon)
        assertTrue(c.find("app:com.whatsapp")!!.hasAppIcon)
        assertEquals(CommandIcon.PRIVATE, c.find(CommandIds.TOGGLE_PRIVATE_MODE)!!.icon)
    }

    @Test
    fun `private mode is a PhysiBoard command on every surface, so a key, a Sym shortcut, the quick launcher and nav mode can all bind it`() {
        val command = catalog().find(CommandIds.TOGGLE_PRIVATE_MODE)!!
        assertEquals(CommandSource.PHYSIBOARD, command.source)
        assertEquals(LaunchSpec.InternalAction(InternalActions.TOGGLE_PRIVATE_MODE), command.launch)
        assertEquals(Command.ALL_SURFACES, command.surfaces)
        assertTrue(AssignmentSheet.candidates(catalog()).any { it.id == CommandIds.TOGGLE_PRIVATE_MODE })
    }

    @Test
    fun `the assignment sheet puts apps starting with the key's letter first, then source rank and label`() {
        val ordered = AssignmentSheet.order(AssignmentSheet.candidates(catalog()), KeyId.Letter('T'), "")
        assertEquals("Telegram", ordered[0].label)
        assertEquals("WhatsApp", ordered[1].label)
        assertEquals(CommandSource.PHYSIBOARD, ordered[2].source)
        val searched = AssignmentSheet.order(AssignmentSheet.candidates(catalog()), KeyId.Letter('T'), "whats")
        assertEquals(listOf("WhatsApp"), searched.map { it.label })
        val chip = AssignmentSheet.order(AssignmentSheet.candidates(catalog()), null, "", CommandSource.DEVICE_CONTROL)
        assertTrue(chip.all { it.source == CommandSource.DEVICE_CONTROL })
        assertEquals(chip.map { it.label.lowercase() }.sorted(), chip.map { it.label.lowercase() })
    }

    @Test
    fun `launch specs round-trip through JSON and unknown shapes read as null`() {
        val specs = listOf(
            LaunchSpec.AppPackage("a.b"),
            LaunchSpec.IntentUri("android.intent.action.VIEW", "niagara://search", "bitpit.launcher", null, listOf("android.intent.category.BROWSABLE"), listOf("clear_top", "k=v")),
            LaunchSpec.InternalAction("open_quick_launcher"),
            LaunchSpec.NavAction("keycode", "TAB"),
        )
        for (spec in specs) assertEquals(spec, LaunchSpec.fromJson(LaunchSpec.toJson(spec)))
        assertEquals(null, LaunchSpec.fromJson(null))
        assertFalse(SourceVisibility.encode(SourceVisibility().with(CommandSource.APPS, false)).contains("\"apps\":{\"quick_launcher\":true}"))
    }
}
