package it.palsoftware.pastiera

import android.content.Context
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
class ClicksPowerButtonSettingsTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun buttonModesDefaultToNativeAndPersistIndependently() {
        assertEquals(SettingsManager.ClicksPowerButtonMode.NATIVE, SettingsManager.getClicksButtonMode(context))
        assertEquals(SettingsManager.ClicksPowerButtonMode.NATIVE, SettingsManager.getClicksMetaButtonMode(context))
        assertEquals(SettingsManager.ClicksPowerButtonMode.NATIVE, SettingsManager.getClicksAltButtonMode(context))
        assertEquals(
            SettingsManager.ClicksPowerButtonMode.NATIVE,
            SettingsManager.getClicksMicrophoneButtonMode(context)
        )

        SettingsManager.setClicksButtonMode(context, SettingsManager.ClicksPowerButtonMode.ALT)
        SettingsManager.setClicksMetaButtonMode(context, SettingsManager.ClicksPowerButtonMode.SYM)
        SettingsManager.setClicksAltButtonMode(context, SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE)
        SettingsManager.setClicksMicrophoneButtonMode(
            context,
            SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER
        )

        assertEquals(SettingsManager.ClicksPowerButtonMode.ALT, SettingsManager.getClicksButtonMode(context))
        assertEquals(SettingsManager.ClicksPowerButtonMode.SYM, SettingsManager.getClicksMetaButtonMode(context))
        assertEquals(
            SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE,
            SettingsManager.getClicksAltButtonMode(context)
        )
        assertEquals(
            SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER,
            SettingsManager.getClicksMicrophoneButtonMode(context)
        )
    }

    @Test
    fun recommendedSettingsEnableBothQuickLauncherButtons() {
        SettingsManager.setAltCtrlSpeechShortcutEnabled(context, false)

        assertTrue(SettingsManager.applyClicksRecommendedButtonModes(context))

        assertEquals(
            SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
            SettingsManager.getClicksButtonMode(context)
        )
        assertEquals(
            SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
            SettingsManager.getClicksMetaButtonMode(context)
        )
        assertEquals(
            SettingsManager.ClicksPowerButtonMode.NATIVE,
            SettingsManager.getClicksAltButtonMode(context)
        )
        assertEquals(
            SettingsManager.ClicksPowerButtonMode.NATIVE,
            SettingsManager.getClicksMicrophoneButtonMode(context)
        )
        assertTrue(SettingsManager.getAltCtrlSpeechShortcutEnabled(context))
    }

    @Test
    fun desiredFirmwareBindingsPersistChoiceAndOutputIndependently() {
        val red = ClicksDesiredButtonBinding("red_quick_launcher", byteArrayOf(0x00, 0x00))
        val keyboard = ClicksDesiredButtonBinding("alt_toggle_keyboard_mode", byteArrayOf(0x00, 0x45))
        val microphone = ClicksDesiredButtonBinding(
            "microphone_dictation",
            byteArrayOf(0xe2.toByte(), 0xe0.toByte())
        )

        SettingsManager.setClicksDesiredButtonBinding(context, ClicksButtonBindingTarget.RED, red)
        SettingsManager.setClicksDesiredButtonBinding(context, ClicksButtonBindingTarget.KEYBOARD, keyboard)
        SettingsManager.setClicksDesiredButtonBinding(context, ClicksButtonBindingTarget.MICROPHONE, microphone)

        assertBindingEquals(red, SettingsManager.getClicksDesiredButtonBinding(context, ClicksButtonBindingTarget.RED))
        assertBindingEquals(
            keyboard,
            SettingsManager.getClicksDesiredButtonBinding(context, ClicksButtonBindingTarget.KEYBOARD)
        )
        assertBindingEquals(
            microphone,
            SettingsManager.getClicksDesiredButtonBinding(context, ClicksButtonBindingTarget.MICROPHONE)
        )
    }

    private fun assertBindingEquals(
        expected: ClicksDesiredButtonBinding,
        actual: ClicksDesiredButtonBinding?
    ) {
        requireNotNull(actual)
        assertEquals(expected.choiceId, actual.choiceId)
        assertTrue(expected.firmwareOutput.contentEquals(actual.firmwareOutput))
    }
}
