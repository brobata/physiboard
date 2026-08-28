package it.palsoftware.pastiera

import it.palsoftware.pastiera.SettingsManager.KeyboardThemeTarget
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HardwareBarHeightMigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences(it.palsoftware.pastiera.SettingsMigration.PREFS, 0)

    @Test
    fun freshInstallGetsTheTallerStrip() {
        prefs.edit().clear().apply()
        assertEquals(
            HARDWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT,
            SettingsManager.getKeyboardTheme(context, KeyboardThemeTarget.HARDWARE).suggestionsHeightScale
        )
    }

    @Test
    fun untouchedStockHeightIsLiftedOnce() {
        prefs.edit().clear()
            .putString(SettingsManager.KEY_KEYBOARD_THEME_HARDWARE, """{"suggestions_height_scale":1.0}""")
            .apply()
        assertEquals(
            HARDWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT,
            SettingsManager.getKeyboardTheme(context, KeyboardThemeTarget.HARDWARE).suggestionsHeightScale
        )
        // A later deliberate return to 1.0 sticks: the migration ran already.
        SettingsManager.setKeyboardTheme(
            context, KeyboardThemeTarget.HARDWARE,
            SettingsManager.getKeyboardTheme(context, KeyboardThemeTarget.HARDWARE).copy(suggestionsHeightScale = 1f)
        )
        assertEquals(1f, SettingsManager.getKeyboardTheme(context, KeyboardThemeTarget.HARDWARE).suggestionsHeightScale)
    }

    @Test
    fun followSystemSlotsAreLiftedToo() {
        prefs.edit().clear()
            .putString("keyboard_theme_dark_hardware", """{"suggestions_height_scale":1.0}""")
            .putString("keyboard_theme_light_hardware", """{"suggestions_height_scale":1.15}""")
            .apply()
        assertEquals(
            HARDWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT,
            SettingsManager.getKeyboardThemeSystemSlot(context, KeyboardThemeTarget.HARDWARE, dark = true).suggestionsHeightScale
        )
        assertEquals(
            1.15f,
            SettingsManager.getKeyboardThemeSystemSlot(context, KeyboardThemeTarget.HARDWARE, dark = false).suggestionsHeightScale
        )
        prefs.edit().clear().apply()
        assertEquals(
            HARDWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT,
            SettingsManager.getKeyboardThemeSystemSlot(context, KeyboardThemeTarget.HARDWARE, dark = false).suggestionsHeightScale
        )
    }

    @Test
    fun deliberateHeightIsKept() {
        prefs.edit().clear()
            .putString(SettingsManager.KEY_KEYBOARD_THEME_HARDWARE, """{"suggestions_height_scale":1.2}""")
            .apply()
        assertEquals(1.2f, SettingsManager.getKeyboardTheme(context, KeyboardThemeTarget.HARDWARE).suggestionsHeightScale)
    }
}
