package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.Intent
import android.widget.Toast
import it.palsoftware.pastiera.MainActivity
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SoftwareKeyboardModeActions

enum class ClicksButtonDirectAction {
    QUICK_LAUNCHER,
    OPEN_PASTIERA,
    TOGGLE_KEYBOARD_MODE,
    TOGGLE_EMOJI_PICKER
}

internal fun SettingsManager.ClicksPowerButtonMode.directActionOrNull(): ClicksButtonDirectAction? =
    when (this) {
        SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER -> ClicksButtonDirectAction.QUICK_LAUNCHER
        SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA -> ClicksButtonDirectAction.OPEN_PASTIERA
        SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE -> ClicksButtonDirectAction.TOGGLE_KEYBOARD_MODE
        SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER -> ClicksButtonDirectAction.TOGGLE_EMOJI_PICKER
        else -> null
    }

internal object ClicksButtonDirectActionExecutor {
    fun execute(context: Context, action: ClicksButtonDirectAction) {
        when (action) {
            ClicksButtonDirectAction.QUICK_LAUNCHER -> QuickLauncherOpener.open(context)
            ClicksButtonDirectAction.OPEN_PASTIERA -> context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ClicksButtonDirectAction.TOGGLE_KEYBOARD_MODE -> {
                val next = SoftwareKeyboardModeActions.toggleTemporaryMode(context)
                if (SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context)) {
                    val message = when (next) {
                        SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL ->
                            R.string.software_keyboard_mode_toggle_now_virtual
                        SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE ->
                            R.string.software_keyboard_mode_toggle_now_hardware
                        SettingsManager.SoftwareKeyboardMode.AUTO ->
                            R.string.software_keyboard_mode_auto_short
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            ClicksButtonDirectAction.TOGGLE_EMOJI_PICKER ->
                ClicksAccessibilityKeyBridge.dispatch(action)
        }
    }
}
