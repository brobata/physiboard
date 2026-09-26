package brobata.physiboard.core.pointer.keyboardswipe

import kotlin.math.abs

/**
 * The keyboard-surface swipe, upstream "trackpad gestures": a finger dragging on the physical
 * keys themselves (the Titan's capacitive touch layer), not the screen trackpad overlay
 * (`core.pointer.trackpad.TrackpadGesture`, a different gesture on a different surface).
 *
 * spec: trackpad-caret-nav.md SS3.3 (the native provider's detection algorithm), SS3.7 (the
 * settings table). This models only the `native_ime` provider (SS3.3); the Shizuku provider
 * (SS3.4) needs a live `getevent` process and is out of this milestone's scope.
 */
enum class SwipeTouchPhase { DOWN, MOVE, UP, CANCEL }

/** One raw sample of a finger on the keyboard's touch layer. spec SS3.3. */
data class SwipeTouchSample(val phase: SwipeTouchPhase, val x: Float, val y: Float, val timeMs: Long)

/** Which third of the surface an accepted up-swipe started in. spec SS3.3's "left third... centre... right otherwise". */
enum class SwipeThird { LEFT, CENTRE, RIGHT }

/** spec SS3.7: which detector runs. */
enum class TrackpadGestureProvider(val preferenceValue: String) {
    SHIZUKU("shizuku"), NATIVE_IME("native_ime");

    companion object {
        /** spec SS3.7: "unknown values read as the default" (`native_ime`). */
        fun fromPreference(value: String?): TrackpadGestureProvider = entries.firstOrNull { it.preferenceValue == value } ?: NATIVE_IME
    }
}

/** spec SS3.6: which source of a left/delete swipe is honoured. */
enum class SwipeToDeleteProvider(val preferenceValue: String) {
    TITAN2_KEYCODE("titan2_keycode"), NATIVE_IME("native_ime");

    companion object {
        fun fromPreference(value: String?): SwipeToDeleteProvider = entries.firstOrNull { it.preferenceValue == value } ?: NATIVE_IME
    }
}

/**
 * The tunables SS3.7 lists for the keyboard-surface swipe. [suggestionThresholdPx] and
 * [deleteThresholdPx] are null when unset, meaning "fall back to [legacyThresholdPx]" (SS3.7:
 * "the legacy value, else 500"); a caller reads [clampedSuggestionThresholdPx]/
 * [clampedDeleteThresholdPx], never the raw nullable fields, for the value the detector actually
 * uses.
 */
data class KeyboardSwipeSettings(
    val gesturesEnabled: Boolean = false,
    val provider: TrackpadGestureProvider = TrackpadGestureProvider.NATIVE_IME,
    val legacyThresholdPx: Float = 500f,
    val suggestionThresholdPx: Float? = null,
    val deleteThresholdPx: Float? = null,
    val swipeToDelete: Boolean = false,
    val swipeToDeleteProvider: SwipeToDeleteProvider = SwipeToDeleteProvider.NATIVE_IME,
    val addWordEnabled: Boolean = true,
    val addWordFullWidthEnabled: Boolean = true,
) {
    /** spec SS3.7: "float 120..750". */
    val clampedLegacyThresholdPx: Float get() = legacyThresholdPx.coerceIn(MIN_THRESHOLD_PX, MAX_THRESHOLD_PX)
    val clampedSuggestionThresholdPx: Float get() = (suggestionThresholdPx ?: legacyThresholdPx).coerceIn(MIN_THRESHOLD_PX, MAX_THRESHOLD_PX)
    val clampedDeleteThresholdPx: Float get() = (deleteThresholdPx ?: legacyThresholdPx).coerceIn(MIN_THRESHOLD_PX, MAX_THRESHOLD_PX)

    companion object {
        const val MIN_THRESHOLD_PX = 120f
        const val MAX_THRESHOLD_PX = 750f
    }
}

/** The result of evaluating one whole gesture (down through up). spec SS3.3. */
sealed class SwipeEvaluation {
    /** Nothing to evaluate yet (down, move or cancel), or gestures are off. */
    data object None : SwipeEvaluation()

    /** spec SS3.3: "Up (accept suggestion)", the third the gesture started in. */
    data class Up(val third: SwipeThird) : SwipeEvaluation()

    /** spec SS3.3: "Left (delete word)". */
    data object Left : SwipeEvaluation()

    /** spec SS3.3: "A gesture that qualifies for neither is recorded... as a 'candidate'". */
    data object Candidate : SwipeEvaluation()

    /** spec SS3.3: "within 250 ms... of the previously accepted gesture is recorded as 'debounced'". */
    data object Debounced : SwipeEvaluation()
}

/** The in-flight gesture's memory between samples. spec SS3.3's per-phase table. */
data class KeyboardSwipeState(
    private val startX: Float = 0f,
    private val startY: Float = 0f,
    private val startTimeMs: Long = 0L,
    private val active: Boolean = false,
    val lastAcceptedAtMs: Long? = null,
) {
    internal fun begun(x: Float, y: Float, timeMs: Long) = copy(startX = x, startY = y, startTimeMs = timeMs, active = true)
    internal fun forgotten() = copy(active = false)
    internal val isActive: Boolean get() = active
    internal val sx: Float get() = startX
    internal val sy: Float get() = startY
    internal val st: Long get() = startTimeMs
}

/**
 * Turns a stream of [SwipeTouchSample]s into [SwipeEvaluation]s, pure and stateless beyond
 * [KeyboardSwipeState]. spec: trackpad-caret-nav.md SS3.3.
 */
object KeyboardSwipeGesture {
    private const val MIN_VELOCITY_PX_PER_MS = 2.0f
    private const val DEBOUNCE_WINDOW_MS = 250L
    /**
     * spec SS3.3 records upstream's 1440 and says so plainly ("The 1440 width is upstream's Titan 2
     * value; against the Elite's 1080 range (D2) the right third can only be reached by starting at
     * x >= 960 of 1080"), and SS11's Keep/Drop makes the correction a condition of keeping this
     * feature at all: "if kept, thirds must be measured against 1080 not 1440". The Elite's touch
     * layer is 1080 wide (SS3.1 D2), so the thirds are 360 px each and all three are reachable.
     */
    private const val SURFACE_WIDTH_PX = 1080f
    private const val LEFT_THIRD_MAX_PX = 360f
    private const val CENTRE_THIRD_MAX_PX = 720f

    /** One [SwipeTouchSample] folded into [state]. spec SS3.3's Down/Move/Up/Cancel table. */
    fun onSample(state: KeyboardSwipeState, sample: SwipeTouchSample, settings: KeyboardSwipeSettings): Pair<KeyboardSwipeState, SwipeEvaluation> {
        if (!settings.gesturesEnabled || settings.provider != TrackpadGestureProvider.NATIVE_IME) return state to SwipeEvaluation.None
        return when (sample.phase) {
            SwipeTouchPhase.DOWN -> state.begun(sample.x, sample.y, sample.timeMs) to SwipeEvaluation.None
            SwipeTouchPhase.MOVE -> state to SwipeEvaluation.None
            SwipeTouchPhase.CANCEL -> state.forgotten() to SwipeEvaluation.None
            SwipeTouchPhase.UP -> evaluate(state, sample, settings)
        }
    }

    private fun evaluate(state: KeyboardSwipeState, sample: SwipeTouchSample, settings: KeyboardSwipeSettings): Pair<KeyboardSwipeState, SwipeEvaluation> {
        if (!state.isActive) return state to SwipeEvaluation.None
        val idle = state.forgotten()
        val dx = sample.x - state.sx
        val dy = sample.y - state.sy
        val durationMs = (sample.timeMs - state.st).coerceAtLeast(1L)

        val upDistance = -dy
        val leftDistance = -dx
        val upVelocity = upDistance / durationMs
        val leftVelocity = leftDistance / durationMs

        // spec SS3.3: "Up is tested first."
        val qualifiesUp = upDistance >= settings.clampedSuggestionThresholdPx &&
            abs(dx) < upDistance / 4f &&
            upVelocity >= MIN_VELOCITY_PX_PER_MS
        val qualifiesLeft = !qualifiesUp &&
            settings.swipeToDelete &&
            settings.swipeToDeleteProvider == SwipeToDeleteProvider.NATIVE_IME &&
            leftDistance >= settings.clampedDeleteThresholdPx &&
            abs(dy) < leftDistance / 4f &&
            leftVelocity >= MIN_VELOCITY_PX_PER_MS

        if (!qualifiesUp && !qualifiesLeft) return idle to SwipeEvaluation.Candidate

        val lastAccepted = state.lastAcceptedAtMs
        if (lastAccepted != null && sample.timeMs - lastAccepted < DEBOUNCE_WINDOW_MS) {
            return idle to SwipeEvaluation.Debounced
        }

        val accepted = idle.copy(lastAcceptedAtMs = sample.timeMs)
        return if (qualifiesUp) {
            accepted to SwipeEvaluation.Up(thirdFor(state.sx))
        } else {
            accepted to SwipeEvaluation.Left
        }
    }

    /** spec SS3.3: "start x clamped... then left third below, centre below, right otherwise", on the Elite's own 1080 range (SS11 Keep/Drop). */
    private fun thirdFor(startX: Float): SwipeThird {
        val clamped = startX.coerceIn(0f, SURFACE_WIDTH_PX)
        return when {
            clamped < LEFT_THIRD_MAX_PX -> SwipeThird.LEFT
            clamped < CENTRE_THIRD_MAX_PX -> SwipeThird.CENTRE
            else -> SwipeThird.RIGHT
        }
    }
}
