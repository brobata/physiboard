package brobata.physiboard.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strip dips out and back in for apps that leave their text box underneath it. Seen in
 * Teams on a Titan 2: the app resets its layout when a field is tapped and then waits for a
 * keyboard animation that a hardware-keyboard IME never produces. The dip must happen once per
 * tap, only for listed apps, only while the strip is actually up, and the window-hidden
 * callback in between must be recognisable as part of the dip.
 */
class KeyboardInsetsNudgeTest {

    private class Harness(
        enabled: Set<String> = setOf("com.microsoft.teams"),
        var stripShown: Boolean = true
    ) {
        var clock = 10_000L
        val events = mutableListOf<String>()
        private val pending = mutableListOf<Pair<Long, () -> Unit>>()

        val nudge = KeyboardInsetsNudge(
            isEnabledFor = { it in enabled },
            isStripShown = { stripShown },
            hideStrip = { events += "hide" },
            showStrip = { events += "show" },
            postDelayed = { delay, action -> pending += (clock + delay) to action },
            now = { clock }
        )

        /** Advances time, running whatever falls due, each action at its own due time. */
        fun advance(ms: Long) {
            val target = clock + ms
            while (true) {
                val next = pending.filter { it.first <= target }.minByOrNull { it.first } ?: break
                pending.remove(next)
                clock = next.first
                next.second()
            }
            clock = target
        }
    }

    @Test
    fun `hides then re-shows the strip for a listed app`() {
        val h = Harness()
        assertTrue(h.nudge.onShowRequestRefused("com.microsoft.teams"))
        assertEquals(listOf("hide"), h.events)
        h.advance(KeyboardInsetsNudge.RESHOW_DELAY_MS)
        assertEquals(listOf("hide", "show"), h.events)
    }

    @Test
    fun `holds the strip down until it re-shows it itself`() {
        val h = Harness()
        assertFalse(h.nudge.holdingHidden)
        h.nudge.onShowRequestRefused("com.microsoft.teams")
        assertTrue("other re-shows must be refused while the hide propagates", h.nudge.holdingHidden)
        h.advance(KeyboardInsetsNudge.RESHOW_DELAY_MS - 1)
        assertTrue(h.nudge.holdingHidden)
        h.advance(1)
        assertFalse(h.nudge.holdingHidden)
        assertEquals(listOf("hide", "show"), h.events)
    }

    @Test
    fun `is in flight from the hide until the re-show has settled`() {
        val h = Harness()
        assertFalse(h.nudge.inFlight)
        h.nudge.onShowRequestRefused("com.microsoft.teams")
        assertTrue(h.nudge.inFlight)
        h.advance(KeyboardInsetsNudge.RESHOW_DELAY_MS)
        assertTrue("still in flight right after the re-show", h.nudge.inFlight)
        h.advance(KeyboardInsetsNudge.SETTLE_MS)
        assertFalse(h.nudge.inFlight)
    }

    @Test
    fun `leaves apps off the list alone`() {
        val h = Harness()
        assertFalse(h.nudge.onShowRequestRefused("com.whatsapp"))
        assertFalse(h.nudge.onShowRequestRefused(null))
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun `does nothing when the strip is not on screen`() {
        val h = Harness(stripShown = false)
        assertFalse(h.nudge.onShowRequestRefused("com.microsoft.teams"))
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun `the paired requests of one tap produce one dip`() {
        val h = Harness()
        assertTrue(h.nudge.onShowRequestRefused("com.microsoft.teams"))
        h.advance(80)
        assertFalse(h.nudge.onShowRequestRefused("com.microsoft.teams"))
        h.advance(KeyboardInsetsNudge.RESHOW_DELAY_MS + KeyboardInsetsNudge.SETTLE_MS)
        assertFalse("inside the cool-down", h.nudge.onShowRequestRefused("com.microsoft.teams"))
        assertEquals(listOf("hide", "show"), h.events)
    }

    @Test
    fun `a later tap gets its own dip`() {
        val h = Harness()
        h.nudge.onShowRequestRefused("com.microsoft.teams")
        h.advance(KeyboardInsetsNudge.COOLDOWN_MS)
        assertTrue(h.nudge.onShowRequestRefused("com.microsoft.teams"))
        h.advance(KeyboardInsetsNudge.RESHOW_DELAY_MS)
        assertEquals(listOf("hide", "show", "hide", "show"), h.events)
    }
}
