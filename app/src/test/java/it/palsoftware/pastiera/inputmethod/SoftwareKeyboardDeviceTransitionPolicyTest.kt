package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareKeyboardDeviceTransitionPolicyTest {
    @Test
    fun clicksOpenRestoresAutoHardwareAndClearsTemporaryOverride() {
        val transition = plan(
            previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            clicksConnectionChanged = true
        )

        assertEquals(SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE, transition?.mode)
        assertTrue(transition?.clearTemporaryOverride == true)
        assertFalse(transition?.closeInput == true)
    }

    @Test
    fun clicksCloseRestoresAutoVirtualUnlessCloseToggleIsEnabled() {
        val showVirtual = plan(
            previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            clicksConnectionChanged = true,
            clicksDisconnected = true
        )
        val closeInput = plan(
            previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            clicksConnectionChanged = true,
            clicksDisconnected = true,
            closeInputOnClicksDisconnect = true
        )

        assertEquals(SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL, showVirtual?.mode)
        assertFalse(showVirtual?.closeInput == true)
        assertTrue(closeInput?.closeInput == true)
    }

    @Test
    fun realClicksChangeClearsTemporaryOverrideEvenWhenBaseModeStaysTheSame() {
        val transition = plan(
            previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            clicksConnectionChanged = true
        )

        assertTrue(transition?.clearTemporaryOverride == true)
    }

    @Test
    fun persistentConfiguredModeBecomesBaseAfterDeviceChange() {
        val transition = plan(
            configuredMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            clicksConnectionChanged = true
        )

        assertEquals(SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL, transition?.mode)
    }

    @Test
    fun irrelevantInputDeviceNotificationDoesNothing() {
        assertNull(
            plan(
                previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
                autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
            )
        )
    }

    private fun plan(
        configuredMode: SettingsManager.SoftwareKeyboardMode = SettingsManager.SoftwareKeyboardMode.AUTO,
        previousAutoMode: SettingsManager.SoftwareKeyboardMode?,
        autoMode: SettingsManager.SoftwareKeyboardMode,
        clicksConnectionChanged: Boolean = false,
        clicksDisconnected: Boolean = false,
        closeInputOnClicksDisconnect: Boolean = false
    ) = SoftwareKeyboardDeviceTransitionPolicy.plan(
        configuredMode = configuredMode,
        previousAutoMode = previousAutoMode,
        autoMode = autoMode,
        clicksConnectionChanged = clicksConnectionChanged,
        clicksDisconnected = clicksDisconnected,
        closeInputOnClicksDisconnect = closeInputOnClicksDisconnect
    )
}
