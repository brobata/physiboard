package it.palsoftware.pastiera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsManagerLayoutSwitchTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun altShiftLayoutSwitch_defaultsDisabled_andPersistsEnabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(SettingsManager.isAltShiftLayoutSwitchEnabled(context))

        SettingsManager.setAltShiftLayoutSwitchEnabled(context, true)

        assertTrue(SettingsManager.isAltShiftLayoutSwitchEnabled(context))
    }

    @Test
    fun layoutSwitchToast_defaultsEnabled_andPersistsDisabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.isToastOnLayoutSwitchEnabled(context))

        SettingsManager.setToastOnLayoutSwitchEnabled(context, false)

        assertFalse(SettingsManager.isToastOnLayoutSwitchEnabled(context))
    }

    @Test
    fun softwareKeyboardMode_defaultsAuto_andPersistsVirtualAndHardwareModes() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(
            SettingsManager.SoftwareKeyboardMode.AUTO,
            SettingsManager.getSoftwareKeyboardMode(context)
        )

        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL)
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            SettingsManager.getSoftwareKeyboardMode(context)
        )

        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE)
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SettingsManager.getSoftwareKeyboardMode(context)
        )
    }

    @Test
    fun softwareKeyboardMode_storageValuesUseVirtualAndHardwareNaming() {
        assertEquals("auto", SettingsManager.SoftwareKeyboardMode.AUTO.storageValue)
        assertEquals("force_virtual", SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL.storageValue)
        assertEquals("force_hardware", SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE.storageValue)
    }

    @Test
    fun navModeCtrlHold_defaultsDisabled_andPersistsEnabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(SettingsManager.getNavModeCtrlHoldEnabled(context))

        SettingsManager.setNavModeCtrlHoldEnabled(context, true)

        assertTrue(SettingsManager.getNavModeCtrlHoldEnabled(context))
    }
}
