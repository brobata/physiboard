package brobata.physiboard.core.speech

/** spec: dictation.md SS6.1, SS6.5: where one session is in its life. */
enum class DictationPhase {
    /** The request is issued; the microphone has not reported itself open yet. */
    STARTING,

    /** The microphone is open and words land as they come, until something stops the session. */
    LISTENING,

    /** The engine has been asked for its last words; the session closes when they arrive or the watchdog gives up. */
    STOPPING,
}

/**
 * Everything [DictationEngine] carries from one input to the next for one dictation session. `null`
 * (never constructed here) means no session is running; spec: dictation.md SS1, SS2, SS6.
 *
 * [nextDeadlineMs] is the one clock fact the caller needs: the earliest of this session's armed
 * timers, so it can schedule a single real-time wakeup and call [DictationEngine.handle] with
 * [DictationEvent.ClockTick] when it arrives.
 */
data class DictationSession(
    /** spec SS2.6 step 2: the package of the field dictation started in, so a different app taking the field over ends the session (SS3). */
    val ownerPackage: String?,
    val sessionStartMs: Long,
    val phase: DictationPhase,
    /** spec SS8.1: the start cue has played, so the stop cue may; exactly one of each per session. */
    val cuePlayed: Boolean,
    /** spec SS6.7: audio focus was taken at the start and must be given back at the end. */
    val audioFocusHeld: Boolean,
    /** spec SS5: the request currently in force; a fallback (plain instead of segmented, online instead of offline) replaces it. */
    val request: RecognizerRequest,
    /** When the current request began; the clock basis for SS6.3's refusal window and SS6.6's fast-failure window. */
    val requestStartMs: Long,
    /** spec SS1: "the session has received at least one non-empty partial, one segment or one final." */
    val heardSpeech: Boolean,
    /** spec SS6.4: the last moment the engine gave any sign of speech (or the session start); the silence limit counts from here. */
    val lastSpeechMs: Long,
    /** spec SS6.4: how long [lastSpeechMs] may lie in the past before the session stops by itself. */
    val silenceLimitMs: Long,
    /** spec SS6.6: fast failures and busy answers in a row; reset by any speech or by a normal quiet ending. */
    val consecutiveFailures: Int,
    val utterance: UtteranceState,
    /** spec SS6.3: the engine has proved it keeps listening past an ordinary final (a continuous session); no probe is needed again. */
    val engineContinues: Boolean = false,
    /** spec SS6.3: armed by an ordinary final on a segmented request; a sign of life clears it, its expiry means the engine went idle. */
    val continuationProbeDeadlineMs: Long? = null,
    val stopWatchdogDeadlineMs: Long? = null,
    val busyRetryDeadlineMs: Long? = null,
    val relistenDeadlineMs: Long? = null,
    val cueFallbackDeadlineMs: Long? = null,
    val editorGoneDeadlineMs: Long? = null,
) {
    /** spec SS6.4: the silence limit, only while the session is still open to speech. */
    val silenceDeadlineMs: Long?
        get() = if (phase == DictationPhase.STOPPING) null else lastSpeechMs + silenceLimitMs

    /** spec SS6.4: the hard cap on one session. */
    val sessionCapDeadlineMs: Long?
        get() = if (phase == DictationPhase.STOPPING) null else sessionStartMs + DictationTiming.SESSION_CAP_MS

    val nextDeadlineMs: Long?
        get() = listOfNotNull(
            stopWatchdogDeadlineMs, busyRetryDeadlineMs, relistenDeadlineMs, cueFallbackDeadlineMs,
            continuationProbeDeadlineMs, editorGoneDeadlineMs, silenceDeadlineMs, sessionCapDeadlineMs,
        ).minOrNull()

    companion object {
        /** spec SS2.6 step 6: "Reset all session state" for a freshly started session. */
        fun fresh(
            ownerPackage: String?,
            now: Long,
            request: RecognizerRequest,
            settings: DictationSettings,
            textBeforeSession: String?,
        ): DictationSession = DictationSession(
            ownerPackage = ownerPackage,
            sessionStartMs = now,
            phase = DictationPhase.STARTING,
            cuePlayed = false,
            audioFocusHeld = settings.pauseMedia,
            request = request,
            requestStartMs = now,
            heardSpeech = false,
            lastSpeechMs = now,
            silenceLimitMs = DictationTiming.silenceLimitMs(settings.stopAfterSilenceMs),
            consecutiveFailures = 0,
            utterance = UtteranceState(UtteranceContext(textBeforeSession), PendingUtterance.None),
        )
    }
}
