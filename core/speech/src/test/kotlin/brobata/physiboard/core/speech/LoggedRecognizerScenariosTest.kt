package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The recognizer sequences the maintainer's Titan logged on 2026-10-07 (dictation.md SS14, D14
 * to D17), replayed against the new session model through [DictationHarness]. Each test states
 * the behaviour the old model got wrong and proves the new one does not: no word is lost, no
 * restart ever happens while the user is speaking, and the session stops exactly when told.
 */
class LoggedRecognizerScenariosTest {

    private fun harness(settings: DictationSettings = DictationSettings(androidApiLevel = 36)) = DictationHarness(settings)

    /** The trigger, "ready" and the first audio report, as the log times them (D14: the microphone opens ~40 ms after the request). */
    private fun DictationHarness.start(now: Long = 0L): DictationHarness {
        send(DictationEvent.Trigger("app", ""), now)
        send(DictationEvent.ReadyForSpeech, now + 10)
        send(DictationEvent.FirstAudio, now + 40)
        return this
    }

    @Test
    fun `D14 - two five-second no-speech endings in a row are re-listened silently and the words that follow all land`() {
        // 09:22:24.872 startListening; 09:22:30.011 NO_SPEECH; 09:22:30.032 startListening;
        // 09:22:35.183 NO_SPEECH. The old model's ten-second grace ended here with a toast.
        val h = harness().start()
        h.send(DictationEvent.Error(DictationErrorCode.SPEECH_TIMEOUT), now = 5_139L)
        assertEquals(2, h.starts(), "re-listened at once")
        h.send(DictationEvent.ReadyForSpeech, now = 5_160L)
        h.send(DictationEvent.Error(DictationErrorCode.SPEECH_TIMEOUT), now = 10_311L)
        assertEquals(3, h.starts(), "re-listened again, no cap while the user is silent")
        assertTrue(h.effects.none { it is DictationEffect.ShowMessage }, "a quiet ending is never a message")
        assertNotNull(h.session, "the session is still open")

        h.send(DictationEvent.ReadyForSpeech, now = 10_330L)
        h.send(DictationEvent.BeginningOfSpeech, now = 14_000L)
        h.send(DictationEvent.PartialResult("hold on"), now = 14_200L)
        h.send(DictationEvent.PartialResult("hold on I need to"), now = 14_600L)
        h.send(DictationEvent.SegmentResult("hold on, I need to finish this thought."), now = 16_000L)
        assertEquals("Hold on, I need to finish this thought. ", h.field.text)
        assertEquals(1, h.effects.count { it is DictationEffect.PlayStartCue }, "one start cue for the whole session")
    }

    @Test
    fun `D15 - a segmented request the engine answered with one plain final no longer leaves seven seconds of dead air`() {
        // The engine ignored EXTRA_SEGMENTED_SESSION (it was sent as a Long, D15) and ran a
        // one-shot. The old model, believing itself segmented, armed a 7 s watchdog and issued no
        // new request: everything said after the first sentence went into a closed microphone.
        val h = harness().start()
        h.send(DictationEvent.PartialResult("first sentence"), now = 2_000L)
        val final = h.send(DictationEvent.FinalResult("first sentence."), now = 3_000L)
        assertEquals("First sentence. ", h.field.text)
        val restart = final.effects.filterIsInstance<DictationEffect.StartListening>().single()
        assertEquals(false, restart.request.segmented, "the same session goes on with one request per utterance")
        assertEquals(true, h.segmentedRefusalLatch, "later sessions ask for one request per utterance from the start")
        assertNotNull(h.session)

        h.send(DictationEvent.ReadyForSpeech, now = 3_100L)
        h.send(DictationEvent.PartialResult("second"), now = 4_000L)
        h.send(DictationEvent.FinalResult("second sentence."), now = 5_000L)
        assertEquals("First sentence. Second sentence. ", h.field.text)
    }

    @Test
    fun `D16 - the engine dying mid-utterance commits the words on screen and says so once`() {
        // CANCELLED / server disconnected while a partial was composing. The words on screen are
        // the user's: committed, not discarded, and one message, not a loop.
        val h = harness().start()
        h.send(DictationEvent.PartialResult("half a thought"), now = 2_000L)
        val died = h.send(DictationEvent.Error(DictationErrorCode.SERVER_DISCONNECTED), now = 2_500L)
        assertNull(died.session)
        assertEquals("Half a thought ", h.field.text)
        assertEquals(
            listOf(DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus, DictationEffect.ShowMessage(DictationMessage.NETWORK_ERROR)),
            died.effects,
        )
    }

    @Test
    fun `late results after the session ended are ignored - nothing lands in the next field`() {
        val h = harness().start()
        h.send(DictationEvent.PartialResult("send this"), now = 2_000L)
        h.send(DictationEvent.Trigger("app", null), now = 3_000L) // stop: the engine is asked for its last words
        h.send(DictationEvent.SegmentResult("send this."), now = 3_200L)
        val ended = h.send(DictationEvent.SegmentedSessionEnded, now = 3_250L)
        assertNull(ended.session)
        assertEquals("Send this. ", h.field.text)

        val late1 = h.send(DictationEvent.FinalResult("send this."), now = 3_400L)
        val late2 = h.send(DictationEvent.PartialResult("and this"), now = 3_500L)
        val late3 = h.send(DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 3_600L)
        for (late in listOf(late1, late2, late3)) {
            assertNull(late.session)
            assertTrue(late.effects.isEmpty())
            assertTrue(late.textOps.isEmpty())
        }
        assertEquals("Send this. ", h.field.text)
    }

    @Test
    fun `no timer ever restarts the recognizer while the user is speaking`() {
        // Thirty seconds of continuous speech with a partial every half second (the log's Chrome
        // session shape, D17), every armed timer fired on time in between.
        val h = harness(DictationSettings(androidApiLevel = 36, stopAfterSilenceMs = 5_000L)).start()
        h.drainEffects()
        var now = 1_000L
        while (now <= 31_000L) {
            h.send(DictationEvent.PartialResult("words " + "more ".repeat((now / 500).toInt())), now)
            h.runClockTo(now)
            now += 500L
        }
        val effects = h.drainEffects()
        assertTrue(effects.none { it is DictationEffect.StartListening || it is DictationEffect.StopListening || it is DictationEffect.CancelListening }, "no restart or stop during speech, got $effects")
        assertNotNull(h.session)
    }

    @Test
    fun `the session stops exactly when told - Fn again commits the engine's last words and closes on its end`() {
        val h = harness().start()
        h.send(DictationEvent.PartialResult("on my way"), now = 2_000L)
        val stop = h.send(DictationEvent.Trigger("app", null), now = 2_500L)
        assertEquals(listOf(DictationEffect.StopListening), stop.effects)
        assertEquals(DictationPhase.STOPPING, stop.session?.phase)
        h.send(DictationEvent.SegmentResult("on my way."), now = 2_700L)
        val end = h.send(DictationEvent.SegmentedSessionEnded, now = 2_750L)
        assertNull(end.session)
        assertEquals(listOf(DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus), end.effects)
        assertEquals("On my way. ", h.field.text)
    }

    @Test
    fun `an engine that never answers the stop is given 1500 ms, then the words on screen are kept and the request cancelled`() {
        val h = harness().start()
        h.send(DictationEvent.PartialResult("on my way"), now = 2_000L)
        h.send(DictationEvent.Trigger("app", null), now = 2_500L)
        h.drainEffects()
        val fired = h.runClockTo(4_000L).last()
        assertNull(fired.session)
        assertEquals(listOf(DictationEffect.CancelListening, DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus), fired.effects)
        assertEquals("On my way ", h.field.text)
    }

    @Test
    fun `a stop answered with a quiet error ends silently`() {
        val h = harness().start()
        h.send(DictationEvent.Trigger("app", null), now = 2_500L)
        val end = h.send(DictationEvent.Error(DictationErrorCode.NO_MATCH), now = 2_600L)
        assertNull(end.session)
        assertTrue(end.effects.none { it is DictationEffect.ShowMessage })
    }

    @Test
    fun `a typed key stops the session at once, keeps the words on screen, and the key is free to do its work`() {
        val h = harness().start()
        h.send(DictationEvent.PartialResult("see you at"), now = 2_000L)
        val typed = h.send(DictationEvent.KeyDown, now = 2_100L)
        assertNull(typed.session)
        assertEquals(listOf(DictationEffect.CancelListening, DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus), typed.effects)
        assertEquals("See you at ", h.field.text)
        // Whatever the cancelled request still says is ignored.
        val late = h.send(DictationEvent.SegmentResult("see you at five"), now = 2_200L)
        assertTrue(late.textOps.isEmpty())
        assertEquals("See you at ", h.field.text)
    }

    @Test
    fun `with stop-on-typing off a key leaves the session running and the c440844 invalidation still protects deleted words`() {
        val h = harness(DictationSettings(androidApiLevel = 36, stopOnTyping = false)).start()
        h.send(DictationEvent.PartialResult("delete me"), now = 2_000L)
        val typed = h.send(DictationEvent.KeyDown, now = 2_100L)
        assertNotNull(typed.session)
        assertTrue(typed.effects.isEmpty())
        h.send(DictationEvent.UserEditedComposingText, now = 2_150L)
        val final = h.send(DictationEvent.SegmentResult("delete me"), now = 2_500L)
        assertTrue(final.textOps.isEmpty(), "the deleted words are never typed back")
    }

    @Test
    fun `music - focus is taken before the microphone opens and given back on every ending`() {
        val h = harness()
        val started = h.send(DictationEvent.Trigger("app", ""), now = 0L)
        assertEquals(listOf(DictationEffect.AcquireAudioFocus, DictationEffect.StartListening(started.session!!.request)), started.effects)

        // Another app taking the audio for good stops dictation at once.
        h.send(DictationEvent.ReadyForSpeech, now = 10L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        h.send(DictationEvent.PartialResult("keep this"), now = 500L)
        val lost = h.send(DictationEvent.AudioFocusLost, now = 600L)
        assertNull(lost.session)
        assertTrue(DictationEffect.ReleaseAudioFocus in lost.effects)
        assertEquals("Keep this ", h.field.text)

        // With the setting off, no focus is touched.
        val quiet = DictationHarness(DictationSettings(androidApiLevel = 36, pauseMedia = false))
        val s = quiet.send(DictationEvent.Trigger("app", ""), now = 0L)
        assertTrue(s.effects.none { it is DictationEffect.AcquireAudioFocus })
        val e = quiet.send(DictationEvent.Trigger("app", ""), now = 100L)
        assertTrue(e.effects.none { it is DictationEffect.ReleaseAudioFocus })
    }

    @Test
    fun `the start cue follows the first audio report, falls back 300 ms after ready, and never plays twice`() {
        val h = harness()
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        val ready = h.send(DictationEvent.ReadyForSpeech, now = 10L)
        assertTrue(ready.effects.isEmpty(), "ready alone is not the cue: the microphone may not be open yet")
        val audio = h.send(DictationEvent.FirstAudio, now = 40L)
        assertEquals(listOf(DictationEffect.PlayStartCue), audio.effects)
        assertTrue(h.runClockTo(1_000L).all { it.effects.isEmpty() }, "no fallback cue once the real one played")
        h.send(DictationEvent.ReadyForSpeech, now = 5_000L) // a re-listen's "ready"
        h.send(DictationEvent.FirstAudio, now = 5_040L)
        assertEquals(1, h.effects.count { it is DictationEffect.PlayStartCue })

        val silentEngine = harness()
        silentEngine.send(DictationEvent.Trigger("app", ""), now = 0L)
        silentEngine.send(DictationEvent.ReadyForSpeech, now = 10L)
        val fallback = silentEngine.runClockTo(310L).single()
        assertEquals(listOf(DictationEffect.PlayStartCue), fallback.effects)
    }

    @Test
    fun `the stop cue plays only when a start cue did`() {
        val h = harness()
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        val failed = h.send(DictationEvent.StartFailed(DictationStartFailureReason.RECOGNITION_UNAVAILABLE), now = 5L)
        assertNull(failed.session)
        assertEquals(listOf(DictationEffect.ReleaseAudioFocus, DictationEffect.ShowMessage(DictationMessage.SPEECH_RECOGNITION_NOT_AVAILABLE)), failed.effects)
    }

    @Test
    fun `Fn again before the microphone opened ends the session instead of starting a second one`() {
        val h = harness()
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        val second = h.send(DictationEvent.Trigger("app", ""), now = 200L)
        assertNull(second.session)
        assertEquals(listOf(DictationEffect.CancelListening, DictationEffect.ReleaseAudioFocus), second.effects)
        assertEquals(1, h.starts())
    }

    @Test
    fun `stop after silence - the keyboard's own limit stops gracefully, counted from the last speech`() {
        val h = harness(DictationSettings(androidApiLevel = 36, stopAfterSilenceMs = 5_000L)).start()
        h.send(DictationEvent.PartialResult("one"), now = 2_000L)
        h.send(DictationEvent.SegmentResult("one."), now = 3_000L)
        h.drainEffects()
        assertTrue(h.runClockTo(7_999L).all { it.effects.isEmpty() })
        val stop = h.runClockTo(8_000L).single()
        assertEquals(listOf(DictationEffect.StopListening), stop.effects)
        val end = h.send(DictationEvent.Error(DictationErrorCode.SPEECH_TIMEOUT), now = 8_100L)
        assertNull(end.session)
        assertTrue(end.effects.none { it is DictationEffect.ShowMessage })
        assertEquals("One. ", h.field.text)
    }

    @Test
    fun `stop after silence off - only the safety limits end a session by themselves`() {
        val h = harness(DictationSettings(androidApiLevel = 36, stopAfterSilenceMs = 0L)).start()
        h.drainEffects()
        // Quiet endings every five seconds for fifty seconds: re-listened, never stopped.
        var now = 5_000L
        while (now < 60_000L) {
            h.send(DictationEvent.Error(DictationErrorCode.SPEECH_TIMEOUT), now)
            h.runClockTo(now)
            now += 5_000L
        }
        assertTrue(h.drainEffects().none { it is DictationEffect.StopListening })
        val stop = h.runClockTo(60_040L).single()
        assertEquals(listOf(DictationEffect.StopListening), stop.effects)

        // The cap: ten minutes of someone talking still ends.
        val long = harness(DictationSettings(androidApiLevel = 36)).start()
        var t = 1_000L
        while (t < 600_000L) {
            long.send(DictationEvent.PartialResult("still talking $t"), t)
            t += 1_000L
        }
        long.drainEffects()
        val capped = long.runClockTo(600_000L).single()
        assertEquals(listOf(DictationEffect.StopListening), capped.effects)
    }

    @Test
    fun `the engine ending its segmented session in silence is re-listened with a live partial committed first`() {
        val h = harness().start()
        h.send(DictationEvent.PartialResult("trailing words"), now = 2_000L)
        val ended = h.send(DictationEvent.SegmentedSessionEnded, now = 9_000L)
        assertNotNull(ended.session)
        assertEquals(listOf(DictationEffect.StartListening(ended.session.request)), ended.effects)
        assertEquals("Trailing words ", h.field.text)
    }

    @Test
    fun `offline language missing - the session goes online once outside private mode, and ends with a message inside it`() {
        val h = harness().start()
        val fallback = h.send(DictationEvent.Error(DictationErrorCode.LANGUAGE_UNAVAILABLE), now = 300L)
        assertNotNull(fallback.session)
        val restart = fallback.effects.filterIsInstance<DictationEffect.StartListening>().single()
        assertEquals(false, restart.request.preferOffline)
        assertTrue(fallback.effects.none { it is DictationEffect.ShowMessage })
        val again = h.send(DictationEvent.Error(DictationErrorCode.LANGUAGE_NOT_SUPPORTED), now = 600L)
        assertNull(again.session)
        assertTrue(DictationEffect.ShowMessage(DictationMessage.OFFLINE_LANGUAGE_MISSING) in again.effects)

        val private = harness(DictationSettings(androidApiLevel = 36, privateMode = true)).start()
        val refused = private.send(DictationEvent.Error(DictationErrorCode.LANGUAGE_UNAVAILABLE), now = 300L)
        assertNull(refused.session)
        assertTrue(DictationEffect.ShowMessage(DictationMessage.PRIVATE_MODE_NEEDS_OFFLINE_LANGUAGE) in refused.effects)
    }

    @Test
    fun `a segmented refusal within the window retries plain in the same session and sets the latch`() {
        val h = harness().start()
        val refused = h.send(DictationEvent.Error(DictationErrorCode.CLIENT), now = 900L)
        assertNotNull(refused.session)
        assertEquals(false, refused.session.request.segmented)
        assertEquals(true, h.segmentedRefusalLatch)
        assertTrue(refused.effects.any { it is DictationEffect.StartListening })
        // Outside the window a client error is a real error.
        val h2 = harness().start()
        val late = h2.send(DictationEvent.Error(DictationErrorCode.CLIENT), now = 1_300L)
        assertNull(late.session)
        assertTrue(DictationEffect.ShowMessage(DictationMessage.SPEECH_RECOGNITION_ERROR) in late.effects)
    }

    @Test
    fun `an engine failing fast is backed off and given up on after five tries, never hammered`() {
        val h = harness().start()
        h.drainEffects()
        var now = 100L
        repeat(4) {
            val o = h.send(DictationEvent.Error(DictationErrorCode.NO_MATCH), now)
            assertNotNull(o.session)
            assertTrue(o.effects.isEmpty(), "a fast failure waits, it does not re-listen at once")
            val retry = h.runClockTo(now + 500L).single()
            assertTrue(retry.effects.any { it is DictationEffect.StartListening })
            now += 600L
        }
        val gaveUp = h.send(DictationEvent.Error(DictationErrorCode.NO_MATCH), now)
        assertNull(gaveUp.session)
        assertTrue(DictationEffect.ShowMessage(DictationMessage.SPEECH_RECOGNITION_ERROR) in gaveUp.effects)
    }

    @Test
    fun `an engine answering every request with an empty final or an instant end is backed off the same way`() {
        for (ending in listOf<DictationEvent>(DictationEvent.FinalResult(null), DictationEvent.SegmentedSessionEnded)) {
            val h = harness().start()
            h.drainEffects()
            var now = 100L
            repeat(4) {
                val o = h.send(ending, now)
                assertNotNull(o.session, "$ending")
                assertTrue(o.effects.none { it is DictationEffect.StartListening }, "$ending within 700 ms waits, it does not re-listen at once")
                assertTrue(h.runClockTo(now + 500L).single().effects.any { it is DictationEffect.StartListening })
                now += 600L
            }
            val gaveUp = h.send(ending, now)
            assertNull(gaveUp.session, "$ending")
            assertTrue(DictationEffect.ShowMessage(DictationMessage.SPEECH_RECOGNITION_ERROR) in gaveUp.effects)
        }
        // An ending that brought words is never a failure, however fast.
        val h = harness().start()
        h.send(DictationEvent.PartialResult("quick"), now = 100L)
        val quick = h.send(DictationEvent.SegmentedSessionEnded, now = 200L)
        assertTrue(quick.effects.any { it is DictationEffect.StartListening })
        assertEquals(0, quick.session?.consecutiveFailures)
    }

    @Test
    fun `private mode turned on mid-session stops a session that went online, and leaves an offline one alone`() {
        val online = harness().start()
        online.send(DictationEvent.Error(DictationErrorCode.LANGUAGE_UNAVAILABLE), now = 300L) // fell back online
        val stopped = online.send(DictationEvent.PrivateModeTurnedOn, now = 1_000L)
        assertEquals(DictationPhase.STOPPING, stopped.session?.phase)
        assertEquals(listOf(DictationEffect.StopListening), stopped.effects)

        val offline = harness().start()
        val unchanged = offline.send(DictationEvent.PrivateModeTurnedOn, now = 1_000L)
        assertEquals(DictationPhase.LISTENING, unchanged.session?.phase)
        assertTrue(unchanged.effects.isEmpty())
    }

    @Test
    fun `busy retries after 300 ms`() {
        val h = harness().start()
        val busy = h.send(DictationEvent.Error(DictationErrorCode.RECOGNIZER_BUSY), now = 100L)
        assertTrue(busy.effects.isEmpty())
        assertEquals(400L, busy.session?.busyRetryDeadlineMs)
        val retried = h.runClockTo(400L).single()
        assertTrue(retried.effects.any { it is DictationEffect.StartListening })
    }

    @Test
    fun `the field going away without a replacement ends the session at the grace deadline, a same-app field keeps it`() {
        val h = harness().start()
        h.send(DictationEvent.EditorFieldClosed, now = 1_000L)
        h.send(DictationEvent.EditorFieldOpened("app"), now = 1_200L)
        assertNull(h.session?.editorGoneDeadlineMs)
        h.send(DictationEvent.EditorFieldClosed, now = 2_000L)
        h.drainEffects()
        val gone = h.runClockTo(2_500L).single()
        assertNull(gone.session)
        assertTrue(gone.effects.containsAll(listOf(DictationEffect.CancelListening, DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus)))

        val other = harness().start()
        val taken = other.send(DictationEvent.EditorFieldOpened("another.app"), now = 1_000L)
        assertNull(taken.session)
    }
}
