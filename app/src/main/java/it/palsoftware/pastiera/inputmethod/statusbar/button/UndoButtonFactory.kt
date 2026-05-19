package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks

class UndoButtonFactory(
    private val isRedo: Boolean
) : StatusBarButtonFactory {

    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = ImageView(context).apply {
            setImageResource(if (isRedo) R.drawable.ic_redo_24 else R.drawable.ic_undo_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(
                if (isRedo) R.string.status_bar_button_redo_description else R.string.status_bar_button_undo_description
            )
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
        }
        button.setOnClickListener {
            callbacks.onHapticFeedback?.invoke()
            if (isRedo) {
                callbacks.onRedoRequested?.invoke()
            } else {
                callbacks.onUndoRequested?.invoke()
            }
        }
        return ButtonCreationResult(view = button)
    }

    override fun update(view: View, state: ButtonState) {}
}
