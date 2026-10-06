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
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shift and a letter typed fast in PersaLink (2026-10-06: "sometimes if I hit Shift then a letter
 * too fast it doesn't catch it, but it mostly works"), driven from the physical keys through
 * [KeyboardPipeline], the real [applyEditorOps] and [characterDelivery] into a stand-in for the
 * whole receiving side: Chrome, xterm.js 5.5 and PersaLink's own input handler.
 *
 * What the stand-in reproduces, each from its source:
 * - Chrome (`ImeKeyEventReplayer`, `ImeAdapterImpl.sendCompositionToNative`): every hardware key
 *   down that types a character is recorded before the keyboard sees it, for one second. A
 *   one-character commit equal to a recorded down's own character (its meta state applied) is
 *   dropped and that down replayed to the page. Any other commit reaches the page as a key down
 *   with key code 229, an insert into xterm's hidden text box, and a key up with key code 229.
 *   Key presses the keyboard sends itself go to the page as they are.
 * - xterm.js (`CoreBrowserTerminal`, `CompositionHelper`): a key down sets "key down seen", a key
 *   up clears it; an insert is read directly only while no key down is seen, which the 229 down
 *   always is; the 229 down instead arms a zero-delay timer that compares the text box with what
 *   it held at that moment and sends the difference.
 * - PersaLink (`TerminalPane.tsx`): the text box is emptied after every piece of input xterm sends.
 *
 * So a capital the page cannot match to a recorded key (a tapped Shift's: the M down was recorded
 * as "m") survives only if xterm's timer runs before the next key reaches the page. On the phone
 * the keyboard spent 30 to 44 ms on every Shift press, so the keys behind it reach the page
 * bunched together. [Phone.timersRunBetweenKeys] false is that bunching; true is a relaxed typist.
 */
class TerminalShiftCapitalTest {

    private val settings = Settings()

    /** The field logged on the phone: TEXT | WEB_EDIT_TEXT | MULTI_LINE | NO_SUGGESTIONS, app on the Terminal mode list. */
    private val persaLinkField = FieldContext(FieldKind.RAW_MODE_APP, isMultiLine = true, appDisablesSuggestions = true)

    private inner class Phone(field: FieldContext, val timersRunBetweenKeys: Boolean) {
        val pipeline = KeyboardPipeline(layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), settings), settings = ImeSettings.keyboardSettings(settings))
        val page = ChromeXtermPage()
        private val terminalMode = field.kind == FieldKind.RAW_MODE_APP
        private var clock = 1_000L
        private var shiftHeld = false
        private var symHeld = false
        private var altHeld = false

        init {
            pipeline.onStartInput(field, textBeforeCursor = "")
        }

        private fun send(key: KeyId, edge: KeyEdge, gapMs: Long) {
            clock += gapMs
            page.clock = clock
            val typed = page.keyMapCharacter(key, shiftHeld, altHeld)
            if (edge == KeyEdge.DOWN) page.onKeyPreIme(typed, clock)
            val snapshot = EditorSnapshot(textBeforeCursor = if (edge == KeyEdge.DOWN) "" else null, nowMs = clock)
            val result = pipeline.onKeyStroke(KeyStroke(key, edge, 0, clock, ModifierFlags(shift = shiftHeld, sym = symHeld, alt = altHeld)), snapshot)
            val delivery = characterDelivery(result.altLayerStroke, terminalMode, keyTypes = if (edge == KeyEdge.DOWN) typed else null, sendAsKeys = page::typeAsKeys)
            page.applyEditorOps(result.ops, windowStartOffset = 0, cursorAbsolute = 0, sendSpaceKeyFallback = {}, haptic = {}, typeAsKeys = delivery)
            if (!result.consumed) page.rawKey(edge, typed)
            if (timersRunBetweenKeys) page.runTimers()
        }

        fun shiftDown(gapMs: Long = 40) { shiftHeld = true; send(SHIFT, KeyEdge.DOWN, gapMs) }
        fun shiftUp(gapMs: Long = 40) { shiftHeld = false; send(SHIFT, KeyEdge.UP, gapMs) }
        fun symDown() { symHeld = true; send(SYM, KeyEdge.DOWN, 40) }
        fun symUp() { symHeld = false; send(SYM, KeyEdge.UP, 40) }
        fun altDown() { altHeld = true; send(ALT, KeyEdge.DOWN, 40) }
        fun altUp() { altHeld = false; send(ALT, KeyEdge.UP, 40) }
        fun down(letter: Char, gapMs: Long = 40) = send(KeyId.Letter(letter), KeyEdge.DOWN, gapMs)
        fun up(letter: Char, gapMs: Long = 40) = send(KeyId.Letter(letter), KeyEdge.UP, gapMs)
        fun type(letter: Char) { down(letter); up(letter) }
        fun space() { send(SPACE, KeyEdge.DOWN, 40); send(SPACE, KeyEdge.UP, 40) }

        /** What reached the shell once every pending page timer has run. */
        fun shell(): String { page.runTimers(); return page.shell.toString() }
    }

    private fun bothSpeeds(check: (Phone) -> Unit) {
        check(Phone(persaLinkField, timersRunBetweenKeys = false))
        check(Phone(persaLinkField, timersRunBetweenKeys = true))
    }

    @Test
    fun `Shift tapped, then M, then the next letter fast gives the capital`() = bothSpeeds { phone ->
        phone.shiftDown(); phone.shiftUp()
        phone.type('M')
        phone.type('A')
        assertEquals("Ma", phone.shell(), "timers between keys: ${phone.timersRunBetweenKeys}")
    }

    @Test
    fun `Shift down, M down, Shift up, M up gives the capital`() = bothSpeeds { phone ->
        phone.shiftDown(); phone.down('M'); phone.shiftUp(); phone.up('M')
        phone.type('A')
        assertEquals("Ma", phone.shell(), "timers between keys: ${phone.timersRunBetweenKeys}")
    }

    @Test
    fun `Shift down, M down, M up, Shift up gives the capital`() = bothSpeeds { phone ->
        phone.shiftDown(); phone.down('M'); phone.up('M'); phone.shiftUp()
        phone.type('A')
        assertEquals("Ma", phone.shell(), "timers between keys: ${phone.timersRunBetweenKeys}")
    }

    @Test
    fun `Shift tapped very fast, inside the letter's own press, gives the capital`() = bothSpeeds { phone ->
        phone.shiftDown(gapMs = 5); phone.shiftUp(gapMs = 15); phone.down('M', gapMs = 5); phone.up('M', gapMs = 20)
        phone.down('A', gapMs = 5); phone.up('A', gapMs = 20)
        assertEquals("Ma", phone.shell(), "timers between keys: ${phone.timersRunBetweenKeys}")
    }

    @Test
    fun `a capital after a word and a space, typed at speed`() = bothSpeeds { phone ->
        phone.type('L'); phone.type('S'); phone.space()
        phone.shiftDown(); phone.shiftUp()
        phone.type('M'); phone.type('A')
        assertEquals("ls Ma", phone.shell(), "timers between keys: ${phone.timersRunBetweenKeys}")
    }

    @Test
    fun `caps lock across a space keeps every capital`() = bothSpeeds { phone ->
        phone.shiftDown(); phone.shiftUp(); phone.shiftDown(); phone.shiftUp()
        phone.type('M'); phone.type('A'); phone.space(); phone.type('X')
        assertEquals("MA X", phone.shell(), "timers between keys: ${phone.timersRunBetweenKeys}")
    }

    @Test
    fun `a capital the key itself types (Shift held) still goes out as Chrome's replay of the real key`() {
        val phone = Phone(persaLinkField, timersRunBetweenKeys = false)
        phone.shiftDown(); phone.down('M'); phone.up('M'); phone.shiftUp()
        assertEquals(listOf("replay M"), phone.page.calls)
    }

    @Test
    fun `a tapped Shift's capital goes out as key presses, never as a commit Chrome cannot match`() {
        val phone = Phone(persaLinkField, timersRunBetweenKeys = false)
        phone.shiftDown(); phone.shiftUp(); phone.type('M')
        assertEquals(listOf("keys(M)"), phone.page.calls)
    }

    @Test
    fun `plain letters in the terminal still go out as commits Chrome replays`() {
        val phone = Phone(persaLinkField, timersRunBetweenKeys = false)
        phone.type('L'); phone.type('S')
        assertEquals(listOf("replay l", "replay s"), phone.page.calls)
        assertEquals("ls", phone.shell())
    }

    @Test
    fun `outside terminal mode a tapped Shift's capital is still a plain commit`() {
        val phone = Phone(FieldContext(FieldKind.NORMAL, isMultiLine = true), timersRunBetweenKeys = true)
        // Past the start of the text, where auto-cap would have armed Shift itself.
        phone.type('L')
        phone.shiftDown(); phone.shiftUp(); phone.type('M')
        assertEquals("commit(M)", phone.page.calls.last(), "calls: ${phone.page.calls}")
    }

    @Test
    fun `a Sym chord symbol reaches the terminal as a key press, with nothing opened`() = bothSpeeds { phone ->
        phone.symDown(); phone.type('Q'); phone.symUp()
        phone.type('A')
        val calls = phone.page.calls
        assertTrue(calls.first().startsWith("keys("), "calls: $calls")
        val symbol = calls.first().removePrefix("keys(").removeSuffix(")")
        assertEquals(symbol + "a", phone.shell(), "timers between keys: ${phone.timersRunBetweenKeys}")
        assertEquals(0, phone.pipeline.currentSymPage, "a Sym chord opens no page")
    }

    @Test
    fun `an Alt symbol reaches the terminal as a key press`() = bothSpeeds { phone ->
        phone.altDown(); phone.type('M'); phone.altUp()
        phone.type('A')
        assertEquals(".a", phone.shell(), "timers between keys: ${phone.timersRunBetweenKeys}")
    }

    /**
     * Chrome, xterm.js and PersaLink as one [InputConnection]: see the class KDoc. [shell] is what
     * PersaLink hands tmux; [calls] records how each character arrived.
     */
    private class ChromeXtermPage : InputConnection {
        private data class Recorded(val character: Char, val timeMs: Long)

        val shell = StringBuilder()
        val calls = mutableListOf<String>()
        var clock = 0L
        private val recorded = ArrayDeque<Recorded>()

        // xterm.js
        private var keyDownSeen = false
        private val textBox = StringBuilder()
        private var timerOldValue: String? = null

        /** TitanKey.kcm for the keys these tests press. */
        fun keyMapCharacter(key: KeyId, shift: Boolean, alt: Boolean): Char? = when (key) {
            is KeyId.Letter -> when {
                alt -> TITAN_ALT[key.qwertyLetter]
                shift -> key.qwertyLetter.uppercaseChar()
                else -> key.qwertyLetter.lowercaseChar()
            }
            KeyId.Control(ControlKey.SPACE) -> ' '
            else -> null
        }

        fun onKeyPreIme(character: Char?, timeMs: Long) {
            recorded.removeAll { timeMs - it.timeMs >= 1_000 }
            if (character != null) recorded.addLast(Recorded(character, timeMs))
        }

        /** A key the keyboard did not consume reaches the page as itself. */
        fun rawKey(edge: KeyEdge, character: Char?) {
            if (edge == KeyEdge.DOWN) pageKeyDown(character) else pageKeyUp()
        }

        private fun pageKeyDown(character: Char?) {
            keyDownSeen = true
            if (character != null) xtermSends(character.toString())
        }

        private fun pageKeyUp() {
            keyDownSeen = false
        }

        /** xterm's onData, as PersaLink handles it: forwarded, then the hidden text box emptied. */
        private fun xtermSends(data: String) {
            shell.append(data)
            textBox.clear()
        }

        fun runTimers() {
            val old = timerOldValue ?: return
            timerOldValue = null
            val now = textBox.toString()
            if (now.length > old.length) xtermSends(now.replace(old, ""))
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            recorded.removeAll { clock - it.timeMs >= 1_000 }
            val match = if (text.length == 1) recorded.firstOrNull { it.character == text[0] } else null
            if (match != null) {
                while (recorded.removeFirst() != match) Unit
                calls += "replay ${match.character}"
                pageKeyDown(match.character)
                return true
            }
            calls += "commit($text)"
            // Key code 229 down: key down seen, and xterm arms its text-box timer.
            keyDownSeen = true
            if (timerOldValue == null) timerOldValue = textBox.toString()
            // The insert: xterm ignores the input event while a key down is seen; the box keeps the text.
            textBox.append(text)
            // Key code 229 up.
            pageKeyUp()
            return true
        }

        /** What [sendCharacterAsKeys] does on the phone: real key presses Chrome passes to the page. */
        fun typeAsKeys(ch: Char): Boolean {
            calls += "keys($ch)"
            pageKeyDown(ch)
            pageKeyUp()
            return true
        }

        override fun beginBatchEdit(): Boolean = true
        override fun endBatchEdit(): Boolean = true
        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean { calls += "setComposingText($text)"; return true }
        override fun finishComposingText(): Boolean = true
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
        val SHIFT = KeyId.Modifier(ModifierKey.SHIFT)
        val SPACE = KeyId.Control(ControlKey.SPACE)
        val SYM = KeyId.Modifier(ModifierKey.SYM)
        val ALT = KeyId.Modifier(ModifierKey.ALT)
        val TITAN_ALT = mapOf('M' to '.', 'Q' to '0')
    }
}
