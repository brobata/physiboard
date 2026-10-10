package brobata.physiboard.core.shell

import brobata.physiboard.core.keys.AltBackspaceAction
import brobata.physiboard.core.settings.BarButton
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SettingsCodec
import brobata.physiboard.core.settings.SettingsKeys
import brobata.physiboard.core.settings.StatusBarVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: settings-catalog.md SS7, adapted for 3.0's single settings store per app-shell.md SS30. */
class BackupCodecTest {

    private val meta = BackupMeta(
        versionCode = 30000,
        versionName = "3.0.0-dev",
        timestampIso = "2026-09-24T00:00:00Z",
        components = listOf("prefs/physiboard_settings.json", "files/ctrl_key_mappings.json"),
    )

    @Test
    fun `meta encode then decode round-trips including components`() {
        val text = BackupCodec.encodeMeta(meta)
        val decoded = BackupCodec.decodeMeta(text)
        assertEquals(meta, decoded)
    }

    @Test
    fun `a meta document with no components field decodes to an empty list, for back-compat`() {
        val decoded = BackupCodec.decodeMeta(
            """{"versionCode": 1, "versionName": "3.0.0-dev", "timestamp": "2026-09-24T00:00:00Z"}""",
        )
        assertEquals(emptyList(), decoded?.components)
    }

    @Test
    fun `a missing backup_meta field fails to decode, modeling not-a-PhysiBoard-backup`() {
        assertNull(BackupCodec.decodeMeta("""{"versionCode": 1}"""))
        assertNull(BackupCodec.decodeMeta("not json at all"))
    }

    @Test
    fun `prefs file encode then decode round-trips the whole flat map under the string type`() {
        val settings = Settings()
        val text = BackupCodec.encodePrefsFile("physiboard_settings", settings)
        val decoded = BackupCodec.decodePrefsFile(text)
        requireNotNull(decoded)
        val (name, entries) = decoded
        assertEquals("physiboard_settings", name)
        assertEquals(SettingsCodec.toMap(settings), entries)
        assertTrue(text.contains(""""type": "string""""))
    }

    /**
     * The 3.1 settings reorganization took every strip-only control off the screens
     * (docs/plans/settings-reorganization.md). Their keys must still leave in a backup and come
     * back on restore exactly as they were, so a backup made before the change loses nothing.
     */
    @Test
    fun `settings no screen offers any more still round-trip through a backup`() {
        val base = Settings()
        val custom = base.copy(
            statusBar = base.statusBar.copy(
                visibility = StatusBarVisibility.ALWAYS,
                apps = setOf("com.example.chat"),
                heightDp = 36,
                leftButtons = listOf(BarButton.UNDO, BarButton.CLIPBOARD),
                rightButtons = listOf(BarButton.REDO),
                hideWhereNothingToSuggest = false,
                accessibilityLiveAnnouncementsEnabled = true,
                accessibilitySuggestionsAnnouncementDelayMs = 900,
                theme = base.statusBar.theme.copy(
                    ledInactive = 0xFF010203.toInt(),
                    ledActive = 0xFF040506.toInt(),
                    ledLocked = 0xFF070809.toInt(),
                    showLeds = true,
                    keyCornerRadiusRatio = 0.5,
                    chromeCornerRadiusRatio = 0.25,
                    suggestionsHeightScale = 1.5,
                ),
            ),
            perApp = base.perApp.copy(nudgePackages = setOf("com.example.dip")),
            correction = base.correction.copy(suggestionsEnabled = false),
        )
        val (_, entries) = requireNotNull(BackupCodec.decodePrefsFile(BackupCodec.encodePrefsFile("physiboard_settings", custom)))
        val outcome = BackupRestore.restore(Settings(), BackupFile(meta = meta, entries = entries))
        assertEquals(0, outcome.skippedCount)
        assertEquals(custom, outcome.settings)
    }

    @Test
    fun `the accessibility service switches leave in a backup and come back on restore`() {
        val base = Settings()
        val custom = base.copy(keys = base.keys.copy(accessibilityFocusField = false, accessibilityFnShortcuts = false))
        val (_, entries) = requireNotNull(BackupCodec.decodePrefsFile(BackupCodec.encodePrefsFile("physiboard_settings", custom)))
        val outcome = BackupRestore.restore(Settings(), BackupFile(meta = meta, entries = entries))
        assertEquals(0, outcome.skippedCount)
        assertEquals(custom, outcome.settings)
    }

    @Test
    fun `an unparsable prefs file fails to decode`() {
        assertNull(BackupCodec.decodePrefsFile("not json at all"))
        assertNull(BackupCodec.decodePrefsFile("""{"entries": {}}"""))
    }

    @Test
    fun `restore applies a known key and skips one the codec never writes`() {
        val backup = BackupFile(
            meta = meta,
            entries = mapOf(
                SettingsKeys.AUTO_CAP_FIRST to "false",
                "some_future_row_this_build_does_not_know" to "x",
            ),
        )
        val outcome = BackupRestore.restore(Settings(), backup)
        assertEquals(1, outcome.appliedCount)
        assertEquals(1, outcome.skippedCount)
        assertEquals(false, outcome.settings.typing.capitalizeAtTextStart)
    }

    @Test
    fun `restore accepts the dynamic auto_correct_custom_ prefix`() {
        val backup = BackupFile(meta = meta, entries = mapOf((SettingsKeys.AUTO_CORRECT_CUSTOM_PREFIX + "en") to """{"teh":"the"}"""))
        val outcome = BackupRestore.restore(Settings(), backup)
        assertEquals(1, outcome.appliedCount)
        assertTrue(outcome.settings.correction.customSubstitutions.containsKey("en"))
    }

    @Test
    fun `restore never carries a pairing key, because the archive never has one`() {
        // BackupCodec only ever writes SettingsCodec's flat map; "embedded_adb" is not a settings
        // key, so it cannot appear in `entries` even if a caller tried to smuggle it in.
        val backup = BackupFile(meta = meta, entries = mapOf("embedded_adb" to "secret"))
        val outcome = BackupRestore.restore(Settings(), backup)
        assertEquals(0, outcome.appliedCount)
        assertEquals(1, outcome.skippedCount)
    }

    @Test
    fun `zip-slip guard accepts an ordinary relative path`() {
        assertTrue(ZipEntryPaths.isSafeRelativePath("ctrl_key_mappings.json"))
        assertTrue(ZipEntryPaths.isSafeRelativePath("keyboard_layouts/custom-1.json"))
    }

    @Test
    fun `zip-slip guard rejects anything that climbs above its own root`() {
        assertFalse(ZipEntryPaths.isSafeRelativePath("../ctrl_key_mappings.json"))
        assertFalse(ZipEntryPaths.isSafeRelativePath("keyboard_layouts/../../etc/passwd"))
        assertFalse(ZipEntryPaths.isSafeRelativePath("/etc/passwd"))
        assertFalse(ZipEntryPaths.isSafeRelativePath("C:/Windows/system.ini"))
        assertFalse(ZipEntryPaths.isSafeRelativePath(""))
    }

    @Test
    fun `zip-slip guard accepts a path that dips into a subdirectory and back out, since it never escapes root`() {
        assertTrue(ZipEntryPaths.isSafeRelativePath("keyboard_layouts/nested/../custom-1.json"))
    }

    @Test
    fun `a name that only escapes once the files prefix is stripped is caught at the root it is written from`() {
        // The whole entry name passes: measured from the archive's root, "files" and
        // "keyboard_layouts" pay for both of the "..", and depth never goes below zero.
        val entryName = "files/keyboard_layouts/../../shared_prefs/embedded_adb.xml"
        assertTrue(ZipEntryPaths.isSafeRelativePath(entryName))

        // Side files are written from filesDir, which is the "files/" segment, so that is the
        // root the path has to be safe against -- and against that root it escapes. Before
        // 2026-09-29 only the first check ran and this resolved to the app's shared preferences.
        assertFalse(ZipEntryPaths.isSafeRelativePath(entryName.removePrefix("files/")))
    }

    @Test
    fun `an ordinary custom layout still passes once the files prefix is stripped`() {
        val entryName = "files/keyboard_layouts/custom-1.json"
        assertTrue(ZipEntryPaths.isSafeRelativePath(entryName))
        assertTrue(ZipEntryPaths.isSafeRelativePath(entryName.removePrefix("files/")))
    }

    @Test
    fun `a backslash separated climb is normalised before depth is counted`() {
        assertFalse(ZipEntryPaths.isSafeRelativePath("keyboard_layouts\\..\\..\\shared_prefs\\x"))
    }

    @Test
    fun `a tampered backup cannot write outside filesDir by climbing out of an allowed directory`() {
        val allowed = listOf("personal_dictionary.json", "ctrl_key_mappings.json", "user_defaults.json", "keyboard_layouts")
        // The escape: passes the whole-name zip-slip check, begins with an allowed directory, and
        // resolves to the app's shared preferences. It must not be restorable.
        assertFalse(ZipEntryPaths.isRestorableSideFilePath("keyboard_layouts/../../shared_prefs/embedded_adb.xml", allowed))
        assertFalse(ZipEntryPaths.isRestorableSideFilePath("keyboard_layouts/../../databases/x", allowed))
        assertFalse(ZipEntryPaths.isRestorableSideFilePath("../user_defaults.json", allowed))
    }

    @Test
    fun `the files a backup really carries are still restorable`() {
        val allowed = listOf("personal_dictionary.json", "ctrl_key_mappings.json", "user_defaults.json", "keyboard_layouts")
        assertTrue(ZipEntryPaths.isRestorableSideFilePath("user_defaults.json", allowed))
        assertTrue(ZipEntryPaths.isRestorableSideFilePath("keyboard_layouts/custom-1.json", allowed))
        assertTrue(ZipEntryPaths.isRestorableSideFilePath("keyboard_layouts/nested/../custom-1.json", allowed))
    }

    @Test
    fun `a path outside the allowed names is still refused`() {
        val allowed = listOf("user_defaults.json", "keyboard_layouts")
        assertFalse(ZipEntryPaths.isRestorableSideFilePath("shared_prefs/embedded_adb.xml", allowed))
        assertFalse(ZipEntryPaths.isRestorableSideFilePath("keyboard_layouts_evil/x", allowed))
    }

    @Test
    fun `a restore keeps the launcher keys a backup actually carries`() {
        // These three are written by the codec only when non-blank, so they are absent from a
        // default Settings map. Before 2026-09-29 that made them "keys the codec never writes"
        // and every restore dropped them.
        for (key in listOf("launcher_shortcuts", "quick_launcher_command_customizations", "command_surface_sources")) {
            val backup = BackupFile(BackupMeta(versionCode = 1, versionName = "x", timestampIso = "t"), mapOf(key to "{}"))
            val outcome = BackupRestore.restore(Settings(), backup)
            assertEquals(1, outcome.appliedCount, "$key should be applied, not skipped")
            assertEquals(0, outcome.skippedCount, "$key should not be skipped")
        }
    }

    @Test
    fun `fix_word_mixups goes out in a backup and comes back through a restore`() {
        val on = Settings().let { it.copy(correction = it.correction.copy(fixWordMixups = true)) }
        val (_, entries) = BackupCodec.decodePrefsFile(BackupCodec.encodePrefsFile("physiboard_settings", on))!!
        assertEquals("true", entries["fix_word_mixups"])
        val backup = BackupFile(BackupMeta(versionCode = 1, versionName = "x", timestampIso = "t"), entries)
        val outcome = BackupRestore.restore(Settings(), backup)
        assertEquals(0, outcome.skippedCount)
        assertTrue(outcome.settings.correction.fixWordMixups)
    }

    @Test
    fun `private mode and clean links go out in a backup and come back through a restore`() {
        val changed = Settings().let { it.copy(privacy = it.privacy.copy(privateMode = true, cleanLinks = false)) }
        val (_, entries) = BackupCodec.decodePrefsFile(BackupCodec.encodePrefsFile("physiboard_settings", changed))!!
        assertEquals("true", entries["private_mode"])
        assertEquals("false", entries["clean_links"])
        val outcome = BackupRestore.restore(Settings(), BackupFile(BackupMeta(versionCode = 1, versionName = "x", timestampIso = "t"), entries))
        assertEquals(0, outcome.skippedCount)
        assertTrue(outcome.settings.privacy.privateMode)
        assertFalse(outcome.settings.privacy.cleanLinks)
    }

    @Test
    fun `emoji_default_skin_tone goes out in a backup and comes back through a restore`() {
        val dark = Settings().let { it.copy(symPages = it.symPages.copy(defaultSkinTone = brobata.physiboard.core.actions.emoji.SkinTone.DARK)) }
        val (_, entries) = BackupCodec.decodePrefsFile(BackupCodec.encodePrefsFile("physiboard_settings", dark))!!
        assertEquals("dark", entries["emoji_default_skin_tone"])
        val backup = BackupFile(BackupMeta(versionCode = 1, versionName = "x", timestampIso = "t"), entries)
        val outcome = BackupRestore.restore(Settings(), backup)
        assertEquals(0, outcome.skippedCount)
        assertEquals(brobata.physiboard.core.actions.emoji.SkinTone.DARK, outcome.settings.symPages.defaultSkinTone)
    }

    @Test
    fun `the GIF page switch, kaomoji and sym_double_tap_chooser go out in a backup and come back through a restore`() {
        val changed = Settings().let {
            it.copy(symPages = it.symPages.copy(doubleTapChooser = false, kaomojiEnabled = true, pages = it.symPages.pages.copy(gifEnabled = false)))
        }
        val (_, entries) = BackupCodec.decodePrefsFile(BackupCodec.encodePrefsFile("physiboard_settings", changed))!!
        assertEquals("false", entries["sym_double_tap_chooser"])
        val backup = BackupFile(BackupMeta(versionCode = 1, versionName = "x", timestampIso = "t"), entries)
        val outcome = BackupRestore.restore(Settings(), backup)
        assertEquals(0, outcome.skippedCount)
        assertFalse(outcome.settings.symPages.doubleTapChooser)
        assertEquals("true", entries["emoji_picker_kaomoji"])
        assertTrue(outcome.settings.symPages.kaomojiEnabled)
        assertFalse(outcome.settings.symPages.pages.gifEnabled)
    }

    @Test
    fun `the Fill page switch, otp_from_notifications and fill_inline_suggestions go out in a backup and come back through a restore`() {
        val changed = Settings().let {
            it.copy(symPages = it.symPages.copy(otpFromNotifications = false, inlineSuggestions = true, pages = it.symPages.pages.copy(fillEnabled = false)))
        }
        val (_, entries) = BackupCodec.decodePrefsFile(BackupCodec.encodePrefsFile("physiboard_settings", changed))!!
        assertEquals("false", entries["otp_from_notifications"])
        val backup = BackupFile(BackupMeta(versionCode = 1, versionName = "x", timestampIso = "t"), entries)
        val outcome = BackupRestore.restore(Settings(), backup)
        assertEquals(0, outcome.skippedCount)
        assertFalse(outcome.settings.symPages.otpFromNotifications)
        assertTrue(outcome.settings.symPages.inlineSuggestions)
        assertFalse(outcome.settings.symPages.pages.fillEnabled)
    }

    @Test
    fun `the long-press accents and the user's own Sym pages go out in a backup and come back through a restore`() {
        val changed = Settings().let {
            it.copy(
                keys = it.keys.copy(variationChooser = false, customVariations = mapOf("a" to listOf("ą", "à"))),
                symPages = it.symPages.copy(
                    pages = it.symPages.pages.copy(custom1Enabled = true),
                    customPages = listOf(brobata.physiboard.core.settings.CustomSymPage("Mine", mapOf("KEYCODE_Q" to "ż")), brobata.physiboard.core.settings.CustomSymPage(), brobata.physiboard.core.settings.CustomSymPage()),
                ),
            )
        }
        val (_, entries) = BackupCodec.decodePrefsFile(BackupCodec.encodePrefsFile("physiboard_settings", changed))!!
        assertTrue(entries.containsKey("custom_variations"))
        assertTrue(entries.containsKey("sym_custom_pages"))
        val outcome = BackupRestore.restore(Settings(), BackupFile(BackupMeta(versionCode = 1, versionName = "x", timestampIso = "t"), entries))
        assertEquals(0, outcome.skippedCount)
        assertEquals(changed.keys, outcome.settings.keys)
        assertEquals(changed.symPages, outcome.settings.symPages)
    }

    @Test
    fun `the Alt+Backspace choice goes out in a backup and comes back through a restore`() {
        val line = Settings().let { it.copy(typing = it.typing.copy(altBackspace = AltBackspaceAction.DELETE_TO_LINE_START)) }
        val (_, entries) = BackupCodec.decodePrefsFile(BackupCodec.encodePrefsFile("physiboard_settings", line))!!
        assertEquals("line", entries[SettingsKeys.ALT_BACKSPACE_DELETE])
        val outcome = BackupRestore.restore(Settings(), BackupFile(meta, entries))
        assertEquals(0, outcome.skippedCount)
        assertEquals(AltBackspaceAction.DELETE_TO_LINE_START, outcome.settings.typing.altBackspace)
    }

    @Test
    fun `a backup made when the row was a switch restores forward delete as forward`() {
        val current = Settings().let { it.copy(typing = it.typing.copy(altBackspace = AltBackspaceAction.DELETE_TO_LINE_START)) }
        val on = BackupRestore.restore(current, BackupFile(meta, mapOf(SettingsKeys.ALT_BACKSPACE_DELETE to "true")))
        assertEquals(1, on.appliedCount)
        assertEquals(AltBackspaceAction.DELETE_FORWARD, on.settings.typing.altBackspace)
        val off = BackupRestore.restore(current, BackupFile(meta, mapOf(SettingsKeys.ALT_BACKSPACE_DELETE to "false")))
        assertEquals(AltBackspaceAction.DELETE_CHARACTER, off.settings.typing.altBackspace)
    }
}
