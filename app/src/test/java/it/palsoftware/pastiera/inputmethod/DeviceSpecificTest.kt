package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import android.view.InputDevice
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
        assertFalse(DeviceSpecific.isMinimalPhoneDevice())
    }

    @Test
    fun originalTitanProfile_detectsOriginalTitanLayout() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "Unihertz",
            manufacturer = "A-gold",
            model = "Titan",
            device = "Titan",
            product = "Titan",
            board = "g61v71c2k_dfl_tee",
            display = "Titan_20221121"
        )

        assertEquals("titan", DeviceSpecific.physicalKeyboardName())
        assertEquals("Unihertz", DeviceSpecific.keyboardName())
        assertFalse(DeviceSpecific.needsRemapping())
        assertFalse(DeviceSpecific.isTitan2Device())
    }

    @Test
    fun minimalPhoneProfile_detectsMp01LayoutWithoutRemapping() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "minimal",
            manufacturer = "minimal company",
            model = "MP01",
            device = "mp01",
            product = "along_mp01"
        )

        assertEquals("mp01", DeviceSpecific.physicalKeyboardName())
        assertEquals("Minimal", DeviceSpecific.keyboardName())
        assertFalse(DeviceSpecific.needsRemapping())
        assertTrue(DeviceSpecific.isMinimalPhoneDevice())
    }

    @Test
    fun minimalPhoneManualOverride_forcesMp01Model() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )

        assertFalse(DeviceSpecific.isMinimalPhoneDevice())
        assertTrue(DeviceSpecific.isMinimalPhoneDevice("mp01"))
    }

    @Test
    fun clicksBuiltInManualOverrides_resolveAsIndependentProfiles() {
        val razr = DeviceSpecific.resolveInputProfile(
            event = null,
            physicalProfileOverride = "clicks_razr"
        )
        val pixel = DeviceSpecific.resolveInputProfile(
            event = null,
            physicalProfileOverride = "clicks_pixel"
        )

        assertEquals("clicks_razr", razr.profileId)
        assertEquals("clicks_pixel", pixel.profileId)
        assertFalse(razr.autoDetected)
        assertFalse(pixel.autoDetected)
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
        assertFalse(DeviceSpecific.needsRemapping())
        assertTrue(DeviceSpecific.isTitan2Device())
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
        assertEquals("Unihertz", DeviceSpecific.keyboardName())
        assertFalse(DeviceSpecific.needsRemapping())
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

    @Test
    fun manualKey2Override_appliesScanCodeNormalizationEvenWhenDeviceIsNotKey2() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )

        val input = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_COMMA,
            metaState = 0,
            scanCode = 50
        )
        val remapped = DeviceSpecific.remapHardwareKeyEvent(
            keyCode = KeyEvent.KEYCODE_COMMA,
            event = input,
            physicalProfileOverride = "key2"
        )

        assertEquals(KeyEvent.KEYCODE_M, remapped.keyCode)
        assertEquals(KeyEvent.KEYCODE_M, remapped.event?.keyCode)
    }

    @Test
    fun manualQ25Override_remapsModifierKeysEvenWhenDeviceIsNotQ25() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "blackberry",
            manufacturer = "blackberry",
            model = "bbf100-4",
            device = "athena",
            product = "lineage_athena"
        )

        val input = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_SHIFT_RIGHT,
            metaState = KeyEvent.META_SHIFT_RIGHT_ON
        )
        val remapped = DeviceSpecific.remapHardwareKeyEvent(
            keyCode = KeyEvent.KEYCODE_SHIFT_RIGHT,
            event = input,
            physicalProfileOverride = "Q25"
        )

        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, remapped.keyCode)
        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, remapped.event?.keyCode)
    }

    @Test
    fun clicksPowerScanCodes_restorePhysicalLetterPositionsAfterAndroidLayoutMapping() {
        val physicalYReportedAsZ = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_Z,
            metaState = 0,
            scanCode = 21
        )
        val yRemapped = DeviceSpecific.remapHardwareKeyEvent(
            keyCode = KeyEvent.KEYCODE_Z,
            event = physicalYReportedAsZ,
            physicalProfileOverride = "clicks_power"
        )
        assertEquals(KeyEvent.KEYCODE_Y, yRemapped.keyCode)
        assertEquals(KeyEvent.KEYCODE_Y, yRemapped.event?.keyCode)

        val physicalZReportedAsY = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_Y,
            metaState = 0,
            scanCode = 44
        )
        val zRemapped = DeviceSpecific.remapHardwareKeyEvent(
            keyCode = KeyEvent.KEYCODE_Y,
            event = physicalZReportedAsY,
            physicalProfileOverride = "clicks_power"
        )
        assertEquals(KeyEvent.KEYCODE_Z, zRemapped.keyCode)
        assertEquals(KeyEvent.KEYCODE_Z, zRemapped.event?.keyCode)
    }

    @Test
    fun clicksPowerNonLetterScanCode_staysUnchanged() {
        val input = keyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_SLASH,
            metaState = 0,
            scanCode = 53
        )
        val remapped = DeviceSpecific.remapHardwareKeyEvent(
            keyCode = KeyEvent.KEYCODE_SLASH,
            event = input,
            physicalProfileOverride = "clicks_power"
        )

        assertEquals(KeyEvent.KEYCODE_SLASH, remapped.keyCode)
        assertSame(input, remapped.event)
    }

    @Test
    fun clicksPowerAccessory_isDetectedPerInputDevice() {
        val profile = DeviceSpecific.resolveInputProfile(
            identity = keyboardIdentity(
                name = "Power Keyboard-A0FC-1",
                vendorId = 2007,
                isExternal = true
            )
        )

        assertEquals("clicks_power", profile.profileId)
        assertEquals(DeviceSpecific.InputDeviceKind.ACCESSORY, profile.kind)
        assertTrue(profile.autoDetected)
    }

    @Test
    fun similarlyNamedBuiltInKeyboard_isNotDetectedAsClicksAccessory() {
        val profile = DeviceSpecific.resolveInputProfile(
            identity = keyboardIdentity(
                name = "Power Keyboard-A0FC-1",
                vendorId = 2007,
                isExternal = false
            )
        )

        assertEquals("unknown", profile.profileId)
        assertEquals(DeviceSpecific.InputDeviceKind.BUILT_IN, profile.kind)
        assertFalse(profile.autoDetected)
    }

    @Test
    fun clicksAccessoryDetection_winsOverBuiltInManualProfileForItsEvents() {
        val profile = DeviceSpecific.resolveInputProfile(
            identity = keyboardIdentity(
                name = "Power Keyboard-A0FC-1",
                vendorId = 2007,
                isExternal = true
            ),
            physicalProfileOverride = "key2"
        )

        assertEquals("clicks_power", profile.profileId)
        assertEquals(DeviceSpecific.InputDeviceKind.ACCESSORY, profile.kind)
        assertTrue(profile.autoDetected)
    }

    private fun keyboardIdentity(
        name: String,
        vendorId: Int,
        isExternal: Boolean
    ): DeviceSpecific.KeyboardInputIdentity {
        return DeviceSpecific.KeyboardInputIdentity(
            name = name,
            descriptor = "test-descriptor",
            vendorId = vendorId,
            productId = 0,
            sources = InputDevice.SOURCE_KEYBOARD,
            keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
            isExternal = isExternal,
            isVirtual = false
        )
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
