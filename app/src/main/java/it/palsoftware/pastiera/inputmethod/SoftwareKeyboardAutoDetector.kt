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

    @Volatile
    private var suppressVirtualPresentationUntilInputHidden: Boolean = false

    fun updateSystemInputViewDecision(shouldShowInputView: Boolean) {
        systemInputViewDecision = shouldShowInputView
    }

    fun onInputDevicesChanged() {
        systemInputViewDecision = null
    }

    fun beginClosingInputForClicksDisconnect() {
        suppressVirtualPresentationUntilInputHidden = true
    }

    fun onInputWindowHidden() {
        suppressVirtualPresentationUntilInputHidden = false
    }

    fun resolve(context: Context): SettingsManager.SoftwareKeyboardMode {
        if (suppressVirtualPresentationUntilInputHidden) {
            return SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        }
        // A recognized built-in keyboard is part of the phone and cannot be transiently absent.
        // Prefer that stable device identity over Android's initial input-view recommendation,
        // which may briefly request the full on-screen keyboard while the IME starts.
        if (DeviceSpecific.hasBuiltInHardwareKeyboard()) {
            return SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        }
        val shouldShowVirtualKeyboard =
            systemInputViewDecision ?: !DeviceSpecific.hasConnectedHardwareKeyboard()
        return if (shouldShowVirtualKeyboard) {
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        } else {
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        }
    }
}
