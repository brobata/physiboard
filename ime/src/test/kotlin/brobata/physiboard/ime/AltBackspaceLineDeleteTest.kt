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
import brobata.physiboard.core.keys.AltBackspaceAction
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.TypingPrefs
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.device.titan.TitanLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * keys-and-modifiers.md SS7.7: Alt+Backspace with `alt_backspace_delete` = `line`, from the key
 * through [KeyboardPipeline], the real [readEditorState] and [applyEditorOps], and the Backspace
 * router, the way KeyboardSession.processKeyStroke drives them, into a fake editor that behaves
 * like Android's: deleteSurroundingText works around the selection, extracted text reports it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AltBackspaceLineDeleteTest {

    private val normal = FieldContext(FieldKind.NORMAL, isMultiLine = true)
    private val terminal = FieldContext(FieldKind.RAW_MODE_APP, isMultiLine = true)

    private inner class Phone(initial: String, field: FieldContext = normal, choice: AltBackspaceAction = AltBackspaceAction.DELETE_TO_LINE_START) {
        private val settings = Settings(typing = TypingPrefs(altBackspace = choice))
        val pipeline = KeyboardPipeline(layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), settings), settings = ImeSettings.keyboardSettings(settings))
        val box = Editor(initial)
        private val router = EditingKeyRouter()
        private val terminalMode = field.kind == FieldKind.RAW_MODE_APP
        private var clock = 1_000L
        private var altHeld = false

        init {
            pipeline.onStartInput(field, textBeforeCursor = box.beforeCursor())
        }

        private fun stroke(key: KeyId, edge: KeyEdge, meta: ModifierFlags = ModifierFlags(alt = altHeld)): Boolean {
            clock += 80
            val stroke = KeyStroke(key, edge, 0, clock, meta)
            val reads = if (edge == KeyEdge.DOWN && key !is KeyId.Modifier) {
                box.readEditorState(clock, wholeDocument = pipeline.needsWholeDocument(stroke), fallbackCursorAbsolute = 0, textBeforeWindow = pipeline.textBeforeWindow(stroke))
            } else {
                null
            }
            val snapshot = reads?.snapshot ?: brobata.physiboard.core.text.EditorSnapshot(textBeforeCursor = null, nowMs = clock)
            val result = pipeline.onKeyStroke(stroke, snapshot)
            box.applyEditorOps(result.ops, reads?.documentStartOffset ?: 0, reads?.cursorAbsolute ?: 0, sendSpaceKeyFallback = {}, haptic = {})
            if (key !is KeyId.Control) return result.consumed
            val action = if (edge == KeyEdge.DOWN) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            val metaState = if (meta.alt) KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON else 0
            val event = KeyEvent(clock, clock, action, KeyEvent.KEYCODE_DEL, 0, metaState)
            return result.consumed || router.route(box, event, editableField = true, terminalMode = terminalMode)
        }

        fun backspace(meta: ModifierFlags = ModifierFlags(alt = altHeld)) {
            stroke(BACKSPACE, KeyEdge.DOWN, meta)
            stroke(BACKSPACE, KeyEdge.UP, meta)
        }

        fun altBackspace() {
            altHeld = true
            stroke(ALT, KeyEdge.DOWN)
            backspace()
            altHeld = false
            stroke(ALT, KeyEdge.UP)
        }

        fun tapAlt() {
            altHeld = true
            stroke(ALT, KeyEdge.DOWN)
            altHeld = false
            stroke(ALT, KeyEdge.UP)
        }
    }

    @Test
    fun `mid-line, held Alt deletes back to the start of the line in one read`() {
        val phone = Phone("Dear Sam,\nthanks for the")
        phone.altBackspace()
        assertEquals("Dear Sam,\n", phone.box.text.toString())
        assertEquals(listOf(LINE_DELETE_WINDOW), phone.box.beforeReads)
    }

    @Test
    fun `pressed again at the line start it joins the line above, then takes that line`() {
        val phone = Phone("Dear Sam,\nthanks")
        phone.altBackspace()
        phone.altBackspace()
        assertEquals("Dear Sam,", phone.box.text.toString())
        phone.altBackspace()
        assertEquals("", phone.box.text.toString())
    }

    @Test
    fun `on the first line everything before the cursor goes`() {
        val phone = Phone("hello world")
        phone.altBackspace()
        assertEquals("", phone.box.text.toString())
    }

    @Test
    fun `text after the cursor stays`() {
        val phone = Phone("one\ntwo three").apply { box.select(7, 7) }
        phone.altBackspace()
        assertEquals("one\n three", phone.box.text.toString())
    }

    @Test
    fun `a selection is deleted, nothing else`() {
        val phone = Phone("one\ntwo three").apply { box.select(4, 7) }
        phone.altBackspace()
        assertEquals("one\n three", phone.box.text.toString())
    }

    @Test
    fun `an empty field still gets the key, for a chip field that deletes on it`() {
        val phone = Phone("")
        phone.altBackspace()
        assertEquals("", phone.box.text.toString())
        assertEquals(listOf(KeyEvent.KEYCODE_DEL), phone.box.received.filter { it.action == KeyEvent.ACTION_DOWN }.map { it.keyCode })
    }

    @Test
    fun `an emoji at the start of the line goes whole and the line break stays`() {
        val phone = Phone("hi\n👍🏽 ok")
        phone.altBackspace()
        assertEquals("hi\n", phone.box.text.toString())
    }

    @Test
    fun `a tapped Alt deletes the line once, the next Backspace one character`() {
        val phone = Phone("one\ntwo\nthree")
        phone.tapAlt()
        phone.backspace()
        assertEquals("one\ntwo\n", phone.box.text.toString())
        phone.backspace()
        assertEquals("one\ntwo", phone.box.text.toString())
    }

    @Test
    fun `a line longer than one read goes a read at a time`() {
        val long = "x".repeat(LINE_DELETE_WINDOW + 1_000)
        val phone = Phone("top\n$long")
        phone.altBackspace()
        assertEquals("top\n" + "x".repeat(1_000), phone.box.text.toString())
        phone.altBackspace()
        assertEquals("top\n", phone.box.text.toString())
    }

    @Test
    fun `an editor that will not hand over the document gets an ordinary Backspace`() {
        val phone = Phone("one\ntwo").apply { box.extractable = false }
        phone.altBackspace()
        assertEquals("one\ntw", phone.box.text.toString())
    }

    @Test
    fun `by default Alt+Backspace deletes one character and reads the usual window`() {
        val phone = Phone("one\ntwo", choice = AltBackspaceAction.DELETE_CHARACTER)
        phone.altBackspace()
        assertEquals("one\ntw", phone.box.text.toString())
        assertEquals(TEXT_BEFORE_CURSOR_WINDOW, phone.box.beforeReads.first())
    }

    @Test
    fun `Shift+Backspace and Ctrl+Backspace do what they do without the line choice`() {
        for (meta in listOf(ModifierFlags(shift = true), ModifierFlags(ctrl = true), ModifierFlags(ctrl = true, alt = true))) {
            val line = Phone("one\ntwo three")
            val plain = Phone("one\ntwo three", choice = AltBackspaceAction.DELETE_CHARACTER)
            line.backspace(meta)
            plain.backspace(meta)
            assertEquals(meta.toString(), plain.box.text.toString(), line.box.text.toString())
            assertEquals(meta.toString(), plain.box.received.map { it.keyCode to it.metaState }, line.box.received.map { it.keyCode to it.metaState })
        }
    }

    @Test
    fun `a Terminal mode app gets the real Alt+Backspace key, held or tapped`() {
        val held = Phone("", field = terminal)
        held.altBackspace()
        val tapped = Phone("", field = terminal)
        tapped.tapAlt()
        tapped.backspace()
        for (phone in listOf(held, tapped)) {
            val sent = phone.box.received
            assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), sent.map { it.action })
            assertTrue(sent.all { it.keyCode == KeyEvent.KEYCODE_DEL && it.isAltPressed && !it.isCtrlPressed })
        }
    }

    /** An editor as Android's text fields answer: reads, extracted text with the selection, deletes around the selection. */
    private class Editor(initial: String) : InputConnection {
        val text = StringBuilder(initial)
        private var selStart = initial.length
        private var selEnd = initial.length
        val received = mutableListOf<KeyEvent>()
        val beforeReads = mutableListOf<Int>()
        var extractable = true

        fun select(start: Int, end: Int) {
            selStart = start
            selEnd = end
        }

        fun beforeCursor(): String = text.substring(0, selStart)

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            received += event
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            beforeReads += n
            return beforeCursor().takeLast(n)
        }
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = text.substring(selEnd).take(n)
        override fun getSelectedText(flags: Int): CharSequence? = if (selStart == selEnd) null else text.substring(selStart, selEnd)
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
            if (!extractable) return null
            return ExtractedText().also {
                it.text = text.toString()
                it.startOffset = 0
                it.selectionStart = selStart
                it.selectionEnd = selEnd
            }
        }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val start = (selStart - beforeLength).coerceAtLeast(0)
            val end = (selEnd + afterLength).coerceAtMost(text.length)
            text.delete(selEnd, end)
            text.delete(start, selStart)
            selEnd = start + (selEnd - selStart)
            selStart = start
            return true
        }
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true
        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean = true
        override fun setComposingRegion(start: Int, end: Int): Boolean = true
        override fun finishComposingText(): Boolean = true
        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            this.text.replace(selStart, selEnd, text.toString())
            selStart += text.length
            selEnd = selStart
            return true
        }
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

    private companion object {
        val BACKSPACE = KeyId.Control(ControlKey.BACKSPACE)
        val ALT = KeyId.Modifier(ModifierKey.ALT)
    }
}
