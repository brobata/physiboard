package brobata.physiboard

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsMigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val legacy
        get() = context.getSharedPreferences(SettingsMigration.LEGACY_PREFS, Context.MODE_PRIVATE)
    private val current
        get() = context.getSharedPreferences(SettingsMigration.PREFS, Context.MODE_PRIVATE)

    @Before
    fun clearBothFiles() {
        legacy.edit().clear().commit()
        current.edit().clear().commit()
    }

    @Test
    fun contentTheUserAuthoredSurvives() {
        legacy.edit()
            .putString("launcher_shortcuts", """{"62":{"type":"command"}}""")
            .putString("sym_mappings_custom", """{"a":"@"}""")
            .putString("keyboard_theme_hardware", """{"background":-15658735}""")
            .putFloat("notification_ring_radius", 45.9375f)
            .commit()

        SettingsMigration.migrateIfNeeded(context)

        assertEquals("""{"62":{"type":"command"}}""", current.getString("launcher_shortcuts", null))
        assertEquals("""{"a":"@"}""", current.getString("sym_mappings_custom", null))
        assertEquals("""{"background":-15658735}""", current.getString("keyboard_theme_hardware", null))
        assertEquals(45.9375f, current.getFloat("notification_ring_radius", 0f), 0.0001f)
    }

    @Test
    fun stringSetsSurvive() {
        // A naive when-on-value drops these silently, and both are real settings.
        legacy.edit()
            .putStringSet("status_bar_apps", setOf("com.whatsapp", "com.Slack"))
            .putStringSet("additional_ime_subtypes", setOf("en_US:qwerty"))
            .commit()

        SettingsMigration.migrateIfNeeded(context)

        assertEquals(setOf("com.whatsapp", "com.Slack"), current.getStringSet("status_bar_apps", emptySet()))
        assertEquals(setOf("en_US:qwerty"), current.getStringSet("additional_ime_subtypes", emptySet()))
    }

    @Test
    fun theStatusBarButtonsKeepTheirLayoutUnderTheNewKeyName() {
        legacy.edit()
            .putString("pastierina_status_bar_slots_left", """["clipboard"]""")
            .putString("pastierina_status_bar_slots_right", """["microphone"]""")
            .commit()

        SettingsMigration.migrateIfNeeded(context)

        assertEquals("""["clipboard"]""", current.getString("status_bar_slots_left", null))
        assertEquals("""["microphone"]""", current.getString("status_bar_slots_right", null))
        assertNull(current.getString("pastierina_status_bar_slots_left", null))
    }

    @Test
    fun behaviouralTogglesAreNotCarriedOver() {
        // These fall through to the first-run defaults instead of accumulating.
        legacy.edit()
            .putBoolean("show_status_bar", false)
            .putBoolean("auto_capitalize_first_letter", false)
            .putInt("status_bar_height_dp", 36)
            .commit()

        SettingsMigration.migrateIfNeeded(context)

        assertFalse(current.contains("show_status_bar"))
        assertFalse(current.contains("auto_capitalize_first_letter"))
        assertFalse(current.contains("status_bar_height_dp"))
    }

    @Test
    fun settingsForRemovedFeaturesAreDropped() {
        legacy.edit()
            .putBoolean("status_bar_variations_visible", true)
            .putString("static_variation_bar_preset", "dev_choice")
            .putInt("dynamic_variation_bar_slot_count", 9)
            .putString("clicks_button_mode", "alt")
            .putString("pastierina_mode_override", "pastierina")
            .commit()

        SettingsMigration.migrateIfNeeded(context)

        listOf(
            "status_bar_variations_visible", "static_variation_bar_preset",
            "dynamic_variation_bar_slot_count", "clicks_button_mode", "pastierina_mode_override"
        ).forEach { assertFalse(it, current.contains(it)) }
    }

    @Test
    fun seedOnceGuardsSurviveSoOneTimeSetupCannotRunOverCarriedContent() {
        // Each of these holds back a one-time write. Reset them and that write happens again,
        // on top of the very content the migration just preserved.
        legacy.edit()
            .putBoolean("tutorial_completed", true)
            .putBoolean("quick_launcher_default_assigned", true)
            .putBoolean("alt_shift_default_initialized", true)
            .putInt("nav_mode_default_mappings_version", 3)
            .putBoolean("side_key_original_captured", true)
            .putString("side_key_original_package", "com.google.android.apps.bard")
            .commit()

        SettingsMigration.migrateIfNeeded(context)

        assertTrue(current.getBoolean("tutorial_completed", false))
        assertTrue(current.getBoolean("quick_launcher_default_assigned", false))
        assertTrue(current.getBoolean("alt_shift_default_initialized", false))
        assertEquals(3, current.getInt("nav_mode_default_mappings_version", 0))
        assertTrue(current.getBoolean("side_key_original_captured", false))
        assertEquals("com.google.android.apps.bard", current.getString("side_key_original_package", null))
    }

    @Test
    fun theFirstRunDefaultsStampAfterMigrationRatherThanBeingInherited() {
        // Inheriting impact_defaults_applied would leave a 1.x user on 1.x defaults forever.
        legacy.edit().putBoolean("impact_defaults_applied", true).commit()

        SettingsMigration.migrateIfNeeded(context)

        assertFalse(current.getBoolean("impact_defaults_applied", false))
    }

    @Test
    fun migrationRunsOnceAndDoesNotClobberLaterChoices() {
        legacy.edit().putString("launcher_shortcuts", "original").commit()
        SettingsMigration.migrateIfNeeded(context)
        current.edit().putString("launcher_shortcuts", "changed by the user").commit()

        SettingsMigration.migrateIfNeeded(context)

        assertEquals("changed by the user", current.getString("launcher_shortcuts", null))
    }

    @Test
    fun theOldFileIsLeftIntactAsTheUndo() {
        legacy.edit().putString("launcher_shortcuts", "mine").putBoolean("show_status_bar", false).commit()

        SettingsMigration.migrateIfNeeded(context)

        assertTrue(SettingsMigration.hasLegacySettings(context))
        assertEquals("mine", legacy.getString("launcher_shortcuts", null))
    }

    @Test
    fun restoringReplaysBehaviourTooButStillDropsRetiredSettings() {
        legacy.edit()
            .putBoolean("show_status_bar", false)
            .putInt("status_bar_height_dp", 36)
            .putBoolean("status_bar_variations_visible", true)
            .putString("pastierina_status_bar_slots_left", """["clipboard"]""")
            .commit()
        SettingsMigration.migrateIfNeeded(context)

        SettingsMigration.restoreLegacySettings(context)

        assertFalse(current.getBoolean("show_status_bar", true))
        assertEquals(36, current.getInt("status_bar_height_dp", 0))
        assertFalse(current.contains("status_bar_variations_visible"))
        assertEquals("""["clipboard"]""", current.getString("status_bar_slots_left", null))
    }

    @Test
    fun aFreshInstallMigratesNothingAndSaysSo() {
        SettingsMigration.migrateIfNeeded(context)

        assertFalse(SettingsMigration.hasLegacySettings(context))
        assertTrue(current.all.keys.none { it != "prefs_migrated_v2" })
    }
}
