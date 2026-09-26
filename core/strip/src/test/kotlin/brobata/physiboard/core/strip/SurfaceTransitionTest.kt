package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: status-bar.md T40 (and the general SS3.2/SS14 retry rule the gap analysis found missing). */
class SurfaceTransitionTest {

    @Test
    fun `a hide request is always satisfied, never retried`() {
        val (state, outcome) = SurfaceTransitionRetry.onCheck(SurfaceTransitionState(), requestedShown = false, actuallyRendered = false)
        assertEquals(SurfaceTransitionOutcome.SATISFIED, outcome)
        assertEquals(SurfaceTransitionState(), state)
    }

    @Test
    fun `a show request already rendered is satisfied`() {
        val (_, outcome) = SurfaceTransitionRetry.onCheck(SurfaceTransitionState(), requestedShown = true, actuallyRendered = true)
        assertEquals(SurfaceTransitionOutcome.SATISFIED, outcome)
    }

    @Test
    fun `T40 - not rendered retries six times then abandons`() {
        var state = SurfaceTransitionState()
        repeat(SurfaceTransitionRetry.MAX_ATTEMPTS) {
            val (next, outcome) = SurfaceTransitionRetry.onCheck(state, requestedShown = true, actuallyRendered = false)
            assertEquals(SurfaceTransitionOutcome.RETRY, outcome)
            state = next
        }
        assertEquals(SurfaceTransitionRetry.MAX_ATTEMPTS, state.attemptsMade)
        val (finalState, finalOutcome) = SurfaceTransitionRetry.onCheck(state, requestedShown = true, actuallyRendered = false)
        assertEquals(SurfaceTransitionOutcome.ABANDONED, finalOutcome)
        assertEquals(state, finalState)
    }

    @Test
    fun `rendering succeeding mid-retry stops the retries`() {
        val (afterOneRetry, outcome1) = SurfaceTransitionRetry.onCheck(SurfaceTransitionState(), requestedShown = true, actuallyRendered = false)
        assertEquals(SurfaceTransitionOutcome.RETRY, outcome1)
        val (_, outcome2) = SurfaceTransitionRetry.onCheck(afterOneRetry, requestedShown = true, actuallyRendered = true)
        assertEquals(SurfaceTransitionOutcome.SATISFIED, outcome2)
    }
}
