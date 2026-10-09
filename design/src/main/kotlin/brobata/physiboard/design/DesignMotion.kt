package brobata.physiboard.design

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import brobata.physiboard.design.DesignTokens.Motion

/**
 * The motion every keyboard panel shares (docs/design/design-system.md, "Motion"): a spring up
 * into place on open, a short drop and fade on close, a fade when a panel is swapped for another
 * in place, and a press that dips a key and springs it back. Each one checks [reduced] first and,
 * when the system says no animation, lands the view in its final state at once.
 */
object DesignMotion {

    enum class Enter { SPRING, FADE, NONE }

    /** True when the system's animator duration scale is 0 (Remove animations, or battery saver's). */
    fun reduced(context: Context): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return scale == 0f || !ValueAnimator.areAnimatorsEnabled()
    }

    private fun dp(view: View, value: Int): Float = value * view.resources.displayMetrics.density

    /** Brings a just-attached panel into place. */
    fun enter(view: View, style: Enter) {
        if (style == Enter.NONE || reduced(view.context)) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        if (style == Enter.FADE) {
            view.animate().alpha(1f).setDuration(Motion.SWAP_FADE_MS).setInterpolator(DecelerateInterpolator()).start()
            return
        }
        view.translationY = dp(view, Motion.OPEN_OFFSET_DP)
        view.animate().alpha(1f).setDuration(Motion.OPEN_FADE_MS).setInterpolator(DecelerateInterpolator()).start()
        SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = Motion.OPEN_STIFFNESS
            spring.dampingRatio = Motion.OPEN_DAMPING_RATIO
            start()
        }
    }

    /**
     * Takes a panel away, then calls [onGone] (where the caller removes its window). A view not
     * attached yet (added this frame) has nothing to animate: [onGone] runs at once.
     */
    fun exit(view: View, onGone: () -> Unit) {
        if (reduced(view.context) || !view.isAttachedToWindow) {
            onGone()
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(view.translationY + dp(view, Motion.CLOSE_OFFSET_DP))
            .setDuration(Motion.CLOSE_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction(onGone)
            .start()
    }

    /**
     * A key that dips under the finger and springs back when it lifts. The listener never
     * consumes the touch, so clicks, long presses and scrolling behave exactly as before.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(view: View) {
        // The system setting is read once per touch, on the way down, not on every move.
        var pressed = false
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = !reduced(v.context)
                    if (pressed) v.animate().scaleX(Motion.PRESS_SCALE).scaleY(Motion.PRESS_SCALE).setDuration(Motion.PRESS_IN_MS).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pressed) {
                    pressed = false
                    release(v)
                }
            }
            false
        }
    }

    private fun release(view: View) {
        view.animate().cancel()
        for (property in listOf(DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y)) {
            SpringAnimation(view, property, 1f).apply {
                spring.stiffness = Motion.RELEASE_STIFFNESS
                spring.dampingRatio = Motion.RELEASE_DAMPING_RATIO
                start()
            }
        }
    }
}
