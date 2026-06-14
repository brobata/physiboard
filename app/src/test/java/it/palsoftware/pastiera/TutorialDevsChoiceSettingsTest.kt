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
class TutorialDevsChoiceSettingsTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun applyDevsChoiceSettings_setsVariationsLongPressAt200Ms() {
        val context = RuntimeEnvironment.getApplication()

        applyDevsChoiceSettings(context)

        assertEquals("variations", SettingsManager.getLongPressModifier(context))
        assertEquals(200L, SettingsManager.getLongPressThreshold(context))
    }
}
