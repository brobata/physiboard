package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.ImageView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks

/**
 * Factory for creating the emoji picker button.
 * Opens the Gboard-like emoji picker (symPage 4).
 */
class EmojiButtonFactory : StatusBarButtonFactory {
    
    companion object {
        private val PRESSED_BLUE = Color.rgb(100, 150, 255)
        private val NORMAL_COLOR = Color.rgb(17, 17, 17)
    }
    
    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        
        // Set up click listener using the emoji-specific callback
        button.setOnClickListener {
            callbacks.onHapticFeedback?.invoke()
            callbacks.onEmojiPickerRequested?.invoke()
        }
        
        return ButtonCreationResult(view = button)
    }
    
    override fun update(view: View, state: ButtonState) {
        // No state to update for emoji button
    }
    
    private fun createButton(context: Context, size: Int): ImageView {
        val normalDrawable = GradientDrawable().apply {
            setColor(NORMAL_COLOR)
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_emoji_emotions_24)
            setColorFilter(Color.WHITE)
            background = stateList
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            // layoutParams will be set by VariationBarView for consistency
        }
    }
}
