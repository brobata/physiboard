package it.palsoftware.pastiera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StatusBarVariationsVisibilityTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("pastiera_prefs", 0).edit().clear().apply()
    }

    @Test
    fun variationRowIsOffUntilAskedFor() {
        assertFalse(SettingsManager.areStatusBarVariationsEnabled(context))
    }

    @Test
    fun toggleRoundTrips() {
        SettingsManager.setStatusBarVariationsEnabled(context, true)
        assertTrue(SettingsManager.areStatusBarVariationsEnabled(context))
        SettingsManager.setStatusBarVariationsEnabled(context, false)
        assertFalse(SettingsManager.areStatusBarVariationsEnabled(context))
    }

    @Test
    fun resettingTheStatusBarLeavesVariationsOff() {
        SettingsManager.setStatusBarVariationsEnabled(context, true)
        SettingsManager.resetStatusBarSlotsToDefault(context)
        assertFalse(SettingsManager.areStatusBarVariationsEnabled(context))
    }
}
