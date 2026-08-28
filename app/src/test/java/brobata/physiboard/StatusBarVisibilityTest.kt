package brobata.physiboard

import brobata.physiboard.SettingsManager.StatusBarVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StatusBarVisibilityTest {
    private val context = RuntimeEnvironment.getApplication()
    private val apps = setOf("com.google.android.apps.messaging")

    @Test
    fun alwaysAndNeverIgnoreTheApp() {
        assertTrue(SettingsManager.isStatusBarShownFor(StatusBarVisibility.ALWAYS, emptySet(), "any"))
        assertTrue(SettingsManager.isStatusBarShownFor(StatusBarVisibility.ALWAYS, emptySet(), null))
        assertFalse(SettingsManager.isStatusBarShownFor(StatusBarVisibility.NEVER, apps, apps.first()))
    }

    @Test
    fun appsModeShowsOnlyInListedApps() {
        assertTrue(SettingsManager.isStatusBarShownFor(StatusBarVisibility.APPS, apps, apps.first()))
        assertFalse(SettingsManager.isStatusBarShownFor(StatusBarVisibility.APPS, apps, "com.android.launcher3"))
        assertFalse(SettingsManager.isStatusBarShownFor(StatusBarVisibility.APPS, apps, null))
    }

    @Test
    fun appListStartsWithMessagingApps() {
        context.getSharedPreferences(brobata.physiboard.SettingsMigration.PREFS, 0).edit().clear().apply()
        val seeded = SettingsManager.getStatusBarAppPackages(context)
        assertTrue("com.whatsapp" in seeded)
        assertTrue("com.google.android.gm" in seeded)
        SettingsManager.setStatusBarApp(context, "com.whatsapp", false)
        assertFalse("com.whatsapp" in SettingsManager.getStatusBarAppPackages(context))
    }

    @Test
    fun legacyBooleanMapsToAlwaysOrNever() {
        val prefs = context.getSharedPreferences(brobata.physiboard.SettingsMigration.PREFS, 0)
        prefs.edit().clear().putBoolean(SettingsManager.KEY_SHOW_STATUS_BAR, true).apply()
        assertEquals(StatusBarVisibility.ALWAYS, SettingsManager.getStatusBarVisibility(context))
        prefs.edit().clear().putBoolean(SettingsManager.KEY_SHOW_STATUS_BAR, false).apply()
        assertEquals(StatusBarVisibility.NEVER, SettingsManager.getStatusBarVisibility(context))
    }

    @Test
    fun neverChosenShowsTheStrip() {
        // The strip carries the suggestions. Silence about it is not a request to hide it.
        context.getSharedPreferences(brobata.physiboard.SettingsMigration.PREFS, 0).edit().clear().apply()
        assertEquals(StatusBarVisibility.ALWAYS, SettingsManager.getStatusBarVisibility(context))
        assertTrue(SettingsManager.getShowStatusBar(context))
    }

    @Test
    fun modeRoundTripsAndKeepsLegacyFlagInStep() {
        SettingsManager.setStatusBarVisibility(context, StatusBarVisibility.APPS)
        assertEquals(StatusBarVisibility.APPS, SettingsManager.getStatusBarVisibility(context))
        assertTrue(SettingsManager.getShowStatusBar(context))
        SettingsManager.setStatusBarApp(context, "a", true)
        assertTrue(SettingsManager.isStatusBarShownFor(context, "a"))
        SettingsManager.setStatusBarApp(context, "a", false)
        assertFalse(SettingsManager.isStatusBarShownFor(context, "a"))
        SettingsManager.setShowStatusBar(context, false)
        assertEquals(StatusBarVisibility.NEVER, SettingsManager.getStatusBarVisibility(context))
    }
}
