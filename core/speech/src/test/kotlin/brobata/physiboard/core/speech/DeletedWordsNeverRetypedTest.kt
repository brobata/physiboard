package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * spec: the fix in git c440844, "words the user deleted are not typed back when the utterance
 * ends." 2.x kept one mutable "last partial" string reused whenever a result carried no text of
 * its own, with nothing recording whether the field still agreed with it; a user who edited or
 * deleted those words while the engine kept listening would see them typed straight back the
 * moment a quiet error or an empty final arrived.
 *
 * [PendingUtterance.Invalidated] carries no text field at all, so once
 * [DictationEvent.UserEditedComposingText] has moved an utterance there, there is no string left
 * anywhere in the state for [DictationEngine] to reuse; both termination paths that would have
 * reused it (an empty final, D3; a quiet error while a partial was on screen, SS6.6 rule 5) are
 * proven here to produce no text. The final assertion is the control: the SAME quiet error, on the
 * SAME words, WITHOUT the intervening edit, does finish the utterance, so this test would fail if
 * invalidation ever became a no-op.
 */
class DeletedWordsNeverRetypedTest {

    private val settings = DictationSettings(pauseMs = 2000L)
    private val textSettings = DictationTextSettings()

    private fun handle(state: DictationSession?, event: DictationEvent, now: Long) =
        DictationEngine.handle(state, event, now, settings, textSettings, segmentedRefusalLatch = false)

    private fun sessionWithComposingWords(): DictationSession? {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        val ready = handle(started.session, DictationEvent.ReadyForSpeech, now = 10L)
        return handle(ready.session, DictationEvent.PartialResult("delete me"), now = 100L).session
    }

    @Test
    fun `an empty final after the user deletes the words inserts nothing`() {
        val edited = handle(sessionWithComposingWords(), DictationEvent.UserEditedComposingText, now = 200L)
        val ended = handle(edited.session, DictationEvent.FinalResult(null), now = 300L)
        assertTrue(ended.textOps.isEmpty())
    }

    @Test
    fun `a quiet error after the user deletes the words inserts nothing`() {
        val edited = handle(sessionWithComposingWords(), DictationEvent.UserEditedComposingText, now = 200L)
        val ended = handle(edited.session, DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 300L)
        assertTrue(ended.textOps.isEmpty())
    }

    @Test
    fun `control- without an edit, the same empty final does finish the utterance`() {
        val ended = handle(sessionWithComposingWords(), DictationEvent.FinalResult(null), now = 300L)
        assertEquals(listOf(DictationTextOp.SetComposingText("Delete me "), DictationTextOp.FinishComposing), ended.textOps)
    }
}
