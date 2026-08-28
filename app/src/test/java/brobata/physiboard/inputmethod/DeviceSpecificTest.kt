package brobata.physiboard.inputmethod

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceSpecificTest {

    @After
    fun tearDown() {
        DeviceSpecific.clearTestOverrides()
    }

    @Test
    fun titan2EliteQwertyProfile_detectsOnlyWithStrictToken() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan2Elite_QWERTY",
            device = "titan2elite_qwerty",
            product = "titan2elite_qwerty"
        )

        assertEquals("titan2elite_qwerty", DeviceSpecific.physicalKeyboardName())
        assertEquals("Unihertz", DeviceSpecific.keyboardName())
        assertTrue(DeviceSpecific.isTitan2Device())
        assertTrue(DeviceSpecific.isTitan2EliteDevice())
        assertFalse(DeviceSpecific.isUntestedTitanDevice())
    }

    @Test
    fun titan2EliteDetectedFromDisplayEvenWithoutStrictToken() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2 Elite",
            device = "titan2",
            product = "titan2",
            display = "Titan 2 Elite_V02.00.00"
        )

        assertEquals("titan2elite_qwerty", DeviceSpecific.physicalKeyboardName())
        assertTrue(DeviceSpecific.isTitan2EliteDevice())
    }

    @Test
    fun titan2EliteDetectedFromBoardToken() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan_2",
            product = "titan_2",
            board = "G72BoardV1"
        )

        assertEquals("titan2elite_qwerty", DeviceSpecific.physicalKeyboardName())
        assertTrue(DeviceSpecific.isTitan2EliteDevice())
    }

    @Test
    fun plainTitan2IsRecognisedButMarkedUntested() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )

        assertEquals("titan2", DeviceSpecific.physicalKeyboardName())
        assertTrue(DeviceSpecific.isTitan2Device())
        assertFalse(DeviceSpecific.isTitan2EliteDevice())
        assertTrue(DeviceSpecific.isUntestedTitanDevice())
        assertTrue(DeviceSpecific.hasBuiltInHardwareKeyboard())
    }

    @Test
    fun devicesOutsideTheTitan2FamilyAreNotClaimed() {
        // Every other keyboard phone the fork used to carry - a KEY2 stands in for all of them.
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "blackberry",
            manufacturer = "blackberry",
            model = "BBF100-1",
            device = "athena",
            product = "athena"
        )

        assertEquals("unknown", DeviceSpecific.physicalKeyboardName())
        assertEquals("unknown", DeviceSpecific.keyboardName())
        assertFalse(DeviceSpecific.isTitan2Device())
        assertFalse(DeviceSpecific.hasBuiltInHardwareKeyboard())
        assertTrue(DeviceSpecific.detectedInputProfiles().isEmpty())
    }

    @Test
    fun theTitanFamilyNeedsNoKeyEventRemapping() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan2Elite_QWERTY",
            device = "titan2elite_qwerty",
            product = "titan2elite_qwerty"
        )

        assertFalse(DeviceSpecific.needsRemapping())
        val remapped = DeviceSpecific.remapHardwareKeyEvent(42, null)
        assertEquals(42, remapped.keyCode)
    }

    @Test
    fun manualOverrideAcceptsOnlyTheTwoSupportedProfiles() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan2Elite_QWERTY",
            device = "titan2elite_qwerty",
            product = "titan2elite_qwerty"
        )

        assertEquals("titan2", DeviceSpecific.resolveInputProfile(null as KeyEvent?, "titan2").profileId)
        assertEquals(
            "titan2elite_qwerty",
            DeviceSpecific.resolveInputProfile(null as KeyEvent?, "titan2elite_qwerty").profileId
        )
        // A retired profile falls back to what the hardware actually is.
        assertEquals("titan2elite_qwerty", DeviceSpecific.resolveInputProfile(null as KeyEvent?, "mp01").profileId)
        assertEquals("titan2elite_qwerty", DeviceSpecific.resolveInputProfile(null as KeyEvent?, "auto").profileId)
    }
}
