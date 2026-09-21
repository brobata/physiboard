package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: text-input.md SS10, "Test cases" T57-T63. */
class SelectionEditingTest {

    private val text = "alpha beta gamma"

    @Test
    fun `T57 move word left from within the last word`() {
        val window = TextWindow(text, cursorOrSelectionStart = 16)
        assertEquals(EditorOp.SetSelection(11, 11), WordMotion.moveLeft(window))
    }

    @Test
    fun `T58 move word right from the start`() {
        val window = TextWindow(text, cursorOrSelectionStart = 0)
        assertEquals(EditorOp.SetSelection(6, 6), WordMotion.moveRight(window))
    }

    @Test
    fun `T59 expand selection word left from an anchor at the end`() {
        val window = TextWindow(text, cursorOrSelectionStart = 11, selectionEnd = 16)
        val state = SelectionAnchorState(anchor = 16, movingEdge = 11)
        val result = SelectionExpansion.expandWord(state, window, MoveDirection.LEFT)
        assertEquals(EditorOp.SetSelection(6, 16), result.op)
    }

    @Test
    fun `T60 expand selection word right from an anchor at the start`() {
        val window = TextWindow(text, cursorOrSelectionStart = 0, selectionEnd = 5)
        val state = SelectionAnchorState(anchor = 0, movingEdge = 5)
        val result = SelectionExpansion.expandWord(state, window, MoveDirection.RIGHT)
        assertEquals(EditorOp.SetSelection(0, 10), result.op)
    }

    @Test
    fun `T61 reversing direction shrinks back to the anchor and collapses`() {
        val window = TextWindow(text, cursorOrSelectionStart = 0, selectionEnd = 5)
        val state = SelectionAnchorState(anchor = 0, movingEdge = 5)
        val result = SelectionExpansion.expandWord(state, window, MoveDirection.LEFT)
        assertEquals(EditorOp.SetSelection(0, 0), result.op)
        assertEquals(null, result.newState)
    }

    @Test
    fun `T62 a fresh character expansion anchors at the cursor`() {
        val window = TextWindow(text, cursorOrSelectionStart = 5)
        val result = SelectionExpansion.expandCharacter(null, window, MoveDirection.LEFT)
        assertEquals(EditorOp.SetSelection(4, 5), result.op)
    }

    @Test
    fun `T63 expanding back toward the anchor collapses the selection`() {
        val window = TextWindow(text, cursorOrSelectionStart = 4, selectionEnd = 5)
        val state = SelectionAnchorState(anchor = 5, movingEdge = 4)
        val result = SelectionExpansion.expandCharacter(state, window, MoveDirection.RIGHT)
        assertEquals(EditorOp.SetSelection(5, 5), result.op)
        assertEquals(null, result.newState)
    }

    @Test
    fun `select-all covers the whole document`() {
        assertEquals(EditorOp.SetSelection(0, text.length), SelectAll.apply(text))
    }
}
