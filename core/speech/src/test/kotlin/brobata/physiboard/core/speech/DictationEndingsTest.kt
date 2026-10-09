package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * spec: dictation.md SS3's ending table: which endings commit the words on screen and which
 * clear them, and the one rule that every ending shares (focus back, one stop cue at most).
 */
class DictationEndingsTest {

    private val clear = listOf(DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing)
    private val commit = listOf(DictationTextOp.SetComposingText("Half a thought "), DictationTextOp.FinishComposing)

    private fun composing(settings: DictationSettings = DictationSettings(androidApiLevel = 36)): DictationHarness {
        val h = DictationHarness(settings)
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        h.send(DictationEvent.ReadyForSpeech, now = 10L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        h.send(DictationEvent.PartialResult("half a thought"), now = 100L)
        return h
    }

    @Test
    fun `the segmented session ending while stopping commits a live partial`() {
        val h = composing()
        h.send(DictationEvent.Trigger("app", null), now = 200L)
        val ended = h.send(DictationEvent.SegmentedSessionEnded, now = 300L)
        assertNull(ended.session)
        assertEquals(commit, ended.textOps)
    }

    @Test
    fun `a failed start clears a live partial`() {
        val ended = composing().send(DictationEvent.StartFailed(DictationStartFailureReason.OTHER_FAILURE), now = 300L)
        assertNull(ended.session)
        assertEquals(clear, ended.textOps)
    }

    @Test
    fun `an editor rejecting the insert clears a live partial`() {
        val ended = composing().send(DictationEvent.EditorRejectedInsert, now = 300L)
        assertNull(ended.session)
        assertEquals(clear, ended.textOps)
    }

    @Test
    fun `another app taking the field clears a live partial`() {
        val ended = composing().send(DictationEvent.EditorFieldOpened("other.app"), now = 300L)
        assertNull(ended.session)
        assertEquals(clear, ended.textOps)
    }

    @Test
    fun `a client error once speech was heard is a real error, not a refusal - the partial is committed`() {
        val ended = composing().send(DictationEvent.Error(DictationErrorCode.CLIENT), now = 300L)
        assertNull(ended.session)
        assertEquals(commit, ended.textOps)
    }

    @Test
    fun `a real error while a partial is on screen commits it`() {
        val ended = composing().send(DictationEvent.Error(DictationErrorCode.SERVER), now = 300L)
        assertNull(ended.session)
        assertEquals(commit, ended.textOps)
        assertEquals(listOf(DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus, DictationEffect.ReleaseImeVisible, DictationEffect.ShowMessage(DictationMessage.NETWORK_ERROR)), ended.effects)
    }

    @Test
    fun `a quiet error while a partial is on screen commits it and re-listens`() {
        val h = composing()
        val quiet = h.send(DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 1_000L)
        assertEquals(commit, quiet.textOps)
        assertTrue(quiet.effects.any { it is DictationEffect.StartListening })
        assertEquals("Half a thought ", h.field.text)
    }

    @Test
    fun `a typed key, a call and the stop watchdog all commit it`() {
        assertEquals(commit, composing().send(DictationEvent.KeyDown, now = 300L).textOps)
        assertEquals(commit, composing().send(DictationEvent.AudioFocusChanged(AudioFocusChange.LOSS_TRANSIENT, callActive = true), now = 300L).textOps)
        assertEquals(commit, composing().send(DictationEvent.CallStarted, now = 300L).textOps)
        val h = composing()
        h.send(DictationEvent.Trigger("app", null), now = 300L)
        val fired = h.runClockTo(1_800L).single()
        assertEquals(commit, fired.textOps)
        assertNull(fired.session)
    }

    @Test
    fun `an ending with nothing composing writes nothing`() {
        val h = DictationHarness()
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        val ended = h.send(DictationEvent.StartFailed(DictationStartFailureReason.OTHER_FAILURE), now = 300L)
        assertTrue(ended.textOps.isEmpty())
    }

    @Test
    fun `a trailing partial after a stop still composes, and the watchdog still ends the session`() {
        val h = composing()
        h.send(DictationEvent.Trigger("app", null), now = 500L)
        val deadline = h.session!!.stopWatchdogDeadlineMs!!
        val trailing = h.send(DictationEvent.PartialResult("half a thought more"), now = 600L)
        assertEquals(deadline, trailing.session!!.stopWatchdogDeadlineMs, "a partial after a stop must not leave the session with no timer")
        h.runClockTo(deadline)
        assertNull(h.session)
        assertEquals("Half a thought more ", h.field.text)
    }
}
