package brobata.physiboard.ime.pointer

import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import brobata.physiboard.core.pointer.keyboardswipe.KeyboardSwipeGesture
import brobata.physiboard.core.pointer.keyboardswipe.KeyboardSwipeSettings
import brobata.physiboard.core.pointer.keyboardswipe.KeyboardSwipeState
import brobata.physiboard.core.pointer.keyboardswipe.SwipeEvaluation
import brobata.physiboard.core.pointer.keyboardswipe.SwipeTouchPhase
import brobata.physiboard.core.pointer.keyboardswipe.SwipeTouchSample
import brobata.physiboard.core.pointer.keyboardswipe.TrackpadGestureProvider

/**
 * The `native_ime` provider for the keyboard-surface swipe: turns the real `MotionEvent`s a
 * `View.OnGenericMotionListener` receives into [SwipeEvaluation]s through the pure
 * [KeyboardSwipeGesture], and supplies the one android-only fact that module cannot have on its
 * own, whether this event is even eligible (device family, Android version, touchpad source).
 *
 * spec: trackpad-caret-nav.md SS3.3. `:ime` (via [KeyboardSession]) owns the listener attachment
 * and everything an accepted [SwipeEvaluation] should do; this class only detects.
 *
 * NEEDS A REAL DEVICE, not proven by a JVM test:
 *  - Whether the Titan 2 Elite's touch layer really arrives as a `SOURCE_TOUCHPAD` motion event on
 *    this build's Android 16 image, or under a device name this class's [isTouchpadSource] check
 *    recognises (D2, D6).
 *  - Whether Android really delivers historical batched samples the way [onGenericMotion] assumes
 *    (this class evaluates only the event's own final x/y/time, not each historical sample, since
 *    the pure detector's Move phase carries no state of its own; SS3.3's per-phase table names
 *    "the final sample" as what matters for evaluation, which is what a real `ACTION_UP` already
 *    carries).
 */
internal class KeyboardSwipeController(
    var settings: KeyboardSwipeSettings,
    private val isEligibleDevice: () -> Boolean,
) {
    private var state = KeyboardSwipeState()

    /** spec SS3.3's guard: gestures on, `native_ime` provider, Titan 2 family, Android 16+, a touchpad-sourced event. */
    fun accepts(event: MotionEvent): Boolean {
        if (!settings.gesturesEnabled || settings.provider != TrackpadGestureProvider.NATIVE_IME) return false
        if (Build.VERSION.SDK_INT < 36) return false // Android 16
        if (!isEligibleDevice()) return false
        return isTouchpadSource(event)
    }

    private fun isTouchpadSource(event: MotionEvent): Boolean {
        val fromTouchpadSource = (event.source and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
        val deviceName = runCatching { event.device?.name }.getOrNull().orEmpty()
        return fromTouchpadSource || deviceName.contains("touchPad", ignoreCase = true)
    }

    /**
     * Feeds one real event through the pure detector. Returns the evaluation so the caller can act
     * on it; every phase is "consumed" per SS3.3's table, so the caller should return `true`
     * whenever [accepts] said yes, whatever this method returns.
     */
    fun onGenericMotion(event: MotionEvent): SwipeEvaluation {
        val phase = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> SwipeTouchPhase.DOWN
            MotionEvent.ACTION_MOVE -> SwipeTouchPhase.MOVE
            MotionEvent.ACTION_UP -> SwipeTouchPhase.UP
            MotionEvent.ACTION_CANCEL -> SwipeTouchPhase.CANCEL
            else -> return SwipeEvaluation.None
        }
        val sample = SwipeTouchSample(phase, event.x, event.y, event.eventTime)
        val (newState, evaluation) = KeyboardSwipeGesture.onSample(state, sample, settings)
        state = newState
        return evaluation
    }
}
