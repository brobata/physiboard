package brobata.physiboard.core.strip

/** How many times [SurfaceTransitionRetry] has re-checked one show request. spec: status-bar.md SS3.2, SS14. */
data class SurfaceTransitionState(val attemptsMade: Int = 0)

/** What `:ime` should do after one rendered-on-screen check. spec: status-bar.md SS3.2, T40. */
enum class SurfaceTransitionOutcome { SATISFIED, RETRY, ABANDONED }

/**
 * The surface-transition retry safety net: spec status-bar.md SS3.2, "PhysiBoard posts, on the
 * next UI turn, 'candidates surface active, candidates view shown', and one turn after that
 * forces the enclosing container back to visible, because Android can leave that container
 * invisible when an already-open window switches to candidates-only mode." SS14 and T40 give the
 * retry its numbers: up to [MAX_ATTEMPTS] checks, [INTERVAL_MS] apart, before the transition is
 * abandoned and "the requested-shown state set to what is actually rendered" rather than staying
 * stuck believing the strip is up when it is not.
 *
 * This type answers only "should `:ime` check again, and how many times has it already asked";
 * the stale-post cancellation half of SS3.2 ("a later evaluation cancels any earlier posted one
 * that has not run yet", T39) is realized with the same token-based `Handler` cancellation this
 * codebase already uses for the cursor-update retries (`KeyboardSession.cursorUpdateToken`), so it
 * is not modelled again here as a second, redundant pure type.
 */
object SurfaceTransitionRetry {
    /** spec SS14: "Surface transition retry interval, attempts | 250 ms, 6". */
    const val INTERVAL_MS: Long = 250
    const val MAX_ATTEMPTS: Int = 6

    /**
     * One rendered-on-screen check for a show request (a hide request never retries: SS3.2's
     * container-visibility fix only applies to a transition to shown). [actuallyRendered] is
     * [brobata.physiboard.core.strip]'s own name for SS12.2's "attached, visible, non-zero size,
     * non-empty visible rectangle" test, already implemented for the dip.
     */
    fun onCheck(state: SurfaceTransitionState, requestedShown: Boolean, actuallyRendered: Boolean): Pair<SurfaceTransitionState, SurfaceTransitionOutcome> {
        if (!requestedShown || actuallyRendered) return state to SurfaceTransitionOutcome.SATISFIED
        if (state.attemptsMade >= MAX_ATTEMPTS) return state to SurfaceTransitionOutcome.ABANDONED
        return state.copy(attemptsMade = state.attemptsMade + 1) to SurfaceTransitionOutcome.RETRY
    }
}
