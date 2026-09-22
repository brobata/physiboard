package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: text-input.md SS4, item 1 (variations replacement). */
class CompositionTest {

    @Test
    fun `replacing one character composes, commits and finishes again`() {
        val ops = Composition.replaceVariation(charsBeforeCursor = 1, targetLength = 1, replacement = "é")
        assertEquals(
            listOf(
                EditorOp.FinishComposing,
                EditorOp.SetComposingRegion(1, 1),
                EditorOp.CommitText("é"),
                EditorOp.FinishComposing,
            ),
            ops,
        )
    }

    @Test
    fun `a selection present before the swap is restored shifted by the length change`() {
        val selection = TextWindow("cafe", cursorOrSelectionStart = 3, selectionEnd = 4)
        val ops = Composition.replaceVariation(1, 1, "éé", selectionBeforeReplacement = selection)
        assertEquals(EditorOp.SetSelection(4, 5), ops.last())
    }

    // spec: rebuild-from-scratch.md "The editor is not a reliable narrator" point 3, "A composing
    // region is a privilege, not a default."

    @Test
    fun `composing-unsafe replaces the span directly without ever setting a composing region`() {
        val ops = Composition.replaceVariation(charsBeforeCursor = 1, targetLength = 1, replacement = "é", composingSafety = ComposingSafety.UNSAFE)
        assertEquals(
            listOf(
                EditorOp.FinishComposing,
                EditorOp.DeleteSurrounding(1, 0),
                EditorOp.CommitText("é"),
            ),
            ops,
        )
        assertTrue(ops.none { it is EditorOp.SetComposingRegion })
    }

    @Test
    fun `composing-unsafe still restores a selection shifted by the length change`() {
        val selection = TextWindow("cafe", cursorOrSelectionStart = 3, selectionEnd = 4)
        val ops = Composition.replaceVariation(1, 1, "éé", selectionBeforeReplacement = selection, composingSafety = ComposingSafety.UNSAFE)
        assertEquals(EditorOp.SetSelection(4, 5), ops.last())
        assertTrue(ops.none { it is EditorOp.SetComposingRegion })
    }

    @Test
    fun `composing-unsafe changes nothing when the span is not anchored at the cursor`() {
        // Without a composing region there is no way to reach a span that does not end at the
        // cursor without an absolute document offset this module never has (spec: "If the app
        // refuses to set the composing region, nothing is changed" applies the same way here).
        val ops = Composition.replaceVariation(charsBeforeCursor = 3, targetLength = 1, replacement = "é", composingSafety = ComposingSafety.UNSAFE)
        assertEquals(emptyList(), ops)
    }
}
