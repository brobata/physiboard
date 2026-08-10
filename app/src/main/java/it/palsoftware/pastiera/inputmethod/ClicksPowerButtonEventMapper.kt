package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager

/**
 * Rebinds the software-addressable Clicks Power Keyboard buttons.
 *
 * The red Clicks button reports native Tab. The Launcher button reports Left Meta. Modifier
 * state is retained per input device so Alt and SYM remain active for ordinary key events while
 * a remapped physical button is held. F12 is reserved as the firmware sentinel for a direct action
 * on the physical keyboard button, keeping it distinct from Alt emitted by the microphone key.
 */
internal class ClicksPowerButtonEventMapper {
    data class Result(
        val keyCode: Int,
        val event: KeyEvent?,
        val consume: Boolean = false,
        val directAction: ClicksButtonDirectAction? = null
    )

    private val core = ClicksButtonMappingCore()

    fun map(
        keyCode: Int,
        event: KeyEvent?,
        isClicksPowerKeyboard: Boolean,
        clicksButtonMode: SettingsManager.ClicksPowerButtonMode,
        metaButtonMode: SettingsManager.ClicksPowerButtonMode,
        altButtonMode: SettingsManager.ClicksPowerButtonMode,
        microphoneButtonMode: SettingsManager.ClicksPowerButtonMode
    ): Result {
        if (!isClicksPowerKeyboard || event == null) return Result(keyCode, event)

        val modes = ClicksButtonMappingCore.Modes(
            clicksButtonMode,
            metaButtonMode,
            altButtonMode,
            microphoneButtonMode
        )
        val evaluation = core.evaluate(event, modes)
        val targetKeyCode = modes.targetKeyCode(evaluation.sourceMode, keyCode)
        val remappedEvent = event.withKeyCodeAndMetaState(
            targetKeyCode,
            evaluation.remapMetaState(event.metaState)
        )
        return Result(
            keyCode = targetKeyCode,
            event = remappedEvent,
            consume = evaluation.sourceMode?.directActionOrNull() != null,
            directAction = evaluation.directAction
        )
    }

    fun resetDevice(deviceId: Int) {
        core.resetDevice(deviceId)
    }

    fun reset() {
        core.reset()
    }
}
