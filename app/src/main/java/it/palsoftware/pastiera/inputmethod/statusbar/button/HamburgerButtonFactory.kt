package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Factory for creating the hamburger menu button.
 */
class HamburgerButtonFactory : StatusBarButtonFactory {
    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context)

        button.setOnClickListener {
            callbacks.onHapticFeedback?.invoke()
            callbacks.onHamburgerMenuRequested?.invoke()
        }

        return ButtonCreationResult(view = button)
    }

    override fun update(view: View, state: ButtonState) {
        // No state to update for hamburger button
    }

    private fun createButton(context: Context): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_menu_24)
            setColorFilter(Color.WHITE)
            background = StatusBarButtonStyles.createButtonDrawable()
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            // layoutParams will be set by VariationBarView for consistency
        }
    }
}
