package brobata.physiboard.core.text

import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.AltBackspaceAction
import brobata.physiboard.core.keys.EditEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: keys-and-modifiers.md SS7.7, Alt+Backspace's `line` choice (T49b-T49k). */
class DeleteToLineStartTest {

    // The count ------------------------------------------------------------------------------

    @Test
    fun `mid-line deletes back to the line break and keeps it`() {
        assertEquals(5, DeleteToLineStart.countToDelete("first\nhello"))
    }

    @Test
    fun `on the first line deletes back to the start of the field`() {
        assertEquals(11, DeleteToLineStart.countToDelete("hello world"))
    }

    @Test
    fun `at a line start deletes the line break, joining the line above`() {
        assertEquals(1, DeleteToLineStart.countToDelete("first\n"))
        assertEquals(1, DeleteToLineStart.countToDelete("\n"))
    }

    @Test
    fun `a Windows line break goes as one`() {
        assertEquals(2, DeleteToLineStart.countToDelete("first\r\n"))
        assertEquals(3, DeleteToLineStart.countToDelete("first\r\nabc"))
    }

    @Test
    fun `an empty field has nothing to delete`() {
        assertEquals(0, DeleteToLineStart.countToDelete(""))
    }

    @Test
    fun `several lines delete only the last one`() {
        assertEquals(3, DeleteToLineStart.countToDelete("one\ntwo\nsix"))
    }

    @Test
    fun `an emoji right after the line break goes whole, the break stays`() {
        // 👍🏽 is four UTF-16 units: a thumbs-up and a skin tone, each a surrogate pair.
        val text = "a\n👍🏽ok"
        assertEquals(6, DeleteToLineStart.countToDelete(text))
        assertEquals("a\n", text.dropLast(6))
    }

    @Test
    fun `a window that starts inside an emoji keeps that half rather than splitting the pair`() {
        // A bounded read cut a long line so the window opens on the low half of a surrogate pair.
        val window = "\uDC4Dabc"
        assertEquals(3, DeleteToLineStart.countToDelete(window))
    }

    // Through the pipeline ---------------------------------------------------------------------

    private val normal = FieldContext(FieldKind.NORMAL, isMultiLine = true)
    private val settings = TextInputSettingsBundle(backspace = BackspaceSettings(altBackspace = AltBackspaceAction.DELETE_TO_LINE_START))

    private fun lineDelete(
        before: String?,
        window: TextWindow? = before?.let { TextWindow(it, it.length) },
        field: FieldContext = normal,
        state: TextInputState = TextInputState(),
        trust: EditorTrust = EditorTrust.FULL,
    ): TextInputResult = TextInputPipeline.handle(
        TextInputRequest.Key(Action.Edit(EditEffect.DELETE_TO_LINE_START), altActive = true),
        field, settings, TextInputResources(), state, EditorSnapshot(textBeforeCursor = before, fullText = window), trust,
    )

    @Test
    fun `the line before the cursor goes in one edit`() {
        val result = lineDelete("Dear Sam,\nthanks for the")
        assertEquals(listOf(EditorOp.FinishComposing, EditorOp.DeleteSurrounding(14, 0)), result.ops)
        assertEquals("", result.state.currentWord.word)
    }

    @Test
    fun `joining a line re-reads the word now before the cursor`() {
        val result = lineDelete("see you\n")
        assertEquals(listOf(EditorOp.FinishComposing, EditorOp.DeleteSurrounding(1, 0)), result.ops)
        assertEquals("you", result.state.currentWord.word)
    }

    @Test
    fun `text after the cursor is left alone`() {
        val text = "one\ntwo three"
        // Cursor after "two": the window holds the whole document, the read only what is before the cursor.
        val result = lineDelete("one\ntwo", window = TextWindow(text, 7))
        assertEquals(listOf(EditorOp.FinishComposing, EditorOp.DeleteSurrounding(3, 0)), result.ops)
    }

    @Test
    fun `a selection is an ordinary Backspace, which deletes the selection`() {
        val result = lineDelete("one\ntwo", window = TextWindow("one\ntwo three", 4, 7))
        assertEquals(listOf(EditorOp.PassThroughKey), result.ops)
    }

    @Test
    fun `an empty field is an ordinary Backspace, so the key still reaches the app`() {
        assertEquals(listOf(EditorOp.PassThroughKey), lineDelete("").ops)
    }

    @Test
    fun `without a document read the selection is unknown, so it is an ordinary Backspace`() {
        assertEquals(listOf(EditorOp.PassThroughKey), lineDelete("one\ntwo", window = null).ops)
    }

    @Test
    fun `an unreadable field is an ordinary Backspace`() {
        assertEquals(listOf(EditorOp.PassThroughKey), lineDelete(null, window = TextWindow("one\ntwo", 7)).ops)
    }

    @Test
    fun `a field whose reads are not trusted is an ordinary Backspace`() {
        val result = lineDelete("one\ntwo", trust = EditorTrust(reads = EditorReadTrust.POSSIBLY_STALE))
        assertEquals(listOf(EditorOp.PassThroughKey), result.ops)
    }

    @Test
    fun `a Terminal mode app gets the real Alt+Backspace key`() {
        val terminal = FieldContext(FieldKind.RAW_MODE_APP, isMultiLine = true)
        val result = lineDelete("$ ls -la", field = terminal)
        assertEquals(listOf(EditorOp.SendKey(EditEffect.DELETE_TO_LINE_START, withAlt = true)), result.ops)
    }

    @Test
    fun `the undo memory goes with the line, so a later Backspace cannot put a correction back`() {
        val state = TextInputState(autocorrectMemory = AutocorrectMemory().afterReplacement("teh", "the"))
        val result = lineDelete("x\nsee the ", state = state)
        assertEquals(listOf(EditorOp.FinishComposing, EditorOp.DeleteSurrounding(8, 0)), result.ops)
        assertNull(result.state.autocorrectMemory.lastReplacement)
    }

    @Test
    fun `a pending deferred space and auto-space are cancelled like any Backspace`() {
        val state = TextInputState(autoSpacePending = true, justCommittedSentenceEnd = true)
        val result = lineDelete("x\nend.", state = state)
        assertEquals(false, result.state.autoSpacePending)
        assertEquals(false, result.state.justCommittedSentenceEnd)
    }
}
