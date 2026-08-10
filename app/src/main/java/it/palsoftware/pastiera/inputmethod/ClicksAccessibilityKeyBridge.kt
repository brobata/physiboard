package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent

/** Connects the optional system key filter to the currently active Pastiera input method. */
internal object ClicksAccessibilityKeyBridge {
    interface Target {
        fun dispatchClicksAccessibilityKeyEvent(event: KeyEvent): Boolean
        fun dispatchClicksDirectAction(action: ClicksButtonDirectAction): Boolean
    }

    @Volatile
    private var target: Target? = null

    fun register(target: Target) {
        this.target = target
    }

    fun unregister(target: Target) {
        if (this.target === target) this.target = null
    }

    fun dispatch(event: KeyEvent): Boolean =
        target?.dispatchClicksAccessibilityKeyEvent(event) == true

    fun dispatch(action: ClicksButtonDirectAction): Boolean =
        target?.dispatchClicksDirectAction(action) == true
}
