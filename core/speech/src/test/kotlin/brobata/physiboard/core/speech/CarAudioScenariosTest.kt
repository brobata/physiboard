package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * spec: dictation.md SS6.7 and SS6.10, the car. The maintainer, 2026-10-09: "I was having issues
 * in the car with Audible/Spotify playing - cutting off or not starting correctly." Each scenario
 * drives the state machine the way the phone's audio would in a truck with a hands-free head
 * unit or Android Auto, and checks the three things that matter: no word lost, no stop the user
 * did not ask for, and the music resumed exactly once at the end.
 */
class CarAudioScenariosTest {

    /**
     * A media app as audio focus moves it: it pauses while the keyboard holds focus, plays
     * otherwise, and counts the times the keyboard's own release resumed it.
     */
    private class FakePlayer {
        var playing = true
        var keyboardHoldsFocus = false
        var resumesByKeyboard = 0

        fun apply(effects: List<DictationEffect>) {
            for (effect in effects) when (effect) {
                DictationEffect.AcquireAudioFocus -> {
                    keyboardHoldsFocus = true
                    playing = false
                }
                DictationEffect.ReleaseAudioFocus -> if (keyboardHoldsFocus) {
                    keyboardHoldsFocus = false
                    if (!playing) resumesByKeyboard++
                    playing = true
                }
                else -> Unit
            }
        }

        /** The app (or the head unit's "play") takes the audio back for good. */
        fun grabsFocus(): DictationEvent {
            keyboardHoldsFocus = false
            playing = true
            return DictationEvent.AudioFocusChanged(AudioFocusChange.LOSS, callActive = false)
        }
    }

    private fun car(route: SessionAudioRoute = SessionAudioRoute.BLUETOOTH) = DictationHarness().also { it.send(DictationEvent.Trigger("app", "", route), now = 0L) }

    private fun DictationHarness.sendAll(player: FakePlayer, event: DictationEvent, now: Long): DictationOutcome =
        send(event, now).also { player.apply(it.effects) }

    @Test
    fun `media app regains focus mid-session - the session listens on, re-pauses once, and resumes it once`() {
        val player = FakePlayer()
        val h = DictationHarness()
        player.apply(h.send(DictationEvent.Trigger("app", ""), now = 0L).effects)
        assertFalse(player.playing, "paused at the trigger")
        h.send(DictationEvent.ReadyForSpeech, now = 30L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        h.send(DictationEvent.PartialResult("pick up"), now = 900L)

        // Spotify asks for the audio again.
        val lost = h.sendAll(player, player.grabsFocus(), now = 1_200L)
        assertNotNull(lost.session, "a media app taking the audio is not a reason to stop")
        assertTrue(lost.effects.none { it is DictationEffect.CancelListening || it is DictationEffect.StopListening || it == DictationEffect.PlayStopCue })
        assertEquals(1, lost.effects.count { it == DictationEffect.AcquireAudioFocus }, "taken back once")
        assertFalse(player.playing)

        h.send(DictationEvent.PartialResult("pick up the order"), now = 1_600L)
        h.send(DictationEvent.FinalResult("pick up the order"), now = 2_000L)

        // It grabs again: the session lets it play and keeps listening.
        val again = h.sendAll(player, player.grabsFocus(), now = 2_300L)
        assertNotNull(again.session)
        assertTrue(again.effects.none { it == DictationEffect.AcquireAudioFocus }, "no tug of war")
        h.send(DictationEvent.PartialResult("at noon"), now = 2_700L)

        val stop = h.sendAll(player, DictationEvent.Trigger("app", null), now = 3_000L)
        assertTrue(stop.effects.none { it == DictationEffect.ReleaseAudioFocus }, "nothing held any more, nothing to release")
        h.sendAll(player, DictationEvent.FinalResult("at noon"), now = 3_200L)
        assertNull(h.session)
        assertEquals("Pick up the order at noon ", h.field.text)
        assertTrue(player.playing)
        assertEquals(0, player.resumesByKeyboard, "the app resumed itself; the keyboard resumed nothing a second time")
    }

    @Test
    fun `media app regains focus once - the keyboard's one release at the end resumes it exactly once`() {
        val player = FakePlayer()
        val h = DictationHarness()
        player.apply(h.send(DictationEvent.Trigger("app", ""), now = 0L).effects)
        h.send(DictationEvent.FirstAudio, now = 40L)
        h.sendAll(player, player.grabsFocus(), now = 500L)
        assertFalse(player.playing)
        h.send(DictationEvent.SegmentResult("hello"), now = 1_000L)
        h.sendAll(player, DictationEvent.Trigger("app", null), now = 1_500L)
        h.sendAll(player, DictationEvent.SegmentedSessionEnded, now = 1_600L)
        assertNull(h.session)
        assertEquals(1, player.resumesByKeyboard)
        assertTrue(player.playing)
        assertEquals("Hello ", h.field.text)
    }

    @Test
    fun `the recognizer's own transient focus and the gain after it change nothing`() {
        val h = car()
        h.send(DictationEvent.FirstAudio, now = 40L)
        val before = h.session
        assertEquals(before, h.send(DictationEvent.AudioFocusChanged(AudioFocusChange.LOSS_TRANSIENT, callActive = false), now = 45L).session)
        assertEquals(before, h.send(DictationEvent.AudioFocusChanged(AudioFocusChange.LOSS_TRANSIENT_CAN_DUCK, callActive = false), now = 46L).session)
        assertEquals(before, h.send(DictationEvent.AudioFocusChanged(AudioFocusChange.GAIN, callActive = false), now = 5_000L).session)
    }

    @Test
    fun `a call ends the session at once with the words kept, transient loss or not`() {
        val h = car()
        h.send(DictationEvent.FirstAudio, now = 40L)
        h.send(DictationEvent.PartialResult("call me back"), now = 800L)
        val ended = h.send(DictationEvent.AudioFocusChanged(AudioFocusChange.LOSS_TRANSIENT, callActive = true), now = 900L)
        assertNull(ended.session)
        assertTrue(DictationEffect.CancelListening in ended.effects)
        assertTrue(DictationEffect.ReleaseAudioFocus in ended.effects)
        assertEquals("Call me back ", h.field.text)
    }

    @Test
    fun `a focus loss while stopping only stops the keyboard releasing what it no longer holds`() {
        val h = car()
        h.send(DictationEvent.FirstAudio, now = 40L)
        h.send(DictationEvent.Trigger("app", null), now = 1_000L)
        val lost = h.send(DictationEvent.AudioFocusChanged(AudioFocusChange.LOSS, callActive = false), now = 1_100L)
        assertTrue(lost.effects.isEmpty())
        val end = h.send(DictationEvent.FinalResult("done"), now = 1_200L)
        assertTrue(DictationEffect.ReleaseAudioFocus !in end.effects)
    }

    @Test
    fun `SCO route comes up 1_5 s after start - the cue waits for it and the first words land`() {
        val h = car()
        h.send(DictationEvent.InputRouteSettling, now = 5L)
        h.send(DictationEvent.ReadyForSpeech, now = 30L)
        val audio = h.send(DictationEvent.FirstAudio, now = 40L)
        assertTrue(DictationEffect.PlayStartCue !in audio.effects, "the hands-free link is still silence")
        // The 300 ms cue fallback does not cue either.
        assertTrue(h.runClockTo(1_000L).none { DictationEffect.PlayStartCue in it.effects })
        // The engine closes its first request on the silence: re-listened, nothing shown.
        val quiet = h.send(DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 1_100L)
        assertTrue(quiet.effects.none { it is DictationEffect.ShowMessage })
        val up = h.send(DictationEvent.InputRouteSettled, now = 1_500L)
        assertTrue(DictationEffect.PlayStartCue in up.effects)

        // The user starts at the cue; a slow first partial on the car's microphone.
        assertTrue(h.runClockTo(4_400L).none { DictationEffect.StopListening in it.effects }, "no stop 2.5 s after the cue before the first words")
        h.send(DictationEvent.PartialResult("on my way"), now = 4_500L)
        h.send(DictationEvent.SegmentResult("on my way"), now = 5_200L)
        assertEquals("On my way ", h.field.text)
        assertEquals(1, h.effects.count { it == DictationEffect.PlayStartCue })
    }

    @Test
    fun `a route that never settles still cues, at the bound, and says so in the log`() {
        val h = car()
        h.send(DictationEvent.InputRouteSettling, now = 0L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        val fired = h.runClockTo(DictationTiming.ROUTE_SETTLE_MAX_MS)
        val cue = fired.single { DictationEffect.PlayStartCue in it.effects }
        assertTrue(DictationEffect.LogMessage(DictationMessage.ROUTE_SETTLE_TIMED_OUT) in cue.effects)
        assertEquals(DictationTiming.ROUTE_SETTLE_MAX_MS, cue.session!!.lastSpeechMs, "the silence limit counts from the cue")
    }

    @Test
    fun `words before the route settles end the wait with no cue in the middle of the sentence`() {
        val h = car()
        h.send(DictationEvent.InputRouteSettling, now = 0L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        val words = h.send(DictationEvent.PartialResult("text Amy that"), now = 900L)
        assertTrue(DictationEffect.PlayStartCue !in words.effects)
        assertTrue(h.runClockTo(3_000L).none { DictationEffect.PlayStartCue in it.effects }, "no late cue at the bound")
        val stop = h.send(DictationEvent.Trigger("app", null), now = 3_000L)
        h.send(DictationEvent.FinalResult("text Amy that"), now = 3_100L)
        assertTrue(h.effects.contains(DictationEffect.PlayStopCue), "the session still ends with its stop cue")
        assertEquals("Text Amy that ", h.field.text)
        assertTrue(stop.effects.contains(DictationEffect.StopListening))
    }

    @Test
    fun `a stop during the wait drops the wait`() {
        val h = car()
        h.send(DictationEvent.InputRouteSettling, now = 0L)
        h.send(DictationEvent.ReadyForSpeech, now = 30L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        h.send(DictationEvent.Trigger("app", null), now = 500L)
        assertNull(h.session?.routeSettleDeadlineMs)
    }

    @Test
    fun `a route that settles before the microphone opens leaves the cue to the first audio report`() {
        val h = car()
        h.send(DictationEvent.InputRouteSettling, now = 0L)
        assertTrue(h.send(DictationEvent.InputRouteSettled, now = 20L).effects.isEmpty())
        assertTrue(DictationEffect.PlayStartCue in h.send(DictationEvent.FirstAudio, now = 60L).effects)
    }

    @Test
    fun `at home the silence limit before the first words is unchanged, but counts from the cue`() {
        val h = DictationHarness()
        h.send(DictationEvent.Trigger("app", "", SessionAudioRoute.LOCAL), now = 0L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        val stops = h.runClockTo(2_600L).filter { DictationEffect.StopListening in it.effects }
        assertEquals(1, stops.size, "2.5 s after the cue at 40 ms")
    }

    @Test
    fun `in the car the silence limit before the first words is six seconds, after them the usual 2_5 s`() {
        val h = car(SessionAudioRoute.CAR)
        h.send(DictationEvent.FirstAudio, now = 40L)
        assertTrue(h.runClockTo(6_000L).none { DictationEffect.StopListening in it.effects })
        h.send(DictationEvent.PartialResult("turn left"), now = 6_000L)
        assertTrue(h.runClockTo(8_400L).none { DictationEffect.StopListening in it.effects })
        assertTrue(h.runClockTo(8_600L).any { DictationEffect.StopListening in it.effects })
    }

    @Test
    fun `route switches mid-session - the audio error keeps the words and listens again`() {
        val h = car()
        h.send(DictationEvent.FirstAudio, now = 40L)
        h.send(DictationEvent.PartialResult("text Amy that"), now = 1_000L)
        val error = h.send(DictationEvent.Error(DictationErrorCode.AUDIO), now = 1_200L)
        assertNotNull(error.session)
        assertTrue(error.effects.none { it is DictationEffect.ShowMessage || it == DictationEffect.PlayStopCue })
        assertEquals("Text Amy that ", h.field.text)
        val relisten = h.runClockTo(1_200L + DictationTiming.AUDIO_ERROR_BACKOFF_MS)
        assertTrue(relisten.any { o -> o.effects.any { it is DictationEffect.StartListening } })
        h.send(DictationEvent.PartialResult("I'm late"), now = 2_000L)
        h.send(DictationEvent.SegmentResult("I'm late"), now = 2_400L)
        assertEquals("Text Amy that I'm late ", h.field.text)
    }

    @Test
    fun `a microphone that keeps failing still ends the session after five audio errors`() {
        val h = car()
        h.send(DictationEvent.FirstAudio, now = 40L)
        var now = 100L
        repeat(4) {
            assertNotNull(h.send(DictationEvent.Error(DictationErrorCode.AUDIO), now).session)
            now += DictationTiming.AUDIO_ERROR_BACKOFF_MS
            h.runClockTo(now)
            now += 10
        }
        val last = h.send(DictationEvent.Error(DictationErrorCode.AUDIO), now)
        assertNull(last.session)
        assertTrue(DictationEffect.ShowMessage(DictationMessage.SPEECH_RECOGNITION_ERROR) in last.effects)
    }

    @Test
    fun `Android Auto projection active - assistant busy, focus bounced by the car, words still land`() {
        val player = FakePlayer()
        val h = DictationHarness()
        player.apply(h.send(DictationEvent.Trigger("app", "", SessionAudioRoute.CAR), now = 0L).effects)
        h.send(DictationEvent.InputRouteSettling, now = 5L)
        // Assistant still holds the recognizer from the steering-wheel press: busy twice.
        h.send(DictationEvent.Error(DictationErrorCode.RECOGNIZER_BUSY), now = 50L)
        h.runClockTo(350L)
        h.send(DictationEvent.Error(DictationErrorCode.RECOGNIZER_BUSY), now = 400L)
        h.runClockTo(700L)
        h.send(DictationEvent.ReadyForSpeech, now = 750L)
        h.send(DictationEvent.FirstAudio, now = 760L)
        // The car's microphone is reported up; the cue plays.
        assertTrue(DictationEffect.PlayStartCue in h.send(DictationEvent.InputRouteSettled, now = 1_200L).effects)
        // The projection bounces focus: a transient loss and a gain, then the car's "play".
        h.send(DictationEvent.AudioFocusChanged(AudioFocusChange.LOSS_TRANSIENT, callActive = false), now = 1_300L)
        h.send(DictationEvent.AudioFocusChanged(AudioFocusChange.GAIN, callActive = false), now = 1_500L)
        h.sendAll(player, player.grabsFocus(), now = 1_700L)
        h.send(DictationEvent.PartialResult("navigate home"), now = 2_500L)
        h.send(DictationEvent.SegmentResult("navigate home"), now = 3_000L)
        assertNotNull(h.session)
        h.sendAll(player, DictationEvent.Trigger("app", null), now = 3_500L)
        h.sendAll(player, DictationEvent.SegmentedSessionEnded, now = 3_600L)
        assertNull(h.session)
        assertEquals("Navigate home ", h.field.text)
        assertEquals(1, player.resumesByKeyboard)
        assertTrue(h.effects.none { it is DictationEffect.ShowMessage })
    }

    @Test
    fun `a call that starts ringing ends the session`() {
        val h = car()
        h.send(DictationEvent.FirstAudio, now = 40L)
        h.send(DictationEvent.PartialResult("almost"), now = 500L)
        val rung = h.send(DictationEvent.CallStarted, now = 600L)
        assertNull(rung.session)
        assertEquals("Almost ", h.field.text)
    }
}
