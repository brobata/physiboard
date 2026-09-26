package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: status-bar.md SS10, SS14, T38. */
class BacklightNudgeTest {

    @Test
    fun `T38 pill still shown at t=100, collapses to dot by t=400 on a refresh`() {
        assertFalse(BacklightNudge.collapsesOnRefresh(shownAtMs = 0, nowMs = 100))
        assertTrue(BacklightNudge.collapsesOnRefresh(shownAtMs = 0, nowMs = 400))
    }

    @Test
    fun `T38 with no refresh, the dot appears on its own clock at 4000 ms`() {
        assertFalse(BacklightNudge.collapsesAfterTimeout(shownAtMs = 0, nowMs = 3999))
        assertTrue(BacklightNudge.collapsesAfterTimeout(shownAtMs = 0, nowMs = 4000))
    }

    @Test
    fun `SS10 visibility follows enabled, applied, dismissed and collapsed`() {
        assertEquals(BacklightNudgeVisibility.HIDDEN, BacklightNudge.visibility(enabled = false, applied = false, dismissedByUser = false, collapsedToDot = false))
        assertEquals(BacklightNudgeVisibility.HIDDEN, BacklightNudge.visibility(enabled = true, applied = true, dismissedByUser = false, collapsedToDot = false))
        assertEquals(BacklightNudgeVisibility.HIDDEN, BacklightNudge.visibility(enabled = true, applied = false, dismissedByUser = true, collapsedToDot = false))
        assertEquals(BacklightNudgeVisibility.PILL, BacklightNudge.visibility(enabled = true, applied = false, dismissedByUser = false, collapsedToDot = false))
        assertEquals(BacklightNudgeVisibility.DOT, BacklightNudge.visibility(enabled = true, applied = false, dismissedByUser = false, collapsedToDot = true))
    }

    @Test
    fun `T38 an episode starts as a pill, then collapses to a dot on a refresh past 350ms`() {
        val (afterFirst, first) = BacklightNudgeEpisode.onRefresh(BacklightNudgeMemory(), enabled = true, applied = false, nowMs = 0)
        assertEquals(BacklightNudgeVisibility.PILL, first)
        val (afterSecond, second) = BacklightNudgeEpisode.onRefresh(afterFirst, enabled = true, applied = false, nowMs = 100)
        assertEquals(BacklightNudgeVisibility.PILL, second, "T38: still a pill at t=100")
        val (_, third) = BacklightNudgeEpisode.onRefresh(afterSecond, enabled = true, applied = false, nowMs = 400)
        assertEquals(BacklightNudgeVisibility.DOT, third, "T38: a dot by t=400")
    }

    @Test
    fun `T38 with no refresh the pill's own timeout collapses it to a dot`() {
        val (shown, _) = BacklightNudgeEpisode.onRefresh(BacklightNudgeMemory(), enabled = true, applied = false, nowMs = 0)
        val collapsed = BacklightNudgeEpisode.onTimeoutElapsed(shown)
        assertTrue(collapsed.collapsedToDot)
    }

    @Test
    fun `applying the backlight or disabling it forgets the whole episode`() {
        val (shown, _) = BacklightNudgeEpisode.onRefresh(BacklightNudgeMemory(), enabled = true, applied = false, nowMs = 0)
        val (afterApplied, visibility) = BacklightNudgeEpisode.onRefresh(shown, enabled = true, applied = true, nowMs = 100)
        assertEquals(BacklightNudgeVisibility.HIDDEN, visibility)
        assertEquals(BacklightNudgeMemory(), afterApplied)
    }

    @Test
    fun `dismissing the pill hides it until the next eligible episode`() {
        val (shown, _) = BacklightNudgeEpisode.onRefresh(BacklightNudgeMemory(), enabled = true, applied = false, nowMs = 0)
        val dismissed = BacklightNudgeEpisode.onDismissed(shown)
        val (_, visibility) = BacklightNudgeEpisode.onRefresh(dismissed, enabled = true, applied = false, nowMs = 50)
        assertEquals(BacklightNudgeVisibility.HIDDEN, visibility)
    }
}
