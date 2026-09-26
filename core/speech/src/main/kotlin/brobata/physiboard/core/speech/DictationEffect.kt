package brobata.physiboard.core.speech

/** One thing `:ime` must do to the real world after a [DictationEngine.handle] call. spec: dictation.md SS2, SS3, SS6, SS8.1. */
sealed class DictationEffect {
    /** Issue a request in [mode]. */
    data class StartListening(val mode: DictationMode) : DictationEffect()

    /** spec SS3, SS6.5: ask the engine to stop, so it delivers whatever it already has as a final. */
    object StopListening : DictationEffect()

    /** spec SS3, SS6.4: destroy the in-flight request; no more callbacks from it are expected or acted on. */
    object CancelListening : DictationEffect()

    /** spec SS6.1: plays once per session, at the first [DictationEvent.ReadyForSpeech]. */
    object PlayStartCue : DictationEffect()

    /** spec SS8.1: "the stop cue plays when the session ends, and only if a start cue was played for it." */
    object PlayStopCue : DictationEffect()

    /** spec SS6.6: the toast text for a real error. */
    data class ShowMessage(val message: DictationMessage) : DictationEffect()

    /** spec SS2.6 steps 4 and 7: a start failure's message is log-only; "the user sees nothing." */
    data class LogMessage(val message: DictationMessage) : DictationEffect()
}

/**
 * The result of one [DictationEngine.handle] call: the session to carry forward (`null` once the
 * session has ended), what `:ime` must still do, the text operations to apply, and, only when this
 * transition decided it, the new value of the segmented-refusal latch.
 *
 * [newSegmentedRefusalLatch] is not part of [session] because the latch outlives one session: spec
 * SS6.3, "the refusal latch is set (segmented mode is not asked for again until the recognizer is
 * rebuilt, which happens when the engine setting changes)". The caller stores it per recognizer and
 * passes it back into the next [DictationEngine.handle] call as `segmentedRefusalLatch`; `null` here
 * means this transition has no opinion and the caller's stored value is unchanged.
 */
data class DictationOutcome(
    val session: DictationSession?,
    val effects: List<DictationEffect> = emptyList(),
    val textOps: List<DictationTextOp> = emptyList(),
    val newSegmentedRefusalLatch: Boolean? = null,
)
