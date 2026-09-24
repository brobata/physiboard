package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The seven sequences a real device cannot produce reliably (Google's own microphone timing is
 * not controllable from a test), driven end to end through [DictationEngine.handle] starting from
 * [DictationEvent.Trigger]. Each test names the spec behaviour it proves.
 */
class DictationEngineRequiredScenariosTest {

    private val settings = DictationSettings(pauseMs = 2000L, segmentedSessionEnabled = true, androidApiLevel = 0) // API 0: always restart-loop
    private val textSettings = DictationTextSettings()

    private fun handle(state: DictationSession?, event: DictationEvent, now: Long, latch: Boolean = false) =
        DictationEngine.handle(state, event, now, settings, textSettings, latch)

    @Test
    fun `a breath before speaking is absorbed by the first-words grace, not reported as failure`() {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        val ready = handle(started.session, DictationEvent.ReadyForSpeech, now = 10L)
        assertEquals(listOf(DictationEffect.PlayStartCue), ready.effects)

        // The engine closes the mic ~2s after the trigger with nothing heard yet (the user drew breath).
        val quietTimeout = handle(ready.session, DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 2000L)
        assertNotNull(quietTimeout.session, "the grace must re-listen instead of ending the session")
        assertEquals(listOf(DictationEffect.StartListening(DictationMode.RESTART_LOOP)), quietTimeout.effects)
        assertEquals(1, quietTimeout.session.restartsUsed)

        val readyAgain = handle(quietTimeout.session, DictationEvent.ReadyForSpeech, now = 2010L)
        assertTrue(readyAgain.effects.isEmpty(), "the start cue plays only once per session")

        val spoke = handle(readyAgain.session, DictationEvent.PartialResult("hello"), now = 4000L)
        assertEquals(true, spoke.session?.heardSpeech)
        assertEquals(listOf(DictationTextOp.SetComposingText("Hello")), spoke.textOps)
    }

    @Test
    fun `a pause mid-sentence does not end the session if speech resumes before the silence timer fires`() {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        val ready = handle(started.session, DictationEvent.ReadyForSpeech, now = 10L)
        val firstWord = handle(ready.session, DictationEvent.PartialResult("hello"), now = 100L)
        val firstFinal = handle(firstWord.session, DictationEvent.FinalResult("hello"), now = 500L)
        val silenceDeadline = firstFinal.session?.silenceDeadlineMs
        assertNotNull(silenceDeadline)
        assertEquals(listOf(DictationEffect.StartListening(DictationMode.RESTART_LOOP)), firstFinal.effects)

        // The user pauses mid-sentence, then resumes talking before the deadline arrives.
        val resumed = handle(firstFinal.session, DictationEvent.PartialResult("world"), now = silenceDeadline - 100)
        assertNull(resumed.session?.silenceDeadlineMs, "a fresh partial must cancel the pending silence timer")

        val tickAtOldDeadline = handle(resumed.session, DictationEvent.ClockTick, now = silenceDeadline)
        assertNotNull(tickAtOldDeadline.session, "the pause must not end a session that already resumed talking")
        assertTrue(tickAtOldDeadline.effects.isEmpty())
    }

    @Test
    fun `a second utterance after a first chains its context without re-reading the editor`() {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        val ready = handle(started.session, DictationEvent.ReadyForSpeech, now = 10L)
        val firstPartial = handle(ready.session, DictationEvent.PartialResult("hello"), now = 100L)
        val firstFinal = handle(firstPartial.session, DictationEvent.FinalResult("hello"), now = 500L)
        assertEquals("Hello ", firstFinal.textOps.filterIsInstance<DictationTextOp.SetComposingText>().first().text)

        val secondPartial = handle(firstFinal.session, DictationEvent.PartialResult("world"), now = 700L)
        assertEquals("world", secondPartial.textOps.filterIsInstance<DictationTextOp.SetComposingText>().first().text, "no capital: the frozen context after 'Hello ' is not a sentence boundary")

        val secondFinal = handle(secondPartial.session, DictationEvent.FinalResult("world"), now = 900L)
        assertEquals("world ", secondFinal.textOps.filterIsInstance<DictationTextOp.SetComposingText>().first().text)
    }

    @Test
    fun `words deleted mid-utterance are never typed back when the utterance ends`() {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        val ready = handle(started.session, DictationEvent.ReadyForSpeech, now = 10L)
        val partial = handle(ready.session, DictationEvent.PartialResult("hello there"), now = 100L)
        assertEquals(listOf(DictationTextOp.SetComposingText("Hello there")), partial.textOps)

        // The user backspaces the composing text while the engine is still listening.
        val edited = handle(partial.session, DictationEvent.UserEditedComposingText, now = 200L)
        assertEquals(PendingUtterance.Invalidated, edited.session?.utterance?.pending)

        // Google's engine delivers its terminal empty final (D3) with no new speech since the edit.
        val ended = handle(edited.session, DictationEvent.FinalResult(null), now = 300L)
        assertTrue(ended.textOps.isEmpty(), "the deleted words must not be typed back")
    }

    @Test
    fun `the field going away without a replacement ends the session at the grace deadline`() {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        val ready = handle(started.session, DictationEvent.ReadyForSpeech, now = 10L)
        val closed = handle(ready.session, DictationEvent.EditorFieldClosed, now = 1000L)
        assertEquals(1500L, closed.session?.editorGoneDeadlineMs)

        val stillOpen = handle(closed.session, DictationEvent.ClockTick, now = 1400L)
        assertNotNull(stillOpen.session, "the grace has not elapsed yet")

        val gone = handle(stillOpen.session, DictationEvent.ClockTick, now = 1500L)
        assertNull(gone.session)
        assertTrue(gone.effects.containsAll(listOf(DictationEffect.CancelListening, DictationEffect.PlayStopCue)))
    }

    @Test
    fun `an engine reporting busy retries once after the 300ms delay, not immediately`() {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        val ready = handle(started.session, DictationEvent.ReadyForSpeech, now = 10L)

        val busy = handle(ready.session, DictationEvent.Error(DictationErrorCode.RECOGNIZER_BUSY), now = 50L)
        assertTrue(busy.effects.isEmpty(), "the retry is delayed, not immediate")
        assertEquals(350L, busy.session?.busyRetryDeadlineMs)

        val tooSoon = handle(busy.session, DictationEvent.ClockTick, now = 200L)
        assertTrue(tooSoon.effects.isEmpty())

        val retried = handle(tooSoon.session, DictationEvent.ClockTick, now = 350L)
        assertEquals(listOf(DictationEffect.StartListening(DictationMode.RESTART_LOOP)), retried.effects)
        assertNull(retried.session?.busyRetryDeadlineMs)
    }

    @Test
    fun `an engine that fails outright ends the session with a toast`() {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        val ready = handle(started.session, DictationEvent.ReadyForSpeech, now = 10L)

        val failed = handle(ready.session, DictationEvent.Error(DictationErrorCode.NETWORK), now = 50L)
        assertNull(failed.session)
        assertEquals(listOf(DictationEffect.PlayStopCue, DictationEffect.ShowMessage(DictationMessage.NETWORK_ERROR)), failed.effects)
    }
}
