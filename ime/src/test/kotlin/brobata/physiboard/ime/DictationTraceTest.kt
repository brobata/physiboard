package brobata.physiboard.ime

import brobata.physiboard.core.speech.AudioFocusChange
import brobata.physiboard.core.speech.DictationEffect
import brobata.physiboard.core.speech.DictationEvent
import brobata.physiboard.core.speech.DictationMessage
import brobata.physiboard.core.speech.DictationStartFailureReason
import brobata.physiboard.core.speech.DictationTextOp
import brobata.physiboard.core.speech.RecognizerRequest
import brobata.physiboard.core.speech.SessionAudioRoute
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** dictation.md SS6.9: what the dictation trace writes, and when it writes nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DictationTraceTest {

    @Before
    fun clear() {
        ShadowLog.clear()
        DictationTrace.privateNow = false
    }

    @After
    fun reset() {
        DictationTrace.privateNow = false
    }

    private fun traced(): List<String> = ShadowLog.getLogsForTag(DictationTrace.TAG).map { it.msg }

    @Test
    fun `a callback is one line in the trace`() {
        DictationTrace.dispatched(DictationEvent.ReadyForSpeech, emptyList(), listOf(DictationEffect.PlayStartCue), null)
        DictationTrace.audio("audio stop music=false players=0 media=0")
        assertEquals(listOf("ReadyForSpeech | ops=[] | effects=[PlayStartCue] | ended", "audio stop music=false players=0 media=0"), traced())
    }

    @Test
    fun `private mode writes nothing at all`() {
        DictationTrace.privateNow = true
        DictationTrace.dispatched(DictationEvent.SegmentResult("meet me at noon"), listOf(DictationTextOp.CommitText("meet me at noon")), listOf(DictationEffect.StopListening), null)
        DictationTrace.dispatched(DictationEvent.PrivateModeTurnedOn, emptyList(), listOf(DictationEffect.CancelListening), null)
        DictationTrace.audio("audio stop music=false players=0 media=0")
        assertTrue(traced().isEmpty())

        DictationTrace.privateNow = false
        DictationTrace.dispatched(DictationEvent.KeyDown, emptyList(), emptyList(), null)
        assertEquals(1, traced().size)
    }

    @Test
    fun `every event, op and effect has a fixed label`() {
        // These strings are what a minified release build prints too: none comes from a class name.
        val events = mapOf(
            DictationEvent.Trigger("com.example.chat", "hello there", SessionAudioRoute.CAR) to "trigger route=CAR",
            DictationEvent.ReadyForSpeech to "ReadyForSpeech",
            DictationEvent.FirstAudio to "FirstAudio",
            DictationEvent.BeginningOfSpeech to "BeginningOfSpeech",
            DictationEvent.EndOfSpeech to "EndOfSpeech",
            DictationEvent.SegmentedSessionEnded to "SegmentedSessionEnded",
            DictationEvent.Error(7) to "error 7",
            DictationEvent.KeyDown to "KeyDown",
            DictationEvent.EngineActivity to "EngineActivity",
            DictationEvent.FieldClearedByApp to "FieldClearedByApp",
            DictationEvent.PrivateModeTurnedOn to "PrivateModeTurnedOn",
            DictationEvent.AudioFocusChanged(AudioFocusChange.LOSS_TRANSIENT, callActive = true) to "AudioFocusChanged LOSS_TRANSIENT call=true",
            DictationEvent.CallStarted to "CallStarted",
            DictationEvent.InputRouteSettling to "InputRouteSettling",
            DictationEvent.InputRouteSettled to "InputRouteSettled",
            DictationEvent.EditorFieldClosed to "EditorFieldClosed",
            DictationEvent.EditorFieldOpened("com.example.chat") to "fieldOpened",
            DictationEvent.UserEditedComposingText to "UserEditedComposingText",
            DictationEvent.EditorRejectedInsert to "EditorRejectedInsert",
            DictationEvent.StartFailed(DictationStartFailureReason.entries.first()) to "startFailed ${DictationStartFailureReason.entries.first().name}",
            DictationEvent.ClockTick to "tick",
        )
        events.forEach { (event, label) -> assertEquals(label, DictationTrace.eventLabel(event)) }

        assertEquals("compose5", DictationTrace.opLabel(DictationTextOp.SetComposingText("hello")))
        assertEquals("finish", DictationTrace.opLabel(DictationTextOp.FinishComposing))
        assertEquals("commit5", DictationTrace.opLabel(DictationTextOp.CommitText("hello")))
        assertEquals("del3", DictationTrace.opLabel(DictationTextOp.DeleteBeforeCursor(3)))

        val request = RecognizerRequest(segmented = true, preferOffline = false, enableFormatting = true, maskOffensive = false, completeSilenceMs = 1_000, minimumLengthMs = 1_000)
        val effects = mapOf(
            DictationEffect.StartListening(request) to "StartListening",
            DictationEffect.StopListening to "StopListening",
            DictationEffect.CancelListening to "CancelListening",
            DictationEffect.HoldImeVisible to "HoldImeVisible",
            DictationEffect.ReleaseImeVisible to "ReleaseImeVisible",
            DictationEffect.AcquireAudioFocus to "AcquireAudioFocus",
            DictationEffect.ReleaseAudioFocus to "ReleaseAudioFocus",
            DictationEffect.PlayStartCue to "PlayStartCue",
            DictationEffect.PlayStopCue to "PlayStopCue",
            DictationEffect.ShowMessage(DictationMessage.NETWORK_ERROR) to "ShowMessage:NETWORK_ERROR",
            DictationEffect.LogMessage(DictationMessage.SEGMENTED_REFUSED) to "LogMessage:SEGMENTED_REFUSED",
        )
        effects.forEach { (effect, label) -> assertEquals(label, DictationTrace.effectLabel(effect)) }
    }

    @Test
    fun `no word of the user's is written, only lengths and a hash`() {
        val words = "meet me at noon"
        val line = DictationTrace.line(
            DictationEvent.SegmentResult(words),
            listOf(DictationTextOp.SetComposingText(words), DictationTextOp.FinishComposing),
            emptyList(),
            null,
        )!!
        assertFalse(line, line.contains("meet") || line.contains("noon"))
        assertTrue(line, line.startsWith("segment len=15 h="))
        assertTrue(line, line.contains("ops=[compose15,finish]"))
        // The trigger's text before the session and an app's package never appear either.
        val trigger = DictationTrace.line(DictationEvent.Trigger("com.example.chat", "secret before", SessionAudioRoute.LOCAL), emptyList(), emptyList(), null)!!
        assertFalse(trigger, trigger.contains("secret") || trigger.contains("example"))
    }

    @Test
    fun `the same text has the same hash, different text a different one`() {
        val a = DictationTrace.eventLabel(DictationEvent.PartialResult("hello world"))
        val b = DictationTrace.eventLabel(DictationEvent.FinalResult("Hello world "))
        val c = DictationTrace.eventLabel(DictationEvent.FinalResult("hello word"))
        assertEquals(a.substringAfter(" h="), b.substringAfter(" h="))
        assertNotEquals(a.substringAfter(" h="), c.substringAfter(" h="))
        assertEquals("final null", DictationTrace.eventLabel(DictationEvent.FinalResult(null)))
    }

    @Test
    fun `a clock tick that did nothing is not a line`() {
        assertNull(DictationTrace.line(DictationEvent.ClockTick, emptyList(), emptyList(), null))
    }
}
