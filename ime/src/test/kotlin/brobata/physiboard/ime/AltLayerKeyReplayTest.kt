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
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Alt layer and Shift in a Chrome-hosted terminal (PersaLink, 2026-10-06: "Alt and M doesn't
 * give me a period"), driven from the physical key through [KeyboardPipeline] and the real
 * [applyEditorOps] into a stand-in for Chrome's side of the input connection.
 *
 * [ChromeTerminalField] reproduces the one Chrome behaviour this is about (Chromium
 * `ImeKeyEventReplayer`, enabled by default, Chrome 148 and 154 alike): every hardware key down
 * is recorded before the keyboard sees it, and a one-character `commitText` that equals what the
 * device's own key map gives one of the downs recorded in the last second is thrown away and that
 * key down replayed to the page instead, its meta state included. The page is xterm.js, which
 * types a replayed key as its character, or as ESC plus the letter (Meta) when Alt is down. The
 * Titan's key map (`/system/usr/keychars/TitanKey.kcm`) gives M: base "m", shift "M", alt ".".
 * Key presses the keyboard sends itself are not recorded and reach the page unchanged.
 */
class AltLayerKeyReplayTest {

    private val settings = Settings()

    /** The field logged on the phone: TEXT | WEB_EDIT_TEXT | MULTI_LINE | NO_SUGGESTIONS, app on the exact-typing list. */
    private val persaLinkField = FieldContext(FieldKind.RAW_MODE_APP, isMultiLine = true, appDisablesSuggestions = true)
    private val plainField = FieldContext(FieldKind.NORMAL, isMultiLine = true)

    /** One physical keyboard driving the pipeline and the fake connection, with the system meta state Android would report. */
    private inner class Phone(field: FieldContext) {
        val pipeline = KeyboardPipeline(layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), settings), settings = ImeSettings.keyboardSettings(settings))
        val app = ChromeTerminalField()
        private var clock = 1_000L
        private var altHeld = false
        private var shiftHeld = false
        val results = mutableListOf<PipelineResult>()

        init {
            pipeline.onStartInput(field, textBeforeCursor = "")
        }

        private fun meta() = ModifierFlags(shift = shiftHeld, alt = altHeld)

        private fun send(key: KeyId, edge: KeyEdge, gapMs: Long = 120) {
            clock += gapMs
            if (edge == KeyEdge.DOWN && key is KeyId.Letter) app.onKeyPreIme(key.qwertyLetter, alt = altHeld, shift = shiftHeld, timeMs = clock)
            // The field answers "" for text around the cursor, as Chrome web fields do; a key-up reads nothing.
            val snapshot = EditorSnapshot(textBeforeCursor = if (edge == KeyEdge.DOWN) "" else null, nowMs = clock)
            val result = pipeline.onKeyStroke(KeyStroke(key, edge, 0, clock, meta()), snapshot)
            results += result
            app.clock = clock
            app.applyEditorOps(result.ops, windowStartOffset = 0, cursorAbsolute = 0, sendSpaceKeyFallback = {}, haptic = {}, typeAsKeys = if (result.altLayerStroke) app::typeAsKeys else null)
            if (!result.consumed && edge == KeyEdge.DOWN && key is KeyId.Letter) app.rawKeyDown(key.qwertyLetter, alt = altHeld, shift = shiftHeld)
        }

        fun altDown() { altHeld = true; send(ALT, KeyEdge.DOWN) }
        fun altUp() { altHeld = false; send(ALT, KeyEdge.UP) }
        fun shiftDown() { shiftHeld = true; send(SHIFT, KeyEdge.DOWN) }
        fun shiftUp() { shiftHeld = false; send(SHIFT, KeyEdge.UP) }
        fun tapAlt() { altDown(); altUp() }
        fun tapShift() { shiftDown(); shiftUp() }
        fun type(letter: Char) { send(KeyId.Letter(letter), KeyEdge.DOWN); send(KeyId.Letter(letter), KeyEdge.UP, gapMs = 60) }

        /** Holds [letter] past the long-press threshold, firing the tick [KeyboardSession] would. */
        fun hold(letter: Char) {
            send(KeyId.Letter(letter), KeyEdge.DOWN)
            clock += 600
            app.clock = clock
            val tick = pipeline.checkLongPressTick(clock, EditorSnapshot(textBeforeCursor = "", nowMs = clock)) ?: error("no long press fired")
            results += tick
            app.applyEditorOps(tick.ops, windowStartOffset = 0, cursorAbsolute = 0, sendSpaceKeyFallback = {}, haptic = {}, typeAsKeys = if (tick.altLayerStroke) app::typeAsKeys else null)
            send(KeyId.Letter(letter), KeyEdge.UP, gapMs = 60)
        }
    }

    @Test
    fun `Alt held with M types a period in the terminal, not Meta-m`() {
        val phone = Phone(persaLinkField)
        phone.altDown()
        phone.type('M')
        phone.altUp()
        assertEquals(".", phone.app.terminal.toString())
    }

    /** A control as much as a check: a tapped Alt leaves no Alt bit on the M Chrome records, so this passed before the fix too. */
    @Test
    fun `Alt tapped, then M, types a period in the terminal`() {
        val phone = Phone(persaLinkField)
        phone.tapAlt()
        phone.type('M')
        assertEquals(".", phone.app.terminal.toString())
    }

    @Test
    fun `a held Alt+M then a tapped Alt+M inside Chrome's one-second memory types two periods`() {
        val phone = Phone(persaLinkField)
        phone.altDown()
        phone.type('M')
        phone.altUp()
        phone.tapAlt()
        phone.type('M')
        assertEquals("..", phone.app.terminal.toString())
    }

    @Test
    fun `an Alt-layer character reaches Chrome as plain key presses, never a one-character commit`() {
        val phone = Phone(persaLinkField)
        phone.altDown()
        phone.type('M')
        phone.altUp()
        assertEquals(listOf("keys(.)"), phone.app.calls.filter { it != "beginBatchEdit" && it != "endBatchEdit" })
    }

    @Test
    fun `a long press of M in Alt mode right after a held Alt+M is never replayed as Meta-m`() {
        val phone = Phone(persaLinkField)
        phone.altDown()
        phone.type('M')
        phone.altUp()
        phone.hold('M')
        assertTrue(phone.results.last { it.ops.isNotEmpty() }.altLayerStroke)
        assertFalse(phone.app.calls.any { it.startsWith("key Alt+") }, "calls: ${phone.app.calls}")
        assertEquals(2, phone.app.calls.count { it == "keys(.)" })
    }

    @Test
    fun `Shift tapped, Shift held and caps lock give capitals in the terminal`() {
        val phone = Phone(persaLinkField)
        phone.tapShift()
        phone.type('M')
        phone.shiftDown()
        phone.type('M')
        phone.shiftUp()
        phone.type('M')
        // Two quick Shift taps: caps lock (keys-and-modifiers.md SS5).
        phone.tapShift()
        phone.tapShift()
        phone.type('M')
        phone.type('A')
        assertEquals("MMmMA", phone.app.terminal.toString())
    }

    @Test
    fun `plain letters still go out as one-character commits Chrome turns into real key presses`() {
        val phone = Phone(persaLinkField)
        phone.type('M')
        phone.type('A')
        assertEquals("ma", phone.app.terminal.toString())
        assertEquals(listOf("key m", "key a"), phone.app.calls.filter { it.startsWith("key") })
        assertFalse(phone.results.any { it.altLayerStroke })
    }

    @Test
    fun `Ctrl+C still reaches the terminal as the raw key`() {
        val phone = Phone(persaLinkField)
        val ctrl = KeyId.Modifier(ModifierKey.CTRL)
        val down = phone.pipeline.onKeyStroke(KeyStroke(ctrl, KeyEdge.DOWN, 0, 5_000, ModifierFlags(ctrl = true)), EditorSnapshot(textBeforeCursor = "", nowMs = 5_000))
        val c = phone.pipeline.onKeyStroke(KeyStroke(KeyId.Letter('C'), KeyEdge.DOWN, 0, 5_100, ModifierFlags(ctrl = true)), EditorSnapshot(textBeforeCursor = "", nowMs = 5_100))
        assertTrue(down.ops.isEmpty())
        assertFalse(c.consumed, "Ctrl+C must not be consumed: the app gets the physical key with its Ctrl bit")
        assertTrue(c.ops.isEmpty())
        assertFalse(c.altLayerStroke)
    }

    @Test
    fun `the same keys when the app is not on the exact-typing list`() {
        val phone = Phone(plainField)
        phone.altDown()
        phone.type('M')
        phone.altUp()
        phone.tapAlt()
        phone.type('M')
        phone.shiftDown()
        phone.type('M')
        phone.shiftUp()
        assertEquals("..M", phone.app.terminal.toString())
    }

    /**
     * Chrome's side of the connection to a page running xterm.js. Only the calls this path makes
     * are modelled; [terminal] is what xterm.js would hand the shell.
     */
    private class ChromeTerminalField : InputConnection {
        private data class RecordedDown(val letter: Char, val alt: Boolean, val shift: Boolean, val unicode: Char?, val timeMs: Long)

        val terminal = StringBuilder()
        val calls = mutableListOf<String>()
        var clock = 0L
        private val recorded = ArrayDeque<RecordedDown>()
        private var composing: String? = null

        /** TitanKey.kcm for the letter keys: base lowercase, shift uppercase, alt only where the file has one. */
        private fun titanKeyMap(letter: Char, alt: Boolean, shift: Boolean): Char? = when {
            alt && shift -> null
            alt -> TITAN_ALT[letter]
            shift -> letter.uppercaseChar()
            else -> letter.lowercaseChar()
        }

        fun onKeyPreIme(letter: Char, alt: Boolean, shift: Boolean, timeMs: Long) {
            recorded.removeAll { timeMs - it.timeMs >= 1_000 }
            val unicode = titanKeyMap(letter, alt, shift) ?: return
            recorded.addLast(RecordedDown(letter, alt, shift, unicode, timeMs))
        }

        /** A key the keyboard let through: the page sees it exactly as a replayed one. */
        fun rawKeyDown(letter: Char, alt: Boolean, shift: Boolean) = xtermKeyDown(RecordedDown(letter, alt, shift, titanKeyMap(letter, alt, shift), clock))

        private fun xtermKeyDown(down: RecordedDown) {
            calls += "key ${if (down.alt) "Alt+" else ""}${down.unicode}"
            terminal.append(if (down.alt) "\u001b" + down.letter.lowercaseChar() else down.unicode.toString())
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            composing = null
            recorded.removeAll { clock - it.timeMs >= 1_000 }
            val match = if (text.length == 1) recorded.firstOrNull { it.unicode == text[0] } else null
            if (match != null) {
                while (recorded.removeFirst() != match) Unit
                xtermKeyDown(match)
            } else {
                calls += "commitText($text)"
                terminal.append(text)
            }
            return true
        }

        /** What [sendCharacterAsKeys] does on the phone: key presses Chrome hands the page as they are, no Alt. */
        fun typeAsKeys(ch: Char): Boolean {
            calls += "keys($ch)"
            terminal.append(ch)
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            calls += "setComposingText($text)"
            composing = text.toString()
            return true
        }

        override fun finishComposingText(): Boolean {
            calls += "finishComposingText"
            composing?.let(terminal::append)
            composing = null
            return true
        }

        override fun beginBatchEdit(): Boolean { calls += "beginBatchEdit"; return true }
        override fun endBatchEdit(): Boolean { calls += "endBatchEdit"; return true }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean { calls += "deleteSurroundingText($beforeLength, $afterLength)"; return true }
        override fun sendKeyEvent(event: KeyEvent): Boolean { calls += "sendKeyEvent"; return true }
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = null
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true
        override fun setComposingRegion(start: Int, end: Int): Boolean = true
        override fun commitCompletion(text: CompletionInfo?): Boolean = true
        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true
        override fun setSelection(start: Int, end: Int): Boolean = true
        override fun performEditorAction(editorAction: Int): Boolean = true
        override fun performContextMenuAction(id: Int): Boolean = true
        override fun clearMetaKeyStates(states: Int): Boolean = true
        override fun reportFullscreenMode(enabled: Boolean): Boolean = true
        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
        override fun getHandler(): Handler? = null
        override fun closeConnection() = Unit
        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = true
    }

    private companion object {
        val ALT = KeyId.Modifier(ModifierKey.ALT)
        val SHIFT = KeyId.Modifier(ModifierKey.SHIFT)
        val TITAN_ALT = mapOf('M' to '.', 'A' to '*')
    }
}
