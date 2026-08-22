package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import kotlin.math.abs

/**
 * "Screen trackpad": while a user-chosen trigger key is held (or after a single/double tap,
 * depending on the activation setting), a transparent full-screen overlay captures touch drags
 * and turns them into DPAD key events so the text cursor can be moved like on a touchscreen
 * keyboard's spacebar trackpad — but using the whole display as the pad.
 *
 * Holding Shift during the drag extends the selection (Shift+DPAD).
 *
 * Key consumption contract: [onKeyDown]/[onKeyUp] return true when the event must be swallowed
 * by the IME. In hold mode the trigger key's down is swallowed and *replayed* through the normal
 * pipeline if the key is released before the hold threshold, so a plain tap still types the key.
 */
internal class ScreenTrackpadController(
    private val service: Context,
    private val handler: Handler,
    private val inputConnectionProvider: () -> InputConnection?,
    private val isShiftActive: () -> Boolean,
    private val replayKey: (downKeyCode: Int, downEvent: KeyEvent, upEvent: KeyEvent?) -> Unit,
) {
    private enum class State { IDLE, PENDING_HOLD, ABORTED, ACTIVE_HOLD, ACTIVE_STICKY }

    private var state = State.IDLE
    private var pendingRawKeyCode = 0
    private var pendingRawDown: KeyEvent? = null
    private var lastTapUpTime = 0L
    private var lastTapKeyCode = 0

    private var overlayView: FrameLayout? = null
    private var hintView: TextView? = null
    private var windowManager: WindowManager? = null

    // Drag accumulation
    private var lastX = 0f
    private var lastY = 0f
    private var accX = 0f
    private var accY = 0f
    private var tracking = false
    private var movedDuringGesture = false
    private var stepPx = 24

    private val holdRunnable = Runnable {
        if (state == State.PENDING_HOLD) {
            if (activate(sticky = false)) {
                state = State.ACTIVE_HOLD
            } else {
                // Couldn't show the overlay (permission): behave like a normal key press.
                val down = pendingRawDown
                state = State.ABORTED
                if (down != null) replayKey(pendingRawKeyCode, down, null)
            }
        }
    }

    val isActive: Boolean
        get() = state == State.ACTIVE_HOLD || state == State.ACTIVE_STICKY

    // ---------------------------------------------------------------------------------------
    // Key handling
    // ---------------------------------------------------------------------------------------

    /**
     * @param keyCode remapped key code
     * @param event remapped event
     * @param rawKeyCode original key code as delivered by the system (used for replay)
     * @param rawEvent original event (used for replay)
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent?, rawKeyCode: Int, rawEvent: KeyEvent?): Boolean {
        if (event == null || rawEvent == null) return false
        if (!SettingsManager.isScreenTrackpadEnabled(service)) return false
        val trigger = isTriggerKey(keyCode)

        if (isActive) {
            if (trigger) {
                if (state == State.ACTIVE_STICKY && event.repeatCount == 0) deactivate()
                return true
            }
            if (state == State.ACTIVE_STICKY && keyCode == KeyEvent.KEYCODE_BACK) {
                deactivate()
                return true
            }
            return false
        }

        if (!trigger) {
            if (state == State.PENDING_HOLD) {
                // Another key while the trigger is down: it's a chord (e.g. Shift+letter),
                // not a trackpad request. Replay the trigger's down and step aside.
                handler.removeCallbacks(holdRunnable)
                val down = pendingRawDown
                state = State.ABORTED
                if (down != null) replayKey(pendingRawKeyCode, down, null)
            }
            return false
        }

        // Trigger key pressed while idle/aborted.
        if (event.repeatCount > 0) {
            return state == State.PENDING_HOLD
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE && (event.isCtrlPressed || event.isAltPressed)) {
            return false
        }

        return when (SettingsManager.getScreenTrackpadActivation(service)) {
            SettingsManager.SCREEN_TRACKPAD_ACTIVATION_HOLD -> {
                state = State.PENDING_HOLD
                pendingRawKeyCode = rawKeyCode
                pendingRawDown = KeyEvent(rawEvent)
                handler.removeCallbacks(holdRunnable)
                handler.postDelayed(holdRunnable, HOLD_THRESHOLD_MS)
                true
            }
            SettingsManager.SCREEN_TRACKPAD_ACTIVATION_DOUBLE_TAP -> {
                val now = event.eventTime
                if (lastTapKeyCode == keyCode && now - lastTapUpTime in 1..DOUBLE_TAP_THRESHOLD_MS) {
                    lastTapUpTime = 0L
                    if (activate(sticky = true)) state = State.ACTIVE_STICKY
                    true
                } else {
                    lastTapKeyCode = keyCode
                    false
                }
            }
            else -> { // single tap
                if (activate(sticky = true)) state = State.ACTIVE_STICKY
                true
            }
        }
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?, rawKeyCode: Int, rawEvent: KeyEvent?): Boolean {
        if (event == null) return false
        if (!isTriggerKey(keyCode)) return false
        return when (state) {
            State.PENDING_HOLD -> {
                handler.removeCallbacks(holdRunnable)
                val down = pendingRawDown
                state = State.IDLE
                pendingRawDown = null
                if (down != null) replayKey(pendingRawKeyCode, down, rawEvent)
                true
            }
            State.ABORTED -> {
                state = State.IDLE
                pendingRawDown = null
                false
            }
            State.ACTIVE_HOLD -> {
                deactivate()
                true
            }
            State.ACTIVE_STICKY -> true
            State.IDLE -> {
                if (SettingsManager.getScreenTrackpadActivation(service) ==
                    SettingsManager.SCREEN_TRACKPAD_ACTIVATION_DOUBLE_TAP && lastTapKeyCode == keyCode
                ) {
                    lastTapUpTime = event.eventTime
                }
                false
            }
        }
    }

    private fun isTriggerKey(keyCode: Int): Boolean {
        return when (SettingsManager.getScreenTrackpadTriggerKey(service)) {
            SettingsManager.SCREEN_TRACKPAD_TRIGGER_SPACE -> keyCode == KeyEvent.KEYCODE_SPACE
            SettingsManager.SCREEN_TRACKPAD_TRIGGER_SHIFT_LEFT -> keyCode == KeyEvent.KEYCODE_SHIFT_LEFT
            SettingsManager.SCREEN_TRACKPAD_TRIGGER_SHIFT_RIGHT -> keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
            SettingsManager.SCREEN_TRACKPAD_TRIGGER_SHIFT_EITHER ->
                keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
            SettingsManager.SCREEN_TRACKPAD_TRIGGER_SYM ->
                keyCode == KeyEvent.KEYCODE_SYM || keyCode == TITAN_KEYCODE_SYM
            else -> false
        }
    }

    // ---------------------------------------------------------------------------------------
    // Overlay
    // ---------------------------------------------------------------------------------------

    /** Returns true when the overlay is showing. */
    private fun activate(sticky: Boolean): Boolean {
        if (overlayView != null) return true
        if (!Settings.canDrawOverlays(service)) {
            Toast.makeText(service, R.string.screen_trackpad_permission_toast, Toast.LENGTH_LONG).show()
            return false
        }
        stepPx = SettingsManager.getScreenTrackpadStepPx(service)
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val view = object : FrameLayout(service) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleTouch(event)
                return true
            }
        }
        view.setBackgroundColor(Color.TRANSPARENT)
        view.isClickable = true

        if (SettingsManager.isScreenTrackpadHintEnabled(service)) {
            val hint = TextView(service).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                val padH = dp(14); val padV = dp(7)
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.argb(200, 20, 20, 20))
                }
                if (sticky) setOnClickListener { deactivate() }
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(56)
            }
            view.addView(hint, lp)
            hintView = hint
            updateHint(sticky)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        return try {
            wm.addView(view, params)
            windowManager = wm
            overlayView = view
            tracking = false
            accX = 0f; accY = 0f
            Log.d(TAG, "activated sticky=$sticky stepPx=$stepPx")
            true
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            hintView = null
            false
        }
    }

    fun deactivate() {
        handler.removeCallbacks(holdRunnable)
        val view = overlayView
        overlayView = null
        hintView = null
        if (view != null) {
            try { windowManager?.removeViewImmediate(view) } catch (e: Exception) { Log.w(TAG, "removeView failed", e) }
        }
        state = State.IDLE
        pendingRawDown = null
        tracking = false
    }

    private fun updateHint(sticky: Boolean) {
        val hint = hintView ?: return
        val selecting = isShiftActive()
        val res = when {
            selecting -> R.string.screen_trackpad_hint_select
            sticky -> R.string.screen_trackpad_hint_sticky
            else -> R.string.screen_trackpad_hint_cursor
        }
        hint.setText(res)
    }

    // ---------------------------------------------------------------------------------------
    // Drag → DPAD
    // ---------------------------------------------------------------------------------------

    private fun handleTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = true
                movedDuringGesture = false
                lastX = event.x; lastY = event.y
                accX = 0f; accY = 0f
                updateHint(state == State.ACTIVE_STICKY)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return
                accX += event.x - lastX
                accY += event.y - lastY
                lastX = event.x; lastY = event.y
                val shift = isShiftActive()
                val stepX = stepPx.toFloat()
                val stepY = stepPx * VERTICAL_STEP_MULTIPLIER
                var budget = MAX_STEPS_PER_EVENT
                while (abs(accX) >= stepX && budget-- > 0) {
                    val right = accX > 0
                    accX += if (right) -stepX else stepX
                    sendDpad(if (right) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT, shift)
                    movedDuringGesture = true
                }
                while (abs(accY) >= stepY && budget-- > 0) {
                    val down = accY > 0
                    accY += if (down) -stepY else stepY
                    sendDpad(if (down) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP, shift)
                    movedDuringGesture = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                accX = 0f; accY = 0f
            }
        }
    }

    private fun sendDpad(keyCode: Int, shift: Boolean) {
        val ic = inputConnectionProvider() ?: return
        val t = SystemClock.uptimeMillis()
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()

    companion object {
        private const val TAG = "ScreenTrackpad"
        private const val HOLD_THRESHOLD_MS = 250L
        private const val DOUBLE_TAP_THRESHOLD_MS = 400L
        private const val VERTICAL_STEP_MULTIPLIER = 2.0f
        private const val MAX_STEPS_PER_EVENT = 12
        /** Titan 2 delivers SYM as raw key code 63 (matches the service's KEYCODE_SYM constant). */
        private const val TITAN_KEYCODE_SYM = 63
    }
}
