package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager

/** Maps configured Clicks buttons before Android or the foreground app can interpret them. */
internal class ClicksLauncherAccessibilityKeyMapper {
    data class Result(
        val consume: Boolean = false,
        val directAction: ClicksButtonDirectAction? = null,
        val forwardedEvent: KeyEvent? = null
    )

    private val core = ClicksButtonMappingCore()

    fun map(
        event: KeyEvent,
        isClicksPowerKeyboard: Boolean,
        redButtonMode: SettingsManager.ClicksPowerButtonMode,
        launcherButtonMode: SettingsManager.ClicksPowerButtonMode,
        altButtonMode: SettingsManager.ClicksPowerButtonMode,
        microphoneButtonMode: SettingsManager.ClicksPowerButtonMode
    ): Result {
        if (!isClicksPowerKeyboard) return Result()

        val modes = ClicksButtonMappingCore.Modes(
            redButtonMode,
            launcherButtonMode,
            altButtonMode,
            microphoneButtonMode
        )
        val evaluation = core.evaluate(event, modes)
        if (evaluation.source != null) {
            val mode = requireNotNull(evaluation.sourceMode)
            if (mode == SettingsManager.ClicksPowerButtonMode.NATIVE) {
                return Result()
            }

            val forwardedEvent = when (mode) {
                SettingsManager.ClicksPowerButtonMode.NATIVE,
                SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
                SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA,
                SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE,
                SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER -> null
                SettingsManager.ClicksPowerButtonMode.ALT,
                SettingsManager.ClicksPowerButtonMode.TAB,
                SettingsManager.ClicksPowerButtonMode.SYM -> event.withKeyCodeAndMetaState(
                    modes.targetKeyCode(mode, event.keyCode),
                    evaluation.remapMetaState(event.metaState)
                )
            }
            return Result(
                consume = true,
                directAction = evaluation.directAction,
                forwardedEvent = forwardedEvent
            )
        }

        if (!evaluation.needsForwarding()) return Result()
        return Result(
            consume = true,
            forwardedEvent = event.withKeyCodeAndMetaState(
                event.keyCode,
                evaluation.remapMetaState(event.metaState)
            )
        )
    }

    fun reset() {
        core.reset()
    }

    fun resetDevice(deviceId: Int) {
        core.resetDevice(deviceId)
    }
}
