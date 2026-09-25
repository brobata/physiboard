package brobata.physiboard.core.shell

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SettingsCodec
import brobata.physiboard.core.settings.SettingsKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: settings-catalog.md SS7, adapted for 3.0's single settings store per app-shell.md SS30. */
class BackupCodecTest {

    private val meta = BackupMeta(versionCode = 30000, versionName = "3.0.0-dev", timestampIso = "2026-09-24T00:00:00Z")

    @Test
    fun `encode then decode round-trips the whole flat map`() {
        val settings = Settings()
        val text = BackupCodec.encode(settings, meta)
        val decoded = BackupCodec.decode(text)
        requireNotNull(decoded)
        assertEquals(meta, decoded.meta)
        assertEquals(SettingsCodec.toMap(settings), decoded.entries)
    }

    @Test
    fun `a missing backup_meta fails to decode, modeling not-a-PhysiBoard-backup`() {
        assertNull(BackupCodec.decode("""{"entries": {}}"""))
        assertNull(BackupCodec.decode("not json at all"))
        assertNull(BackupCodec.decode("""{"backup_meta": {"versionCode": 1}}"""))
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
}
