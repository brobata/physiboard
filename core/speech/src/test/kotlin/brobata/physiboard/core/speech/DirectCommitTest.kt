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

    @Test
    fun `releasing the region drops what was staged and writes nothing`() {
        val written = run(
            listOf(DictationTextOp.SetComposingText("ls -la")),
            listOf(DictationTextOp.ReleaseComposing),
            listOf(DictationTextOp.FinishComposing),
        )
        assertEquals(emptyList(), written)
    }

    /**
     * A web terminal, `dictation_stop_on_typing` off: "ls -la" is staged (never shown), the user
     * presses Enter, and the keyboard reports the edit. The staged words must not be typed after
     * the Enter; an edit report that came out as a finish did exactly that (a commit of the raw
     * partial into the shell).
     */
    @Test
    fun `an edit under a staged utterance never types it after the edit, through the engine`() {
        val settings = DictationSettings(androidApiLevel = 36, stopOnTyping = false)
        val textSettings = DictationTextSettings()
        var session: DictationSession? = null
        var state = DirectCommitState()
        val written = mutableListOf<DictationTextOp>()
        fun send(event: DictationEvent, now: Long) {
            val outcome = DictationEngine.handle(session, event, now, settings, textSettings, segmentedRefusalLatch = false)
            session = outcome.session
            val translated = DirectCommit.translate(outcome.textOps, state)
            state = translated.state
            written += translated.ops
        }
        send(DictationEvent.Trigger("terminal", ""), 0L)
        send(DictationEvent.ReadyForSpeech, 10L)
        send(DictationEvent.PartialResult("ls -la"), 100L)
        send(DictationEvent.UserEditedComposingText, 200L)
        assertEquals(emptyList(), written, "the edit report writes nothing")
        send(DictationEvent.FinalResult("ls -la"), 300L)
        send(DictationEvent.Error(DictationErrorCode.NO_MATCH), 400L)
        assertEquals(emptyList(), written, "nor does anything later in that utterance")
    }
}
