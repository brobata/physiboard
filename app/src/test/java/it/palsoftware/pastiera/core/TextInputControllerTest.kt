package it.palsoftware.pastiera.core

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.View
import it.palsoftware.pastiera.SettingsManager
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
class TextInputControllerTest {

    private lateinit var context: Context
    private lateinit var modifierStateController: ModifierStateController
    private lateinit var controller: TextInputController

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        modifierStateController = ModifierStateController(500L)
        controller = TextInputController(
            context = context,
            modifierStateController = modifierStateController,
            doubleTapThreshold = 500L
        )
    }

    @Test
    fun doubleSpaceToPeriod_enabled_replacesVirtualKeyboardSecondSpace() {
        SettingsManager.setDoubleSpaceToPeriod(context, true)
        val inputConnection = FakeInputConnection(context, "hello")

        val firstHandled = controller.handleDoubleSpaceToPeriod(
            keyCode = KeyEvent.KEYCODE_SPACE,
            inputConnection = inputConnection,
            shouldDisableDoubleSpaceToPeriod = false,
            shouldDisableAutoCapitalize = true,
            onStatusBarUpdate = {}
        )
        if (!firstHandled) inputConnection.commitText(" ", 1)

        val secondHandled = controller.handleDoubleSpaceToPeriod(
            keyCode = KeyEvent.KEYCODE_SPACE,
            inputConnection = inputConnection,
            shouldDisableDoubleSpaceToPeriod = false,
            shouldDisableAutoCapitalize = true,
            onStatusBarUpdate = {}
        )

        assertFalse(firstHandled)
        assertTrue(secondHandled)
        assertEquals("hello. ", inputConnection.text)
    }

    @Test
    fun doubleSpaceToPeriod_settingDisabled_keepsVirtualKeyboardSpaces() {
        SettingsManager.setDoubleSpaceToPeriod(context, false)
        val inputConnection = FakeInputConnection(context, "hello")

        repeat(2) {
            val handled = controller.handleDoubleSpaceToPeriod(
                keyCode = KeyEvent.KEYCODE_SPACE,
                inputConnection = inputConnection,
                shouldDisableDoubleSpaceToPeriod = false,
                shouldDisableAutoCapitalize = true,
                onStatusBarUpdate = {}
            )
            if (!handled) inputConnection.commitText(" ", 1)
        }

        assertEquals("hello  ", inputConnection.text)
    }

    @Test
    fun doubleSpaceToPeriod_fieldDisabled_keepsVirtualKeyboardSpacesEvenWhenSettingEnabled() {
        SettingsManager.setDoubleSpaceToPeriod(context, true)
        val inputConnection = FakeInputConnection(context, "hello")

        repeat(2) {
            val handled = controller.handleDoubleSpaceToPeriod(
                keyCode = KeyEvent.KEYCODE_SPACE,
                inputConnection = inputConnection,
                shouldDisableDoubleSpaceToPeriod = true,
                shouldDisableAutoCapitalize = true,
                onStatusBarUpdate = {}
            )
            if (!handled) inputConnection.commitText(" ", 1)
        }

        assertEquals("hello  ", inputConnection.text)
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
