package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.SettingsManager

internal object SoftwareKeyboardDeviceTransitionPolicy {
    data class Transition(
        val mode: SettingsManager.SoftwareKeyboardMode,
        val clearTemporaryOverride: Boolean,
        val closeInput: Boolean
    )

    fun plan(
        configuredMode: SettingsManager.SoftwareKeyboardMode,
        previousAutoMode: SettingsManager.SoftwareKeyboardMode?,
        autoMode: SettingsManager.SoftwareKeyboardMode,
        clicksConnectionChanged: Boolean,
        clicksDisconnected: Boolean,
        closeInputOnClicksDisconnect: Boolean
    ): Transition? {
        val baseDeviceModeChanged = autoMode != previousAutoMode
        val clearTemporaryOverride = baseDeviceModeChanged || clicksConnectionChanged
        if (!clearTemporaryOverride && !clicksDisconnected) return null

        val baseMode = if (configuredMode == SettingsManager.SoftwareKeyboardMode.AUTO) {
            autoMode
        } else {
            configuredMode
        }
        return Transition(
            mode = baseMode,
            clearTemporaryOverride = clearTemporaryOverride,
            closeInput = clicksDisconnected && closeInputOnClicksDisconnect
        )
    }
}
