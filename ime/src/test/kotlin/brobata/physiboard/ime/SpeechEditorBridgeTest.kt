package brobata.physiboard.ime

import brobata.physiboard.core.speech.DictationTextOp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * dictation.md SS7.7: the join a spoken mark makes onto the sentence before it is a verified
 * delete. Against an editor with Android's composing-span behaviour ([DictationCaretTest.ComposingField]),
 * the field is checked over the whole anchor, so a cursor the user moved to some other ". "
 * costs them nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpeechEditorBridgeTest {

    private val join = listOf(
        DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing,
        DictationTextOp.DeleteBeforeCursorIfText(". ", anchor = "I went home. "),
        DictationTextOp.SetComposingText("! "), DictationTextOp.FinishComposing,
    )

    @Test
    fun `the engine's mark and space the session wrote give way to the spoken mark`() {
        val field = DictationCaretTest.ComposingField()
        field.preset("Thanks. I went home. ", caret = 21)
        field.applyDictationTextOps(join)
        assertEquals("Thanks. I went home! ", field.text)
    }

    @Test
    fun `a cursor moved to the user's own full stop deletes nothing - the anchor does not match there`() {
        val field = DictationCaretTest.ComposingField()
        field.preset("Thanks. I went home. ", caret = 21)
        field.tap(8) // just after the user's "Thanks. "
        field.applyDictationTextOps(join)
        assertEquals("Thanks. ! I went home. ", field.text)
    }

    @Test
    fun `a partial still composing is closed before the field's end is read`() {
        val field = DictationCaretTest.ComposingField()
        field.preset("I went home. ", caret = 13)
        field.applyDictationTextOps(listOf(DictationTextOp.SetComposingText("!"))) // the preview of "exclamation point"
        assertEquals("I went home. !", field.text)
        field.applyDictationTextOps(join)
        assertEquals("I went home! ", field.text)
    }

    @Test
    fun `a no-break space, as a web editor stores the finisher's space, counts as the space`() {
        val field = DictationCaretTest.ComposingField()
        field.preset("I went home. ", caret = 13)
        field.applyDictationTextOps(join)
        assertEquals("I went home! ", field.text)
    }
}
