package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The maintainer's own complaints (dictation.md SS14 D14 to D17, verbatim in the task), each as a
 * sequence a real device cannot produce reliably, driven through [DictationHarness].
 */
class DictationEngineRequiredScenariosTest {

    private fun started(settings: DictationSettings = DictationSettings(androidApiLevel = 36)): DictationHarness {
        val h = DictationHarness(settings)
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        h.send(DictationEvent.ReadyForSpeech, now = 10L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        return h
    }

    @Test
    fun `I start talking right at the cue - the cue comes with the open microphone, and a breath first costs nothing`() {
        val h = DictationHarness()
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        h.send(DictationEvent.ReadyForSpeech, now = 10L)
        val cue = h.send(DictationEvent.FirstAudio, now = 40L)
        assertEquals(listOf(DictationEffect.PlayStartCue), cue.effects)
        // Five seconds of breath: the engine gives up, the keyboard does not.
        val quiet = h.send(DictationEvent.Error(DictationErrorCode.SPEECH_TIMEOUT), now = 5_040L)
        assertNotNull(quiet.session)
        assertTrue(quiet.effects.any { it is DictationEffect.StartListening })
        assertTrue(quiet.effects.none { it is DictationEffect.ShowMessage })
        h.send(DictationEvent.PartialResult("there we go"), now = 6_000L)
        assertEquals("There we go", h.field.text)
    }

    @Test
    fun `a long pause mid-sentence never cuts me off`() {
        // A count to ten at the 15 s default; twenty seconds of thinking with the limit off.
        for ((settings, pauseMs) in listOf(DictationSettings(androidApiLevel = 36) to 12_000L, DictationSettings(androidApiLevel = 36, stopAfterSilenceMs = 0L) to 20_000L)) {
            val h = started(settings)
            h.send(DictationEvent.PartialResult("the thing is"), now = 1_000L)
            h.send(DictationEvent.SegmentResult("the thing is"), now = 2_500L)
            h.drainEffects()
            val outcomes = h.runClockTo(2_500L + pauseMs)
            assertTrue(outcomes.all { it.effects.isEmpty() }, "no timer acts during a pause under the silence limit")
            assertNotNull(h.session)
            h.send(DictationEvent.PartialResult("that we should go"), now = 3_000L + pauseMs)
            h.send(DictationEvent.SegmentResult("that we should go."), now = 4_000L + pauseMs)
            assertEquals("The thing is that we should go. ", h.field.text)
        }
    }

    @Test
    fun `a second utterance after a first chains its context without re-reading the editor`() {
        val h = started()
        h.send(DictationEvent.PartialResult("hello"), now = 100L)
        val firstFinal = h.send(DictationEvent.SegmentResult("hello"), now = 500L)
        assertEquals("Hello ", firstFinal.textOps.filterIsInstance<DictationTextOp.SetComposingText>().first().text)
        val secondPartial = h.send(DictationEvent.PartialResult("world"), now = 700L)
        assertEquals("world", secondPartial.textOps.filterIsInstance<DictationTextOp.SetComposingText>().first().text, "no capital: the frozen context after 'Hello ' is not a sentence boundary")
        h.send(DictationEvent.SegmentResult("world"), now = 900L)
        assertEquals("Hello world ", h.field.text)
    }

    @Test
    fun `words deleted mid-utterance are never typed back when the utterance ends`() {
        val h = started(DictationSettings(androidApiLevel = 36, stopOnTyping = false))
        h.send(DictationEvent.PartialResult("hello there"), now = 100L)
        assertEquals("Hello there", h.field.text)
        // The user backspaces the composing text while the engine is still listening.
        h.field.apply(listOf(DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing))
        val edited = h.send(DictationEvent.UserEditedComposingText, now = 200L)
        assertEquals(PendingUtterance.Invalidated, edited.session?.utterance?.pending)
        // Google's engine delivers its terminal empty final (D3) with no new speech since the edit.
        val ended = h.send(DictationEvent.FinalResult(null), now = 300L)
        assertTrue(ended.textOps.isEmpty(), "the deleted words must not be typed back")
        assertEquals("", h.field.text)
    }

    @Test
    fun `Fn again is the stop, and it is honoured within 1500 ms whatever the engine does`() {
        val h = started()
        h.send(DictationEvent.PartialResult("last words"), now = 1_000L)
        h.send(DictationEvent.Trigger("app", null), now = 1_500L)
        assertEquals(DictationPhase.STOPPING, h.session?.phase)
        h.runClockTo(3_000L)
        assertNull(h.session)
        assertEquals("Last words ", h.field.text)
    }

    @Test
    fun `an engine that fails outright ends the session with a toast and keeps what was heard`() {
        val h = started()
        h.send(DictationEvent.PartialResult("keep"), now = 500L)
        val failed = h.send(DictationEvent.Error(DictationErrorCode.AUDIO), now = 600L)
        assertNull(failed.session)
        assertEquals("Keep ", h.field.text)
        assertEquals(listOf(DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus, DictationEffect.ReleaseImeVisible, DictationEffect.ShowMessage(DictationMessage.SPEECH_RECOGNITION_ERROR)), failed.effects)
    }
}
