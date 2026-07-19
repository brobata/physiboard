package it.palsoftware.pastiera

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
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
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

    @Test
    fun clicksPowerKeyboardCanBeSelected() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_power")

        assertEquals("clicks_power", SettingsManager.getPhysicalKeyboardProfileOverride(context))
    }

    @Test
    fun clicksBuiltInProfilesCanBeSelected() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_razr")
        assertEquals("clicks_razr", SettingsManager.getPhysicalKeyboardProfileOverride(context))

        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_pixel")
        assertEquals("clicks_pixel", SettingsManager.getPhysicalKeyboardProfileOverride(context))
    }
}
