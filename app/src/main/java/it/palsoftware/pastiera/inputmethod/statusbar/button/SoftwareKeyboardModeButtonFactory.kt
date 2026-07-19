package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SoftwareKeyboardModeActions
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks

/** Hardware/virtual keyboard override, matching the Ctrl+B Nav Mode command icon. */
class SoftwareKeyboardModeButtonFactory : StatusBarButtonFactory {
    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = ImageView(context).apply {
            setImageResource(R.drawable.expansion_panels_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.status_bar_button_software_keyboard_mode_description)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                SoftwareKeyboardModeActions.toggleForceMode(context)
            }
        }
        return ButtonCreationResult(view = button)
    }

    override fun update(view: View, state: ButtonState) = Unit
}
