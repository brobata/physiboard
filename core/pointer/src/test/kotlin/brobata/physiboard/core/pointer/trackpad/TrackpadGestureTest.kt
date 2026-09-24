package brobata.physiboard.core.pointer.trackpad

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: trackpad-caret-nav.md SS10, test cases T14-T21 (the finger-movement subset owned by [TrackpadGesture]). */
class TrackpadGestureTest {

    private val settings32 = TrackpadGestureSettings(horizontalStepPx = 32f)

    private fun down(x: Float, y: Float, t: Long) = TouchSample(TouchPhase.DOWN, x, y, t)
    private fun move(x: Float, y: Float, t: Long, shiftActive: Boolean = false) = TouchSample(TouchPhase.MOVE, x, y, t, shiftActive)
    private fun up(x: Float, y: Float, t: Long) = TouchSample(TouchPhase.UP, x, y, t)

    @Test
    fun `T14 - a 70px rightward drag at step 32 emits two RIGHT steps and leaves 6px in the accumulator`() {
        var acc = TrackpadAccumulator()
        var previous: TouchSample? = null
        val d = down(100f, 100f, 0)
        var result = TrackpadGesture.step(acc, previous, d, settings32)
        acc = result.accumulator; previous = d
        assertEquals(emptyList(), result.steps)

        val m = move(170f, 100f, 10)
        result = TrackpadGesture.step(acc, previous, m, settings32)
        assertEquals(listOf(CursorStep(CursorAxis.HORIZONTAL, true, false), CursorStep(CursorAxis.HORIZONTAL, true, false)), result.steps)
        assertEquals(6f, result.accumulator.horizontal)
    }

    @Test
    fun `T15 - a slow drag under one more step produces no movement and keeps accumulating`() {
        val steps = TrackpadGesture.runGesture(
            listOf(down(100f, 100f, 0), move(170f, 100f, 10), move(180f, 100f, 20)),
            settings32,
        )
        // Only the first move (T14) crosses a step boundary; the second move's own 10px leaves the
        // combined leftover (6 + 10 = 16) still below the 32px step, so it emits nothing on its own.
        assertEquals(2, steps.size)
        var acc = TrackpadAccumulator()
        var previous: TouchSample? = null
        for (sample in listOf(down(100f, 100f, 0), move(170f, 100f, 10), move(180f, 100f, 20))) {
            acc = TrackpadGesture.step(acc, previous, sample, settings32).accumulator
            previous = sample
        }
        assertEquals(16f, acc.horizontal)
    }

    @Test
    fun `T16 - a downward drag emits DPAD_DOWN using the 2x vertical step`() {
        val d = down(0f, 0f, 0)
        val m = move(0f, 70f, 10)
        val afterDown = TrackpadGesture.step(TrackpadAccumulator(), null, d, settings32)
        val result = TrackpadGesture.step(afterDown.accumulator, d, m, settings32)
        assertEquals(listOf(CursorStep(CursorAxis.VERTICAL, true, false)), result.steps)
        assertEquals(6f, result.accumulator.vertical)
    }

    @Test
    fun `T17 - an upward drag emits DPAD_UP`() {
        val d = down(0f, 0f, 0)
        val m = move(0f, -64f, 10)
        val afterDown = TrackpadGesture.step(TrackpadAccumulator(), null, d, settings32)
        val result = TrackpadGesture.step(afterDown.accumulator, d, m, settings32)
        assertEquals(listOf(CursorStep(CursorAxis.VERTICAL, false, false)), result.steps)
        assertEquals(0f, result.accumulator.vertical)
    }

    @Test
    fun `T18 - a fast diagonal move at step 8 is capped at 12 events, spending the whole budget horizontally`() {
        val settings8 = TrackpadGestureSettings(horizontalStepPx = 8f, maxEventsPerMove = 12)
        val d = down(0f, 0f, 0)
        val m = move(200f, 200f, 10)
        val afterDown = TrackpadGesture.step(TrackpadAccumulator(), null, d, settings8)
        val result = TrackpadGesture.step(afterDown.accumulator, d, m, settings8)
        assertEquals(12, result.steps.size)
        assertEquals(12, result.steps.count { it.axis == CursorAxis.HORIZONTAL })
        assertEquals(0, result.steps.count { it.axis == CursorAxis.VERTICAL })
        assertEquals(104f, result.accumulator.horizontal)
    }

    @Test
    fun `T19 - Shift held marks every emitted step as a selection`() {
        val d = down(0f, 0f, 0)
        val m = move(-32f, 0f, 10, shiftActive = true)
        val afterDown = TrackpadGesture.step(TrackpadAccumulator(), null, d, settings32)
        val result = TrackpadGesture.step(afterDown.accumulator, d, m, settings32)
        assertEquals(listOf(CursorStep(CursorAxis.HORIZONTAL, false, true)), result.steps)
    }

    @Test
    fun `T20 - a release then a new finger down resets the accumulator`() {
        val steps = TrackpadGesture.runGesture(
            listOf(
                down(0f, 0f, 0),
                move(30f, 0f, 10), // 30px, below the 32px step: no event yet
                up(30f, 0f, 20),
                down(100f, 100f, 30),
                move(105f, 100f, 40), // only 5px into the new gesture
            ),
            settings32,
        )
        assertEquals(emptyList(), steps)
    }

    @Test
    fun `T21 - the stored step preference is clamped to 8 to 64`() {
        assertEquals(8, TrackpadGestureSettings.clampStoredStepPx(4))
        assertEquals(64, TrackpadGestureSettings.clampStoredStepPx(100))
    }

    @Test
    fun `T22 - an unknown trigger or activation preference falls back to the default`() {
        assertEquals(TriggerKey.SPACE, TriggerKey.fromPreference("bogus"))
        assertEquals(ActivationMode.HOLD, ActivationMode.fromPreference("bogus"))
    }
}
