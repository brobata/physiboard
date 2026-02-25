package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InputEventRouterForwardDeleteAlternativesTest {

    private lateinit var context: Context
    private lateinit var router: InputEventRouter

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val modifierStateController = ModifierStateController(300L)
        val navModeController = NavModeController(context, modifierStateController)
        router = InputEventRouter(context, navModeController)

        SettingsManager.setShiftBackspaceDelete(context, false)
        SettingsManager.setAltBackspaceDelete(context, false)
        SettingsManager.setBackspaceAtStartDelete(context, false)
    }

    @Test
    fun defaults_doNotHandleBackspaceAsForwardDelete() {
        val ic = mockInputConnection()

        val handled = router.handleConfiguredForwardDeleteAlternatives(
            context = context,
            keyCode = KeyEvent.KEYCODE_DEL,
            event = keyDown(KeyEvent.KEYCODE_DEL),
            inputConnection = ic,
            altActive = false
        )

        assertFalse(handled)
        verify(ic, never()).deleteSurroundingText(0, 1)
    }

    @Test
    fun shiftBackspace_whenEnabled_performsForwardDelete() {
        SettingsManager.setShiftBackspaceDelete(context, true)
        val ic = mockInputConnection()

        val handled = router.handleConfiguredForwardDeleteAlternatives(
            context = context,
            keyCode = KeyEvent.KEYCODE_DEL,
            event = keyDown(KeyEvent.KEYCODE_DEL, KeyEvent.META_SHIFT_ON),
            inputConnection = ic,
            altActive = false
        )

        assertTrue(handled)
        verify(ic, times(1)).deleteSurroundingText(0, 1)
    }

    @Test
    fun altBackspace_whenEnabled_andAltLatched_performsForwardDelete() {
        SettingsManager.setAltBackspaceDelete(context, true)
        val ic = mockInputConnection()

        val handled = router.handleConfiguredForwardDeleteAlternatives(
            context = context,
            keyCode = KeyEvent.KEYCODE_DEL,
            event = keyDown(KeyEvent.KEYCODE_DEL),
            inputConnection = ic,
            altActive = true
        )

        assertTrue(handled)
        verify(ic, times(1)).deleteSurroundingText(0, 1)
    }

    @Test
    fun altBackspace_whenDisabled_doesNotTriggerEvenIfAltActive() {
        val ic = mockInputConnection()

        val handled = router.handleConfiguredForwardDeleteAlternatives(
            context = context,
            keyCode = KeyEvent.KEYCODE_DEL,
            event = keyDown(KeyEvent.KEYCODE_DEL),
            inputConnection = ic,
            altActive = true
        )

        assertFalse(handled)
        verify(ic, never()).deleteSurroundingText(0, 1)
    }

    @Test
    fun backspaceAtLineStart_whenEnabled_andCursorAtStart_performsForwardDelete() {
        SettingsManager.setBackspaceAtStartDelete(context, true)
        val ic = mockInputConnection(textBeforeCursor = "")

        val handled = router.handleConfiguredForwardDeleteAlternatives(
            context = context,
            keyCode = KeyEvent.KEYCODE_DEL,
            event = keyDown(KeyEvent.KEYCODE_DEL),
            inputConnection = ic,
            altActive = false
        )

        assertTrue(handled)
        verify(ic, times(1)).deleteSurroundingText(0, 1)
        verify(ic, times(1)).getTextBeforeCursor(1, 0)
    }

    @Test
    fun backspaceAtLineStart_whenEnabled_butNotAtStart_doesNotTrigger() {
        SettingsManager.setBackspaceAtStartDelete(context, true)
        val ic = mockInputConnection(textBeforeCursor = "x")

        val handled = router.handleConfiguredForwardDeleteAlternatives(
            context = context,
            keyCode = KeyEvent.KEYCODE_DEL,
            event = keyDown(KeyEvent.KEYCODE_DEL),
            inputConnection = ic,
            altActive = false
        )

        assertFalse(handled)
        verify(ic, never()).deleteSurroundingText(0, 1)
        verify(ic, times(1)).getTextBeforeCursor(1, 0)
    }

    @Test
    fun lineStartDelete_doesNotRunWhenShiftPressed_withoutShiftMapping() {
        SettingsManager.setBackspaceAtStartDelete(context, true)
        val ic = mockInputConnection(textBeforeCursor = "")

        val handled = router.handleConfiguredForwardDeleteAlternatives(
            context = context,
            keyCode = KeyEvent.KEYCODE_DEL,
            event = keyDown(KeyEvent.KEYCODE_DEL, KeyEvent.META_SHIFT_ON),
            inputConnection = ic,
            altActive = false
        )

        assertFalse(handled)
        verify(ic, never()).getTextBeforeCursor(1, 0)
        verify(ic, never()).deleteSurroundingText(0, 1)
    }

    @Test
    fun lineStartDelete_doesNotRunWhenAltActive_withoutAltMapping() {
        SettingsManager.setBackspaceAtStartDelete(context, true)
        val ic = mockInputConnection(textBeforeCursor = "")

        val handled = router.handleConfiguredForwardDeleteAlternatives(
            context = context,
            keyCode = KeyEvent.KEYCODE_DEL,
            event = keyDown(KeyEvent.KEYCODE_DEL),
            inputConnection = ic,
            altActive = true
        )

        assertFalse(handled)
        verify(ic, never()).getTextBeforeCursor(1, 0)
        verify(ic, never()).deleteSurroundingText(0, 1)
    }

    @Test
    fun shiftAndAltEnabled_triggersSingleForwardDelete() {
        SettingsManager.setShiftBackspaceDelete(context, true)
        SettingsManager.setAltBackspaceDelete(context, true)
        val ic = mockInputConnection()

        val handled = router.handleConfiguredForwardDeleteAlternatives(
            context = context,
            keyCode = KeyEvent.KEYCODE_DEL,
            event = keyDown(KeyEvent.KEYCODE_DEL, KeyEvent.META_SHIFT_ON),
            inputConnection = ic,
            altActive = true
        )

        assertTrue(handled)
        verify(ic, times(1)).deleteSurroundingText(0, 1)
    }

    private fun mockInputConnection(textBeforeCursor: CharSequence? = null): InputConnection {
        val ic = mock(InputConnection::class.java)
        `when`(ic.deleteSurroundingText(0, 1)).thenReturn(true)
        `when`(ic.getTextBeforeCursor(1, 0)).thenReturn(textBeforeCursor)
        return ic
    }

    private fun keyDown(keyCode: Int, metaState: Int = 0): KeyEvent {
        return KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
    }
}
