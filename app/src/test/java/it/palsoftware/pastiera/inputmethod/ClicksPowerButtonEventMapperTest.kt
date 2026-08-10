package it.palsoftware.pastiera.inputmethod

import android.view.InputDevice
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
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
class ClicksPowerButtonEventMapperTest {
    private val mapper = ClicksPowerButtonEventMapper()

    @Test
    fun redClicksButtonCanRemainNativeTab() {
        val input = event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)

        val result = map(input, clicksMode = SettingsManager.ClicksPowerButtonMode.NATIVE)

        assertEquals(KeyEvent.KEYCODE_TAB, result.keyCode)
        assertSame(input, result.event)
        assertFalse(result.consume)
    }

    @Test
    fun redClicksButtonCanActAsHeldAltModifier() {
        val down = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB),
            clicksMode = SettingsManager.ClicksPowerButtonMode.ALT
        )
        val letter = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_K),
            clicksMode = SettingsManager.ClicksPowerButtonMode.ALT
        )
        val up = map(
            event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB),
            clicksMode = SettingsManager.ClicksPowerButtonMode.ALT
        )

        assertEquals(KeyEvent.KEYCODE_ALT_LEFT, down.keyCode)
        assertTrue(down.event!!.isAltPressed)
        assertTrue(letter.event!!.isAltPressed)
        assertEquals(KeyEvent.KEYCODE_ALT_LEFT, up.keyCode)
        assertTrue(up.event!!.isAltPressed)
    }

    @Test
    fun launcherMetaCanActAsRealSymAndRemovesRawMeta() {
        val rawMeta = KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        val down = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, rawMeta),
            metaMode = SettingsManager.ClicksPowerButtonMode.SYM
        )
        val letter = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U, rawMeta),
            metaMode = SettingsManager.ClicksPowerButtonMode.SYM
        )
        val up = map(
            event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, rawMeta),
            metaMode = SettingsManager.ClicksPowerButtonMode.SYM
        )

        assertEquals(KeyEvent.KEYCODE_SYM, down.keyCode)
        assertTrue(down.event!!.isSymPressed)
        assertEquals(0, down.event!!.metaState and KeyEvent.META_META_MASK)
        assertTrue(letter.event!!.isSymPressed)
        assertEquals(0, letter.event!!.metaState and KeyEvent.META_META_MASK)
        assertEquals(KeyEvent.KEYCODE_SYM, up.keyCode)
    }

    @Test
    fun eitherButtonCanSendTab() {
        val result = map(
            event(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_META_LEFT,
                KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
            ),
            metaMode = SettingsManager.ClicksPowerButtonMode.TAB
        )

        assertEquals(KeyEvent.KEYCODE_TAB, result.keyCode)
        assertEquals(0, result.event!!.metaState and KeyEvent.META_META_MASK)
    }

    @Test
    fun quickLauncherConsumesDownRepeatAndUpButOpensOnlyOnce() {
        val down = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT),
            metaMode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER
        )
        val repeat = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, repeatCount = 1),
            metaMode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER
        )
        val up = map(
            event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT),
            metaMode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER
        )

        assertTrue(down.consume)
        assertEquals(ClicksButtonDirectAction.QUICK_LAUNCHER, down.directAction)
        assertTrue(repeat.consume)
        assertEquals(null, repeat.directAction)
        assertTrue(up.consume)
        assertEquals(null, up.directAction)
    }

    @Test
    fun f12SentinelCanToggleKeyboardModeWithoutCapturingRealAlt() {
        val sentinel = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12),
            altMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE
        )
        val microphoneAlt = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT),
            altMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE
        )

        assertTrue(sentinel.consume)
        assertEquals(ClicksButtonDirectAction.TOGGLE_KEYBOARD_MODE, sentinel.directAction)
        assertFalse(microphoneAlt.consume)
    }

    @Test
    fun f11SentinelCanToggleEmojiPickerWithoutAffectingKeyboardButton() {
        val microphone = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F11),
            microphoneMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER
        )
        val keyboard = map(
            event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12),
            microphoneMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER
        )

        assertTrue(microphone.consume)
        assertEquals(ClicksButtonDirectAction.TOGGLE_EMOJI_PICKER, microphone.directAction)
        assertFalse(keyboard.consume)
    }

    @Test
    fun eventsFromOtherKeyboardsAreUntouched() {
        val input = event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT)

        val result = mapper.map(
            keyCode = input.keyCode,
            event = input,
            isClicksPowerKeyboard = false,
            clicksButtonMode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
            metaButtonMode = SettingsManager.ClicksPowerButtonMode.SYM,
            altButtonMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE,
            microphoneButtonMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER
        )

        assertSame(input, result.event)
        assertEquals(input.keyCode, result.keyCode)
        assertFalse(result.consume)
    }

    private fun map(
        input: KeyEvent,
        clicksMode: SettingsManager.ClicksPowerButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
        metaMode: SettingsManager.ClicksPowerButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
        altMode: SettingsManager.ClicksPowerButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
        microphoneMode: SettingsManager.ClicksPowerButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE
    ) = mapper.map(
        keyCode = input.keyCode,
        event = input,
        isClicksPowerKeyboard = true,
        clicksButtonMode = clicksMode,
        metaButtonMode = metaMode,
        altButtonMode = altMode,
        microphoneButtonMode = microphoneMode
    )

    private fun event(
        action: Int,
        keyCode: Int,
        metaState: Int = 0,
        repeatCount: Int = 0
    ) = KeyEvent(
        1_000L,
        1_100L,
        action,
        keyCode,
        repeatCount,
        metaState,
        7,
        0,
        0,
        InputDevice.SOURCE_KEYBOARD
    )
}
