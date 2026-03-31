package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
    fun q25Profile_detectsBlackberryAndEnablesRemapping() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "zinwa",
            manufacturer = "zinwa",
            model = "Q25",
            device = "Q25",
            product = "q25"
        )

        assertEquals("Q25", DeviceSpecific.physicalKeyboardName())
        assertEquals("Blackberry", DeviceSpecific.keyboardName())
        assertTrue(DeviceSpecific.needsRemapping())
    }

    @Test
    fun key2Profile_detectsAthenaAndUsesKey2LayoutWithoutRemapping() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "blackberry",
            manufacturer = "blackberry",
            model = "bbf100-1",
            device = "athena",
            product = "lineage_athena"
        )

        assertEquals("key2", DeviceSpecific.physicalKeyboardName())
        assertEquals("Blackberry", DeviceSpecific.keyboardName())
        assertFalse(DeviceSpecific.needsRemapping())
    }

    @Test
    fun titanPocketProfile_mapsToTitan2Layout() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan Pocket",
            device = "titan_pocket",
            product = "titan_pocket"
        )

        assertEquals("titan2", DeviceSpecific.physicalKeyboardName())
        assertEquals("Unihertz", DeviceSpecific.keyboardName())
        assertFalse(DeviceSpecific.needsRemapping())
    }

    @Test
    fun q25CtrlEvent_remapsToCtrlKeyAndMeta() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "zinwa",
            manufacturer = "zinwa",
            model = "Q25",
            device = "Q25",
            product = "q25"
        )

        val input = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_SHIFT_RIGHT,
            metaState = KeyEvent.META_SHIFT_RIGHT_ON
        )

        val remapped = DeviceSpecific.remapHardwareKeyEvent(KeyEvent.KEYCODE_SHIFT_RIGHT, input)

        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, remapped.keyCode)
        val event = remapped.event ?: error("Expected remapped event")
        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, event.keyCode)
        assertTrue((event.metaState and KeyEvent.META_CTRL_ON) != 0)
        assertTrue((event.metaState and KeyEvent.META_CTRL_LEFT_ON) != 0)
    }

    @Test
    fun q25SymEvent_remapsToSymKeyAndMeta() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "zinwa",
            manufacturer = "zinwa",
            model = "Q25",
            device = "Q25",
            product = "q25"
        )

        val input = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_ALT_RIGHT,
            metaState = KeyEvent.META_ALT_RIGHT_ON
        )

        val remapped = DeviceSpecific.remapHardwareKeyEvent(KeyEvent.KEYCODE_ALT_RIGHT, input)

        assertEquals(KeyEvent.KEYCODE_SYM, remapped.keyCode)
        val event = remapped.event ?: error("Expected remapped event")
        assertEquals(KeyEvent.KEYCODE_SYM, event.keyCode)
        assertTrue((event.metaState and KeyEvent.META_SYM_ON) != 0)
    }

    @Test
    fun nonQ25Event_staysUnchanged() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )

        val input = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_A,
            metaState = 0
        )

        val remapped = DeviceSpecific.remapHardwareKeyEvent(KeyEvent.KEYCODE_A, input)
        assertEquals(KeyEvent.KEYCODE_A, remapped.keyCode)
        assertSame(input, remapped.event)
    }

    @Test
    fun key2Bbf1004ScanCodes_areNormalizedToExpectedKeyCodes() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "blackberry",
            manufacturer = "blackberry",
            model = "bbf100-4",
            device = "athena",
            product = "lineage_athena"
        )

        val mInput = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_COMMA,
            metaState = 0,
            scanCode = 50
        )
        val mRemapped = DeviceSpecific.remapHardwareKeyEvent(KeyEvent.KEYCODE_COMMA, mInput)
        assertEquals(KeyEvent.KEYCODE_M, mRemapped.keyCode)
        assertEquals(KeyEvent.KEYCODE_M, mRemapped.event?.keyCode)

        val wInput = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_Z,
            metaState = 0,
            scanCode = 17
        )
        val wRemapped = DeviceSpecific.remapHardwareKeyEvent(KeyEvent.KEYCODE_Z, wInput)
        assertEquals(KeyEvent.KEYCODE_W, wRemapped.keyCode)
        assertEquals(KeyEvent.KEYCODE_W, wRemapped.event?.keyCode)

        val zInput = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_W,
            metaState = 0,
            scanCode = 44
        )
        val zRemapped = DeviceSpecific.remapHardwareKeyEvent(KeyEvent.KEYCODE_W, zInput)
        assertEquals(KeyEvent.KEYCODE_Z, zRemapped.keyCode)
        assertEquals(KeyEvent.KEYCODE_Z, zRemapped.event?.keyCode)
    }

    private fun keyEvent(action: Int, keyCode: Int, metaState: Int, scanCode: Int = 1): KeyEvent {
        return KeyEvent(
            1000L,
            1010L,
            action,
            keyCode,
            0,
            metaState,
            1,
            scanCode,
            0,
            0
        )
    }
}
