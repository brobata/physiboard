package it.palsoftware.pastiera.inputmethod

import android.view.InputDevice
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClicksLauncherAccessibilityKeyMapperTest {
    private val mapper = ClicksLauncherAccessibilityKeyMapper()

    @Test
    fun nativeMetaIsNotConsumed() {
        val result = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, rawMeta))

        assertFalse(result.consume)
        assertNull(result.forwardedEvent)
    }

    @Test
    fun quickLauncherConsumesMetaAndOpensOnlyOnce() {
        val mode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER
        val down = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, rawMeta), launcher = mode)
        val repeat = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, rawMeta, repeatCount = 1),
            launcher = mode
        )
        val up = map(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, rawMeta), launcher = mode)

        assertTrue(down.consume)
        assertEquals(ClicksButtonDirectAction.QUICK_LAUNCHER, down.directAction)
        assertTrue(repeat.consume)
        assertNull(repeat.directAction)
        assertTrue(up.consume)
        assertNull(up.directAction)
    }

    @Test
    fun tabReplacesMetaWithoutLeakingMetaState() {
        val result = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, rawMeta),
            launcher = SettingsManager.ClicksPowerButtonMode.TAB
        )

        assertEquals(KeyEvent.KEYCODE_TAB, result.forwardedEvent!!.keyCode)
        assertEquals(0, result.forwardedEvent!!.metaState and KeyEvent.META_META_MASK)
    }

    @Test
    fun heldMetaCanActAsAltForFollowingKey() {
        val mode = SettingsManager.ClicksPowerButtonMode.ALT
        map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, rawMeta), launcher = mode)
        val letter = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U, rawMeta), launcher = mode)

        assertTrue(letter.consume)
        assertTrue(letter.forwardedEvent!!.isAltPressed)
        assertEquals(0, letter.forwardedEvent!!.metaState and KeyEvent.META_META_MASK)
    }

    @Test
    fun rejectedForwardingCanResetHeldModifierForFollowingNativeKeys() {
        val mode = SettingsManager.ClicksPowerButtonMode.ALT
        map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, rawMeta), launcher = mode)

        mapper.resetDevice(24)
        val letter = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U), launcher = mode)

        assertFalse(letter.consume)
        assertNull(letter.forwardedEvent)
    }

    @Test
    fun heldMetaCanActAsRealSymForFollowingKey() {
        val mode = SettingsManager.ClicksPowerButtonMode.SYM
        val down = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, rawMeta), launcher = mode)
        val letter = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U, rawMeta), launcher = mode)

        assertEquals(KeyEvent.KEYCODE_SYM, down.forwardedEvent!!.keyCode)
        assertTrue(down.forwardedEvent!!.isSymPressed)
        assertTrue(letter.forwardedEvent!!.isSymPressed)
    }

    @Test
    fun f12SentinelTogglesKeyboardMode() {
        val mode = SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE
        val down = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12), alt = mode)
        val up = map(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F12), alt = mode)

        assertTrue(down.consume)
        assertEquals(ClicksButtonDirectAction.TOGGLE_KEYBOARD_MODE, down.directAction)
        assertTrue(up.consume)
        assertNull(up.directAction)
    }

    @Test
    fun f11SentinelTogglesEmojiPickerIndependently() {
        val mode = SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER
        val down = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F11), microphone = mode)
        val up = map(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F11), microphone = mode)

        assertTrue(down.consume)
        assertEquals(ClicksButtonDirectAction.TOGGLE_EMOJI_PICKER, down.directAction)
        assertTrue(up.consume)
        assertNull(up.directAction)
    }

    @Test
    fun duplicatedF12TapWithinDebounceWindowTriggersToggleOnlyOnce() {
        val mode = SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE
        val first = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12, eventTime = 1_000L),
            alt = mode
        )
        map(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F12, eventTime = 1_090L), alt = mode)
        val duplicate = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12, eventTime = 1_212L),
            alt = mode
        )
        map(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F12, eventTime = 1_272L), alt = mode)
        val laterIntentionalPress = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12, eventTime = 1_400L),
            alt = mode
        )

        assertEquals(ClicksButtonDirectAction.TOGGLE_KEYBOARD_MODE, first.directAction)
        assertNull(duplicate.directAction)
        assertEquals(
            ClicksButtonDirectAction.TOGGLE_KEYBOARD_MODE,
            laterIntentionalPress.directAction
        )
    }

    @Test
    fun microphoneAltCtrlChordIsUntouchedByKeyboardButtonAction() {
        val mode = SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE
        val altDown = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT), alt = mode)
        val ctrlDown = map(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT), alt = mode)
        val ctrlUp = map(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT), alt = mode)
        val altUp = map(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT), alt = mode)

        listOf(altDown, ctrlDown, ctrlUp, altUp).forEach { result ->
            assertFalse(result.consume)
            assertNull(result.directAction)
            assertNull(result.forwardedEvent)
        }
    }

    @Test
    fun otherKeyboardsAreUntouched() {
        val result = mapper.map(
            event = event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, rawMeta),
            isClicksPowerKeyboard = false,
            redButtonMode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
            launcherButtonMode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
            altButtonMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE,
            microphoneButtonMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER
        )

        assertFalse(result.consume)
    }

    private fun map(
        event: KeyEvent,
        red: SettingsManager.ClicksPowerButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
        launcher: SettingsManager.ClicksPowerButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
        alt: SettingsManager.ClicksPowerButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
        microphone: SettingsManager.ClicksPowerButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE
    ) = mapper.map(
        event = event,
        isClicksPowerKeyboard = true,
        redButtonMode = red,
        launcherButtonMode = launcher,
        altButtonMode = alt,
        microphoneButtonMode = microphone
    )

    private fun event(
        action: Int,
        keyCode: Int,
        metaState: Int = 0,
        repeatCount: Int = 0,
        eventTime: Long = 1_100L
    ) = KeyEvent(
        eventTime,
        eventTime,
        action,
        keyCode,
        repeatCount,
        metaState,
        24,
        0,
        0,
        InputDevice.SOURCE_KEYBOARD
    )

    private companion object {
        const val rawMeta = KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
    }
}
