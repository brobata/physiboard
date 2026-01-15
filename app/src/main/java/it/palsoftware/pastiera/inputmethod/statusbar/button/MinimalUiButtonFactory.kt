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
 * Factory for creating the minimal UI (pastierina) toggle button.
 */
class MinimalUiButtonFactory : StatusBarButtonFactory {

    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = ImageView(context).apply {
            setImageResource(R.drawable.ic_minimal_ui_24)
            setColorFilter(Color.WHITE)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener {
                callbacks.onHapticFeedback?.invoke()
                callbacks.onMinimalUiToggleRequested?.invoke()
            }
        }
        button.setTag(R.id.tag_minimal_ui_state, MinimalUiStateHolder(baseHeightPx = size))
        return ButtonCreationResult(view = button)
    }

    override fun update(view: View, state: ButtonState) {
        if (state !is ButtonState.MinimalUiState) return
        val button = view as? ImageView ?: return
        val stateHolder = view.getTag(R.id.tag_minimal_ui_state) as? MinimalUiStateHolder ?: return
        stateHolder.isActive = state.isActive

        val heightPx = if (button.height > 0) button.height else stateHolder.baseHeightPx
        val background = if (state.isActive) {
            StatusBarButtonStyles.createButtonDrawable(
                heightPx,
                normalColor = StatusBarButtonStyles.PRESSED_BLUE,
                pressedColor = StatusBarButtonStyles.PRESSED_BLUE
            )
        } else {
            StatusBarButtonStyles.createButtonDrawable(heightPx)
        }
        button.background = background
        button.rotation = if (state.isActive) 180f else 0f
    }

    private data class MinimalUiStateHolder(
        val baseHeightPx: Int,
        var isActive: Boolean = false
    )
}
