package it.palsoftware.pastiera.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreManagerAndBackupContractTest {

    @Test
    fun userDictionaryEntries_isRecognizedForFreshInstallRestore() {
        val recognized = PreferenceSchemas.isRecognized(
            prefName = "pastiera_prefs",
            key = "user_dictionary_entries",
            currentKeys = emptySet()
        )

        assertTrue(recognized)
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "user_dictionary_entries")
        )
    }

    @Test
    fun layoutSwitchPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("pastiera_prefs", "alt_shift_layout_switch")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("pastiera_prefs", "toast_on_layout_switch")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "software_keyboard_mode")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "input_style_suggestion_locales")
        )
    }

    @Test
    fun quickLauncherPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "quick_launcher_behavior")
        )
    }

    @Test
    fun statusBarAndVariationPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "static_variation_bar_preset")
        )
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("pastiera_prefs", "status_bar_variations_visible")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "status_bar_slot_left")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "status_bar_slot_right_1")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "status_bar_slot_right_2")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "status_bar_slots_left")
        )
        assertEquals(
            PreferenceValueType.STRING,
            PreferenceSchemas.expectedType("pastiera_prefs", "status_bar_slots_right")
        )
    }

    @Test
    fun symPreferences_areRecognizedForRestore() {
        assertEquals(
            PreferenceValueType.BOOLEAN,
            PreferenceSchemas.expectedType("pastiera_prefs", "emoji_picker_expanded_height")
        )
    }

    @Test
    fun shouldNotifyUserDictionaryRefresh_whenUserDictionaryPrefsRestored() {
        val prefs = PreferencesRestoreSummary(
            appliedKeys = listOf("pastiera_prefs:user_dictionary_entries"),
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
            appliedKeys = listOf("pastiera_prefs:user_dictionary_entries"),
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
