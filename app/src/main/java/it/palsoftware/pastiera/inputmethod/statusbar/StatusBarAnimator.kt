package it.palsoftware.pastiera.inputmethod.statusbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Handles animations for status bar elements.
 * 
 * Provides standardized fade-in/fade-out animations for variations row
 * and other status bar components.
 */
class StatusBarAnimator {
    
    companion object {
        private const val FADE_IN_DURATION_MS = 75L
        private const val FADE_OUT_DURATION_MS = 50L
    }
    
    private var currentAnimator: ValueAnimator? = null
    
    /**
     * Animates a view fading in (alpha 0 -> 1).
     * 
     * @param view The view to animate
     * @param onComplete Optional callback when animation completes
     */
    fun animateIn(view: View, onComplete: (() -> Unit)? = null) {
        // Cancel any running animation
        currentAnimator?.cancel()
        
        view.alpha = 0f
        view.visibility = View.VISIBLE
        
        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FADE_IN_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.alpha = 1f
                    currentAnimator = null
                    onComplete?.invoke()
                }
            })
        }
        currentAnimator?.start()
    }
    
    /**
     * Animates a view fading out (alpha 1 -> 0).
     * 
     * @param view The view to animate
     * @param hideAfter If true, sets visibility to GONE after animation
     * @param onComplete Optional callback when animation completes
     */
    fun animateOut(view: View, hideAfter: Boolean = true, onComplete: (() -> Unit)? = null) {
        // Cancel any running animation
        currentAnimator?.cancel()
        
        currentAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = FADE_OUT_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (hideAfter) {
                        view.visibility = View.GONE
                    }
                    view.alpha = 1f
                    currentAnimator = null
                    onComplete?.invoke()
                }
            })
        }
        currentAnimator?.start()
    }
    
    /**
     * Immediately shows a view without animation.
     * 
     * @param view The view to show
     */
    fun showImmediate(view: View) {
        currentAnimator?.cancel()
        currentAnimator = null
        view.alpha = 1f
        view.visibility = View.VISIBLE
    }
    
    /**
     * Immediately hides a view without animation.
     * 
     * @param view The view to hide
     */
    fun hideImmediate(view: View) {
        currentAnimator?.cancel()
        currentAnimator = null
        view.visibility = View.GONE
        view.alpha = 1f
    }
    
    /**
     * Cancels any running animation.
     */
    fun cancel() {
        currentAnimator?.cancel()
        currentAnimator = null
    }
    
    /**
     * Checks if an animation is currently running.
     */
    fun isAnimating(): Boolean = currentAnimator?.isRunning == true
}
