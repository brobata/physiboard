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
}
