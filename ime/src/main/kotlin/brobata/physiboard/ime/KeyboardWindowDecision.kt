package brobata.physiboard.ime

import android.os.Handler
import brobata.physiboard.core.strip.StripVisibility

/**
 * The keyboard window's per-field decision, posted for the next UI turn, and the leave-the-field
 * handling that stands in for a window hide when no window is up. spec: status-bar.md SS3.2
 * ("a later evaluation cancels any earlier posted one that has not run yet", T39), SS3.4 ("No
 * strip, no window") and SS13 (T44).
 *
 * [request] posts the decision under [token], cancelling anything still posted under it, so a
 * field that finishes and another that starts in the same turn never see the leave. When a
 * decision runs, [apply] carries it out (the real `setCandidatesViewShown` and its safety net);
 * if it leaves an editable field with no window of the keyboard's own up, [leftField] then runs
 * the window-hidden handling, since Android has no window to hide and reports no hide. A window
 * that is up reports its own hide through [onWindowHidden]. The main thread owns this.
 */
internal class KeyboardWindowDecision(
    private val handler: Handler,
    private val token: Any,
    private val apply: (shown: Boolean) -> Unit,
    private val leftField: () -> Unit,
) {
    /** Whether Android has the keyboard's own window up, from its shown and hidden callbacks. */
    var windowShown: Boolean = false
        private set

    /** Whether the last decision that ran had an editable field open. */
    private var fieldOpen = false

    /** Posts a decision: show the window or not, and whether an editable field is open (where the window used to be up). */
    fun request(shown: Boolean, fieldOpen: Boolean) {
        handler.removeCallbacksAndMessages(token)
        handler.postDelayed({ perform(shown, fieldOpen) }, token, 0L)
    }

    private fun perform(shown: Boolean, nowOpen: Boolean) {
        // Read before [apply]: a window that is up hides inside it and reports that itself.
        val left = StripVisibility.leavesFieldWithoutWindow(fieldOpen, nowOpen, windowShown)
        apply(shown)
        if (left) leftField()
        fieldOpen = nowOpen
    }

    fun onWindowShown() {
        windowShown = true
    }

    /**
     * The window went, really or stood in for. When no editable field is open any more
     * ([fieldStillOpen] false) that was the field's leave, and the next decision is none. A hide
     * while the field is still open (the app hid the keyboard, or the inline suggestions' window
     * went) is not the leave: the field's leave still runs when it comes.
     */
    fun onWindowHidden(fieldStillOpen: Boolean) {
        windowShown = false
        if (!fieldStillOpen) fieldOpen = false
    }
}
