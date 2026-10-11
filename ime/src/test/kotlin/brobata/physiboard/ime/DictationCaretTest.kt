package brobata.physiboard.ime

import android.Manifest
import android.content.ComponentName
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import brobata.physiboard.core.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration

/**
 * dictation.md SS7.4, seen on the maintainer's Titan in Google Messages (2026-10-11): after
 * dictating, the user tapped elsewhere in the message and picked a symbol, and the whole dictated
 * sentence was replaced by the symbol; the cursor also jumped back to the words on its own. The
 * dictated partial stayed the keyboard's composing span after the user had moved away, and any
 * later write (a quiet error finishing it, a key's stop, a commit) replaced that span.
 *
 * These drive the real [KeyboardSession] and [DictationController] against [ComposingField], an
 * editor that keeps a composing span the way Android's own does (a commit or a new composing text
 * replaces it; finishing it leaves the words) and reports every change back through
 * onUpdateSelection, after the edit, on the main looper, with the span's bounds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DictationCaretTest {

    private val field = ComposingField()
    private lateinit var keyboard: KeyboardSession

    private fun start(stopOnTyping: Boolean = true, inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        // A recognition service is installed, so SpeechRecognizer counts as available.
        val recognizerService = ComponentName("com.example.speech", "com.example.speech.Recognizer")
        shadowOf(app.packageManager).addServiceIfNotPresent(recognizerService)
        shadowOf(app.packageManager).addIntentFilterForService(recognizerService, IntentFilter(RecognitionService.SERVICE_INTERFACE))
        val service = Robolectric.buildService(FieldService::class.java).create().get()
        service.field = field
        val settings = Settings().let { it.copy(dictation = it.dictation.copy(stopOnTyping = stopOnTyping)) }
        keyboard = KeyboardSession(service, settingsSource = object : SettingsSource {
            override val settings: Flow<Settings> = flowOf(settings)
        })
        field.onUpdate = keyboard::onUpdateSelection
        val info = EditorInfo().apply {
            packageName = "com.google.android.apps.messaging"
            this.inputType = inputType
            initialSelStart = 0
            initialSelEnd = 0
        }
        service.info = info
        keyboard.onStartInput(info, false)
        idle()
    }

    @After
    fun tearDown() {
        if (::keyboard.isInitialized) keyboard.onServiceDestroyed()
        ShadowSpeechRecognizer.reset()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun recognizer() = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())

    private fun words(text: String) = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) }

    /** Fn's trigger, the engine opening, and a partial on screen as composing text. */
    private fun dictate(partial: String) {
        keyboard.onDictationTrigger()
        idle()
        recognizer().triggerOnReadyForSpeech(Bundle())
        recognizer().triggerOnPartialResults(words(partial))
        idle()
    }

    /** A quick press and release, on the real clock, as the phone delivers it; true when the keyboard consumed the press (false: it goes on to the app). */
    private fun press(keyCode: Int): Boolean {
        val downAt = SystemClock.uptimeMillis()
        val consumed = keyboard.onKeyEvent(KeyEvent(downAt, downAt, KeyEvent.ACTION_DOWN, keyCode, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        keyboard.onKeyEvent(KeyEvent(downAt, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        return consumed
    }

    @Test
    fun `the partial composes at the caret, the setup every case below starts from`() {
        start()
        dictate("how are you")
        assertEquals("How are you", field.text)
        assertEquals(0 to 11, field.composing)
        assertEquals(11, field.caret)
    }

    @Test
    fun `the keyboard's own writes are never taken for a move, even from a field that never reports its span`() {
        start()
        field.reportsSpan = false
        dictate("how are you")
        recognizer().triggerOnPartialResults(words("how are you doing"))
        idle()
        assertEquals("How are you doing", field.text)
        assertEquals(0 to 17, field.composing)
    }

    @Test
    fun `the span-end report is the keyboard's own, anything else is worth a read`() {
        assertFalse(DictatedSpanCaret.mayHaveLeft(11, 11, 0, 11))
        assertTrue(DictatedSpanCaret.mayHaveLeft(3, 3, 0, 11))
        assertTrue(DictatedSpanCaret.mayHaveLeft(0, 11, 0, 11))
        assertTrue(DictatedSpanCaret.mayHaveLeft(11, 11, -1, -1))
        assertTrue(DictatedSpanCaret.mayHaveLeft(20, 20, 0, 11))
    }

    @Test
    fun `a tap away from the words, then the engine's quiet error, writes nothing and leaves the cursor where the user put it`() {
        start()
        dictate("how are you")
        field.tap(3)
        idle()
        recognizer().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        idle()
        assertEquals("How are you", field.text)
        assertEquals(3, field.caret)
        assertEquals(null, field.composing)
    }

    @Test
    fun `a tap away from the words, then a key, types the key at the tapped cursor`() {
        start()
        dictate("how are you")
        field.tap(3)
        idle()
        press(KeyEvent.KEYCODE_K)
        // The letter's case is the typing pipeline's business (its view of the field resyncs
        // after a debounce); where it lands is what this is about.
        assertEquals("howk are you", field.text.lowercase())
        assertEquals(4, field.caret)
    }

    /** Sym twice, apart: the default cycle opens the emoji picker first, then the Symbols grid. */
    private fun openSymbolsGrid() {
        press(KeyEvent.KEYCODE_SYM)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        press(KeyEvent.KEYCODE_SYM)
    }

    @Test
    fun `a Sym page tap mid-dictation keeps the words and puts the symbol after them`() {
        start()
        dictate("how are you")
        openSymbolsGrid()
        // The Symbols grid's C is "&".
        keyboard.symGridListener.onKeyTapped('C')
        idle()
        assertEquals("How are you &", field.text)
        assertEquals(field.text.length, field.caret)
    }

    @Test
    fun `with stop-on-typing off, a Sym page tap after a tap away inserts at the tapped cursor and keeps the words`() {
        start(stopOnTyping = false)
        dictate("how are you")
        field.tap(3)
        idle()
        openSymbolsGrid()
        keyboard.symGridListener.onKeyTapped('C')
        idle()
        assertEquals("How& are you", field.text)
        assertEquals(4, field.caret)
    }

    @Test
    fun `with stop-on-typing off, a Sym page tap right after the words keeps them`() {
        start(stopOnTyping = false)
        dictate("how are you")
        openSymbolsGrid()
        keyboard.symGridListener.onKeyTapped('C')
        idle()
        assertEquals("How are you&", field.text)
    }

    @Test
    fun `with stop-on-typing off, a key typed right after the words never replaces them`() {
        start(stopOnTyping = false)
        dictate("how are you")
        press(KeyEvent.KEYCODE_K)
        assertEquals("how are youk", field.text.lowercase())
    }

    @Test
    fun `in a field that cannot hold a composing region, the staged words are never typed after a key`() {
        start(stopOnTyping = false, inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        dictate("ls la")
        assertEquals("nothing staged is shown in such a field", "", field.text)
        press(KeyEvent.KEYCODE_K)
        recognizer().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        idle()
        assertFalse("the partial was typed after the key: '${field.text}'", field.text.contains("ls", ignoreCase = true))
        assertEquals("k", field.text.lowercase())
    }

    @Test
    fun `a tap just past a newline after the words is a move too`() {
        field.preset("\nsee you", caret = 0)
        start()
        dictate("how are you")
        assertEquals("How are you\nsee you", field.text)
        field.tap(12)
        idle()
        recognizer().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        idle()
        assertEquals("How are you\nsee you", field.text)
        assertEquals(12, field.caret)
    }

    @Test
    fun `in a field that cannot hold a composing region, Enter goes out alone and the staged words never follow it`() {
        start(stopOnTyping = false, inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        dictate("ls la")
        val consumed = press(KeyEvent.KEYCODE_ENTER)
        recognizer().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        idle()
        val enterWent = !consumed || field.keysSent.any { it.keyCode == KeyEvent.KEYCODE_ENTER } || field.editorActions.isNotEmpty() || field.text.contains('\n')
        assertTrue("the Enter reached the app (consumed=$consumed keys=${field.keysSent} actions=${field.editorActions})", enterWent)
        assertFalse("the partial was typed after the Enter: '${field.text}'", field.text.contains("ls", ignoreCase = true))
    }

    @Test
    fun `control- in that field the words do arrive, once, when the utterance is final`() {
        start(stopOnTyping = false, inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        dictate("ls la")
        recognizer().triggerOnResults(words("ls la"))
        idle()
        assertEquals("ls la ", field.text.lowercase())
    }

    /** An InputMethodService whose connection and editor are the test's own. */
    class FieldService : InputMethodService() {
        var field: InputConnection? = null
        var info: EditorInfo? = null
        override fun getCurrentInputConnection(): InputConnection? = field
        override fun getCurrentInputEditorInfo(): EditorInfo? = info
    }

    /**
     * An editor with Android's composing-span behaviour: [setComposingText] and [commitText]
     * replace the span (or the selection when there is none), [finishComposingText] keeps the
     * words and drops the span. After every edit, once any batch has ended, the change is reported
     * through [onUpdate] on the main looper, with the span's bounds or -1.
     */
    class ComposingField : InputConnection {
        private val buffer = StringBuilder()
        private var selStart = 0
        private var selEnd = 0
        private var spanStart = -1
        private var spanEnd = -1
        private var batch = 0
        private var reportedStart = 0
        private var reportedEnd = 0
        private val main = Handler(Looper.getMainLooper())
        var onUpdate: ((Int, Int, Int, Int, Int, Int) -> Unit)? = null

        /** False: every report says -1 for the span, as some apps' fields do. */
        var reportsSpan = true

        val text: String get() = buffer.toString()
        val caret: Int get() = selStart
        val composing: Pair<Int, Int>? get() = if (spanStart < 0) null else spanStart to spanEnd

        /** The user taps the text at [position]: the caret moves, the span (if any) stays where it is. */
        /** Text already in the field before the test starts, with the caret at [caret]. */
        fun preset(initial: String, caret: Int) {
            buffer.setLength(0)
            buffer.append(initial)
            selStart = caret
            selEnd = caret
            reportedStart = caret
            reportedEnd = caret
        }

        fun tap(position: Int) {
            selStart = position
            selEnd = position
            report()
        }

        private fun report() {
            if (batch > 0) return
            val oldStart = reportedStart
            val oldEnd = reportedEnd
            val values = if (reportsSpan) intArrayOf(selStart, selEnd, spanStart, spanEnd) else intArrayOf(selStart, selEnd, -1, -1)
            reportedStart = selStart
            reportedEnd = selEnd
            main.post { onUpdate?.invoke(oldStart, oldEnd, values[0], values[1], values[2], values[3]) }
        }

        private fun replaceTarget(): Pair<Int, Int> = if (spanStart >= 0) spanStart to spanEnd else selStart to selEnd

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            val (start, end) = replaceTarget()
            buffer.replace(start, end, text.toString())
            if (text.isEmpty()) {
                spanStart = -1
                spanEnd = -1
            } else {
                spanStart = start
                spanEnd = start + text.length
            }
            selStart = start + text.length
            selEnd = selStart
            report()
            return true
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            val (start, end) = replaceTarget()
            buffer.replace(start, end, text.toString())
            spanStart = -1
            spanEnd = -1
            selStart = start + text.length
            selEnd = selStart
            report()
            return true
        }

        override fun finishComposingText(): Boolean {
            spanStart = -1
            spanEnd = -1
            report()
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            spanStart = start
            spanEnd = end
            report()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val start = (selStart - beforeLength).coerceAtLeast(0)
            val end = (selEnd + afterLength).coerceAtMost(buffer.length)
            buffer.delete(selEnd, end)
            buffer.delete(start, selStart)
            selStart = start
            selEnd = start
            spanStart = -1
            spanEnd = -1
            report()
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            selStart = start
            selEnd = end
            report()
            return true
        }

        override fun beginBatchEdit(): Boolean {
            batch++
            return true
        }

        override fun endBatchEdit(): Boolean {
            if (batch > 0) batch--
            if (batch == 0) report()
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.substring(0, selStart).takeLast(n)
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = buffer.substring(selEnd).take(n)
        override fun getSelectedText(flags: Int): CharSequence? = if (selStart == selEnd) null else buffer.substring(selStart, selEnd)
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = deleteSurroundingText(beforeLength, afterLength)
        /** Key events the keyboard handed to the app (an Enter, a Backspace it lets through). */
        val keysSent = mutableListOf<KeyEvent>()

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            keysSent += event
            return true
        }
        override fun commitCompletion(text: CompletionInfo?): Boolean = true
        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true
        val editorActions = mutableListOf<Int>()

        override fun performEditorAction(editorAction: Int): Boolean {
            editorActions += editorAction
            return true
        }
        override fun performContextMenuAction(id: Int): Boolean = true
        override fun clearMetaKeyStates(states: Int): Boolean = true
        override fun reportFullscreenMode(enabled: Boolean): Boolean = true
        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
        override fun getHandler(): Handler? = null
        override fun closeConnection() = Unit
        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
    }
}
