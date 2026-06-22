package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.TextInputController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SoftwareKeyboardTextInputHandlerTest {

    private lateinit var context: Context
    private lateinit var textInputController: TextInputController

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        textInputController = TextInputController(
            context = context,
            modifierStateController = ModifierStateController(500L),
            doubleTapThreshold = 500L
        )
    }

    @Test
    fun doubleSpaceToPeriodDoesNotRunNormalBoundaryAfterReplacement() {
        SettingsManager.setDoubleSpaceToPeriod(context, true)
        val inputConnection = FakeInputConnection(context, "hello")
        var normalBoundaryCalls = 0
        var doubleSpaceHandledCalls = 0

        SoftwareKeyboardTextInputHandler.handleSpaceInput(
            textInputController = textInputController,
            inputConnection = inputConnection,
            shouldDisableDoubleSpaceToPeriod = false,
            shouldDisableAutoCapitalize = true,
            shouldDisableSuggestions = false,
            onDoubleSpaceHandled = { doubleSpaceHandledCalls++ },
            onNormalBoundary = {
                normalBoundaryCalls++
                false
            },
            onCommitSpace = { inputConnection.commitText(" ", 1) },
            onStatusBarUpdate = {}
        )

        assertEquals("hello ", inputConnection.text)
        assertEquals(1, normalBoundaryCalls)
        assertEquals(0, doubleSpaceHandledCalls)

        val handled = SoftwareKeyboardTextInputHandler.handleSpaceInput(
            textInputController = textInputController,
            inputConnection = inputConnection,
            shouldDisableDoubleSpaceToPeriod = false,
            shouldDisableAutoCapitalize = true,
            shouldDisableSuggestions = false,
            onDoubleSpaceHandled = { doubleSpaceHandledCalls++ },
            onNormalBoundary = {
                normalBoundaryCalls++
                inputConnection.commitText(" ", 1)
                true
            },
            onCommitSpace = { inputConnection.commitText(" ", 1) },
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("hello. ", inputConnection.text)
        assertEquals(1, normalBoundaryCalls)
        assertEquals(1, doubleSpaceHandledCalls)
    }

    @Test
    fun normalSpaceStillUsesBoundaryWhenDoubleSpaceDoesNotTrigger() {
        SettingsManager.setDoubleSpaceToPeriod(context, true)
        val inputConnection = FakeInputConnection(context, "hello")
        var boundaryCommitted = false

        SoftwareKeyboardTextInputHandler.handleSpaceInput(
            textInputController = textInputController,
            inputConnection = inputConnection,
            shouldDisableDoubleSpaceToPeriod = false,
            shouldDisableAutoCapitalize = true,
            shouldDisableSuggestions = false,
            onDoubleSpaceHandled = {},
            onNormalBoundary = {
                boundaryCommitted = true
                inputConnection.commitText(" ", 1)
                true
            },
            onCommitSpace = { inputConnection.commitText(" ", 1) },
            onStatusBarUpdate = {}
        )

        assertTrue(boundaryCommitted)
        assertEquals("hello ", inputConnection.text)
    }

    @Test
    fun suggestionsDisabledCommitsSpaceDirectly() {
        SettingsManager.setDoubleSpaceToPeriod(context, true)
        val inputConnection = FakeInputConnection(context, "hello")
        var normalBoundaryCalled = false

        SoftwareKeyboardTextInputHandler.handleSpaceInput(
            textInputController = textInputController,
            inputConnection = inputConnection,
            shouldDisableDoubleSpaceToPeriod = false,
            shouldDisableAutoCapitalize = true,
            shouldDisableSuggestions = true,
            onDoubleSpaceHandled = {},
            onNormalBoundary = {
                normalBoundaryCalled = true
                true
            },
            onCommitSpace = { inputConnection.commitText(" ", 1) },
            onStatusBarUpdate = {}
        )

        assertFalse(normalBoundaryCalled)
        assertEquals("hello ", inputConnection.text)
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)

        val text: String
            get() = buffer.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            return buffer.takeLast(n)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(deleteStart, buffer.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text ?: "")
            return true
        }
    }
}
