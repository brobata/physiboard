package brobata.physiboard.core.pointer.trackpad

import kotlin.math.absoluteValue

/** One phase of a finger's contact with the overlay. spec: trackpad-caret-nav.md SS2.6, SS2.7. */
enum class TouchPhase { DOWN, MOVE, UP, CANCEL }

/**
 * One raw sample of a finger on the trackpad overlay.
 *
 * [shiftActive] is spec SS2.6's "whether Shift is on is re-read on every move event": a physically
 * held Shift, an armed Shift one-shot, or the visual Shift layer latched (SS2.5's own definition of
 * "Shift active" for the pill); the caller resolves that from `:core:keys`' `ModifierState`, since
 * this module has no opinion of its own about what counts as Shift.
 */
data class TouchSample(val phase: TouchPhase, val x: Float, val y: Float, val timeMs: Long, val shiftActive: Boolean = false)

/** One axis a cursor step moves along. spec: trackpad-caret-nav.md SS2.6. */
enum class CursorAxis { HORIZONTAL, VERTICAL }

/**
 * One synthetic DPAD press-and-release the overlay's caller should send through the input
 * connection. spec: trackpad-caret-nav.md SS2.6 ("Each DPAD event is a key down followed by a key
 * up with the same timestamp"): one [CursorStep] here is that whole down/up pair, since the pure
 * layer has no reason to model a synthetic event's two edges separately.
 *
 * [positive] on [CursorAxis.HORIZONTAL] means DPAD_RIGHT (false means DPAD_LEFT); on
 * [CursorAxis.VERTICAL] it means DPAD_DOWN, "finger moving down the screen" (false means DPAD_UP).
 */
data class CursorStep(val axis: CursorAxis, val positive: Boolean, val shiftSelecting: Boolean)

/**
 * The tunables the finger-to-cursor conversion reads. spec: trackpad-caret-nav.md SS2.6, SS2.8.
 * There is deliberately no dead-zone or acceleration field: SS2.6 states plainly "There is no dead
 * zone, no acceleration, no key repeat and no velocity term", and SS11's Keep/Drop table keeps
 * exactly that ("32 px step, 2x vertical, 12 events per move, no acceleration | keep | tuned on the
 * Elite; the trackpad's whole feel"). A sub-step drag already produces no movement on its own,
 * through the accumulator below, without a separate dead-zone parameter to encode it.
 */
data class TrackpadGestureSettings(
    val horizontalStepPx: Float = 32f,
    val verticalStepMultiplier: Float = 2.0f,
    val maxEventsPerMove: Int = 12,
) {
    /** spec: SS2.8's slider range, test T21 ("step preference written as 4, then 100 -> read back as 8, then 64"). */
    val clampedHorizontalStepPx: Float get() = horizontalStepPx.coerceIn(MIN_STEP_PX, MAX_STEP_PX)
    val verticalStepPx: Float get() = clampedHorizontalStepPx * verticalStepMultiplier

    companion object {
        const val MIN_STEP_PX = 8f
        const val MAX_STEP_PX = 64f

        /** spec SS2.8 ("Range 8 to 64"), applied to a raw stored preference value. */
        fun clampStoredStepPx(storedPx: Int): Int = storedPx.coerceIn(MIN_STEP_PX.toInt(), MAX_STEP_PX.toInt())
    }
}

/** The per-axis leftover movement carried from one touch sample to the next. spec: SS2.6 ("Leftover movement under a step carries over"). */
data class TrackpadAccumulator(val horizontal: Float = 0f, val vertical: Float = 0f)

/**
 * Converts a finger's raw movement on the screen trackpad overlay into DPAD cursor steps.
 *
 * spec: trackpad-caret-nav.md SS2.6 ("Finger movement to DPAD events") and SS2.7 ("What happens on
 * release"). This is deliberately a function from a stream of [TouchSample]s and an accumulator to
 * a list of [CursorStep]s, with no knowledge of windows, key events or `InputConnection`, so
 * [runGesture] can drive a whole drag end to end in a JVM test.
 */
object TrackpadGesture {

    /** The result of folding one [TouchSample] into the running accumulator. */
    data class StepResult(val accumulator: TrackpadAccumulator, val steps: List<CursorStep>)

    /**
     * Folds one touch sample into [accumulator]. [previous] is the immediately preceding sample
     * (null only before the very first DOWN), used to compute this move's delta; the caller is
     * expected to pass the actual previous sample it fed in, not a remembered "last processed
     * position" of its own, since [TrackpadAccumulator] alone is this module's whole memory.
     */
    fun step(accumulator: TrackpadAccumulator, previous: TouchSample?, sample: TouchSample, settings: TrackpadGestureSettings): StepResult = when (sample.phase) {
        // spec SS2.6: "Accumulator reset: on finger down, finger up and cancel."
        TouchPhase.DOWN -> StepResult(TrackpadAccumulator(), emptyList())
        TouchPhase.UP, TouchPhase.CANCEL -> StepResult(TrackpadAccumulator(), emptyList())
        TouchPhase.MOVE -> {
            val from = previous ?: sample
            val dx = sample.x - from.x
            val dy = sample.y - from.y
            emitSteps(accumulator.copy(horizontal = accumulator.horizontal + dx, vertical = accumulator.vertical + dy), sample.shiftActive, settings)
        }
    }

    /**
     * Convenience for a whole gesture at once (what a test drives): folds [samples] in order from
     * an empty accumulator and returns every [CursorStep] emitted, in event order.
     */
    fun runGesture(samples: List<TouchSample>, settings: TrackpadGestureSettings = TrackpadGestureSettings()): List<CursorStep> {
        var accumulator = TrackpadAccumulator()
        var previous: TouchSample? = null
        val steps = mutableListOf<CursorStep>()
        for (sample in samples) {
            val result = step(accumulator, previous, sample, settings)
            accumulator = result.accumulator
            steps += result.steps
            previous = sample
        }
        return steps
    }

    /**
     * spec SS2.6: "horizontal steps are emitted first, then vertical, until the budget is spent";
     * [TrackpadGestureSettings.maxEventsPerMove] is one shared budget across both axes for this one
     * move event.
     */
    private fun emitSteps(accumulator: TrackpadAccumulator, shiftActive: Boolean, settings: TrackpadGestureSettings): StepResult {
        var budget = settings.maxEventsPerMove
        val steps = mutableListOf<CursorStep>()

        var horizontal = accumulator.horizontal
        val horizontalStep = settings.clampedHorizontalStepPx
        while (budget > 0 && horizontal.absoluteValue >= horizontalStep) {
            val positive = horizontal > 0
            horizontal -= if (positive) horizontalStep else -horizontalStep
            steps += CursorStep(CursorAxis.HORIZONTAL, positive, shiftActive)
            budget--
        }

        var vertical = accumulator.vertical
        val verticalStep = settings.verticalStepPx
        while (budget > 0 && vertical.absoluteValue >= verticalStep) {
            val positive = vertical > 0
            vertical -= if (positive) verticalStep else -verticalStep
            steps += CursorStep(CursorAxis.VERTICAL, positive, shiftActive)
            budget--
        }

        return StepResult(TrackpadAccumulator(horizontal, vertical), steps)
    }
}
