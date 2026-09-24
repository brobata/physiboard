package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: status-bar.md SS12, SS13, SS18 rows T8 to T14, one test per row plus the sequence in full. */
class StripDipTest {

    private val teams = "com.microsoft.teams"

    /**
     * A tiny timeline: refusals and timer ticks at absolute instants, collecting the effects the
     * way `:ime` would apply them to the real candidates view.
     */
    private class Timeline(private val listed: Set<String> = StripDip.SEEDED_APPS) {
        var state = DipState()
            private set
        val events = mutableListOf<DipEffect>()

        fun refuse(atMs: Long, packageName: String? = "com.microsoft.teams", rendered: Boolean = true, configChange: Boolean = false): Boolean {
            val decision = StripDip.onShowRefused(state, atMs, packageName, listed, rendered, configChange)
            state = decision.state
            events += decision.effects
            return decision.started
        }

        fun tick(atMs: Long) {
            val (next, effects) = StripDip.onHoldElapsed(state, atMs)
            state = next
            events += effects
        }
    }

    @Test
    fun `T8 a listed app with the strip shown hides at once and re-shows at 200 ms`() {
        val timeline = Timeline()
        assertTrue(timeline.refuse(atMs = 0))
        assertEquals(listOf(DipEffect.HIDE_STRIP), timeline.events)
        assertEquals(200L, StripDip.reshowDueAtMs(timeline.state))
        timeline.tick(atMs = 199)
        assertEquals(listOf(DipEffect.HIDE_STRIP), timeline.events, "a timer that fires early must not re-show")
        timeline.tick(atMs = 200)
        assertEquals(listOf(DipEffect.HIDE_STRIP, DipEffect.SHOW_STRIP), timeline.events)
        timeline.tick(atMs = 250)
        assertEquals(listOf(DipEffect.HIDE_STRIP, DipEffect.SHOW_STRIP), timeline.events, "a second tick never re-shows twice")
        assertNull(StripDip.reshowDueAtMs(timeline.state))
    }

    @Test
    fun `T9 the hold is on from the refusal until 200 ms exclusive`() {
        val timeline = Timeline()
        timeline.refuse(atMs = 0)
        assertTrue(StripDip.isHolding(timeline.state, 0))
        assertTrue(StripDip.isHolding(timeline.state, 199))
        assertFalse(StripDip.isHolding(timeline.state, 200))
        assertTrue(StripDip.refusesShowRequest(timeline.state, 100), "PhysiBoard's own re-show is refused during the hold")
        assertFalse(StripDip.refusesShowRequest(timeline.state, 200))
    }

    @Test
    fun `T10 the dip is in flight from the refusal, still at 200 ms, over at 500 ms`() {
        val timeline = Timeline()
        timeline.refuse(atMs = 0)
        assertTrue(StripDip.isInFlight(timeline.state, 0))
        assertTrue(StripDip.isInFlight(timeline.state, 200))
        assertTrue(StripDip.isInFlight(timeline.state, 499))
        assertFalse(StripDip.isInFlight(timeline.state, 500))
        assertTrue(StripDip.skipsWindowHidden(timeline.state, 450), "SS13: window-hidden is skipped while in flight")
        assertFalse(StripDip.skipsWindowHidden(timeline.state, 500))
    }

    @Test
    fun `T11 an unlisted app or a null package never dips`() {
        val timeline = Timeline()
        assertFalse(timeline.refuse(atMs = 0, packageName = "com.example.other"))
        assertFalse(timeline.refuse(atMs = 0, packageName = null))
        assertTrue(timeline.events.isEmpty())
        assertFalse(StripDip.isInFlight(timeline.state, 0))
    }

    @Test
    fun `T12 a strip that is not rendered on screen never dips`() {
        val timeline = Timeline()
        assertFalse(timeline.refuse(atMs = 0, rendered = false))
        assertTrue(timeline.events.isEmpty())
    }

    @Test
    fun `T13 a second refusal at 80 ms and a third at 500 ms are both swallowed`() {
        val timeline = Timeline()
        assertTrue(timeline.refuse(atMs = 0))
        timeline.tick(atMs = 200)
        assertFalse(timeline.refuse(atMs = 80), "in flight")
        assertFalse(timeline.refuse(atMs = 500), "cool-down")
        assertEquals(listOf(DipEffect.HIDE_STRIP, DipEffect.SHOW_STRIP), timeline.events)
    }

    @Test
    fun `T14 a second refusal at 1500 ms dips again`() {
        val timeline = Timeline()
        assertTrue(timeline.refuse(atMs = 0))
        timeline.tick(atMs = 200)
        assertTrue(timeline.refuse(atMs = 1500))
        timeline.tick(atMs = 1700)
        assertEquals(listOf(DipEffect.HIDE_STRIP, DipEffect.SHOW_STRIP, DipEffect.HIDE_STRIP, DipEffect.SHOW_STRIP), timeline.events)
    }

    @Test
    fun `SS17 a configuration change never triggers a dip`() {
        val timeline = Timeline()
        assertFalse(timeline.refuse(atMs = 0, configChange = true))
        assertTrue(timeline.events.isEmpty())
    }

    @Test
    fun `SS12 a dip list with another app dips for that app`() {
        val timeline = Timeline(listed = setOf("com.example.chat"))
        assertFalse(timeline.refuse(atMs = 0, packageName = teams))
        assertTrue(timeline.refuse(atMs = 0, packageName = "com.example.chat"))
    }

    @Test
    fun `SS14 the timings are the spec's numbers`() {
        assertEquals(200L, StripDip.HOLD_MS)
        assertEquals(300L, StripDip.SETTLE_MS)
        assertEquals(500L, StripDip.IN_FLIGHT_MS)
        assertEquals(1500L, StripDip.COOLDOWN_MS)
        assertEquals(setOf(teams), StripDip.SEEDED_APPS)
    }

    @Test
    fun `before any dip nothing holds, nothing is in flight`() {
        val state = DipState()
        assertFalse(StripDip.isHolding(state, 0))
        assertFalse(StripDip.isInFlight(state, 0))
        assertNull(StripDip.reshowDueAtMs(state))
        val (unchanged, effects) = StripDip.onHoldElapsed(state, 1000)
        assertEquals(state, unchanged)
        assertTrue(effects.isEmpty())
    }
}
