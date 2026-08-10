package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager

/** Shared state machine for Clicks button identity, held modifiers, and direct-action debounce. */
internal class ClicksButtonMappingCore {
    enum class SourceButton { RED_CLICKS, LAUNCHER_META, KEYBOARD_SENTINEL, MICROPHONE_SENTINEL }

    data class Modes(
        val redClicks: SettingsManager.ClicksPowerButtonMode,
        val launcher: SettingsManager.ClicksPowerButtonMode,
        val keyboard: SettingsManager.ClicksPowerButtonMode,
        val microphone: SettingsManager.ClicksPowerButtonMode
    )

    data class Evaluation(
        val source: SourceButton?,
        val sourceMode: SettingsManager.ClicksPowerButtonMode?,
        val activeModes: Map<SourceButton, SettingsManager.ClicksPowerButtonMode>,
        val directAction: ClicksButtonDirectAction?
    ) {
        fun needsForwarding(): Boolean = activeModes.any { (source, mode) ->
            (source == SourceButton.LAUNCHER_META && mode != SettingsManager.ClicksPowerButtonMode.NATIVE) ||
                mode == SettingsManager.ClicksPowerButtonMode.ALT ||
                mode == SettingsManager.ClicksPowerButtonMode.SYM
        }

        fun remapMetaState(original: Int): Int {
            var result = original
            if (activeModes[SourceButton.LAUNCHER_META]
                ?.let { it != SettingsManager.ClicksPowerButtonMode.NATIVE } == true
            ) {
                result = result and KeyEvent.META_META_MASK.inv()
            }
            if (activeModes.values.any { it == SettingsManager.ClicksPowerButtonMode.ALT }) {
                result = result or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            }
            if (activeModes.values.any { it == SettingsManager.ClicksPowerButtonMode.SYM }) {
                result = result or KeyEvent.META_SYM_ON
            }
            return KeyEvent.normalizeMetaState(result)
        }
    }

    private val activeModesByDevice =
        mutableMapOf<Int, MutableMap<SourceButton, SettingsManager.ClicksPowerButtonMode>>()
    private val lastDirectActionTimes = mutableMapOf<Pair<Int, SourceButton>, Long>()

    fun evaluate(event: KeyEvent, modes: Modes): Evaluation {
        val source = sourceButton(event.keyCode)
        val deviceModes = activeModesByDevice[event.deviceId]
            ?: if (source != null) {
                mutableMapOf<SourceButton, SettingsManager.ClicksPowerButtonMode>().also {
                    activeModesByDevice[event.deviceId] = it
                }
            } else {
                mutableMapOf()
            }
        val configuredMode = when (source) {
            SourceButton.RED_CLICKS -> modes.redClicks
            SourceButton.LAUNCHER_META -> modes.launcher
            SourceButton.KEYBOARD_SENTINEL -> modes.keyboard
            SourceButton.MICROPHONE_SENTINEL -> modes.microphone
            null -> null
        }
        val sourceMode = source?.let {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> deviceModes.getOrPut(it) { requireNotNull(configuredMode) }
                KeyEvent.ACTION_UP -> deviceModes[it] ?: configuredMode
                else -> configuredMode
            }
        }
        val activeModes = deviceModes.toMap()
        val directAction = sourceMode?.directActionOrNull()
            ?.takeIf { event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 }
            ?.takeUnless {
                val actionKey = event.deviceId to requireNotNull(source)
                val previous = lastDirectActionTimes[actionKey]
                val bounced = previous != null && event.eventTime >= previous &&
                    event.eventTime - previous < DIRECT_ACTION_DEBOUNCE_MS
                if (!bounced) lastDirectActionTimes[actionKey] = event.eventTime
                bounced
            }
        if (source != null && event.action == KeyEvent.ACTION_UP) removeActiveMode(event.deviceId, source)
        return Evaluation(source, sourceMode, activeModes, directAction)
    }

    fun resetDevice(deviceId: Int) {
        activeModesByDevice.remove(deviceId)
        lastDirectActionTimes.keys.removeAll { it.first == deviceId }
    }

    fun reset() {
        activeModesByDevice.clear()
        lastDirectActionTimes.clear()
    }

    private fun removeActiveMode(deviceId: Int, source: SourceButton) {
        val modes = activeModesByDevice[deviceId] ?: return
        modes.remove(source)
        if (modes.isEmpty()) activeModesByDevice.remove(deviceId)
    }

    private fun sourceButton(keyCode: Int): SourceButton? = when (keyCode) {
        KeyEvent.KEYCODE_TAB -> SourceButton.RED_CLICKS
        KeyEvent.KEYCODE_META_LEFT -> SourceButton.LAUNCHER_META
        KeyEvent.KEYCODE_F12 -> SourceButton.KEYBOARD_SENTINEL
        KeyEvent.KEYCODE_F11 -> SourceButton.MICROPHONE_SENTINEL
        else -> null
    }

    private companion object {
        const val DIRECT_ACTION_DEBOUNCE_MS = 350L
    }
}

internal fun ClicksButtonMappingCore.Modes.targetKeyCode(
    sourceMode: SettingsManager.ClicksPowerButtonMode?,
    originalKeyCode: Int
): Int = when (sourceMode) {
    SettingsManager.ClicksPowerButtonMode.ALT -> KeyEvent.KEYCODE_ALT_LEFT
    SettingsManager.ClicksPowerButtonMode.TAB -> KeyEvent.KEYCODE_TAB
    SettingsManager.ClicksPowerButtonMode.SYM -> KeyEvent.KEYCODE_SYM
    else -> originalKeyCode
}

internal fun KeyEvent.withKeyCodeAndMetaState(keyCode: Int, metaState: Int): KeyEvent {
    val normalizedMetaState = KeyEvent.normalizeMetaState(metaState)
    if (this.keyCode == keyCode && this.metaState == normalizedMetaState) return this
    return KeyEvent(
        downTime,
        eventTime,
        action,
        keyCode,
        repeatCount,
        normalizedMetaState,
        deviceId,
        scanCode,
        flags,
        source
    )
}
