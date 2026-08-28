package brobata.physiboard.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreManagerAndBackupContractTest {

    @Test
    fun userDictionaryEntries_isRecognizedForFreshInstallRestore() {
        val recognized = PreferenceSchemas.isRecognized(
            prefName = brobata.physiboard.SettingsMigration.PREFS,
            key = "user_dictionary_entries",
            currentKeys = emptySet()
        )

        assertTrue(recognized)
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "user_dictionary_entries")
        )
    }

    @Test
    fun snippetExpansionPreferences_areRecognizedForFreshInstallRestore() {
        val expected = mapOf(
            "snippets_enabled" to PreferenceValueType.BOOLEAN,
            "snippets_prefix" to PreferenceValueType.STRING,
            "snippets_v1" to PreferenceValueType.STRING,
            "snippets_presentation" to PreferenceValueType.STRING,
            "snippets_exact_on_space" to PreferenceValueType.BOOLEAN,
            "snippets_accept_prefix_with_space" to PreferenceValueType.BOOLEAN,
            "snippets_accept_with_tab" to PreferenceValueType.BOOLEAN,
            "snippets_accept_with_enter" to PreferenceValueType.BOOLEAN
        )
        expected.forEach { (key, type) ->
            assertTrue(PreferenceSchemas.isRecognized(brobata.physiboard.SettingsMigration.PREFS, key, emptySet()))
            assertEquals(type, PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, key))
        }
    }

    @Test
    fun emojiAndSymbolExpansionPreferences_areNoLongerRecognized() {
        // Emoji and symbol shortcodes were removed in 2.0, so their preferences left the schema.
        // A backup written by 1.x still carries them: restore has to treat them as unrecognized
        // and skip them rather than fail, which is what dropping them from the schema achieves.
        val retired = listOf(
            "emoji_shortcodes_enabled",
            "symbol_shortcodes_enabled",
            "emoji_symbols_presentation",
            "emoji_symbols_exact_on_space",
            "emoji_symbols_accept_prefix_with_space",
            "emoji_symbols_accept_with_tab",
            "emoji_symbols_accept_with_enter",
            "emoji_symbols_exact_on_close"
        )
        retired.forEach { key ->
            assertFalse(PreferenceSchemas.isRecognized(brobata.physiboard.SettingsMigration.PREFS, key, emptySet()))
            assertNull(PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, key))
        }
    }

    @Test
    fun layoutSwitchPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "alt_shift_layout_switch")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "alt_enter_layout_switch")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "toast_on_layout_switch")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "software_keyboard_mode_toggle_toasts")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "software_keyboard_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "input_style_suggestion_locales")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "hidden_system_input_styles")
        )
    }

    @Test
    fun quickLauncherPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "quick_launcher_behavior")
        )
    }

    @Test
    fun accidentalKeyProtectionPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "overlapping_keys_enabled")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_overlapping_keys_enabled")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_overlapping_keys_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_number_row_input_mode")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_number_row_repeat_enabled")
        )
    }

    @Test
    fun clicksButtonModes_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_button_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_meta_button_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_alt_button_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_microphone_button_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_red_button_binding_choice")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_red_button_binding_output")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_keyboard_button_binding_choice")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_keyboard_button_binding_output")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_microphone_button_binding_choice")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "clicks_microphone_button_binding_output")
        )
    }

    @Test
    fun clicksPowerStateAndSocCalibrationAreRecognizedOnFreshInstall() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(
                brobata.physiboard.SettingsMigration.PREFS,
                "clicks_power_keyboard_snapshots_v1"
            )
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(
                brobata.physiboard.SettingsMigration.PREFS,
                "clicks_power_soc_calibration_PK-42"
            )
        )
        assertTrue(
            PreferenceSchemas.isRecognized(
                prefName = brobata.physiboard.SettingsMigration.PREFS,
                key = "clicks_power_keyboard_snapshots_v1",
                currentKeys = emptySet()
            )
        )
        assertTrue(
            PreferenceSchemas.isRecognized(
                prefName = brobata.physiboard.SettingsMigration.PREFS,
                key = "clicks_power_soc_calibration_PK-42",
                currentKeys = emptySet()
            )
        )
    }

    @Test
    fun punctuationSpacingPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "auto_space_punctuation")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "space_after_punctuation")
        )
    }

    @Test
    fun deleteMethodsPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "shift_backspace_delete")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "alt_backspace_delete")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "backspace_at_start_delete")
        )
    }

    @Test
    fun statusBarAndVariationPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "titan2_elite_rounded_corner_insets")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "static_variation_bar_preset")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "status_bar_variations_visible")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "status_bar_slot_left")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "status_bar_slot_right_1")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "status_bar_slot_right_2")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "status_bar_slots_left")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "status_bar_slots_right")
        )
    }

    @Test
    fun symPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType(brobata.physiboard.SettingsMigration.PREFS, "emoji_picker_expanded_height")
        )
    }

    @Test
    fun shouldNotifyUserDictionaryRefresh_whenUserDictionaryPrefsRestored() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = listOf("${brobata.physiboard.SettingsMigration.PREFS}:user_dictionary_entries"),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = emptyList(),
            skippedFiles = emptyList()
        )

        assertTrue(RestoreManager.shouldNotifyUserDictionaryRefresh(prefs, files))
    }

    @Test
    fun shouldNotifyUserDictionaryRefresh_whenUserDefaultsFileRestored() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = emptyList(),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = listOf("user_defaults.json"),
            skippedFiles = emptyList()
        )

        assertTrue(RestoreManager.shouldNotifyUserDictionaryRefresh(prefs, files))
    }

    @Test
    fun collectTriggeredPostRestoreActions_detectsUserDictionaryFromNestedFilePath() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = emptyList(),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = listOf("files/user_defaults.json"),
            skippedFiles = emptyList()
        )

        val actions = RestoreManager.collectTriggeredPostRestoreActions(prefs, files)

        assertTrue(actions.contains(RestoreManager.PostRestoreAction.REFRESH_USER_DICTIONARY))
        assertEquals(1, actions.size)
    }

    @Test
    fun collectTriggeredPostRestoreActions_deduplicatesWhenPrefAndFileBothMatch() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = listOf("${brobata.physiboard.SettingsMigration.PREFS}:user_dictionary_entries"),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = listOf("user_defaults.json"),
            skippedFiles = emptyList()
        )

        val actions = RestoreManager.collectTriggeredPostRestoreActions(prefs, files)

        assertEquals(setOf(RestoreManager.PostRestoreAction.REFRESH_USER_DICTIONARY), actions)
    }

    @Test
    fun shouldNotifyUserDictionaryRefresh_falseForUnrelatedRestore() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = listOf("pastiera_prefs:keyboard_layout"),
            skippedKeys = emptyList()
        )
        val files = FileRestoreSummary(
            restoredFiles = listOf("variations.json"),
            skippedFiles = emptyList()
        )

        assertFalse(RestoreManager.shouldNotifyUserDictionaryRefresh(prefs, files))
    }
}
