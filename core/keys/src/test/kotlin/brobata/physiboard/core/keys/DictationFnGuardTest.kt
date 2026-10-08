package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: dictation.md SS2, the Fn press that stops a session, replayed as the Titan delivers Fn. */
class DictationFnGuardTest {

    @Test
    fun `the hold that starts a session does not stop it on its next repeat`() {
        val g = DictationFnGuard()
        // Five repeats 50 ms apart with no session; the fifth starts one.
        for (t in listOf(400L, 450L, 500L, 550L)) assertFalse(g.onFnEvent(t, sessionActive = false))
        assertTrue(g.allowsStart(600L))
        assertFalse(g.onFnEvent(600L, sessionActive = false))
        // The hold goes on: its repeats are not a press.
        assertFalse(g.onFnEvent(650L, sessionActive = true))
        assertFalse(g.onFnEvent(700L, sessionActive = true))
    }

    @Test
    fun `a new press while listening stops, and nothing that press produces starts again, however the session ends`() {
        val g = DictationFnGuard()
        for (t in listOf(400L, 450L, 500L, 550L, 600L)) g.onFnEvent(t, sessionActive = false)
        // Released; 3 s later a press while the session runs.
        assertTrue(g.onFnEvent(3_600L, sessionActive = true))
        // Its repeats, with one gap wide enough to look like a new press (a busy keyboard), and
        // the session already ended underneath them.
        assertFalse(g.onFnEvent(3_650L, sessionActive = true))
        assertFalse(g.onFnEvent(3_900L, sessionActive = false))
        assertFalse(g.onFnEvent(3_950L, sessionActive = false))
        assertFalse(g.allowsStart(3_800L), "the burst of the stop press")
        assertFalse(g.allowsStart(4_200L), "0.6 s after the stop: still that press")
        assertTrue(g.allowsStart(5_100L), "a fresh hold 1.5 s later starts")
    }

    @Test
    fun `a session the app ended counts like a stop press for the press that follows`() {
        val g = DictationFnGuard()
        g.noteStoppedByOther(10_000L)
        assertFalse(g.onFnEvent(11_000L, sessionActive = false))
        assertFalse(g.allowsStart(11_200L), "the user meant to stop what had already stopped")
        assertTrue(g.allowsStart(11_600L))
    }
}
