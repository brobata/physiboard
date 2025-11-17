package it.palsoftware.pastiera.inputmethod

import android.util.Log
import android.view.KeyEvent

/**
 * Handles modifier key state management and double-tap detection
 * for Shift (Caps Lock), Ctrl (latch), and Alt (latch).
 */
class ModifierKeyHandler(
    private val doubleTapThreshold: Long = 500L
) {
    companion object {
        private const val TAG = "ModifierKeyHandler"
    }

    data class ShiftState(
        var pressed: Boolean = false,
        var oneShot: Boolean = false,
        var latchActive: Boolean = false, // Caps Lock for Shift
        var physicallyPressed: Boolean = false,
        var lastReleaseTime: Long = 0,
        var oneShotEnabledTime: Long = 0
    )

    data class CtrlState(
        var pressed: Boolean = false,
        var oneShot: Boolean = false,
        var latchActive: Boolean = false,
        var physicallyPressed: Boolean = false,
        var lastReleaseTime: Long = 0,
        var latchFromNavMode: Boolean = false
    )

    data class AltState(
        var pressed: Boolean = false,
        var oneShot: Boolean = false,
        var latchActive: Boolean = false,
        var physicallyPressed: Boolean = false,
        var lastReleaseTime: Long = 0
    )

    data class ModifierKeyResult(
        val shouldConsume: Boolean = false,
        val shouldUpdateStatusBar: Boolean = false,
        val shouldRefreshStatusBar: Boolean = false
    )

    // ========== Shift Handling ==========

    /**
     * Handles Shift key press with the following behavior:
     * 1. Single tap when inactive -> enable shift one-shot (latch mode, remains active until used)
     * 2. Double tap -> enable Caps Lock
     * 3. Single tap when shift one-shot is active -> disable shift one-shot
     * 4. Single tap when Caps Lock is active -> disable Caps Lock
     */
    fun handleShiftKeyDown(
        keyCode: Int,
        state: ShiftState
    ): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_SHIFT_LEFT && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            return ModifierKeyResult()
        }

        state.physicallyPressed = true
        val currentTime = System.currentTimeMillis()

        when {
            state.latchActive -> {
                // Caps Lock active: single tap disables it
                state.latchActive = false
                state.lastReleaseTime = 0
                return ModifierKeyResult(shouldUpdateStatusBar = true)
            }
            state.oneShot -> {
                // Shift one-shot is active: check for double-tap to enable Caps Lock
                if (currentTime - state.lastReleaseTime < doubleTapThreshold && state.lastReleaseTime > 0) {
                    // Double-tap: enable Caps Lock and disable one-shot
                    state.oneShot = false
                    state.oneShotEnabledTime = 0
                    state.latchActive = true
                    state.lastReleaseTime = 0
                    Log.d(TAG, "Shift double-tap: one-shot -> Caps Lock")
                    return ModifierKeyResult(shouldRefreshStatusBar = true)
                } else {
                    // Single tap while one-shot is active: disable one-shot
                    state.oneShot = false
                    state.oneShotEnabledTime = 0
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldRefreshStatusBar = true)
                }
            }
            else -> {
                // No active state: check for double-tap to enable Caps Lock
                if (currentTime - state.lastReleaseTime < doubleTapThreshold && state.lastReleaseTime > 0) {
                    // Double-tap: enable Caps Lock
                    state.latchActive = true
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Single tap: enable shift one-shot (latch mode, stays active until used)
                    state.oneShot = true
                    state.oneShotEnabledTime = currentTime
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
        }
    }

    /**
     * Handles Shift key release.
     * IMPORTANT: Does NOT disable shift one-shot - it remains active until used (when a letter is typed).
     * Only tracks the release time for double-tap detection.
     */
    fun handleShiftKeyUp(keyCode: Int, state: ShiftState): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_SHIFT_LEFT && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            // Record release time for double-tap detection
            // BUT: do NOT disable shift one-shot here - it stays active until used
            state.lastReleaseTime = System.currentTimeMillis()
            state.pressed = false
            state.physicallyPressed = false
            // Only update status bar to reflect physical release, not one-shot state change
            return ModifierKeyResult(shouldUpdateStatusBar = true)
        }
        return ModifierKeyResult()
    }

    // ========== Ctrl Handling ==========

    fun handleCtrlKeyDown(
        keyCode: Int,
        state: CtrlState,
        isInputViewActive: Boolean,
        onNavModeDeactivated: (() -> Unit)? = null
    ): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_CTRL_LEFT && keyCode != KeyEvent.KEYCODE_CTRL_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            return ModifierKeyResult()
        }

        state.physicallyPressed = true
        val currentTime = System.currentTimeMillis()

        when {
            state.latchActive -> {
                // Latch active: single tap disables it
                // Special handling for nav mode
                if (state.latchFromNavMode && !isInputViewActive) {
                    state.latchActive = false
                    state.latchFromNavMode = false
                    onNavModeDeactivated?.invoke()
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldConsume = true)
                } else if (!state.latchFromNavMode) {
                    state.latchActive = false
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Nav mode in text field (should not happen)
                    state.latchActive = false
                    state.latchFromNavMode = false
                    onNavModeDeactivated?.invoke()
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
            state.oneShot -> {
                // One-shot active: check for double-tap
                if (currentTime - state.lastReleaseTime < doubleTapThreshold && state.lastReleaseTime > 0) {
                    state.oneShot = false
                    state.latchActive = true
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Single tap: disable one-shot
                    state.oneShot = false
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
            else -> {
                // Check for double-tap to enable latch
                if (currentTime - state.lastReleaseTime < doubleTapThreshold && state.lastReleaseTime > 0) {
                    state.latchActive = true
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Single tap: enable one-shot
                    state.oneShot = true
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
        }
    }

    fun handleCtrlKeyUp(keyCode: Int, state: CtrlState): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_CTRL_LEFT && keyCode != KeyEvent.KEYCODE_CTRL_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            state.lastReleaseTime = System.currentTimeMillis()
            state.pressed = false
            state.physicallyPressed = false
            return ModifierKeyResult(shouldUpdateStatusBar = true)
        }
        return ModifierKeyResult()
    }

    // ========== Alt Handling ==========

    fun handleAltKeyDown(
        keyCode: Int,
        state: AltState
    ): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_ALT_LEFT && keyCode != KeyEvent.KEYCODE_ALT_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            return ModifierKeyResult()
        }

        state.physicallyPressed = true
        val currentTime = System.currentTimeMillis()

        when {
            state.latchActive -> {
                // Latch active: single tap disables it
                state.latchActive = false
                state.lastReleaseTime = 0
                return ModifierKeyResult(shouldUpdateStatusBar = true)
            }
            state.oneShot -> {
                // One-shot active: check for double-tap
                if (currentTime - state.lastReleaseTime < doubleTapThreshold && state.lastReleaseTime > 0) {
                    state.oneShot = false
                    state.latchActive = true
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Single tap: disable one-shot
                    state.oneShot = false
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
            else -> {
                // Check for double-tap to enable latch
                if (currentTime - state.lastReleaseTime < doubleTapThreshold && state.lastReleaseTime > 0) {
                    state.latchActive = true
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Single tap: enable one-shot
                    state.oneShot = true
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
        }
    }

    fun handleAltKeyUp(keyCode: Int, state: AltState): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_ALT_LEFT && keyCode != KeyEvent.KEYCODE_ALT_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            state.lastReleaseTime = System.currentTimeMillis()
            state.pressed = false
            state.physicallyPressed = false
            return ModifierKeyResult(shouldUpdateStatusBar = true)
        }
        return ModifierKeyResult()
    }

    // ========== Reset Helpers ==========

    fun resetShiftState(state: ShiftState) {
        state.pressed = false
        state.oneShot = false
        state.oneShotEnabledTime = 0
        state.lastReleaseTime = 0
    }

    fun resetCtrlState(state: CtrlState, preserveNavMode: Boolean = false) {
        if (!preserveNavMode || !state.latchFromNavMode) {
            state.latchActive = false
            state.latchFromNavMode = false
        }
        state.pressed = false
        state.oneShot = false
        state.lastReleaseTime = 0
    }

    fun resetAltState(state: AltState) {
        state.pressed = false
        state.oneShot = false
        state.latchActive = false
        state.lastReleaseTime = 0
    }
}

