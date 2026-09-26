package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals

/** The maintainer's terminal drew dictation's staged words and adopted none of them (2026-09-26). */
class DirectCommitTest {
    @Test
    fun `each staged text replaces the one before it with real committed text`() {
        var state = DirectCommitState()
        val first = DirectCommit.translate(listOf(DictationTextOp.SetComposingText("hello")), state)
        assertEquals(listOf(DictationTextOp.CommitText("hello")), first.ops)
        state = first.state
        val second = DirectCommit.translate(listOf(DictationTextOp.SetComposingText("hello there")), state)
        assertEquals(listOf(DictationTextOp.DeleteBeforeCursor(5), DictationTextOp.CommitText("hello there")), second.ops)
        state = second.state
        val finished = DirectCommit.translate(
            listOf(DictationTextOp.SetComposingText("Hello there. "), DictationTextOp.FinishComposing), state,
        )
        assertEquals(listOf(DictationTextOp.DeleteBeforeCursor(11), DictationTextOp.CommitText("Hello there. ")), finished.ops)
        assertEquals(0, finished.state.written, "a finished utterance is ordinary text and is never deleted again")
    }

    @Test
    fun `clearing an abandoned utterance deletes what it wrote and commits nothing`() {
        val state = DirectCommit.translate(listOf(DictationTextOp.SetComposingText("wrong")), DirectCommitState()).state
        val cleared = DirectCommit.translate(
            listOf(DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing), state,
        )
        assertEquals(listOf(DictationTextOp.DeleteBeforeCursor(5)), cleared.ops)
        assertEquals(0, cleared.state.written)
    }

    @Test
    fun `a direct commit passes through and is never counted as replaceable`() {
        val out = DirectCommit.translate(listOf(DictationTextOp.CommitText(" ")), DirectCommitState(written = 4))
        assertEquals(listOf(DictationTextOp.CommitText(" ")), out.ops)
        assertEquals(0, out.state.written)
    }
}
