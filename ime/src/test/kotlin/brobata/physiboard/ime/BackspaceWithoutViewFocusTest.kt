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
    fun `Backspace deletes in a connected box with no view focus`() {
        assertTrue(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN))
        assertTrue(press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_UP))
        assertEquals("hell", box.text.toString())
        assertEquals(emptyList<KeyEvent>(), window)
        assertEquals("no key event: an app with no focused view drops them", emptyList<KeyEvent>(), box.received)
    }

    @Test
    fun `holding Backspace repeats as text edits`() {
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN, repeat = 1)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN, repeat = 2)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_UP)
        assertEquals("he", box.text.toString())
        assertEquals(emptyList<KeyEvent>(), box.received)
    }

    @Test
    fun `with a selection Backspace deletes the selection`() {
        box.select(1, 4)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_DOWN, meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        press(KeyEvent.KEYCODE_DEL, KeyEvent.ACTION_UP)
        assertEquals("ho", box.text.toString())
    }

    @Test
    fun `forward delete takes the same road`() {
        box.select(0, 0)
        press(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.ACTION_DOWN)
        press(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.ACTION_UP)
        assertEquals("ello", box.text.toString())
    }

    @Test
    fun `an emoji with a skin tone goes in one press`() {
        val emojiBox = MessagesBox("hi \uD83D\uDC4D\uD83C\uDFFD")
        val router = EditingKeyRouter()
        assertTrue(router.route(emojiBox, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL), editableField = true, terminalMode = false))
        assertEquals("hi ", emojiBox.text.toString())
    }

    @Test
    fun `an empty box, or one that won't say what's in it, gets the key itself`() {
        // A chip field deletes its last chip on the key; a web field answers "" to every read.
        val empty = MessagesBox("")
        val router = EditingKeyRouter()
        assertTrue(router.route(empty, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL), editableField = true, terminalMode = false))
        assertTrue(router.route(empty, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL), editableField = true, terminalMode = false))
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), empty.received.map { it.action })
        val silent = MessagesBox("hello").apply { readable = false }
        assertTrue(router.route(silent, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL), editableField = true, terminalMode = false))
        assertEquals(listOf(KeyEvent.ACTION_DOWN), silent.received.map { it.action })
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

    /**
     * A connected text box as a messaging app has it right after opening: text edits through the
     * connection apply, but key events are dropped, because no view holds key focus yet (Titan,
     * Google Messages, 2026-10-09). The first fix passed against a fake that applied key events,
     * and failed on the phone.
     */
    private class MessagesBox(initial: String) : InputConnection {
        val text = StringBuilder(initial)
        private var selStart = initial.length
        private var selEnd = initial.length
        val received = mutableListOf<KeyEvent>()

        /** An inactive connection answers false or null to everything, as the IME side does once the editor is gone. */
        var alive = true

        /** False: the editor answers reads with null (some web fields do). */
        var readable = true

        fun select(start: Int, end: Int) {
            selStart = start
            selEnd = end
        }

        fun beforeCursor(): String = text.substring(0, selStart)

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (!alive) return false
            received += event // delivered to the app, which has no focused view: nothing happens
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = if (!alive || !readable) null else beforeCursor().takeLast(n)
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = if (!alive || !readable) null else text.substring(selEnd).take(n)
        override fun getSelectedText(flags: Int): CharSequence? = if (!alive || !readable || selStart == selEnd) null else text.substring(selStart, selEnd)
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (!alive) return false
            val start = (selStart - beforeLength).coerceAtLeast(0)
            val end = (selEnd + afterLength).coerceAtMost(text.length)
            text.delete(selEnd, end)
            text.delete(start, selStart)
            selStart = start
            selEnd = start
            return true
        }
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = alive
        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean = alive
        override fun setComposingRegion(start: Int, end: Int): Boolean = alive
        override fun finishComposingText(): Boolean = alive
        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            if (!alive) return false
            this.text.replace(selStart, selEnd, text.toString())
            selStart += text.length
            selEnd = selStart
            return true
        }
        override fun commitCompletion(text: CompletionInfo?): Boolean = alive
        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = alive
        override fun setSelection(start: Int, end: Int): Boolean = alive
        override fun performEditorAction(editorAction: Int): Boolean = alive
        override fun performContextMenuAction(id: Int): Boolean = alive
        override fun beginBatchEdit(): Boolean = alive
        override fun endBatchEdit(): Boolean = alive
        override fun clearMetaKeyStates(states: Int): Boolean = alive
        override fun reportFullscreenMode(enabled: Boolean): Boolean = alive
        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = alive
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = alive
        override fun getHandler(): Handler? = null
        override fun closeConnection() = Unit
        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
    }
}
