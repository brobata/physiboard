package it.palsoftware.pastiera.core

import it.palsoftware.pastiera.inputmethod.AutoCapitalizeHelper
import it.palsoftware.pastiera.inputmethod.ModifierKeyHandler

/**
 * Centralizes modifier key state (Shift/Ctrl/Alt) and keeps one-shot / latch
 * bookkeeping in sync with auto-capitalization helpers.
 */
class ModifierStateController(
    doubleTapThreshold: Long,
    private val autoCapitalizeState: AutoCapitalizeHelper.AutoCapitalizeState
) {
    private val modifierKeyHandler = ModifierKeyHandler(doubleTapThreshold)

    private val shiftState = ModifierKeyHandler.ShiftState()
    private val ctrlState = ModifierKeyHandler.CtrlState()
    private val altState = ModifierKeyHandler.AltState()

    data class Snapshot(
        val capsLockEnabled: Boolean,
        val shiftPhysicallyPressed: Boolean,
        val shiftOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlPhysicallyPressed: Boolean,
        val ctrlOneShot: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altLatchActive: Boolean,
        val altPhysicallyPressed: Boolean,
        val altOneShot: Boolean
    )

    var capsLockEnabled: Boolean
        get() = shiftState.latchActive
        set(value) { shiftState.latchActive = value }

    var shiftPressed: Boolean
        get() = shiftState.pressed
        set(value) { shiftState.pressed = value }

    var shiftPhysicallyPressed: Boolean
        get() = shiftState.physicallyPressed
        set(value) { shiftState.physicallyPressed = value }

    var shiftOneShot: Boolean
        get() = autoCapitalizeState.shiftOneShot
        set(value) {
            autoCapitalizeState.shiftOneShot = value
            syncShiftOneShotToShiftState()
        }

    var shiftOneShotEnabledTime: Long
        get() = autoCapitalizeState.shiftOneShotEnabledTime
        set(value) {
            autoCapitalizeState.shiftOneShotEnabledTime = value
            syncShiftOneShotToShiftState()
        }

    var ctrlLatchActive: Boolean
        get() = ctrlState.latchActive
        set(value) { ctrlState.latchActive = value }

    var ctrlPressed: Boolean
        get() = ctrlState.pressed
        set(value) { ctrlState.pressed = value }

    var ctrlPhysicallyPressed: Boolean
        get() = ctrlState.physicallyPressed
        set(value) { ctrlState.physicallyPressed = value }

    var ctrlOneShot: Boolean
        get() = ctrlState.oneShot
        set(value) { ctrlState.oneShot = value }

    var ctrlLatchFromNavMode: Boolean
        get() = ctrlState.latchFromNavMode
        set(value) { ctrlState.latchFromNavMode = value }

    var ctrlLastReleaseTime: Long
        get() = ctrlState.lastReleaseTime
        set(value) { ctrlState.lastReleaseTime = value }

    var altLatchActive: Boolean
        get() = altState.latchActive
        set(value) { altState.latchActive = value }

    var altPressed: Boolean
        get() = altState.pressed
        set(value) { altState.pressed = value }

    var altPhysicallyPressed: Boolean
        get() = altState.physicallyPressed
        set(value) { altState.physicallyPressed = value }

    var altOneShot: Boolean
        get() = altState.oneShot
        set(value) { altState.oneShot = value }

    fun snapshot(): Snapshot {
        return Snapshot(
            capsLockEnabled = capsLockEnabled,
            shiftPhysicallyPressed = shiftPhysicallyPressed,
            shiftOneShot = autoCapitalizeState.shiftOneShot,
            ctrlLatchActive = ctrlLatchActive,
            ctrlPhysicallyPressed = ctrlPhysicallyPressed,
            ctrlOneShot = ctrlOneShot,
            ctrlLatchFromNavMode = ctrlLatchFromNavMode,
            altLatchActive = altLatchActive,
            altPhysicallyPressed = altPhysicallyPressed,
            altOneShot = altOneShot
        )
    }

    fun handleShiftKeyDown(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        if (shiftPressed) {
            return ModifierKeyHandler.ModifierKeyResult()
        }
        val result = modifierKeyHandler.handleShiftKeyDown(keyCode, shiftState)
        shiftPressed = true
        syncShiftOneShotFromShiftState()
        return result
    }

    fun handleShiftKeyUp(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        val result = modifierKeyHandler.handleShiftKeyUp(keyCode, shiftState)
        shiftPressed = false
        syncShiftOneShotFromShiftState()
        return result
    }

    fun handleCtrlKeyDown(
        keyCode: Int,
        isInputViewActive: Boolean,
        onNavModeDeactivated: (() -> Unit)? = null
    ): ModifierKeyHandler.ModifierKeyResult {
        if (ctrlPressed) {
            return ModifierKeyHandler.ModifierKeyResult()
        }
        val result = modifierKeyHandler.handleCtrlKeyDown(
            keyCode,
            ctrlState,
            isInputViewActive,
            onNavModeDeactivated
        )
        ctrlPressed = true
        return result
    }

    fun handleCtrlKeyUp(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        val result = modifierKeyHandler.handleCtrlKeyUp(keyCode, ctrlState)
        ctrlPressed = false
        return result
    }

    fun handleAltKeyDown(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        if (altPressed) {
            return ModifierKeyHandler.ModifierKeyResult()
        }
        val result = modifierKeyHandler.handleAltKeyDown(keyCode, altState)
        altPressed = true
        return result
    }

    fun handleAltKeyUp(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        val result = modifierKeyHandler.handleAltKeyUp(keyCode, altState)
        altPressed = false
        return result
    }

    fun resetModifiers(
        preserveNavMode: Boolean,
        onNavModeCancelled: () -> Unit
    ) {
        val savedCtrlLatch = if (preserveNavMode && (ctrlLatchActive || ctrlLatchFromNavMode)) {
            if (ctrlLatchActive) {
                ctrlLatchFromNavMode = true
                true
            } else {
                ctrlLatchFromNavMode
            }
        } else {
            false
        }

        modifierKeyHandler.resetShiftState(shiftState)
        capsLockEnabled = false
        AutoCapitalizeHelper.reset(autoCapitalizeState)

        if (preserveNavMode && savedCtrlLatch) {
            ctrlLatchActive = true
        } else {
            if (ctrlLatchFromNavMode || ctrlLatchActive) {
                onNavModeCancelled()
            }
            modifierKeyHandler.resetCtrlState(ctrlState, preserveNavMode = false)
        }
        modifierKeyHandler.resetAltState(altState)
    }

    fun syncShiftOneShotFromShiftState() {
        autoCapitalizeState.shiftOneShot = shiftState.oneShot
        if (shiftState.oneShot && shiftState.oneShotEnabledTime > 0) {
            autoCapitalizeState.shiftOneShotEnabledTime = shiftState.oneShotEnabledTime
        } else if (!shiftState.oneShot) {
            autoCapitalizeState.shiftOneShotEnabledTime = 0
        }
    }

    fun syncShiftOneShotToShiftState() {
        shiftState.oneShot = autoCapitalizeState.shiftOneShot
        if (autoCapitalizeState.shiftOneShot && autoCapitalizeState.shiftOneShotEnabledTime > 0) {
            shiftState.oneShotEnabledTime = autoCapitalizeState.shiftOneShotEnabledTime
        } else if (!autoCapitalizeState.shiftOneShot) {
            shiftState.oneShotEnabledTime = 0
        }
    }
}

