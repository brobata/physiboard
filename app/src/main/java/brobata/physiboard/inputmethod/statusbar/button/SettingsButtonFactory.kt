package brobata.physiboard.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import brobata.physiboard.R
import brobata.physiboard.inputmethod.statusbar.ButtonCreationResult
import brobata.physiboard.inputmethod.statusbar.ButtonState
import brobata.physiboard.inputmethod.statusbar.StatusBarCallbacks
import brobata.physiboard.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Factory for creating the settings button.
 * Opens the application settings screen.
 */
class SettingsButtonFactory : StatusBarButtonFactory {

    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        
        // Set up click listener using the settings callback
        button.setOnClickListener {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            callbacks.onOpenSettings?.invoke()
        }
        
        return ButtonCreationResult(view = button)
    }
    
    override fun update(view: View, state: ButtonState) {
        // No state to update for settings button
    }
    
    private fun createButton(context: Context, size: Int): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_settings_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.status_bar_button_settings_description)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            // layoutParams will be set by VariationBarView for consistency
        }
    }
}
