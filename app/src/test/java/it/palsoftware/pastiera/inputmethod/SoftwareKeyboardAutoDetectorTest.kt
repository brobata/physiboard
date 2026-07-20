package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SoftwareKeyboardAutoDetectorTest {

    @After
    fun tearDown() {
        SoftwareKeyboardAutoDetector.onInputDevicesChanged()
        DeviceSpecific.clearTestOverrides()
    }

    @Test
    fun autoResolvesToHardware_forDetectedPhysicalKeyboardDevices() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )

        val context = RuntimeEnvironment.getApplication()
        SoftwareKeyboardAutoDetector.updateSystemInputViewDecision(true)
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SoftwareKeyboardAutoDetector.resolve(context)
        )
    }

    @Test
    fun autoResolvesToVirtual_forNonPhysicalKeyboardDevices() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "google",
            manufacturer = "google",
            model = "Pixel 7a",
            device = "lynx",
            product = "lynx"
        )

        val context = RuntimeEnvironment.getApplication()
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            SoftwareKeyboardAutoDetector.resolve(context)
        )
    }
}
