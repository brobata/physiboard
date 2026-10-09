package brobata.physiboard.core.speech

/**
 * Every duration the dictation state machine reasons about, named and cited so a future change can
 * find its justification without re-deriving it from a magic number. spec: dictation.md SS6, SS14.
 *
 * None of these timers ever restarts the recognizer while the user is speaking: the only restarts
 * this module issues follow an ending the recognizer itself reported (a quiet error, an ordinary
 * final, the end of a segmented session), which by definition happens in silence.
 */
object DictationTiming {

    /**
     * spec SS6.5: after an explicit stop asks the engine for its last words, how long to wait for
     * them before the words already on screen are committed and the session is closed anyway.
     */
    const val STOP_WATCHDOG_MS = 1_500L

    /** spec SS6.6: "A busy error re-listens after a 300 ms delay (a busy engine is usually the previous request still winding down)." */
    const val BUSY_RETRY_DELAY_MS = 300L

    /**
     * spec SS6.6: a quiet error or busy answer arriving this soon after the request began is the
     * engine failing fast, not silence; such answers are re-listened after [FAST_FAILURE_BACKOFF_MS]
     * and counted, so a broken engine cannot be hammered for ever.
     */
    const val FAST_FAILURE_WINDOW_MS = 700L

    /** spec SS6.6: the delay before re-listening after a fast failure. */
    const val FAST_FAILURE_BACKOFF_MS = 500L

    /** spec SS6.6: consecutive fast failures (or busy answers) that end the session with a real error. */
    const val MAX_CONSECUTIVE_FAILURES = 5

    /** spec SS6.3: the window in which an early failure with nothing heard counts as the engine refusing the segmented request. */
    const val SEGMENTED_REFUSAL_WINDOW_MS = 1_200L

    /**
     * spec SS8.1: the start cue follows the first audio level report (the microphone is open and
     * audio is flowing); an engine that reports no levels gets the cue this long after "ready".
     */
    const val CUE_FALLBACK_MS = 300L

    /**
     * spec SS6.4: with "Stop after silence" off, a session still ends by itself after this much
     * continuous silence, so a microphone left open does not transcribe the room for ever.
     */
    const val SAFETY_SILENCE_MS = 60_000L

    /** spec SS6.4: no session outlives this, whatever is heard. */
    const val SESSION_CAP_MS = 10 * 60_000L

    /**
     * spec SS5: the engine is asked to end its segmented session this much later than the
     * keyboard's own silence limit, so the keyboard's timer is the one that decides and the
     * engine's own ending never races it.
     */
    const val ENGINE_SILENCE_MARGIN_MS = 1_000L

    /**
     * spec SS6.3: after an ordinary final on a segmented request, how long the keyboard waits for
     * a sign of life (speech, a partial, an audio level) before concluding the engine ran a
     * one-shot and went idle. Google's continuous session delivers its finals as ordinary
     * results and keeps going (D24), so a final alone proves nothing.
     */
    const val CONTINUATION_PROBE_MS = 1_500L

    /**
     * spec SS6.10: the longest the start cue waits for a Bluetooth microphone to come up. A
     * hands-free link takes about a second; one that never comes up must not leave the user
     * without a cue.
     */
    const val ROUTE_SETTLE_MAX_MS = 2_500L

    /**
     * spec SS6.10: on a car or Bluetooth route, the silence limit before the first words is at
     * least this long, counted from the start cue: the microphone, the engine and the person
     * driving are all slower to begin there, and the 2.5 s default ended sessions before the
     * first partial arrived.
     */
    const val REMOTE_ROUTE_FIRST_WORDS_GRACE_MS = 6_000L

    /** spec SS6.6: the wait before listening again after an audio error (the input route changing under the recording). */
    const val AUDIO_ERROR_BACKOFF_MS = 300L

    /** spec SS3: "the app closes its text field and no new field replaces it within 500 ms." */
    const val EDITOR_GONE_GRACE_MS = 500L

    /** spec SS6.4: how long the session waits in silence before stopping by itself; `stopAfterSilenceMs` 0 means the safety limit. */
    fun silenceLimitMs(stopAfterSilenceMs: Long): Long = if (stopAfterSilenceMs > 0L) stopAfterSilenceMs else SAFETY_SILENCE_MS

    /** spec SS5: the complete-silence length handed to the engine for a given silence limit. */
    fun engineSilenceMs(stopAfterSilenceMs: Long): Long = silenceLimitMs(stopAfterSilenceMs) + ENGINE_SILENCE_MARGIN_MS
}
