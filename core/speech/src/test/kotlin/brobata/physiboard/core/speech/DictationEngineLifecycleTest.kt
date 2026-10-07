package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: dictation.md SS16, the lifecycle rows T1 to T12, each driven through [DictationHarness]. */
class DictationEngineLifecycleTest {

    private fun started(settings: DictationSettings = DictationSettings(androidApiLevel = 36)): DictationHarness {
        val h = DictationHarness(settings)
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        h.send(DictationEvent.ReadyForSpeech, now = 10L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        return h
    }

    @Test
    fun `T1 a trigger with no session starts one in the STARTING phase with the planned request`() {
        val h = DictationHarness(DictationSettings(androidApiLevel = 36, stopAfterSilenceMs = 10_000L))
        val started = h.send(DictationEvent.Trigger("app", "Hello "), now = 0L)
        val session = assertNotNull(started.session)
        assertEquals(DictationPhase.STARTING, session.phase)
        assertEquals("app", session.ownerPackage)
        assertEquals("Hello ", session.utterance.context.textBeforeUtterance)
        assertEquals(10_000L, session.silenceLimitMs)
        assertEquals(11_000L, session.request.completeSilenceMs)
        assertEquals(true, session.request.segmented)
    }

    @Test
    fun `T2 ready moves the session to LISTENING and arms the cue fallback`() {
        val h = DictationHarness()
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        val ready = h.send(DictationEvent.ReadyForSpeech, now = 10L)
        assertEquals(DictationPhase.LISTENING, ready.session?.phase)
        assertEquals(310L, ready.session?.cueFallbackDeadlineMs)
    }

    @Test
    fun `T3 the first audio report plays the cue and clears the fallback`() {
        val h = DictationHarness()
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        h.send(DictationEvent.ReadyForSpeech, now = 10L)
        val audio = h.send(DictationEvent.FirstAudio, now = 40L)
        assertEquals(listOf(DictationEffect.PlayStartCue), audio.effects)
        assertEquals(true, audio.session?.cuePlayed)
        assertNull(audio.session?.cueFallbackDeadlineMs)
    }

    @Test
    fun `T4 a trigger while LISTENING asks the engine to stop and arms the watchdog`() {
        val h = started()
        val stop = h.send(DictationEvent.Trigger("app", null), now = 1_000L)
        assertEquals(DictationPhase.STOPPING, stop.session?.phase)
        assertEquals(2_500L, stop.session?.stopWatchdogDeadlineMs)
        assertEquals(listOf(DictationEffect.StopListening), stop.effects)
        assertNull(stop.session?.silenceDeadlineMs, "no silence limit while stopping")
    }

    @Test
    fun `T5 a trigger while STOPPING ends the session at once`() {
        val h = started()
        h.send(DictationEvent.Trigger("app", null), now = 1_000L)
        val again = h.send(DictationEvent.Trigger("app", null), now = 1_200L)
        assertNull(again.session)
        assertTrue(DictationEffect.CancelListening in again.effects)
    }

    @Test
    fun `T6 a final while LISTENING commits and re-listens at once, dropping the segmented ask`() {
        val h = started()
        val final = h.send(DictationEvent.FinalResult("hello"), now = 1_000L)
        assertEquals("Hello ", h.field.text)
        assertEquals(DictationEffect.StartListening(final.session!!.request), final.effects.last())
        assertEquals(false, final.session.request.segmented)
        assertEquals(true, final.newSegmentedRefusalLatch)
    }

    @Test
    fun `T7 a final while STOPPING commits and ends`() {
        val h = started()
        h.send(DictationEvent.PartialResult("hello"), now = 500L)
        h.send(DictationEvent.Trigger("app", null), now = 1_000L)
        val final = h.send(DictationEvent.FinalResult(null), now = 1_100L)
        assertNull(final.session)
        assertEquals("Hello ", h.field.text, "an empty final finishes from the partial (D3)")
        assertEquals(listOf(DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus), final.effects)
    }

    @Test
    fun `T8 a segment commits and the session goes on with no new request`() {
        val h = started()
        h.drainEffects()
        val segment = h.send(DictationEvent.SegmentResult("hello"), now = 1_000L)
        assertEquals("Hello ", h.field.text)
        assertTrue(segment.effects.isEmpty())
        assertEquals(1_000L, segment.session?.lastSpeechMs)
    }

    @Test
    fun `T9 speech refreshes the silence limit`() {
        val h = started(DictationSettings(androidApiLevel = 36, stopAfterSilenceMs = 5_000L))
        assertEquals(5_000L, h.session?.silenceDeadlineMs)
        h.send(DictationEvent.BeginningOfSpeech, now = 3_000L)
        assertEquals(8_000L, h.session?.silenceDeadlineMs)
        h.send(DictationEvent.PartialResult("a"), now = 4_000L)
        assertEquals(9_000L, h.session?.silenceDeadlineMs)
    }

    @Test
    fun `T10 an editor rejecting the insert clears the partial and ends`() {
        val h = started()
        h.send(DictationEvent.PartialResult("half"), now = 500L)
        val rejected = h.send(DictationEvent.EditorRejectedInsert, now = 600L)
        assertNull(rejected.session)
        assertEquals("", h.field.text)
        assertTrue(DictationEffect.CancelListening in rejected.effects)
    }

    @Test
    fun `T11 every ending releases focus exactly once and plays the stop cue only after a start cue`() {
        val h = started()
        val end = h.send(DictationEvent.KeyDown, now = 500L)
        assertEquals(1, end.effects.count { it is DictationEffect.ReleaseAudioFocus })
        assertEquals(1, end.effects.count { it is DictationEffect.PlayStopCue })

        val noCue = DictationHarness()
        noCue.send(DictationEvent.Trigger("app", ""), now = 0L)
        val early = noCue.send(DictationEvent.KeyDown, now = 5L)
        assertEquals(0, early.effects.count { it is DictationEffect.PlayStopCue })
        assertEquals(1, early.effects.count { it is DictationEffect.ReleaseAudioFocus })
    }

    @Test
    fun `T12 a whitespace-only final finishes from the last partial instead of writing the whitespace`() {
        val h = started()
        h.send(DictationEvent.PartialResult("half a thought"), now = 500L)
        h.send(DictationEvent.FinalResult("   "), now = 1_000L)
        assertEquals("Half a thought ", h.field.text)
    }
}
