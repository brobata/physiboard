package brobata.physiboard.ime.pointer

import android.content.Context
import android.graphics.PixelFormat
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputConnection
import android.widget.Toast
import brobata.physiboard.core.pointer.OverlayAvailability
import brobata.physiboard.core.pointer.trackpad.CursorAxis
import brobata.physiboard.core.pointer.trackpad.CursorStep
import brobata.physiboard.core.pointer.trackpad.TrackpadActivation
import brobata.physiboard.core.pointer.trackpad.TrackpadActivationEffect
import brobata.physiboard.core.pointer.trackpad.TrackpadActivationSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadActivationState
import brobata.physiboard.core.pointer.trackpad.TrackpadGestureSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadPhase
import brobata.physiboard.core.pointer.trackpad.TrackpadPhysicalKey

/**
 * Owns the screen trackpad's real overlay window: opens and closes it from
 * [TrackpadActivationEffect] flags, and turns [CursorStep]s into DPAD key events on the current
 * `InputConnection`.
 *
 * spec: trackpad-caret-nav.md SS2.4 (window properties), SS2.6 ("Each DPAD event is a key down
 * followed by a key up with the same timestamp"). This class supplies the one `android.*` fact
 * `:core:pointer`'s pure [TrackpadActivation] cannot have on its own: whether the window manager
 * will actually take the window, read fresh through [OverlayPermission] on every check exactly as
 * SS4.6 requires of the caret badge (SS2.1 makes the same permission the trackpad's own).
 *
 * NEEDS A REAL DEVICE, not done by this class:
 *  - Running [onKeyDown]/[onKeyUp] ahead of the normal key pipeline for the configured trigger key
 *    (SS2.2: "before everything else in the key pipeline") and only forwarding the event to
 *    [KeyboardPipeline] when it comes back not consumed. `PhysiBoardInputMethodService`'s current
 *    `onKeyDown`/`onKeyUp` call `KeyboardSession.onKeyEvent` unconditionally; wiring this in ahead
 *    of that call, and replaying [TrackpadActivationEffect.replayTriggerDownAndUp] /
 *    [TrackpadActivationEffect.replayTriggerDownOnly] back through the normal pipeline the way
 *    SS2.3's "replaying flag" describes, is the remaining integration work.
 *  - Whether the overlay actually claims every touch and leaves the editor focused underneath it
 *    (SS2.4's "not focusable... the app underneath receives none while the overlay is up") can
 *    only be observed on the Titan.
 */
internal class TrackpadOverlayController(
    private val service: InputMethodService,
    private val handler: Handler,
    var activationSettings: TrackpadActivationSettings,
    var gestureSettings: TrackpadGestureSettings,
    private val isShiftActive: () -> Boolean,
    private val currentInputConnection: () -> InputConnection?,
) {
    private var activationState = TrackpadActivationState()
    private var overlayView: TrackpadOverlayView? = null
    private val timerRunnable = Runnable { onHoldTimerTick() }

    /** True when the caller should treat the key as handled and not pass it on. */
    fun onKeyDown(key: TrackpadPhysicalKey?, repeatCount: Int, timeMs: Long, carriesDisqualifyingMeta: Boolean): Boolean {
        val result = TrackpadActivation.onKeyDown(activationState, key, repeatCount, timeMs, carriesDisqualifyingMeta, activationSettings, availability())
        apply(result.state, result.effect)
        scheduleTimerIfNeeded()
        return result.consumed
    }

    fun onKeyUp(key: TrackpadPhysicalKey?, timeMs: Long): Boolean {
        val result = TrackpadActivation.onKeyUp(activationState, key, timeMs, activationSettings)
        apply(result.state, result.effect)
        scheduleTimerIfNeeded()
        return result.consumed
    }

    /** Called when the keyboard window is torn down or hidden for real (not the status bar's per-app dip). spec SS2.4's overlay lifetime. */
    fun onKeyboardWindowHidden() {
        handler.removeCallbacks(timerRunnable)
        activationState = TrackpadActivationState()
        closeOverlay()
    }

    private fun onHoldTimerTick() {
        val result = TrackpadActivation.onHoldTimerFired(activationState, SystemClock.uptimeMillis(), activationSettings, availability())
        apply(result.state, result.effect)
    }

    private fun scheduleTimerIfNeeded() {
        handler.removeCallbacks(timerRunnable)
        val deadline = TrackpadActivation.pendingDeadlineMs(activationState, activationSettings) ?: return
        val delay = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(timerRunnable, delay)
    }

    private fun availability(): OverlayAvailability = OverlayPermission.availability(service)

    private fun apply(newState: TrackpadActivationState, effect: TrackpadActivationEffect) {
        activationState = newState
        if (effect.openOverlayHold || effect.openOverlaySticky) openOverlay()
        if (effect.closeOverlay) closeOverlay()
        if (effect.showPermissionToast) {
            runCatching {
                Toast.makeText(service, "Screen trackpad needs Display over other apps. Enable it in PhysiBoard settings.", Toast.LENGTH_SHORT).show()
            }
        }
        // replayTriggerDownAndUp / replayTriggerDownOnly: see this class's own "NEEDS A REAL
        // DEVICE" note above; there is no owning caller yet to hand the replay back to.
    }

    private fun openOverlay() {
        if (overlayView != null) return
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val view = TrackpadOverlayView(
            context = service,
            settings = gestureSettings,
            isShiftActive = isShiftActive,
            onSteps = ::sendSteps,
            onPillTapped = ::onPillTapped,
        )
        view.hintText = hintFor(isShiftActive())
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (added) overlayView = view
    }

    private fun closeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        runCatching { windowManager.removeView(view) }
    }

    /** spec SS2.5: "Tap on the pill | sticky mode only: closes the trackpad. In hold mode the pill is inert." */
    private fun onPillTapped() {
        if (activationState.phase == TrackpadPhase.ACTIVE_STICKY) {
            activationState = TrackpadActivationState()
            closeOverlay()
        }
    }

    private fun hintFor(shiftActive: Boolean): String = when {
        shiftActive -> "⇧ Select"
        activationState.phase == TrackpadPhase.ACTIVE_STICKY -> "✥ Cursor · tap to exit"
        else -> "✥ Cursor"
    }

    private fun sendSteps(steps: List<CursorStep>) {
        val ic = currentInputConnection() ?: return
        val now = SystemClock.uptimeMillis()
        for (step in steps) {
            val keyCode = keyCodeFor(step)
            val meta = if (step.shiftSelecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }
    }

    private fun keyCodeFor(step: CursorStep): Int = when {
        step.axis == CursorAxis.HORIZONTAL && step.positive -> KeyEvent.KEYCODE_DPAD_RIGHT
        step.axis == CursorAxis.HORIZONTAL -> KeyEvent.KEYCODE_DPAD_LEFT
        step.positive -> KeyEvent.KEYCODE_DPAD_DOWN
        else -> KeyEvent.KEYCODE_DPAD_UP
    }
}
