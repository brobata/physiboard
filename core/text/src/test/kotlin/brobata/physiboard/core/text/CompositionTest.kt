package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
