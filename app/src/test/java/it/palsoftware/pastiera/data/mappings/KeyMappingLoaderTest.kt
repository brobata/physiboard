package it.palsoftware.pastiera.data.mappings

import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyMappingLoaderTest {

    @After
    fun tearDown() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "auto")
        DeviceSpecific.clearTestOverrides()
    }

    @Test
    fun loadAltMappings_mp01ManualOverride_exposesCustomDedicatedKeys() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "mp01")

        val mappings = KeyMappingLoader.loadAltKeyMappings(context.assets, context)

        assertTrue(mappings.isNotEmpty())
        assertEquals("&", mappings[KeyEvent.KEYCODE_Q])
        assertEquals("0", mappings[666])
        assertEquals(".", mappings[667])
    }

    @Test
    fun loadAltMappings_unknownDevice_usesCompleteTitan2FallbackForVirtualKeyboard() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "auto")
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "google",
            manufacturer = "google",
            model = "Pixel 7a",
            device = "lynx",
            product = "lynx"
        )

        val mappings = KeyMappingLoader.loadAltKeyMappings(context.assets, context)

        assertTrue(mappings.size >= 26)
        assertEquals("-", mappings[KeyEvent.KEYCODE_U])
        assertEquals("0", mappings[KeyEvent.KEYCODE_Q])
        assertEquals("?", mappings[KeyEvent.KEYCODE_M])
    }
}
