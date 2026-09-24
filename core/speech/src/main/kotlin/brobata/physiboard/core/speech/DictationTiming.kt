package brobata.physiboard.core.speech

/**
 * Every duration the dictation state machine reasons about, named and cited so a future change can
 * find its justification without re-deriving it from a magic number. spec: dictation.md SS6, SS14.
 */
object DictationTiming {

    /**
     * spec SS6.2, D2: Google's engines close the microphone about 2 s after any sound and report
     * "no match" if no words came through, so a user drawing breath before speaking would otherwise
     * be told nothing was heard. While the session has not yet heard speech, a quiet or busy error
     * keeps re-listening for up to this long since the session started.
     */
    const val FIRST_WORDS_GRACE_MS = 10_000L

    /** spec SS6.2: the grace also caps how many re-listens it will spend, not just how long. */
    const val FIRST_WORDS_GRACE_MAX_RESTARTS = 5

    /** spec SS6.2: "A busy error re-listens after a 300 ms delay (a busy engine is usually the previous request still winding down)." */
    const val BUSY_RETRY_DELAY_MS = 300L

    /** spec SS6.3: the window in which an early failure with zero segments seen counts as the engine refusing the segmented request. */
    const val SEGMENTED_REFUSAL_WINDOW_MS = 1_200L

    /** spec SS6.4: below this age, a continuation's quiet error is a fast failure loop, not ordinary silence. */
    const val CONTINUATION_FAILURE_WINDOW_MS = 700L

    /** spec SS6.4: the silence timer never runs shorter than this, however small the configured pause is. */
    const val SILENCE_TIMER_FLOOR_MS = 400L

    /** spec SS6.4: "the keyboard arms its silence timer for max(pause - 1000, 400) ms (the engine has already waited about 1 s of silence before delivering the final)." */
    const val SILENCE_TIMER_PAUSE_OFFSET_MS = 1_000L

    /** spec SS6.3: the segmented watchdog and the stop-requested watchdog both run this far past the configured pause. */
    const val SEGMENTED_WATCHDOG_OFFSET_MS = 5_000L

    /** spec SS3: "the app closes its text field and no new field replaces it within 500 ms." */
    const val EDITOR_GONE_GRACE_MS = 500L

    /** spec SS6.4: the restart-loop silence timer, floored so a tiny configured pause never arms an unusably short timer. */
    fun silenceTimerMs(pauseMs: Long): Long = (pauseMs - SILENCE_TIMER_PAUSE_OFFSET_MS).coerceAtLeast(SILENCE_TIMER_FLOOR_MS)

    /** spec SS6.3: the segmented watchdog's deadline for one configured pause. */
    fun watchdogMs(pauseMs: Long): Long = pauseMs + SEGMENTED_WATCHDOG_OFFSET_MS
}
