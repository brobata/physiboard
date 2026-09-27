package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The maintainer dictated one sentence into a terminal and it arrived seven times over, each
 * copy longer than the last (2026-09-27): every partial the recognizer produced had been
 * committed, and the delete meant to remove the previous one cannot work in a field that has
 * already consumed the characters. Nothing is written now until the utterance is finished.
 */
class DirectCommitTest {

    /** Feeds successive dispatches through the translation, carrying the state as the controller does. */
    private fun run(vararg dispatches: List<DictationTextOp>): List<DictationTextOp> {
        var state = DirectCommitState()
        val written = mutableListOf<DictationTextOp>()
        for (ops in dispatches) {
            val translated = DirectCommit.translate(ops, state)
            state = translated.state
            written += translated.ops
        }
        return written
    }

    @Test
    fun `a sentence dictated through many partials is written once, when it is finished`() {
        val written = run(
            listOf(DictationTextOp.SetComposingText("Don't worry")),
            listOf(DictationTextOp.SetComposingText("Don't worry about")),
            listOf(DictationTextOp.SetComposingText("Don't worry about 3.0")),
            listOf(DictationTextOp.SetComposingText("Don't worry about 3.0 forward "), DictationTextOp.FinishComposing),
        )
        assertEquals(listOf(DictationTextOp.CommitText("Don't worry about 3.0 forward ")), written)
    }

    @Test
    fun `a partial never reaches the field on its own`() {
        assertEquals(emptyList(), run(listOf(DictationTextOp.SetComposingText("half a thought"))))
    }

    @Test
    fun `an abandoned utterance writes nothing at all`() {
        val written = run(
            listOf(DictationTextOp.SetComposingText("wrong words")),
            listOf(DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing),
        )
        assertEquals(emptyList(), written)
    }

    @Test
    fun `two utterances in one session are written one after the other`() {
        val written = run(
            listOf(DictationTextOp.SetComposingText("first one")),
            listOf(DictationTextOp.SetComposingText("First one. "), DictationTextOp.FinishComposing),
            listOf(DictationTextOp.SetComposingText("second")),
            listOf(DictationTextOp.SetComposingText("Second one. "), DictationTextOp.FinishComposing),
        )
        assertEquals(
            listOf(DictationTextOp.CommitText("First one. "), DictationTextOp.CommitText("Second one. ")),
            written,
        )
    }

    @Test
    fun `a direct commit passes straight through and clears anything staged`() {
        val written = run(
            listOf(DictationTextOp.SetComposingText("staged")),
            listOf(DictationTextOp.CommitText(" ")),
            listOf(DictationTextOp.FinishComposing),
        )
        assertEquals(listOf(DictationTextOp.CommitText(" ")), written)
    }
}
