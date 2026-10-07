package brobata.physiboard.core.speech

/**
 * Every input [DictationEngine.handle] accepts: the callbacks a real recognizer produces, the
 * editor-lifecycle and key facts only `:ime` can observe, and the clock. spec: dictation.md SS2,
 * SS3, SS6, SS7.
 */
sealed class DictationEvent {
    /**
     * spec SS2: "All triggers call the same 'start or stop' action." Handed in as a plain function
     * call by whatever `:ime` wires to a key (the Fn burst, keys-and-modifiers.md SS3.3); this module
     * does not know or care which key produced it. [textBeforeSession] is the one legitimate live
     * editor read this whole module ever consumes (see [UtteranceContext]'s KDoc); it is ignored
     * when this trigger turns out to be a stop.
     */
    data class Trigger(val ownerPackage: String?, val textBeforeSession: String?) : DictationEvent()

    /** spec SS6.1: the engine is ready for speech. */
    object ReadyForSpeech : DictationEvent()

    /** spec SS8.1: the engine's first audio level report of the session: the microphone is open and audio is flowing. */
    object FirstAudio : DictationEvent()

    /** spec SS6.4: the engine heard the user start to speak. */
    object BeginningOfSpeech : DictationEvent()

    /** The recognizer's `onEndOfSpeech()`; nothing in the session turns on it, but it is logged. */
    object EndOfSpeech : DictationEvent()

    /** spec SS7.1. Blank text is ignored by [DictationEngine], per SS7.1's "Empty partials are ignored." */
    data class PartialResult(val text: String) : DictationEvent()

    /** spec SS7.3. `null` or empty models D3's "final without text." */
    data class FinalResult(val text: String?) : DictationEvent()

    /** spec SS6.2: one utterance inside a segmented session. */
    data class SegmentResult(val text: String) : DictationEvent()

    /** spec SS6.2: the engine ended its segmented session. */
    object SegmentedSessionEnded : DictationEvent()

    /** spec SS6.6, SS1's numeric code catalog. */
    data class Error(val code: Int) : DictationEvent()

    /**
     * spec SS3: a key other than a modifier went down while the session was running. With
     * `dictation_stop_on_typing` on this stops the session at once, committing the words on
     * screen, and the key then does its usual work; with it off nothing happens here.
     */
    object KeyDown : DictationEvent()

    /** spec SS4.3: private mode was turned on; a session whose request is online stops, since its audio must not keep leaving the phone. */
    object PrivateModeTurnedOn : DictationEvent()

    /** spec SS6.7: another app took audio focus for good (not the engine's own transient request); the session stops at once. */
    object AudioFocusLost : DictationEvent()

    /** spec SS3: the field the session was dictating into has closed. */
    object EditorFieldClosed : DictationEvent()

    /** spec SS3: a field is now current; `null` when nothing is. */
    data class EditorFieldOpened(val ownerPackage: String?) : DictationEvent()

    /**
     * `:ime`'s own signal that the user changed the field's text while a dictation utterance was
     * still in progress and the session was allowed to go on (`dictation_stop_on_typing` off).
     * spec: the dictation bug of git c440844: this is what moves [PendingUtterance] to
     * [PendingUtterance.Invalidated] so that text can never be typed back. See [PendingUtterance]'s
     * own KDoc for why the type, not a runtime check, is what makes this unwriteable.
     */
    object UserEditedComposingText : DictationEvent()

    /** spec SS3: "The field rejected an insert (exception while writing)." */
    object EditorRejectedInsert : DictationEvent()

    /**
     * spec SS2.6 steps 4 and 7: the first request could not even be issued. [reason] names which
     * message the user sees.
     */
    data class StartFailed(val reason: DictationStartFailureReason) : DictationEvent()

    /** The clock reaching [DictationSession.nextDeadlineMs]; see that property's KDoc. */
    object ClockTick : DictationEvent()
}
