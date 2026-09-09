package brobata.physiboard.ring

import android.Manifest
import android.content.Context
import android.provider.Settings
import brobata.physiboard.SettingsManager
import brobata.physiboard.SettingsMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The switch this touches outlives the app, so what is tested here is mostly the putting back:
 * every ring has to leave the keyboard the way it found it, including the rings that never
 * appeared and the ones whose process died.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RingBacklightTest {

    private lateinit var context: Context

    private fun prefs() = SettingsMigration.preferences(context)

    private fun switchValue(): Int =
        Settings.Global.getInt(context.contentResolver, RingBacklight.SETTING, -1)

    private fun setSwitch(value: Int) {
        Settings.Global.putInt(context.contentResolver, RingBacklight.SETTING, value)
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs().edit().clear().commit()
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        SettingsManager.setNotificationRingEnabled(context, true)
        setSwitch(1)
        RingBacklight.restore(context)
        prefs().edit().clear().commit()
        SettingsManager.setNotificationRingEnabled(context, true)
        setSwitch(1)
    }

    @Test
    fun theKeyboardGoesDarkAndComesBack() {
        RingBacklight.suppress(context)
        assertEquals(0, switchValue())
        assertTrue(SettingsManager.isRingBacklightSuppressed(context))

        RingBacklight.restore(context)
        assertEquals(1, switchValue())
        assertFalse(SettingsManager.isRingBacklightSuppressed(context))
    }

    @Test
    fun theValueToPutBackIsRecordedBeforeTheSwitchIsTouched() {
        RingBacklight.suppress(context)
        // Everything a fresh process needs to undo this is in the store, not in memory.
        assertTrue(prefs().getBoolean("ring_backlight_prev_captured", false))
        assertEquals(1, prefs().getInt("ring_backlight_prev", -1))
    }

    @Test
    fun aSecondNotificationDoesNotOverwriteTheRecordedValue() {
        RingBacklight.suppress(context)
        RingBacklight.suppress(context)
        assertEquals(1, SettingsManager.getRingBacklightPrior(context))

        RingBacklight.restore(context)
        assertEquals(1, switchValue())
    }

    @Test
    fun aKeyboardTheUserAlreadyTurnedOffIsLeftAlone() {
        setSwitch(0)
        RingBacklight.suppress(context)
        // Nothing was ours to turn off, so nothing may later be turned back on.
        assertFalse(SettingsManager.isRingBacklightSuppressed(context))

        RingBacklight.restore(context)
        assertEquals(0, switchValue())
    }

    @Test
    fun theSwitchIsUntouchedWhenTheFeatureIsOff() {
        SettingsManager.setRingKeyboardDarkEnabled(context, false)
        RingBacklight.suppress(context)
        assertEquals(1, switchValue())
        assertFalse(SettingsManager.isRingBacklightSuppressed(context))
    }

    @Test
    fun theSwitchIsUntouchedWhenTheRingItselfIsOff() {
        SettingsManager.setNotificationRingEnabled(context, false)
        RingBacklight.suppress(context)
        assertEquals(1, switchValue())
    }

    @Test
    fun aSuppressionThatOutlivedItsRingIsHealedAtStartUp() {
        RingBacklight.suppress(context)
        assertEquals(0, switchValue())
        // Stands in for the process dying with the ring up: the record is all that is left.
        RingBacklight.restore(context)

        assertEquals(1, switchValue())
        assertFalse(SettingsManager.isRingBacklightSuppressed(context))
    }

    @Test
    fun restoringTwiceIsHarmless() {
        RingBacklight.suppress(context)
        RingBacklight.restore(context)
        setSwitch(0)
        // The second restore has no claim left, so the user's own choice stands.
        RingBacklight.restore(context)
        assertEquals(0, switchValue())
    }

    @Test
    fun nothingHappensWithoutThePermission() {
        shadowOf(RuntimeEnvironment.getApplication())
            .denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        RingBacklight.suppress(context)
        assertEquals(1, switchValue())
        assertFalse(SettingsManager.isRingBacklightSuppressed(context))
    }

    @Test
    fun theRecordSurvivesAPermissionThatDisappearedMidFlight() {
        RingBacklight.suppress(context)
        shadowOf(RuntimeEnvironment.getApplication())
            .denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)

        RingBacklight.restore(context)
        // Dropping the record here would strand the keyboard off with nothing left to undo it.
        assertTrue(SettingsManager.isRingBacklightSuppressed(context))
    }
}
