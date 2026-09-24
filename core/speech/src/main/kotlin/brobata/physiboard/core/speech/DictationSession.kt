package brobata.physiboard.core.speech

/**
 * Everything [DictationEngine] carries from one input to the next for one dictation session. `null`
 * (never constructed here) means no session is running; spec: dictation.md SS1, SS2, SS6.
 *
 * [nextDeadlineMs] is the one clock fact the caller needs: the earliest of this session's armed
 * timers, so it can schedule a single real-time wakeup and call [DictationEngine.handle] with
 * [DictationEvent.ClockTick] when it arrives, exactly the way `KeyboardPipeline.pendingLongPressDeadlineMs`
 * already drives `:ime`'s one other real timer.
 */
data class DictationSession(
    /** spec SS2.6 step 2: the package of the field dictation started in, so a different app taking the field over ends the session (SS3). */
    val ownerPackage: String?,
    val sessionStartMs: Long,
    /** spec SS6.1: becomes true at the first "ready for speech", not at the trigger; the start cue and the strip's lit state are gated on this becoming true, once. */
    val active: Boolean,
    /** spec SS3, SS6.5: the trigger fired again, or an explicit stop was asked for, while active. */
    val stopRequested: Boolean,
    /** spec SS1: "the session has received at least one non-empty partial or one final." */
    val heardSpeech: Boolean,
    /** spec SS6.2: how many quiet/busy re-listens the first-words grace has already spent. */
    val restartsUsed: Int,
    val mode: DictationMode,
    /** spec SS1: "a new request started right after a final, inside the same session, in restart-loop mode." */
    val isContinuation: Boolean,
    /** When the current request (or continuation) began; the clock basis for SS6.3's refusal window and SS6.4's fast-failure window. */
    val requestStartMs: Long,
    /** spec SS6.3: segments delivered this session; 0 still counts as "the engine ignored the request" once the watchdog fires. */
    val segmentsSeen: Int,
    val utterance: UtteranceState,
    val silenceDeadlineMs: Long? = null,
    val watchdogDeadlineMs: Long? = null,
    val busyRetryDeadlineMs: Long? = null,
    val editorGoneDeadlineMs: Long? = null,
) {
    val nextDeadlineMs: Long?
        get() = listOfNotNull(silenceDeadlineMs, watchdogDeadlineMs, busyRetryDeadlineMs, editorGoneDeadlineMs).minOrNull()

    companion object {
        /** spec SS2.6 step 6: "Reset all session state" for a freshly started (or restarted) session. */
        fun fresh(ownerPackage: String?, now: Long, mode: DictationMode, textBeforeSession: String?): DictationSession = DictationSession(
            ownerPackage = ownerPackage,
            sessionStartMs = now,
            active = false,
            stopRequested = false,
            heardSpeech = false,
            restartsUsed = 0,
            mode = mode,
            isContinuation = false,
            requestStartMs = now,
            segmentsSeen = 0,
            utterance = UtteranceState(UtteranceContext(textBeforeSession), PendingUtterance.None),
        )
    }
}
