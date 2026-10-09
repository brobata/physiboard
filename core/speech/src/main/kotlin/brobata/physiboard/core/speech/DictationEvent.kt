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
    data class Trigger(
        val ownerPackage: String?,
        val textBeforeSession: String?,
        /** spec SS6.10: the audio route at the start; a car or Bluetooth route gets a longer wait for the first words. */
        val audioRoute: SessionAudioRoute = SessionAudioRoute.LOCAL,
    ) : DictationEvent()

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

    /** spec SS6.3: the engine gave a sign of life (an audio level report) while a continuation probe was armed. */
    object EngineActivity : DictationEvent()

    /** spec SS3: the app emptied the field itself (a send); what was dictated went with it, and nothing the engine says later may land in the emptied field. */
    object FieldClearedByApp : DictationEvent()

    /** spec SS4.3: private mode was turned on; a session whose request is online stops, since its audio must not keep leaving the phone. */
    object PrivateModeTurnedOn : DictationEvent()

    /**
     * spec SS6.7: the session's own audio focus changed. [callActive] is whether a phone call was
     * ringing or running at that moment (the audio mode), which is the one loss that ends the
     * session; a media app taking the audio back does not.
     */
    data class AudioFocusChanged(val change: AudioFocusChange, val callActive: Boolean) : DictationEvent()

    /** spec SS6.7: a phone call started ringing or was answered (the audio mode changed); the session ends at once. */
    object CallStarted : DictationEvent()

    /**
     * spec SS6.10: the recognizer's microphone may be a Bluetooth one that is still coming up (a
     * head unit's hands-free link takes about a second, all of it silence). The start cue waits
     * for [InputRouteSettled], at most [DictationTiming.ROUTE_SETTLE_MAX_MS].
     */
    object InputRouteSettling : DictationEvent()

    /** spec SS6.10: the input route is up, or was never going to be Bluetooth. */
    object InputRouteSettled : DictationEvent()

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

/** spec SS6.7: the platform's audio focus changes, as [DictationEvent.AudioFocusChanged] carries them. */
enum class AudioFocusChange { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

/** spec SS6.10: where the session's audio goes, as far as the keyboard can tell at the start. */
enum class SessionAudioRoute {
    /** The phone's own microphone and speaker, or a wired headset. */
    LOCAL,

    /** A Bluetooth audio device is connected (a head unit's hands-free and media links, headphones). */
    BLUETOOTH,

    /** The phone is in car mode (Android Auto). */
    CAR,
    ;

    /** A route whose microphone, or whose first seconds, are slower than the phone's own. */
    val isRemote: Boolean get() = this != LOCAL
}
