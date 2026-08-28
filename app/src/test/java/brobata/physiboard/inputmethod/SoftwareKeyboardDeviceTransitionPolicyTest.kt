package brobata.physiboard.inputmethod

import brobata.physiboard.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareKeyboardDeviceTransitionPolicyTest {
    @Test
    fun aChangedAutoModeRestoresTheBaseModeAndClearsTheTemporaryOverride() {
        val transition = plan(
            previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        )

        assertEquals(SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE, transition?.mode)
        assertTrue(transition?.clearTemporaryOverride == true)
    }

    @Test
    fun anUnchangedAutoModePlansNothing() {
        assertNull(
            plan(
                previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
                autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
            )
        )
    }

    @Test
    fun anExplicitConfiguredModeWinsOverWhatWasAutoDetected() {
        val transition = plan(
            configuredMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        )

        assertEquals(SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL, transition?.mode)
    }

    @Test
    fun theFirstObservationHasNoPreviousModeAndStillPlans() {
        val transition = plan(
            previousAutoMode = null,
            autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        )

        assertEquals(SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE, transition?.mode)
    }

    private fun plan(
        configuredMode: SettingsManager.SoftwareKeyboardMode =
            SettingsManager.SoftwareKeyboardMode.AUTO,
        previousAutoMode: SettingsManager.SoftwareKeyboardMode?,
        autoMode: SettingsManager.SoftwareKeyboardMode
    ) = SoftwareKeyboardDeviceTransitionPolicy.plan(
        configuredMode = configuredMode,
        previousAutoMode = previousAutoMode,
        autoMode = autoMode
    )
}
