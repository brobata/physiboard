package it.palsoftware.pastiera

object SoftwareKeyboardModeActions {
    const val ACTION_TOGGLE = "it.palsoftware.pastiera.action.TOGGLE_SOFTWARE_KEYBOARD_MODE"

    fun toggleForceMode(context: android.content.Context): SettingsManager.SoftwareKeyboardMode {
        val configured = SettingsManager.getSoftwareKeyboardMode(context)
        val current = if (configured == SettingsManager.SoftwareKeyboardMode.AUTO) {
            SettingsManager.resolveEffectiveSoftwareKeyboardMode(context)
        } else {
            configured
        }
        val next = when (current) {
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL -> SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SettingsManager.SoftwareKeyboardMode.AUTO -> SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        }
        if (configured == SettingsManager.SoftwareKeyboardMode.AUTO) {
            SettingsManager.setSoftwareKeyboardModeRuntimeOverride(context, next)
        } else {
            SettingsManager.setSoftwareKeyboardMode(context, next)
        }
        return next
    }
}
