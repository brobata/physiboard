package brobata.physiboard.core.speech

/** One thing `:ime` must do to the real world after a [DictationEngine.handle] call. spec: dictation.md SS2, SS3, SS6, SS8.1. */
sealed class DictationEffect {
    /** Issue [request] to the recognizer. */
    data class StartListening(val request: RecognizerRequest) : DictationEffect()

    /** spec SS3, SS6.5: ask the engine to stop, so it delivers whatever it already has. */
    object StopListening : DictationEffect()

    /** spec SS3, SS6.5: destroy the in-flight request; no more callbacks from it are expected or acted on. */
    object CancelListening : DictationEffect()

    /**
     * spec SS6.8: keep the keyboard visible to the input-method service for the whole session.
     * Android grants a keyboard the microphone only while the system considers it shown (the
     * visible binding carries the microphone capability); with the window hidden the recording
     * is silently fed zeros (D22). Issued before the first request, released at every ending.
     */
    object HoldImeVisible : DictationEffect()

    /** spec SS6.8: the keyboard may hide again. */
    object ReleaseImeVisible : DictationEffect()

    /** spec SS6.7: take exclusive transient audio focus for the session, so music pauses once. */
    object AcquireAudioFocus : DictationEffect()

    /** spec SS6.7: give audio focus back, so music resumes once. */
    object ReleaseAudioFocus : DictationEffect()

    /** spec SS8.1: plays once per session, when the microphone is open. */
    object PlayStartCue : DictationEffect()

    /** spec SS8.1: "the stop cue plays when the session ends, and only if a start cue was played for it." */
    object PlayStopCue : DictationEffect()

    /** spec SS6.6: a message the user sees. */
    data class ShowMessage(val message: DictationMessage) : DictationEffect()

    /** spec SS6.6: a message for the log only. */
    data class LogMessage(val message: DictationMessage) : DictationEffect()
}

/**
 * The result of one [DictationEngine.handle] call: the session to carry forward (`null` once the
 * session has ended), what `:ime` must still do, the text operations to apply, and, only when this
 * transition decided it, the new value of the segmented-refusal latch.
 *
 * [newSegmentedRefusalLatch] is not part of [session] because the latch outlives one session: spec
 * SS6.3, "the refusal latch is set (segmented mode is not asked for again until the engine setting
 * changes)". The caller stores it per engine and passes it back into the next
 * [DictationEngine.handle] call as `segmentedRefusalLatch`; `null` here means this transition has
 * no opinion and the caller's stored value is unchanged.
 */
data class DictationOutcome(
    val session: DictationSession?,
    val effects: List<DictationEffect> = emptyList(),
    val textOps: List<DictationTextOp> = emptyList(),
    val newSegmentedRefusalLatch: Boolean? = null,
)
