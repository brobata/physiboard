package brobata.physiboard.core.shell

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SettingsCodec
import brobata.physiboard.core.settings.SettingsKeys
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
}
