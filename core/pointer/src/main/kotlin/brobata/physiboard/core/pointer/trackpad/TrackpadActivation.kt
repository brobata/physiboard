package brobata.physiboard.core.pointer.trackpad

import brobata.physiboard.core.pointer.OverlayAvailability

/** Whether the screen trackpad overlay is up, and how it got there. spec: trackpad-caret-nav.md SS2.3. */
enum class TrackpadPhase {
    /** No trigger activity in progress. */
    IDLE,

    /** Hold mode only: the trigger is down and its 250 ms timer has not yet fired or been cancelled. */
    PENDING,

    /** The overlay is open and closes the instant the trigger key comes back up. */
    ACTIVE_HOLD,

    /** The overlay is open and stays open until the trigger, Back, or the pill explicitly closes it. */
    ACTIVE_STICKY,

    /** Hold mode only: a chord was detected while pending; waiting for the trigger's real key-up, which then flows through untouched. */
    ABORTED,
}

/**
 * Everything the activation state machine carries between key events.
 *
 * [lastReleaseAtMs] and [lastReleaseKey] serve double-tap mode's own memory of "the first tap's
 * release" (trackpad-caret-nav.md SS2.3); they are meaningless in the other two modes but cost
 * nothing to carry.
 */
data class TrackpadActivationState(
    val phase: TrackpadPhase = TrackpadPhase.IDLE,
    val pendingSinceMs: Long? = null,
    val lastReleaseAtMs: Long? = null,
    val lastReleaseKey: TrackpadPhysicalKey? = null,
    /**
     * A trigger down just closed a sticky overlay and was swallowed, so its up is still to come:
     * that up is swallowed too (never an unpaired up for the app) and is not the first tap of a
     * new double tap. spec SS2.3, every swallowed down is paired or replayed.
     */
    val closingTapUpPending: Boolean = false,
)

/**
 * What the caller must do about one key event, beyond updating its own record of the state.
 *
 * A flag set means "do this"; several can be set together (spec SS2.3: the missing-permission row
 * both shows a toast and replays the trigger down).
 */
data class TrackpadActivationEffect(
    val openOverlayHold: Boolean = false,
    val openOverlaySticky: Boolean = false,
    val closeOverlay: Boolean = false,
    val replayTriggerDownAndUp: Boolean = false,
    val replayTriggerDownOnly: Boolean = false,
    val showPermissionToast: Boolean = false,
) {
    companion object {
        val NONE = TrackpadActivationEffect()
    }
}

/** The answer to one key event: the new state, whether the key is consumed, and what to do about the window. */
data class TrackpadActivationResult(
    val state: TrackpadActivationState,
    val consumed: Boolean,
    val effect: TrackpadActivationEffect = TrackpadActivationEffect.NONE,
)

/**
 * The screen trackpad's trigger-key state machine: decides, from key events alone, whether the
 * full-screen cursor overlay should be open, without knowing anything about fingers on the glass
 * (that is [TrackpadGesture]).
 *
 * spec: trackpad-caret-nav.md SS2.2 (which key), SS2.3 (hold/double-tap/single-tap timings and the
 * "timings in the hold flow" table, whose active-overlay rows this module treats as applying to
 * whichever mode reached [TrackpadPhase.ACTIVE_HOLD] or [TrackpadPhase.ACTIVE_STICKY], not only to
 * hold mode itself), SS2.7 (release). A caller is expected to run this ahead of the ordinary key
 * pipeline (SS2.2: "before everything else in the key pipeline") and to feed every key event
 * through exactly one of [onKeyDown]/[onKeyUp], plus [onHoldTimerFired] for the one real-time timer
 * this needs, using the same "arm on down, real clock schedules a tick" pattern `:core:keys`' own
 * long-press already establishes.
 */
object TrackpadActivation {

    /**
     * One key-down. [key] is null for a key this module has no opinion about identifying as the
     * trigger, Back, or a modifier candidate check subject; the caller still calls this for every
     * key-down so a chord during a pending hold is detected (SS2.3's "other key down" row).
     * [carriesDisqualifyingMeta] is SS2.2's "a Space down that already carries Ctrl or Alt in its
     * meta state is never a trigger": the caller passes true only for that combination.
     */
    fun onKeyDown(
        state: TrackpadActivationState,
        key: TrackpadPhysicalKey?,
        repeatCount: Int,
        timeMs: Long,
        carriesDisqualifyingMeta: Boolean,
        settings: TrackpadActivationSettings,
        overlayAvailability: OverlayAvailability,
    ): TrackpadActivationResult {
        if (key != null && TrackpadPhysicalKey.matchesTrigger(key, settings.triggerKey) && !carriesDisqualifyingMeta) {
            return onTriggerDown(state, key, repeatCount, timeMs, settings, overlayAvailability)
        }
        return when (key) {
            TrackpadPhysicalKey.BACK -> onBackDown(state)
            else -> onOtherKeyDown(state)
        }
    }

    /** One key-up. Same [key] convention as [onKeyDown]. */
    fun onKeyUp(
        state: TrackpadActivationState,
        key: TrackpadPhysicalKey?,
        timeMs: Long,
        settings: TrackpadActivationSettings,
    ): TrackpadActivationResult {
        if (key == null || !TrackpadPhysicalKey.matchesTrigger(key, settings.triggerKey)) {
            return TrackpadActivationResult(state, consumed = false)
        }
        return onTriggerUp(state, key, timeMs, settings)
    }

    /**
     * The hold timer firing at [nowMs]. A no-op (not an error) when the state has moved on since
     * the timer was armed, exactly like `:core:keys`' own long-press tick: the caller cancels
     * nothing, it just stops mattering. spec: SS2.3 ("If the timer fires, the overlay opens...").
     */
    fun onHoldTimerFired(
        state: TrackpadActivationState,
        nowMs: Long,
        settings: TrackpadActivationSettings,
        overlayAvailability: OverlayAvailability,
    ): TrackpadActivationResult {
        if (state.phase != TrackpadPhase.PENDING) return TrackpadActivationResult(state, consumed = false)
        val since = state.pendingSinceMs ?: return TrackpadActivationResult(state, consumed = false)
        if (nowMs - since < settings.holdThresholdMs) return TrackpadActivationResult(state, consumed = false)
        return if (overlayAvailability == OverlayAvailability.AVAILABLE) {
            TrackpadActivationResult(
                state.copy(phase = TrackpadPhase.ACTIVE_HOLD, pendingSinceMs = null),
                consumed = true,
                effect = TrackpadActivationEffect(openOverlayHold = true),
            )
        } else {
            TrackpadActivationResult(
                state.copy(phase = TrackpadPhase.ABORTED, pendingSinceMs = null),
                consumed = true,
                effect = TrackpadActivationEffect(showPermissionToast = true, replayTriggerDownOnly = true),
            )
        }
    }

    /** The real-time deadline [onHoldTimerFired] should be scheduled for, or null when no timer is pending. */
    fun pendingDeadlineMs(state: TrackpadActivationState, settings: TrackpadActivationSettings): Long? =
        state.pendingSinceMs?.takeIf { state.phase == TrackpadPhase.PENDING }?.plus(settings.holdThresholdMs)

    // -----------------------------------------------------------------------------------------

    private fun onTriggerDown(
        state: TrackpadActivationState,
        key: TrackpadPhysicalKey,
        repeatCount: Int,
        timeMs: Long,
        settings: TrackpadActivationSettings,
        overlayAvailability: OverlayAvailability,
    ): TrackpadActivationResult = when (state.phase) {
        TrackpadPhase.ACTIVE_HOLD -> TrackpadActivationResult(state, consumed = true)
        TrackpadPhase.ACTIVE_STICKY -> if (repeatCount == 0) {
            TrackpadActivationResult(TrackpadActivationState(closingTapUpPending = true), consumed = true, effect = TrackpadActivationEffect(closeOverlay = true))
        } else {
            TrackpadActivationResult(state, consumed = true)
        }
        TrackpadPhase.PENDING -> TrackpadActivationResult(state, consumed = true)
        TrackpadPhase.ABORTED -> TrackpadActivationResult(state, consumed = false)
        TrackpadPhase.IDLE -> onTriggerDownIdle(state, key, repeatCount, timeMs, settings, overlayAvailability)
    }

    private fun onTriggerDownIdle(
        state: TrackpadActivationState,
        key: TrackpadPhysicalKey,
        repeatCount: Int,
        timeMs: Long,
        settings: TrackpadActivationSettings,
        overlayAvailability: OverlayAvailability,
    ): TrackpadActivationResult = when (settings.activationMode) {
        ActivationMode.HOLD -> if (repeatCount == 0) {
            TrackpadActivationResult(state.copy(phase = TrackpadPhase.PENDING, pendingSinceMs = timeMs), consumed = true)
        } else {
            TrackpadActivationResult(state, consumed = false)
        }

        ActivationMode.SINGLE_TAP -> if (repeatCount == 0) {
            openSticky(overlayAvailability)
        } else {
            TrackpadActivationResult(state, consumed = true)
        }

        ActivationMode.DOUBLE_TAP -> {
            if (repeatCount != 0) return TrackpadActivationResult(state, consumed = false)
            val gap = state.lastReleaseAtMs?.let { timeMs - it }
            val isConsecutiveTap = state.lastReleaseKey == key && gap != null &&
                gap in settings.doubleTapMinGapMs..settings.doubleTapMaxGapMs
            if (isConsecutiveTap) {
                openSticky(overlayAvailability)
            } else {
                // spec T7/T9: "the first tap of the trigger is not consumed" (types normally); the
                // release that follows is what records the memory this checks next time.
                TrackpadActivationResult(state, consumed = false)
            }
        }
    }

    private fun openSticky(overlayAvailability: OverlayAvailability): TrackpadActivationResult = if (overlayAvailability == OverlayAvailability.AVAILABLE) {
        TrackpadActivationResult(TrackpadActivationState(phase = TrackpadPhase.ACTIVE_STICKY), consumed = true, effect = TrackpadActivationEffect(openOverlaySticky = true))
    } else {
        // SPEC GAP: SS2.3's missing-permission toast is written for the hold-timer-fires row only;
        // SS2.1 and SS2.9 make "the overlay cannot be added without the permission" a fact of the
        // whole feature, not one specific to hold mode, so the same toast-and-replay choice is
        // extended here rather than silently opening a window that will fail: the swallowed
        // trigger down is replayed so the key (Space, most often) still types.
        TrackpadActivationResult(idle(), consumed = true, effect = TrackpadActivationEffect(showPermissionToast = true, replayTriggerDownOnly = true))
    }

    private fun onTriggerUp(
        state: TrackpadActivationState,
        key: TrackpadPhysicalKey,
        timeMs: Long,
        settings: TrackpadActivationSettings,
    ): TrackpadActivationResult = when (state.phase) {
        TrackpadPhase.PENDING -> {
            val since = state.pendingSinceMs
            if (since != null && timeMs - since < settings.holdThresholdMs) {
                TrackpadActivationResult(idle(), consumed = true, effect = TrackpadActivationEffect(replayTriggerDownAndUp = true))
            } else {
                // The timer should have fired by now but has not (a late handler): nothing opened,
                // so the held key is replayed as a tap rather than vanishing.
                TrackpadActivationResult(idle(), consumed = true, effect = TrackpadActivationEffect(replayTriggerDownAndUp = true))
            }
        }
        TrackpadPhase.ACTIVE_HOLD -> TrackpadActivationResult(idle(), consumed = true, effect = TrackpadActivationEffect(closeOverlay = true))
        TrackpadPhase.ACTIVE_STICKY -> TrackpadActivationResult(state, consumed = true)
        TrackpadPhase.ABORTED -> TrackpadActivationResult(idle(), consumed = false)
        TrackpadPhase.IDLE -> when {
            state.closingTapUpPending -> TrackpadActivationResult(idle(), consumed = true)
            settings.activationMode == ActivationMode.DOUBLE_TAP ->
                TrackpadActivationResult(state.copy(lastReleaseAtMs = timeMs, lastReleaseKey = key), consumed = false)
            else -> TrackpadActivationResult(state, consumed = false)
        }
    }

    private fun onBackDown(state: TrackpadActivationState): TrackpadActivationResult = if (state.phase == TrackpadPhase.ACTIVE_STICKY) {
        TrackpadActivationResult(idle(), consumed = true, effect = TrackpadActivationEffect(closeOverlay = true))
    } else {
        TrackpadActivationResult(state, consumed = false)
    }

    /** spec SS2.3: "the pending trigger is treated as a chord"; SS2.6's "any other key while active" is the same not-consumed default. */
    private fun onOtherKeyDown(state: TrackpadActivationState): TrackpadActivationResult = if (state.phase == TrackpadPhase.PENDING) {
        TrackpadActivationResult(state.copy(phase = TrackpadPhase.ABORTED), consumed = false, effect = TrackpadActivationEffect(replayTriggerDownOnly = true))
    } else {
        TrackpadActivationResult(state, consumed = false)
    }

    private fun idle(): TrackpadActivationState = TrackpadActivationState()
}
