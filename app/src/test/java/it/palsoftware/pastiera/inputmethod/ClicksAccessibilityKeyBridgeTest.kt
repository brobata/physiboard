package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClicksAccessibilityKeyBridgeTest {
    private var registeredTarget: ClicksAccessibilityKeyBridge.Target? = null

    @After
    fun tearDown() {
        registeredTarget?.let(ClicksAccessibilityKeyBridge::unregister)
    }

    @Test
    fun dispatchFailsOpenWithoutTarget() {
        assertFalse(ClicksAccessibilityKeyBridge.dispatch(keyDown()))
    }

    @Test
    fun dispatchReportsWhetherActiveTargetAcceptedEvent() {
        val rejectingTarget = target(accept = false)
        ClicksAccessibilityKeyBridge.register(rejectingTarget)
        registeredTarget = rejectingTarget
        assertFalse(ClicksAccessibilityKeyBridge.dispatch(keyDown()))

        ClicksAccessibilityKeyBridge.unregister(rejectingTarget)
        val acceptingTarget = target(accept = true)
        ClicksAccessibilityKeyBridge.register(acceptingTarget)
        registeredTarget = acceptingTarget
        assertTrue(ClicksAccessibilityKeyBridge.dispatch(keyDown()))
    }

    @Test
    fun directActionsReachTheActiveInputMethod() {
        val acceptingTarget = target(accept = true)
        ClicksAccessibilityKeyBridge.register(acceptingTarget)
        registeredTarget = acceptingTarget

        assertTrue(
            ClicksAccessibilityKeyBridge.dispatch(ClicksButtonDirectAction.TOGGLE_EMOJI_PICKER)
        )
    }

    private fun target(accept: Boolean) = object : ClicksAccessibilityKeyBridge.Target {
        override fun dispatchClicksAccessibilityKeyEvent(event: KeyEvent): Boolean = accept
        override fun dispatchClicksDirectAction(action: ClicksButtonDirectAction): Boolean = accept
    }

    private fun keyDown() = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U)
}
