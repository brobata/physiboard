package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Three ways a session used to fail to end cleanly. spec: dictation.md SS6.3 ("the watchdog is
 * also armed ... on an explicit stop"), SS6.5 (a stop ends the session, so nothing that was
 * scheduled to reopen the microphone may still do so), and SS3's ending table, where every row
 * that ends the session says the composing partial is cleared.
 */
class DictationEndingsTest {

    private val restartLoop = DictationSettings(pauseMs = 2000L, androidApiLevel = 31)
    private val segmented = DictationSettings(pauseMs = 2000L, androidApiLevel = 34)
    private val textSettings = DictationTextSettings()

    private fun handle(state: DictationSession?, event: DictationEvent, now: Long, settings: DictationSettings) =
        DictationEngine.handle(state, event, now, settings, textSettings, segmentedRefusalLatch = false)

    private fun readySession(settings: DictationSettings): DictationSession? {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L, settings)
        return handle(started.session, DictationEvent.ReadyForSpeech, now = 10L, settings).session
    }

    private fun composing(settings: DictationSettings): DictationSession? =
        handle(readySession(settings), DictationEvent.PartialResult("half a thought"), now = 100L, settings).session

    private val clear = listOf(DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing)

    // C4: a stop must arm the watchdog in restart-loop mode too, or a recognizer that never
    // answers stopListening leaves the session active for ever, and every later trigger stops it.

    @Test
    fun `an explicit stop in restart-loop mode arms the watchdog`() {
        val session = readySession(restartLoop)
        assertEquals(DictationMode.RESTART_LOOP, session!!.mode)
        val stopped = handle(session, DictationEvent.Trigger("app", ""), now = 500L, restartLoop)
        val after = stopped.session!!
        assertTrue(after.stopRequested)
        assertEquals(500L + DictationTiming.watchdogMs(2000L), after.watchdogDeadlineMs)
    }

    @Test
    fun `an explicit stop in segmented mode still arms the watchdog`() {
        val session = readySession(segmented)
        assertEquals(DictationMode.SEGMENTED, session!!.mode)
        val stopped = handle(session, DictationEvent.Trigger("app", ""), now = 500L, segmented)
        assertEquals(500L + DictationTiming.watchdogMs(2000L), stopped.session!!.watchdogDeadlineMs)
    }

    @Test
    fun `the watchdog after an unanswered stop ends the session and cancels the recognizer, latching only in segmented mode`() {
        val stopped = handle(readySession(restartLoop), DictationEvent.Trigger("app", ""), now = 500L, restartLoop)
        val fired = handle(stopped.session, DictationEvent.ClockTick, now = stopped.session!!.watchdogDeadlineMs!!, restartLoop)
        assertNull(fired.session)
        assertTrue(DictationEffect.CancelListening in fired.effects)
        assertNull(fired.newSegmentedRefusalLatch, "a restart-loop watchdog says nothing about segmented support")
    }

    // C5: the busy retry must not outlive a stop, or the microphone reopens after the user
    // asked for it to close.

    @Test
    fun `an explicit stop clears a pending busy retry so the microphone does not reopen`() {
        val busy = handle(readySession(restartLoop), DictationEvent.Error(DictationErrorCode.RECOGNIZER_BUSY), now = 100L, restartLoop)
        assertNotNull(busy.session!!.busyRetryDeadlineMs)

        val stopped = handle(busy.session, DictationEvent.Trigger("app", ""), now = 200L, restartLoop)
        assertNull(stopped.session!!.busyRetryDeadlineMs)

        val tick = handle(stopped.session, DictationEvent.ClockTick, now = 400L, restartLoop)
        assertFalse(tick.effects.any { it is DictationEffect.StartListening })
    }

    // C6: most endings clear live composing text; the exceptions below (this task's fourth
    // finding) commit it instead, so an unproven recognizer shape never means lost words.

    @Test
    fun `the segmented session ending commits a live partial instead of discarding it`() {
        // Not the literal SS3 table (silent on this case), but the survivable choice: the engine
        // can end a segmented session at any moment, including with an un-finalized partial on
        // screen, and losing those words is exactly the maintainer's report.
        val ended = handle(composing(segmented), DictationEvent.SegmentedSessionEnded, now = 300L, segmented)
        assertNull(ended.session)
        assertEquals(listOf(DictationTextOp.SetComposingText("Half a thought "), DictationTextOp.FinishComposing), ended.textOps)
    }

    @Test
    fun `a failed start clears a live partial`() {
        val ended = handle(composing(restartLoop), DictationEvent.StartFailed(DictationStartFailureReason.OTHER_FAILURE), now = 300L, restartLoop)
        assertNull(ended.session)
        assertEquals(clear, ended.textOps)
    }

    @Test
    fun `an editor rejecting the insert clears a live partial`() {
        val ended = handle(composing(restartLoop), DictationEvent.EditorRejectedInsert, now = 300L, restartLoop)
        assertNull(ended.session)
        assertEquals(clear, ended.textOps)
    }

    @Test
    fun `a segmented refusal re-listen clears the partial it drops`() {
        val refused = handle(composing(segmented), DictationEvent.Error(DictationErrorCode.CLIENT), now = 300L, segmented)
        assertEquals(DictationMode.RESTART_LOOP, refused.session!!.mode)
        assertEquals(clear, refused.textOps)
    }

    @Test
    fun `an ending with nothing composing writes nothing`() {
        val ended = handle(readySession(restartLoop), DictationEvent.StartFailed(DictationStartFailureReason.OTHER_FAILURE), now = 300L, restartLoop)
        assertTrue(ended.textOps.isEmpty())
    }

    @Test
    fun `a real error while a partial is on screen commits it instead of discarding it`() {
        // spec SS6.6 rule 8's literal text says "clear partial", but a real error is exactly what an
        // unverified recognizer shape (SS5) could raise instead of ending cleanly; committing what
        // was already heard, same as rule 5 already does for a quiet error, is this task's fourth
        // finding.
        val ended = handle(composing(restartLoop), DictationEvent.Error(DictationErrorCode.SERVER), now = 300L, restartLoop)
        assertNull(ended.session)
        assertEquals(listOf(DictationTextOp.SetComposingText("Half a thought "), DictationTextOp.FinishComposing), ended.textOps)
        assertEquals(listOf(DictationEffect.PlayStopCue, DictationEffect.ShowMessage(DictationMessage.SPEECH_RECOGNITION_ERROR)), ended.effects)
    }

    @Test
    fun `the segmented watchdog commits a live partial instead of discarding it`() {
        // The watchdog can now fire with a partial on screen because end of speech arms it too
        // (this task's first finding); the fourth finding says that must not lose the words.
        val readyState = readySession(segmented)!!
        val begun = handle(readyState, DictationEvent.BeginningOfSpeech, now = 50L, segmented).session!!
        val spoke = handle(begun, DictationEvent.PartialResult("half a thought"), now = 100L, segmented).session!!
        val afterEndOfSpeech = handle(spoke, DictationEvent.EndOfSpeech, now = 150L, segmented).session!!
        assertNotNull(afterEndOfSpeech.watchdogDeadlineMs)
        val fired = handle(afterEndOfSpeech, DictationEvent.ClockTick, now = afterEndOfSpeech.watchdogDeadlineMs, segmented)
        assertNull(fired.session)
        assertEquals(listOf(DictationTextOp.SetComposingText("Half a thought "), DictationTextOp.FinishComposing), fired.textOps)
        assertEquals(true, fired.newSegmentedRefusalLatch, "zero segments were ever seen")
    }

    // C8: three small slips against SS1, SS6.3 and SS7.3.

    @Test
    fun `a final counts as heard speech (spec SS1) so the first-words grace no longer applies`() {
        val continued = handle(readySession(restartLoop), DictationEvent.FinalResult("hello"), now = 100L, restartLoop)
        assertTrue(continued.session!!.heardSpeech)
    }

    @Test
    fun `the segmented refusal window is measured from the session start, not the latest re-listen`() {
        // A quiet re-listen at 500 ms restarts the request clock; a client error at 1500 ms is
        // 1000 ms into that request but 1500 ms into the session: past SS6.3's 1200 ms window.
        val reListened = handle(readySession(segmented), DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 500L, segmented)
        assertNotNull(reListened.session)
        val late = handle(reListened.session, DictationEvent.Error(DictationErrorCode.CLIENT), now = 1500L, segmented)
        assertNull(late.newSegmentedRefusalLatch, "a late client error is not a segmented refusal")
    }

    @Test
    fun `a whitespace-only final finishes from the last partial instead of writing the whitespace`() {
        val ended = handle(composing(restartLoop), DictationEvent.FinalResult("   "), now = 300L, restartLoop)
        assertEquals(listOf(DictationTextOp.SetComposingText("Half a thought "), DictationTextOp.FinishComposing), ended.textOps)
    }

    @Test
    fun `a trailing partial after a stop does not disarm the watchdog, so the session still ends`() {
        val stopped = handle(readySession(restartLoop), DictationEvent.Trigger("app", ""), now = 500L, restartLoop)
        val deadline = stopped.session!!.watchdogDeadlineMs!!
        val trailing = handle(stopped.session, DictationEvent.PartialResult("late words"), now = 600L, restartLoop)
        assertEquals(deadline, trailing.session!!.watchdogDeadlineMs, "a partial after a stop must not leave the session with no timer")

        val fired = handle(trailing.session, DictationEvent.ClockTick, now = deadline, restartLoop)
        assertNull(fired.session)
    }
}
