package brobata.physiboard.core.pointer.awake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The touch-awake pulse against trackpad-caret-nav.md SS6 and SS10's rows T74 to T77, which use a
 * 1000 ms pulse rather than the shipped 200 ms one.
 */
class TouchAwakePulseTest {

    private val testPulseMs = 1000L

    @Test
    fun `T74 a touch down holds the pulse at 999 ms and has let go at 1001 ms`() {
        val result = TouchAwakePulse.onTouchDown(TouchAwakeState(), nowMs = 0, pulseMs = testPulseMs)
        assertEquals(TouchAwakeEffect.Acquire(timeoutMs = testPulseMs, releaseFirst = false), result.effect)
        assertTrue(TouchAwakePulse.isHeld(result.state, nowMs = 999))
        assertFalse(TouchAwakePulse.isHeld(result.state, nowMs = 1001))
    }

    @Test
    fun `T75 a second down extends the pulse to 1000 ms after itself, releasing the first`() {
        val first = TouchAwakePulse.onTouchDown(TouchAwakeState(), nowMs = 0, pulseMs = testPulseMs)
        val second = TouchAwakePulse.onTouchDown(first.state, nowMs = 800, pulseMs = testPulseMs)
        // SS6's renewal row: the lock is not reference counted, so the held pulse is dropped first.
        assertEquals(TouchAwakeEffect.Acquire(timeoutMs = testPulseMs, releaseFirst = true), second.effect)
        assertTrue(TouchAwakePulse.isHeld(second.state, nowMs = 1600))
        assertFalse(TouchAwakePulse.isHeld(second.state, nowMs = 1801))
    }

    @Test
    fun `T76 moves and ups alone never take the pulse`() {
        val moved = TouchAwakePulse.onOtherTouchEvent(TouchAwakeState())
        assertEquals(TouchAwakeEffect.None, moved.effect)
        assertFalse(TouchAwakePulse.isHeld(moved.state, nowMs = 0))

        val upped = TouchAwakePulse.onOtherTouchEvent(moved.state)
        assertEquals(TouchAwakeEffect.None, upped.effect)
        assertFalse(TouchAwakePulse.isHeld(upped.state, nowMs = 10))
    }

    @Test
    fun `T77 the keyboard window going away drops a held pulse at once and it stays gone`() {
        val held = TouchAwakePulse.onTouchDown(TouchAwakeState(), nowMs = 0, pulseMs = testPulseMs)
        val detached = TouchAwakePulse.onChromeDetached(held.state, nowMs = 100)
        assertEquals(TouchAwakeEffect.Release, detached.effect)
        assertFalse(TouchAwakePulse.isHeld(detached.state, nowMs = 100))
        assertFalse(TouchAwakePulse.isHeld(detached.state, nowMs = 2100))
    }

    @Test
    fun `detaching with no pulse held asks for nothing`() {
        val detached = TouchAwakePulse.onChromeDetached(TouchAwakeState(), nowMs = 50)
        assertEquals(TouchAwakeEffect.None, detached.effect)
    }

    @Test
    fun `a down after the pulse has expired takes a fresh one rather than renewing`() {
        val first = TouchAwakePulse.onTouchDown(TouchAwakeState(), nowMs = 0, pulseMs = testPulseMs)
        val later = TouchAwakePulse.onTouchDown(first.state, nowMs = 5000, pulseMs = testPulseMs)
        assertEquals(TouchAwakeEffect.Acquire(timeoutMs = testPulseMs, releaseFirst = false), later.effect)
    }

    @Test
    fun `the shipped pulse is the 200 ms SS6 names, under the tag a battery dump shows`() {
        assertEquals(200L, TouchAwakePulse.PULSE_MS)
        assertEquals("PhysiBoard:ImeTouch", TouchAwakePulse.TAG)
        val shipped = TouchAwakePulse.onTouchDown(TouchAwakeState(), nowMs = 0)
        assertEquals(TouchAwakeEffect.Acquire(timeoutMs = 200, releaseFirst = false), shipped.effect)
        assertTrue(TouchAwakePulse.isHeld(shipped.state, nowMs = 199))
        assertFalse(TouchAwakePulse.isHeld(shipped.state, nowMs = 200))
    }
}
