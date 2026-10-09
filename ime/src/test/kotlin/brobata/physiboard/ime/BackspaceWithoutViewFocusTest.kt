package brobata.physiboard.ime

import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.device.titan.TitanLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * text-input.md SS8.1, per-app-behavior.md D9: "When I open Messages I can start typing at once,
 * but Backspace does nothing until I tap the box." The box is connected to the keyboard but no
 * view has key focus, so a key handed back to the window reaches nothing; [MessagesBox] is that
 * box: it edits its own text for whatever key events arrive through the connection, and [window] stands for the window's own key path,
 * which has no focused view to give anything to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackspaceWithoutViewFocusTest {

    private val settings = Settings()
    private val field = FieldContext(FieldKind.NORMAL, isMultiLine = true)
    private val box = MessagesBox("hello")
    private val router = EditingKeyRouter()
    private val window = mutableListOf<KeyEvent>()
    private val pipeline = KeyboardPipeline(layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), settings), settings = ImeSettings.keyboardSettings(settings))
    private var clock = 1_000L

    init {
        pipeline.onStartInput(field, textBeforeCursor = box.text.toString())
    }

    /** What KeyboardSession.processKeyStroke does with one key: the pipeline, the ops, then the router for a key the pipeline let through; anything still unconsumed goes to the window. */
    private fun press(keyCode: Int, action: Int, repeat: Int = 0, meta: Int = 0, kind: FieldKind = FieldKind.NORMAL): Boolean {
        clock += 50
        val key = if (keyCode == KeyEvent.KEYCODE_DEL) KeyId.Control(ControlKey.BACKSPACE) else KeyId.Control(ControlKey.FORWARD_DELETE)
        val edge = if (action == KeyEvent.ACTION_DOWN) KeyEdge.DOWN else KeyEdge.UP
        val result = pipeline.onKeyStroke(KeyStroke(key, edge, repeat, clock, ModifierFlags()), EditorSnapshot(textBeforeCursor = box.beforeCursor(), nowMs = clock))
        val event = KeyEvent(clock, clock, action, keyCode, repeat, meta)
        val consumed = result.consumed || router.route(box, event, editableField = kind != FieldKind.NOT_EDITABLE, terminalMode = kind == FieldKind.RAW_MODE_APP)
        if (!consumed) window += event
        return consumed
    }

    @Test
    fun `Backspace reaches the connected box with no view focus`() {
        assertTrue(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN))
        assertTrue(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_UP))
        assertEquals("hell", box.text.toString())
        assertEquals(emptyList<KeyEvent>(), window)
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), box.received.map { it.action })
    }

    @Test
    fun `holding Backspace repeats through the connection`() {
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN, repeat = 1)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN, repeat = 2)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_UP)
        assertEquals("he", box.text.toString())
        assertEquals(listOf(0, 1, 2), box.received.filter { it.action == KeyEvent.ACTION_DOWN }.map { it.repeatCount })
    }

    @Test
    fun `with a selection Backspace deletes the selection, and the meta state goes along`() {
        box.select(1, 4)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN, meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_UP)
        assertEquals("ho", box.text.toString())
        assertTrue(box.received.first().isShiftPressed)
    }

    @Test
    fun `forward delete takes the same road`() {
        box.select(0, 0)
        press(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.ACTION_DOWN)
        press(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.ACTION_UP)
        assertEquals("ello", box.text.toString())
    }

    @Test
    fun `no editable field, or a terminal-mode app, keeps the window's path`() {
        assertFalse(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN, kind = FieldKind.NOT_EDITABLE))
        assertFalse(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_UP, kind = FieldKind.NOT_EDITABLE))
        assertFalse(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN, kind = FieldKind.RAW_MODE_APP))
        assertFalse(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_UP, kind = FieldKind.RAW_MODE_APP))
        assertEquals(4, window.size)
        assertEquals("hello", box.text.toString())
        assertEquals(emptyList<KeyEvent>(), box.received)
    }

    @Test
    fun `a connection that has gone hands the key back to the window`() {
        box.alive = false
        assertFalse(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN))
        assertFalse(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_UP))
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), window.map { it.action })
        assertEquals("hello", box.text.toString())
    }

    @Test
    fun `other keys are never taken off the window's path`() {
        val router = EditingKeyRouter()
        for (code in listOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_A)) {
            assertFalse(router.route(box, KeyEvent(KeyEvent.ACTION_DOWN, code), editableField = true, terminalMode = false))
        }
        // A release whose press went to the window stays with the window.
        assertFalse(router.route(box, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL), editableField = true, terminalMode = false))
    }

    /** A connected text box that applies the keys it is sent itself, with its own selection. */
    private class MessagesBox(initial: String) : InputConnection {
        val text = StringBuilder(initial)
        private var selStart = initial.length
        private var selEnd = initial.length
        val received = mutableListOf<KeyEvent>()

        /** An inactive connection answers false to everything, as the IME side does once the editor is gone. */
        var alive = true

        fun select(start: Int, end: Int) {
            selStart = start
            selEnd = end
        }

        fun beforeCursor(): String = text.substring(0, selStart)

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (!alive) return false
            received += event
            if (event.action != KeyEvent.ACTION_DOWN) return true
            when {
                selStart != selEnd -> text.delete(selStart, selEnd).also { selEnd = selStart }
                event.keyCode == KeyEvent.KEYCODE_DEL && selStart > 0 -> {
                    text.deleteCharAt(selStart - 1)
                    selStart--
                    selEnd = selStart
                }
                event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL && selStart < text.length -> text.deleteCharAt(selStart)
            }
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = beforeCursor().takeLast(n)
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = text.substring(selEnd).take(n)
        override fun getSelectedText(flags: Int): CharSequence? = if (selStart == selEnd) null else text.substring(selStart, selEnd)
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = true
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true
        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean = true
        override fun setComposingRegion(start: Int, end: Int): Boolean = true
        override fun finishComposingText(): Boolean = true
        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean = true
        override fun commitCompletion(text: CompletionInfo?): Boolean = true
        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true
        override fun setSelection(start: Int, end: Int): Boolean = true
        override fun performEditorAction(editorAction: Int): Boolean = true
        override fun performContextMenuAction(id: Int): Boolean = true
        override fun beginBatchEdit(): Boolean = true
        override fun endBatchEdit(): Boolean = true
        override fun clearMetaKeyStates(states: Int): Boolean = true
        override fun reportFullscreenMode(enabled: Boolean): Boolean = true
        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
        override fun getHandler(): Handler? = null
        override fun closeConnection() = Unit
        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
    }
}
