package brobata.physiboard.core.pointer.awake

/** What the caller must do to the real wake lock about one event. spec: trackpad-caret-nav.md SS6. */
sealed interface TouchAwakeEffect {
    /** Nothing to do. */
    data object None : TouchAwakeEffect

    /**
     * Take the pulse for [timeoutMs] more milliseconds. [releaseFirst] is SS6's renewal row ("a new
     * down while the pulse is held releases it and acquires again"): the lock is not reference
     * counted, so a second acquire without that release would leave the first timeout running and
     * the pulse would end early, before 200 ms after the last touch.
     */
    data class Acquire(val timeoutMs: Long, val releaseFirst: Boolean) : TouchAwakeEffect

    /** Let the pulse go now, without waiting for its timeout. */
    data object Release : TouchAwakeEffect
}

/** How long the pulse still has to run, or nothing when it is not held. spec: trackpad-caret-nav.md SS6. */
data class TouchAwakeState(val heldUntilMs: Long? = null)

/** One event's answer: the new state and what to do to the wake lock. */
data class TouchAwakeResult(val state: TouchAwakeState, val effect: TouchAwakeEffect)

/**
 * Keeps the display awake while the strip is being touched.
 *
 * spec: trackpad-caret-nav.md SS6 ("Touches delivered to an input method window are not always
 * counted as user activity, so the screen could dim and lock while the user was tapping the
 * strip"). Every touch down on the keyboard's chrome layout takes a short screen-bright pulse; the
 * pulse always ends [PULSE_MS] after the last down, and the keyboard window going away drops it at
 * once.
 *
 * The decision is here, pure, so SS10's rows T74 to T77 can be driven on a fake clock; the real
 * `PowerManager` wake lock (tag [TAG], screen bright, on-after-release, not reference counted) is
 * the `:ime` adapter's, since a wake lock is a platform handle and this module holds no
 * `android.*` import. [pulseMs] is a parameter for exactly that reason: the spec's own test rows
 * use a 1000 ms pulse while the shipped value is [PULSE_MS].
 */
object TouchAwakePulse {

    /** spec SS6: "acquired with a 200 ms timeout". */
    const val PULSE_MS: Long = 200

    /** spec SS6: the wake lock tag, which shows up in a battery-blame dump under this exact name. */
    const val TAG: String = "PhysiBoard:ImeTouch"

    /**
     * One touch down on the chrome layout at [nowMs]. spec SS6: "every touch down (action down
     * only...) on the keyboard's chrome layout, that is the strip and everything drawn in the
     * keyboard window", and the renewal row.
     */
    fun onTouchDown(state: TouchAwakeState, nowMs: Long, pulseMs: Long = PULSE_MS): TouchAwakeResult =
        TouchAwakeResult(
            state = TouchAwakeState(heldUntilMs = nowMs + pulseMs),
            effect = TouchAwakeEffect.Acquire(timeoutMs = pulseMs, releaseFirst = isHeld(state, nowMs)),
        )

    /** spec SS6: "moves and ups do nothing". */
    fun onOtherTouchEvent(state: TouchAwakeState): TouchAwakeResult = TouchAwakeResult(state, TouchAwakeEffect.None)

    /**
     * The chrome layout leaving its window. spec SS6: "when the chrome layout is detached from its
     * window (keyboard window torn down), any held pulse is released at once".
     */
    fun onChromeDetached(state: TouchAwakeState, nowMs: Long): TouchAwakeResult =
        if (isHeld(state, nowMs)) TouchAwakeResult(TouchAwakeState(), TouchAwakeEffect.Release)
        else TouchAwakeResult(TouchAwakeState(), TouchAwakeEffect.None)

    /**
     * Whether the pulse is still running at [nowMs]. The timeout is exclusive, so a pulse taken at
     * 0 with a 1000 ms window is held at 999 and gone at 1000 (SS10 T74).
     */
    fun isHeld(state: TouchAwakeState, nowMs: Long): Boolean {
        val until = state.heldUntilMs ?: return false
        return nowMs < until
    }
}
