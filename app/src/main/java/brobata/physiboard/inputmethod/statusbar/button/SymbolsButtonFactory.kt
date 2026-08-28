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
 * Factory for creating the symbols page button.
 * Opens the SYM symbols page (page 2).
 */
class SymbolsButtonFactory : StatusBarButtonFactory {

    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        
        // Set up click listener using the symbols-specific callback
        button.setOnClickListener {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            callbacks.onSymbolsPageRequested?.invoke()
        }
        
        return ButtonCreationResult(view = button)
    }
    
    override fun update(view: View, state: ButtonState) {
        // No state to update for symbols button
    }
    
    private fun createButton(context: Context, size: Int): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_emoji_symbols_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.status_bar_button_symbols_description)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            // layoutParams will be set by VariationBarView for consistency
        }
    }
}
