package brobata.physiboard.core.speech

/**
 * Every input [DictationEngine.handle] accepts: the callbacks a real recognizer produces, the
 * editor-lifecycle facts only `:ime` can observe, and the clock. spec: dictation.md SS2, SS3, SS6,
 * SS7.
 */
sealed class DictationEvent {
    /**
     * spec SS2: "All triggers call the same 'start or stop' action." Handed in as a plain function
     * call by whatever `:ime` wires to a key later (this task does not bind one); this module does
     * not know or care which key, if any, produced it. [textBeforeSession] is the one legitimate
     * live editor read this whole module ever consumes (see [UtteranceContext]'s KDoc); it is
     * ignored when this trigger turns out to be a stop.
     */
    data class Trigger(val ownerPackage: String?, val textBeforeSession: String?) : DictationEvent()

    /** spec SS6.1: the engine is ready for speech. */
    object ReadyForSpeech : DictationEvent()

    /** spec SS6.3, SS6.4: cancels the silence timer and the segmented watchdog. */
    object BeginningOfSpeech : DictationEvent()

    /** spec SS7.1. Blank text is ignored by [DictationEngine], per SS7.1's "Empty partials are ignored." */
    data class PartialResult(val text: String) : DictationEvent()

    /** spec SS7.3. `null` or empty models D3's "final without text." */
    data class FinalResult(val text: String?) : DictationEvent()

    /** spec SS6.3: one utterance inside a segmented session. */
    data class SegmentResult(val text: String) : DictationEvent()

    /** spec SS6.3: "the engine ends a segmented session on the pause." */
    object SegmentedSessionEnded : DictationEvent()

    /** spec SS6.6, SS1's numeric code catalog. */
    data class Error(val code: Int) : DictationEvent()

    /** spec SS3: the field the session was dictating into has closed. */
    object EditorFieldClosed : DictationEvent()

    /** spec SS3: a field is now current; `null` when nothing is. */
    data class EditorFieldOpened(val ownerPackage: String?) : DictationEvent()

    /**
     * `:ime`'s own signal that the user changed the field's text while a dictation utterance was
     * still in progress (a Backspace, or any other edit, reaching the field while this session is
     * listening). spec: rebuild-from-scratch.md, the dictation bug of git c440844: this is what
     * moves [PendingUtterance] to [PendingUtterance.Invalidated] so that text can never be typed
     * back. See [PendingUtterance]'s own KDoc for why the type, not a runtime check, is what makes
     * this unwriteable.
     */
    object UserEditedComposingText : DictationEvent()

    /** spec SS3: "The field rejected an insert (exception while writing)." */
    object EditorRejectedInsert : DictationEvent()

    /** spec SS2.6 step 7: the first request could not even be issued (permission or platform failure). */
    object StartFailed : DictationEvent()

    /** The clock reaching [DictationSession.nextDeadlineMs]; see that property's KDoc. */
    object ClockTick : DictationEvent()
}
