package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [DictationEngine] through the exact SS16 sequences a device test cannot produce
 * reliably: error-ordering rows T41-T48, the cue count of T50, and the editor-lifecycle rows
 * T55-T57.
 */
class DictationEngineLifecycleTest {

    private val settings = DictationSettings(pauseMs = 2500L, segmentedSessionEnabled = true, androidApiLevel = 33)
    private val textSettings = DictationTextSettings()

    private fun session(
        mode: DictationMode = DictationMode.RESTART_LOOP,
        active: Boolean = true,
        stopRequested: Boolean = false,
        isContinuation: Boolean = false,
        requestStartMs: Long = 0L,
        segmentsSeen: Int = 0,
        heardSpeech: Boolean = false,
        pending: PendingUtterance = PendingUtterance.None,
        silenceDeadlineMs: Long? = null,
        watchdogDeadlineMs: Long? = null,
        editorGoneDeadlineMs: Long? = null,
        ownerPackage: String? = "app",
    ) = DictationSession(
        ownerPackage = ownerPackage,
        sessionStartMs = 0L,
        active = active,
        stopRequested = stopRequested,
        heardSpeech = heardSpeech,
        restartsUsed = 0,
        mode = mode,
        isContinuation = isContinuation,
        requestStartMs = requestStartMs,
        segmentsSeen = segmentsSeen,
        utterance = UtteranceState(UtteranceContext(""), pending),
        silenceDeadlineMs = silenceDeadlineMs,
        watchdogDeadlineMs = watchdogDeadlineMs,
        editorGoneDeadlineMs = editorGoneDeadlineMs,
    )

    private fun handle(state: DictationSession?, event: DictationEvent, now: Long, latch: Boolean = false) =
        DictationEngine.handle(state, event, now, settings, textSettings, latch)

    @Test
    fun `T41 a segmented refusal within the window retries as plain and sets the latch`() {
        val state = session(mode = DictationMode.SEGMENTED, segmentsSeen = 0, requestStartMs = 0L)
        val outcome = handle(state, DictationEvent.Error(DictationErrorCode.CLIENT), now = 900L)
        assertEquals(DictationMode.RESTART_LOOP, outcome.session?.mode)
        assertEquals(listOf(DictationEffect.StartListening(DictationMode.RESTART_LOOP)), outcome.effects)
        assertEquals(true, outcome.newSegmentedRefusalLatch)
    }

    @Test
    fun `T42 a network error is not a refusal and reaches rule 8`() {
        val state = session(mode = DictationMode.SEGMENTED, segmentsSeen = 0, requestStartMs = 0L)
        val outcome = handle(state, DictationEvent.Error(DictationErrorCode.NETWORK), now = 900L)
        assertNull(outcome.session)
        assertEquals(listOf(DictationEffect.PlayStopCue, DictationEffect.ShowMessage(DictationMessage.NETWORK_ERROR)), outcome.effects)
        assertNull(outcome.newSegmentedRefusalLatch)
    }

    @Test
    fun `T43 outside the 1200ms window a client error is not a refusal either`() {
        val state = session(mode = DictationMode.SEGMENTED, segmentsSeen = 0, requestStartMs = 0L)
        val outcome = handle(state, DictationEvent.Error(DictationErrorCode.CLIENT), now = 1300L)
        assertNull(outcome.session)
        assertEquals(listOf(DictationEffect.PlayStopCue, DictationEffect.ShowMessage(DictationMessage.SPEECH_RECOGNITION_ERROR)), outcome.effects)
    }

    @Test
    fun `T44 a continuation quiet error past 700ms continues while the silence timer is pending`() {
        val state = session(isContinuation = true, requestStartMs = 9200L, silenceDeadlineMs = 15_000L, pending = PendingUtterance.Live("hello"))
        val outcome = handle(state, DictationEvent.Error(DictationErrorCode.SPEECH_TIMEOUT), now = 10_000L)
        assertNotNull(outcome.session)
        assertEquals(listOf(DictationEffect.StartListening(DictationMode.RESTART_LOOP)), outcome.effects)
        assertTrue(outcome.textOps.isNotEmpty(), "the pending partial must still be finished")
    }

    @Test
    fun `T45 a continuation quiet error whose timer already fired just ends`() {
        val state = session(isContinuation = true, requestStartMs = 9200L, silenceDeadlineMs = null, pending = PendingUtterance.Live("hello"))
        val outcome = handle(state, DictationEvent.Error(DictationErrorCode.SPEECH_TIMEOUT), now = 10_000L)
        assertNull(outcome.session)
        assertEquals(listOf(DictationEffect.PlayStopCue), outcome.effects)
        assertTrue(outcome.textOps.isNotEmpty(), "the pending partial is still finished, not discarded")
    }

    @Test
    fun `T46 a fast continuation failure with no partial reaches rule 8`() {
        val state = session(isContinuation = true, requestStartMs = 9700L)
        val outcome = handle(state, DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 10_000L)
        assertNull(outcome.session)
        assertEquals(listOf(DictationEffect.PlayStopCue, DictationEffect.ShowMessage(DictationMessage.NO_TEXT_RECOGNIZED)), outcome.effects)
        assertTrue(outcome.textOps.isEmpty())
    }

    @Test
    fun `T47 segmented with segments already seen ends quietly`() {
        val state = session(mode = DictationMode.SEGMENTED, segmentsSeen = 2, heardSpeech = true)
        val outcome = handle(state, DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 5000L)
        assertNull(outcome.session)
        assertEquals(listOf(DictationEffect.PlayStopCue), outcome.effects)
    }

    @Test
    fun `T48 an event with no session is ignored`() {
        val outcome = handle(null, DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 5000L)
        assertNull(outcome.session)
        assertTrue(outcome.effects.isEmpty())
        assertTrue(outcome.textOps.isEmpty())
    }

    @Test
    fun `T50 exactly one start cue and one stop cue, and a stray callback after end is ignored`() {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        val ready = handle(started.session, DictationEvent.ReadyForSpeech, now = 10L)
        assertEquals(listOf(DictationEffect.PlayStartCue), ready.effects)

        val ended = handle(ready.session, DictationEvent.Error(DictationErrorCode.SERVER), now = 20L)
        assertNull(ended.session)
        assertTrue(ended.effects.contains(DictationEffect.PlayStopCue))

        val strayCallback = handle(ended.session, DictationEvent.Error(DictationErrorCode.SERVER), now = 30L)
        assertTrue(strayCallback.effects.isEmpty(), "a callback after the session ended must not vibrate again")
    }

    // SS6.3: "The watchdog is also armed ... when the engine reports end of speech." This task's
    // first finding: there was no event for onEndOfSpeech() at all before this.

    @Test
    fun `end of speech arms the segmented watchdog`() {
        val state = session(mode = DictationMode.SEGMENTED, watchdogDeadlineMs = null)
        val outcome = handle(state, DictationEvent.EndOfSpeech, now = 1000L)
        assertEquals(1000L + DictationTiming.watchdogMs(settings.pauseMs), outcome.session?.watchdogDeadlineMs)
        assertTrue(outcome.effects.isEmpty(), "arming the watchdog is a pure state change, not an effect")
    }

    @Test
    fun `end of speech does nothing in restart-loop mode`() {
        val state = session(mode = DictationMode.RESTART_LOOP, watchdogDeadlineMs = null)
        val outcome = handle(state, DictationEvent.EndOfSpeech, now = 1000L)
        assertNull(outcome.session?.watchdogDeadlineMs, "restart-loop mode has no watchdog to arm here")
    }

    // SS2.6 steps 4 and 7: this task's third finding. A start failure now carries a reason, and
    // the engine turns it into the spec's log-only message; never a toast.

    @Test
    fun `a start failure reports 'Speech recognition not available' and never toasts`() {
        val outcome = handle(session(active = false), DictationEvent.StartFailed(DictationStartFailureReason.RECOGNITION_UNAVAILABLE), now = 100L)
        assertNull(outcome.session)
        assertEquals(listOf(DictationEffect.LogMessage(DictationMessage.SPEECH_RECOGNITION_NOT_AVAILABLE)), outcome.effects)
    }

    @Test
    fun `a start failure from a security exception reports 'Microphone permission denied'`() {
        val outcome = handle(session(active = false), DictationEvent.StartFailed(DictationStartFailureReason.SECURITY_FAILURE), now = 100L)
        assertEquals(listOf(DictationEffect.LogMessage(DictationMessage.MIC_PERMISSION_DENIED)), outcome.effects)
    }

    @Test
    fun `any other start failure reports 'Speech recognition error'`() {
        val outcome = handle(session(active = false), DictationEvent.StartFailed(DictationStartFailureReason.OTHER_FAILURE), now = 100L)
        assertEquals(listOf(DictationEffect.LogMessage(DictationMessage.SPEECH_RECOGNITION_ERROR)), outcome.effects)
    }

    @Test
    fun `T55 a new field in the same app cancels the editor-gone grace`() {
        val closed = handle(session(), DictationEvent.EditorFieldClosed, now = 1000L)
        assertEquals(1500L, closed.session?.editorGoneDeadlineMs)

        val reopened = handle(closed.session, DictationEvent.EditorFieldOpened("app"), now = 1200L)
        assertNotNull(reopened.session)
        assertNull(reopened.session.editorGoneDeadlineMs)

        val tick = handle(reopened.session, DictationEvent.ClockTick, now = 1500L)
        assertNotNull(tick.session, "nothing should happen once the grace was cancelled")
        assertTrue(tick.effects.isEmpty())
    }

    @Test
    fun `T56 the field closing with nothing to replace it ends the session at 500ms`() {
        val closed = handle(session(), DictationEvent.EditorFieldClosed, now = 1000L)
        val tick = handle(closed.session, DictationEvent.ClockTick, now = 1500L)
        assertNull(tick.session)
        assertTrue(tick.effects.contains(DictationEffect.CancelListening))
        assertTrue(tick.effects.contains(DictationEffect.PlayStopCue))
    }

    @Test
    fun `T57 a field from a different app ends the session immediately`() {
        val outcome = handle(session(ownerPackage = "app"), DictationEvent.EditorFieldOpened("other.app"), now = 1000L)
        assertNull(outcome.session)
        assertTrue(outcome.effects.contains(DictationEffect.CancelListening))
        assertTrue(outcome.effects.contains(DictationEffect.PlayStopCue))
    }
}
