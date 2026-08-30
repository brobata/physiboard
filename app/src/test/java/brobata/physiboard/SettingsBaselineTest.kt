package brobata.physiboard

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsBaselineTest {

    private lateinit var context: Context

    private fun prefs() = SettingsMigration.preferences(context)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs().edit().clear().commit()
        File(context.filesDir, SettingsBaseline.SNAPSHOT_FILE).delete()
    }

    @Test
    fun freshInstallIsSeededFromTheBaseline() {
        SettingsBaseline.applyIfNeeded(context)

        // A few values that the shipped asset carries; if the asset is regenerated these are the
        // ones a keyboard is unusable without.
        assertTrue(prefs().contains("auto_capitalize_first_letter"))
        assertTrue(prefs().contains("status_bar_height_dp"))
        assertEquals(56, prefs().getInt("status_bar_height_dp", -1))
    }

    @Test
    fun freshInstallDoesNotSkipOnboardingOrTheWhatsNewCard() {
        SettingsBaseline.applyIfNeeded(context)

        // Shipping the maintainer's bookkeeping would silently swallow both of these.
        assertFalse(prefs().contains("tutorial_completed"))
        assertFalse(prefs().contains("last_seen_whats_new_version"))
    }

    @Test
    fun theBaselineCarriesTheWholeWorkingConfiguration() {
        SettingsBaseline.applyIfNeeded(context)

        // Every one of these phones is the same hardware, so the fitted ring, the theme and the
        // SYM pages are all meant to travel.
        listOf(
            "keyboard_theme_hardware",
            "sym_pages_config",
            "notification_ring_cx",
            "notification_ring_radius",
            "launcher_shortcuts",
            "status_bar_apps",
        ).forEach { key ->
            assertTrue("$key belongs in the baseline", prefs().contains(key))
        }
    }

    @Test
    fun capturedPerDeviceStateIsStillHeldBack() {
        SettingsBaseline.applyIfNeeded(context)

        // Not preservation - correctness. These record what a key pointed at on ONE phone before
        // PhysiBoard changed it; shipping them would make Reset to stock restore the wrong app.
        listOf(
            "side_key_original_package",
            "side_key_original_activity",
            "side_key_original_captured",
        ).forEach { key ->
            assertFalse("$key must not ship in the baseline", prefs().contains(key))
        }
    }

    @Test
    fun upgradeReplacesTheWholeStoreIncludingWhatTheUserHadSet() {
        prefs().edit()
            // the 2.0 casualty: stored, and no screen left to change it
            .putInt("status_bar_height_dp", 36)
            .putBoolean("auto_capitalize_first_letter", false)
            // a hand-set theme and ring fit: these go too, deliberately
            .putString("keyboard_theme_hardware", "{\"background\":-1}")
            .putFloat("notification_ring_cx", 12.5f)
            // a key the baseline does not carry at all must not survive the clear
            .putString("some_stale_1x_key", "junk")
            .commit()

        SettingsBaseline.applyIfNeeded(context)

        assertEquals(56, prefs().getInt("status_bar_height_dp", -1))
        assertTrue(prefs().getBoolean("auto_capitalize_first_letter", false))
        assertFalse(prefs().contains("some_stale_1x_key"))

        // replaced by the baseline, not left at the user's value
        assertFalse("{\"background\":-1}" == prefs().getString("keyboard_theme_hardware", null))
        assertFalse(12.5f == prefs().getFloat("notification_ring_cx", 0f))
    }

    @Test
    fun upgradeKeepsBookkeepingSoOnboardingDoesNotReappear() {
        prefs().edit()
            .putBoolean("tutorial_completed", true)
            .putString("last_seen_whats_new_version", "2.0.0")
            .putBoolean("prefs_migrated_v2", true)
            .commit()

        SettingsBaseline.applyIfNeeded(context)

        assertTrue(prefs().getBoolean("tutorial_completed", false))
        assertEquals("2.0.0", prefs().getString("last_seen_whats_new_version", null))
        assertTrue(prefs().getBoolean("prefs_migrated_v2", false))
    }

    @Test
    fun theRecordOfWhatSystemSettingsWereBeforeSurvivesTheReset() {
        // These outlive an uninstall. If the reset threw them away, Reset to stock could never
        // put the side key or the backlight tile back.
        prefs().edit()
            .putString("side_key_original_package", "com.example.assistant")
            .putString("side_key_original_activity", "com.example.assistant.Main")
            .putBoolean("side_key_original_captured", true)
            .putBoolean("qs_backlight_prev_captured", true)
            .putBoolean("fn_ctrl_captured", true)
            .commit()

        SettingsBaseline.applyIfNeeded(context)

        assertEquals("com.example.assistant", prefs().getString("side_key_original_package", null))
        assertEquals(
            "com.example.assistant.Main",
            prefs().getString("side_key_original_activity", null)
        )
        assertTrue(prefs().getBoolean("side_key_original_captured", false))
        assertTrue(prefs().getBoolean("qs_backlight_prev_captured", false))
        assertTrue(prefs().getBoolean("fn_ctrl_captured", false))
    }

    @Test
    fun theLocalFnLayerCopyIsDroppedSoTheShippedDefaultApplies() {
        prefs().edit().putInt("status_bar_height_dp", 36).commit()
        val mappings = File(context.filesDir, "ctrl_key_mappings.json")
        mappings.writeText("{\"mappings\":{\"KEYCODE_Q\":{\"type\":\"none\"}}}")

        SettingsBaseline.applyIfNeeded(context)

        assertFalse("a stale Fn layer must not survive", mappings.exists())
    }

    @Test
    fun theFnLayerIsLeftAloneOnAFreshInstall() {
        val mappings = File(context.filesDir, "ctrl_key_mappings.json")
        mappings.writeText("{\"mappings\":{}}")

        SettingsBaseline.applyIfNeeded(context)

        assertTrue("nothing to clean up on a new install", mappings.exists())
    }

    @Test
    fun thePre2xStoreIsDeletedSoItsValuesCannotComeBack() {
        prefs().edit().putInt("status_bar_height_dp", 36).commit()
        context.getSharedPreferences(SettingsMigration.LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("some_1x_setting", true).commit()
        assertTrue(SettingsMigration.hasLegacySettings(context))

        SettingsBaseline.applyIfNeeded(context)

        assertFalse(SettingsMigration.hasLegacySettings(context))
        // and with it the migration notice and its "Restore my old settings" button
        assertFalse(SettingsMigration.shouldShowMigrationNotice(context))
    }

    @Test
    fun theStoreIsSnapshotBeforeItIsCleared() {
        prefs().edit().putInt("status_bar_height_dp", 36).commit()

        SettingsBaseline.applyIfNeeded(context)

        val snapshot = File(context.filesDir, SettingsBaseline.SNAPSHOT_FILE)
        assertTrue("a reset must leave a way back", snapshot.exists())
        assertTrue(snapshot.readText().contains("status_bar_height_dp"))
    }

    @Test
    fun aFreshInstallIsNotSnapshotted() {
        SettingsBaseline.applyIfNeeded(context)

        assertFalse(File(context.filesDir, SettingsBaseline.SNAPSHOT_FILE).exists())
    }

    @Test
    fun theResetRunsOnceAndNotAgainOnEveryLaunch() {
        SettingsBaseline.applyIfNeeded(context)

        // What the user changes after the reset has to survive the next launch.
        prefs().edit().putInt("status_bar_height_dp", 64).commit()
        SettingsBaseline.applyIfNeeded(context)
        SettingsBaseline.applyIfNeeded(context)

        assertEquals(64, prefs().getInt("status_bar_height_dp", -1))
    }
}
