package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: text-input.md SS8, "Test cases" T68-T77. */
class BackspaceTest {

    private val noAlternatives = BackspaceSettings()

    @Test
    fun `T68 ctrl-backspace deletes the last word and the whitespace after it`() {
        assertEquals(5, DeleteWordBackward.countToDelete("hello world"))
    }

    @Test
    fun `T69 ctrl-backspace with a selection is not this function's concern`() {
        // A selection is deleted directly by the caller; nothing here needs to run.
        assertEquals(0, DeleteWordBackward.countToDelete(""))
    }

    @Test
    fun `T70 shift-backspace deletes forward when enabled`() {
        val settings = noAlternatives.copy(shiftBackspaceDeletesForward = true)
        val decision = Backspace.decide(hasSelection = false, shiftHeld = true, altActive = false, settings, charsBeforeCursor = 2, AutocorrectMemory(), null)
        assertEquals(Backspace.Decision.DeleteForward, decision)
    }

    @Test
    fun `T71 alt-backspace deletes forward when enabled`() {
        val settings = noAlternatives.copy(altBackspaceDeletesForward = true)
        val decision = Backspace.decide(hasSelection = false, shiftHeld = false, altActive = true, settings, charsBeforeCursor = 2, AutocorrectMemory(), null)
        assertEquals(Backspace.Decision.DeleteForward, decision)
    }

    @Test
    fun `T72 a selection disables shift-backspace's forward delete`() {
        val settings = noAlternatives.copy(shiftBackspaceDeletesForward = true)
        val decision = Backspace.decide(hasSelection = true, shiftHeld = true, altActive = false, settings, charsBeforeCursor = 1, AutocorrectMemory(), null)
        assertEquals(Backspace.Decision.FallThrough, decision)
    }

    @Test
    fun `T73 backspace at start deletes forward`() {
        val settings = noAlternatives.copy(backspaceAtStartDeletesForward = true)
        val decision = Backspace.decide(hasSelection = false, shiftHeld = false, altActive = false, settings, charsBeforeCursor = 0, AutocorrectMemory(), null)
        assertEquals(Backspace.Decision.DeleteForward, decision)
    }

    @Test
    fun `T74 backspace at start does not apply away from the start`() {
        val settings = noAlternatives.copy(backspaceAtStartDeletesForward = true)
        val decision = Backspace.decide(hasSelection = false, shiftHeld = false, altActive = false, settings, charsBeforeCursor = 1, AutocorrectMemory(), null)
        assertEquals(Backspace.Decision.FallThrough, decision)
    }

    @Test
    fun `T75 backspace at start alone does not apply with shift held`() {
        val settings = noAlternatives.copy(backspaceAtStartDeletesForward = true)
        val decision = Backspace.decide(hasSelection = false, shiftHeld = true, altActive = false, settings, charsBeforeCursor = 0, AutocorrectMemory(), null)
        assertEquals(Backspace.Decision.FallThrough, decision)
    }

    @Test
    fun `T76 both rows on, shift held, fires once`() {
        val settings = noAlternatives.copy(shiftBackspaceDeletesForward = true, altBackspaceDeletesForward = true)
        val decision = Backspace.decide(hasSelection = false, shiftHeld = true, altActive = false, settings, charsBeforeCursor = 2, AutocorrectMemory(), null)
        assertEquals(Backspace.Decision.DeleteForward, decision)
    }

    @Test
    fun `T77 backspace undoes an auto-replace and rejects the original`() {
        val memory = AutocorrectMemory().afterReplacement("teh", "the")
        val decision = Backspace.decide(hasSelection = false, shiftHeld = false, altActive = false, noAlternatives, charsBeforeCursor = 4, memory, undoTextBeforeCursor = "the ")
        val undo = (decision as Backspace.Decision.Undo).result
        assertEquals(listOf(EditorOp.DeleteSurrounding(4, 0), EditorOp.CommitText("teh")), undo.ops)
        assertEquals(true, undo.memory.isRejected("teh"))
        assertEquals("teh", undo.addWordCandidate)
    }

    @Test
    fun `T77b undoing a replacement of an apostrophe word also rejects its root`() {
        // spec: autocorrect-suggestions.md SS7.5 step 3: undoing "dell'amico" (which corrected
        // "dell'amivo") must also reject "amivo", the apostrophe root, not just the whole word.
        val memory = AutocorrectMemory().afterReplacement("dell'amivo", "dell'amico")
        val decision = Backspace.decide(
            hasSelection = false, shiftHeld = false, altActive = false, noAlternatives,
            charsBeforeCursor = 11, memory, undoTextBeforeCursor = "dell'amico ",
        )
        val undo = (decision as Backspace.Decision.Undo).result
        assertEquals(true, undo.memory.isRejected("dell'amivo"))
        assertEquals(true, undo.memory.isRejected("amivo"))
    }
}
