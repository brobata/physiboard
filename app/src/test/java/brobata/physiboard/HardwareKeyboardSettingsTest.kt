package brobata.physiboard

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HardwareKeyboardSettingsTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(brobata.physiboard.SettingsMigration.PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun hardwareSettingsPersistIndependentlyFromLanguageLayouts() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setPhysicalKeyboardProfileOverride(context, "titan2")
        SettingsManager.setPhysicalKeyboardCurrencySymbol(context, "£")
        SettingsManager.setTitan2LayoutEnabled(context, true)

        assertEquals("titan2", SettingsManager.getPhysicalKeyboardProfileOverride(context))
        assertEquals("£", SettingsManager.getPhysicalKeyboardCurrencySymbol(context))
        assertEquals(true, SettingsManager.isTitan2LayoutEnabled(context))
    }






}
