package it.palsoftware.pastiera.inputmethod

import android.content.Context
import it.palsoftware.pastiera.SettingsManager

/**
 * Resolves AUTO software-keyboard behavior.
 * Uses the currently available hardware keyboards rather than a manually selected mapping profile.
 */
object SoftwareKeyboardAutoDetector {

    @Volatile
    private var systemInputViewDecision: Boolean? = null

    fun updateSystemInputViewDecision(shouldShowInputView: Boolean) {
        systemInputViewDecision = shouldShowInputView
    }

    fun onInputDevicesChanged() {
        systemInputViewDecision = null
    }

    fun resolve(context: Context): SettingsManager.SoftwareKeyboardMode {
        val shouldShowVirtualKeyboard =
            systemInputViewDecision ?: !DeviceSpecific.hasConnectedHardwareKeyboard()
        return if (shouldShowVirtualKeyboard) {
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        } else {
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        }
    }
}
