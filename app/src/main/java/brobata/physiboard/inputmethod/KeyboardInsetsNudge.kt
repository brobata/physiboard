package brobata.physiboard.inputmethod

import android.os.SystemClock

/**
 * Dips the suggestion strip out and back in so an app that positions its own text box will
 * notice the strip is there.
 *
 * Apps that use `adjustNothing` place their text box themselves from the keyboard inset, and
 * some only do so when the keyboard animates in or out, or when their window regains focus.
 * On a phone with a hardware keyboard the strip is usually already on screen when the user
 * taps a field. The app asks for the keyboard, the keyboard declines because there is nothing
 * more to show, no inset changes, and the app leaves its text box exactly where it was: under
 * the strip. Anything that changes window focus afterwards repairs it, which is why the fault
 * looks intermittent. Hiding and re-showing the strip gives the app the animation it waits
 * for, at the cost of a visible blink, so it is applied only to apps on a list.
 *
 * The hide has to reach the app as its own event: a re-show in the same frame is folded into
 * it by the window manager and the app sees nothing (measured on a Titan 2: a re-show 21 ms
 * after the hide left Teams' inset untouched). So the strip is held down for [RESHOW_DELAY_MS],
 * and [holdingHidden] lets the keyboard refuse its own re-show attempts in the meantime.
 */
class KeyboardInsetsNudge(
    private val isEnabledFor: (packageName: String?) -> Boolean,
    private val isStripShown: () -> Boolean,
    private val hideStrip: () -> Unit,
    private val showStrip: () -> Unit,
    private val postDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
    private val now: () -> Long = SystemClock::uptimeMillis
) {
    /** True from the hide until the re-show has had a moment to land; callers skip hide side effects meanwhile. */
    var inFlight: Boolean = false
        private set

    /** True while the strip must stay down: any other request to show it is dropped. */
    var holdingHidden: Boolean = false
        private set

    private var lastNudgeAt = Long.MIN_VALUE / 2

    /**
     * The app asked for the keyboard and the keyboard declined. Returns true when a nudge was
     * started. Requests arrive in pairs (the app's own and the system's on attach) and some
     * apps ask again on every focus change, so a cool-down keeps this to one blink per tap.
     */
    fun onShowRequestRefused(packageName: String?): Boolean {
        if (inFlight) return false
        if (!isEnabledFor(packageName)) return false
        if (!isStripShown()) return false
        val at = now()
        if (at - lastNudgeAt < COOLDOWN_MS) return false
        lastNudgeAt = at
        inFlight = true
        holdingHidden = true
        hideStrip()
        postDelayed(RESHOW_DELAY_MS) {
            holdingHidden = false
            showStrip()
            postDelayed(SETTLE_MS) { inFlight = false }
        }
        return true
    }

    companion object {
        /** Long enough for the hide to be its own animation rather than folded into the show. */
        const val RESHOW_DELAY_MS = 200L
        /** How long after the re-show the window callbacks are still treated as part of the nudge. */
        const val SETTLE_MS = 300L
        const val COOLDOWN_MS = 1500L
    }
}
