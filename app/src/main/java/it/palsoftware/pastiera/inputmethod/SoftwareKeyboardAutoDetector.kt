package it.palsoftware.pastiera.inputmethod

import android.content.Context
import it.palsoftware.pastiera.SettingsManager

/**
 * Resolves AUTO software-keyboard behavior.
 * Physical-keyboard devices default to hardware-keyboard mode, all others to virtual-keyboard mode.
 */
object SoftwareKeyboardAutoDetector {

    fun resolve(context: Context): SettingsManager.SoftwareKeyboardMode {
        return if (DeviceSpecific.isPhysicalKeyboardDevice(SettingsManager.getPhysicalKeyboardProfileOverride(context))) {
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        } else {
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        }
    }
}
