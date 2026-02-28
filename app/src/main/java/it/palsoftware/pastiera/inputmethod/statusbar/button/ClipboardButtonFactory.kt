package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Factory for creating the clipboard button with badge and flash animation.
 */
class ClipboardButtonFactory : StatusBarButtonFactory {
    
    companion object {
        private const val FLASH_DURATION_MS = 350L
        
        // Tag keys for storing auxiliary views
        private const val TAG_BADGE = "clipboard_badge"
        private const val TAG_FLASH_OVERLAY = "clipboard_flash"
        private const val TAG_ANIMATOR = "clipboard_animator"
    }
    
    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        val badge = createBadge(context)
        val flashOverlay = createFlashOverlay(context, size)
        
        // Set up click listener using the clipboard-specific callback
        button.setOnClickListener {
            callbacks.onHapticFeedback?.invoke()
            callbacks.onClipboardRequested?.invoke()
        }
        
        // Store references in tags for later updates
        button.setTag(R.id.tag_badge_view, badge)
        button.setTag(R.id.tag_flash_overlay, flashOverlay)
        
        return ButtonCreationResult(
            view = button,
            badgeView = badge,
            flashOverlayView = flashOverlay
        )
    }
    
    override fun update(view: View, state: ButtonState) {
        if (state !is ButtonState.ClipboardState) return
        
        val badge = view.getTag(R.id.tag_badge_view) as? TextView ?: return
        val flashOverlay = view.getTag(R.id.tag_flash_overlay) as? View
        val previousCount = view.getTag(R.id.tag_previous_count) as? Int
        
        updateBadge(badge, state.itemCount)
        
        // Flash when count increases
        if (previousCount != null && state.itemCount > 0 && state.itemCount != previousCount && flashOverlay != null) {
            flashButton(view, flashOverlay)
        }
        
        view.setTag(R.id.tag_previous_count, state.itemCount)
    }
    
    override fun cleanup(view: View) {
        // Cancel any running animation
        val animator = view.getTag(R.id.tag_animator) as? ValueAnimator
        animator?.cancel()
        view.setTag(R.id.tag_animator, null)
    }
    
    private fun createButton(context: Context, size: Int): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_content_paste_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.status_bar_button_clipboard_description)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            // layoutParams will be set by VariationBarView for consistency
        }
    }
    
    private fun createBadge(context: Context): TextView {
        val padding = dpToPx(context, 2f)
        return TextView(context).apply {
            background = null
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            minWidth = 0
            minHeight = 0
            visibility = View.GONE
        }
    }
    
    private fun createFlashOverlay(context: Context, size: Int): View {
        return View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.RED)
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
    }
    
    private fun updateBadge(badge: TextView, count: Int) {
        if (count <= 0) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = count.toString()
    }
    
    private fun flashButton(button: View, overlay: View) {
        // Cancel any existing animation
        val existingAnimator = button.getTag(R.id.tag_animator) as? ValueAnimator
        existingAnimator?.cancel()
        
        overlay.visibility = View.VISIBLE
        val animator = ValueAnimator.ofFloat(0f, 0.4f, 0f).apply {
            duration = FLASH_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                overlay.alpha = valueAnimator.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlay.alpha = 0f
                    overlay.visibility = View.GONE
                }
            })
        }
        button.setTag(R.id.tag_animator, animator)
        animator.start()
    }
    
    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
