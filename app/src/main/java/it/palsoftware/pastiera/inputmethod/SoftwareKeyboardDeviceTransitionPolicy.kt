package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.SettingsManager

internal object SoftwareKeyboardDeviceTransitionPolicy {
    data class Transition(
        val mode: SettingsManager.SoftwareKeyboardMode,
        val clearTemporaryOverride: Boolean
    )

    fun plan(
        configuredMode: SettingsManager.SoftwareKeyboardMode,
        previousAutoMode: SettingsManager.SoftwareKeyboardMode?,
        autoMode: SettingsManager.SoftwareKeyboardMode
    ): Transition? {
        if (autoMode == previousAutoMode) return null
        val baseMode = if (configuredMode == SettingsManager.SoftwareKeyboardMode.AUTO) {
            autoMode
        } else {
            configuredMode
        }
        return Transition(mode = baseMode, clearTemporaryOverride = true)
    }
}
