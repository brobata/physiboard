package brobata.physiboard.core.pointer.keyboardswipe

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: trackpad-caret-nav.md SS3.3 (the native provider's detection algorithm). */
class KeyboardSwipeGestureTest {

    private val settings = KeyboardSwipeSettings(gesturesEnabled = true)

    private fun run(samples: List<SwipeTouchSample>, settings: KeyboardSwipeSettings = this.settings): SwipeEvaluation {
        var state = KeyboardSwipeState()
        var last: SwipeEvaluation = SwipeEvaluation.None
        for (sample in samples) {
            val (newState, evaluation) = KeyboardSwipeGesture.onSample(state, sample, settings)
            state = newState
            last = evaluation
        }
        return last
    }

    @Test
    fun `disabled gestures never evaluate`() {
        val samples = listOf(
            SwipeTouchSample(SwipeTouchPhase.DOWN, 500f, 500f, 0),
            SwipeTouchSample(SwipeTouchPhase.UP, 500f, 0f, 100),
        )
        assertEquals(SwipeEvaluation.None, run(samples, settings.copy(gesturesEnabled = false)))
    }

    @Test
    fun `a straight fast upward swipe past the threshold accepts as Up in the centre third`() {
        val samples = listOf(
            SwipeTouchSample(SwipeTouchPhase.DOWN, 700f, 600f, 0),
            SwipeTouchSample(SwipeTouchPhase.UP, 700f, 0f, 100),
        )
        assertEquals(SwipeEvaluation.Up(SwipeThird.CENTRE), run(samples))
    }

    @Test
    fun `start x in the first third of the Elite's 1080 surface lands in the left third`() {
        val samples = listOf(
            SwipeTouchSample(SwipeTouchPhase.DOWN, 100f, 600f, 0),
            SwipeTouchSample(SwipeTouchPhase.UP, 100f, 0f, 100),
        )
        assertEquals(SwipeEvaluation.Up(SwipeThird.LEFT), run(samples))
    }

    @Test
    fun `start x in the last third of the Elite's 1080 surface lands in the right third`() {
        val samples = listOf(
            SwipeTouchSample(SwipeTouchPhase.DOWN, 1000f, 600f, 0),
            SwipeTouchSample(SwipeTouchPhase.UP, 1000f, 0f, 100),
        )
        assertEquals(SwipeEvaluation.Up(SwipeThird.RIGHT), run(samples))
    }

    /**
     * spec SS11's Keep/Drop condition on this feature: "if kept, thirds must be measured against
     * 1080 not 1440". Measured against upstream's 1440 the boundaries are 480 and 960, so a swipe
     * starting two thirds of the way across the Elite's own 1080 wide touch layer (x = 760) would
     * report the centre third and the right slot would be all but unreachable.
     */
    @Test
    fun `the thirds divide the Elite's own range, not upstream's 1440`() {
        for ((startX, expected) in listOf(0f to SwipeThird.LEFT, 359f to SwipeThird.LEFT, 360f to SwipeThird.CENTRE, 719f to SwipeThird.CENTRE, 720f to SwipeThird.RIGHT, 760f to SwipeThird.RIGHT, 1079f to SwipeThird.RIGHT)) {
            val samples = listOf(
                SwipeTouchSample(SwipeTouchPhase.DOWN, startX, 600f, 0),
                SwipeTouchSample(SwipeTouchPhase.UP, startX, 0f, 100),
            )
            assertEquals(SwipeEvaluation.Up(expected), run(samples), "start x $startX")
        }
    }

    @Test
    fun `too much horizontal drift for the vertical distance is only a candidate`() {
        val samples = listOf(
            SwipeTouchSample(SwipeTouchPhase.DOWN, 700f, 600f, 0),
            // dy=-500 (upDistance 500), dx=200: 200 is not less than 500/4=125, so not straight enough.
            SwipeTouchSample(SwipeTouchPhase.UP, 900f, 100f, 100),
        )
        assertEquals(SwipeEvaluation.Candidate, run(samples))
    }

    @Test
    fun `too slow a swipe is only a candidate`() {
        val samples = listOf(
            SwipeTouchSample(SwipeTouchPhase.DOWN, 700f, 600f, 0),
            // 500px over 1000ms is 0.5 px/ms, under the 2.0 px/ms floor.
            SwipeTouchSample(SwipeTouchPhase.UP, 700f, 100f, 1000),
        )
        assertEquals(SwipeEvaluation.Candidate, run(samples))
    }

    @Test
    fun `a left swipe accepts only when swipe to delete is on with the native_ime provider`() {
        val samples = listOf(
            SwipeTouchSample(SwipeTouchPhase.DOWN, 700f, 300f, 0),
            SwipeTouchSample(SwipeTouchPhase.UP, 100f, 300f, 100),
        )
        assertEquals(SwipeEvaluation.Candidate, run(samples))
        assertEquals(
            SwipeEvaluation.Left,
            run(samples, settings.copy(swipeToDelete = true, swipeToDeleteProvider = SwipeToDeleteProvider.NATIVE_IME)),
        )
        assertEquals(
            SwipeEvaluation.Candidate,
            run(samples, settings.copy(swipeToDelete = true, swipeToDeleteProvider = SwipeToDeleteProvider.TITAN2_KEYCODE)),
        )
    }

    @Test
    fun `a second qualifying swipe within 250ms of the first is debounced`() {
        var state = KeyboardSwipeState()
        val down1 = KeyboardSwipeGesture.onSample(state, SwipeTouchSample(SwipeTouchPhase.DOWN, 700f, 600f, 0), settings)
        state = down1.first
        val up1 = KeyboardSwipeGesture.onSample(state, SwipeTouchSample(SwipeTouchPhase.UP, 700f, 0f, 100), settings)
        state = up1.first
        assertEquals(SwipeEvaluation.Up(SwipeThird.CENTRE), up1.second)

        val down2 = KeyboardSwipeGesture.onSample(state, SwipeTouchSample(SwipeTouchPhase.DOWN, 700f, 600f, 150), settings)
        state = down2.first
        val up2 = KeyboardSwipeGesture.onSample(state, SwipeTouchSample(SwipeTouchPhase.UP, 700f, 0f, 250), settings)
        assertEquals(SwipeEvaluation.Debounced, up2.second)
    }

    @Test
    fun `a qualifying swipe after the debounce window accepts again`() {
        var state = KeyboardSwipeState()
        val up1 = KeyboardSwipeGesture.onSample(
            KeyboardSwipeGesture.onSample(state, SwipeTouchSample(SwipeTouchPhase.DOWN, 700f, 600f, 0), settings).first,
            SwipeTouchSample(SwipeTouchPhase.UP, 700f, 0f, 100),
            settings,
        )
        state = up1.first
        val down2 = KeyboardSwipeGesture.onSample(state, SwipeTouchSample(SwipeTouchPhase.DOWN, 700f, 600f, 400), settings)
        val up2 = KeyboardSwipeGesture.onSample(down2.first, SwipeTouchSample(SwipeTouchPhase.UP, 700f, 0f, 500), settings)
        assertEquals(SwipeEvaluation.Up(SwipeThird.CENTRE), up2.second)
    }

    @Test
    fun `custom suggestion threshold below the legacy default is honoured`() {
        val samples = listOf(
            SwipeTouchSample(SwipeTouchPhase.DOWN, 700f, 200f, 0),
            SwipeTouchSample(SwipeTouchPhase.UP, 700f, 0f, 100),
        )
        // 200px upward: under the 500 default, at/over a 150 custom threshold.
        assertEquals(SwipeEvaluation.Candidate, run(samples))
        assertEquals(SwipeEvaluation.Up(SwipeThird.CENTRE), run(samples, settings.copy(suggestionThresholdPx = 150f)))
    }

    @Test
    fun `thresholds outside 120 to 750 are clamped`() {
        assertEquals(750f, settings.copy(legacyThresholdPx = 5000f).clampedLegacyThresholdPx)
        assertEquals(120f, settings.copy(legacyThresholdPx = 1f).clampedLegacyThresholdPx)
    }
}
