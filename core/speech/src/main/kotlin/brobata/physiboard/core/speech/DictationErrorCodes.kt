package brobata.physiboard.core.speech

/**
 * The recognizer's own numeric error vocabulary, spec: dictation.md SS1 ("Android's numeric codes
 * used throughout"). Named here so nothing downstream compares against a bare integer literal.
 */
object DictationErrorCode {
    const val NETWORK_TIMEOUT = 1
    const val NETWORK = 2
    const val AUDIO = 3
    const val SERVER = 4
    const val CLIENT = 5
    const val SPEECH_TIMEOUT = 6
    const val NO_MATCH = 7
    const val RECOGNIZER_BUSY = 8
    const val INSUFFICIENT_PERMISSIONS = 9
    const val TOO_MANY_REQUESTS = 10
    const val SERVER_DISCONNECTED = 11
    const val LANGUAGE_NOT_SUPPORTED = 12
    const val LANGUAGE_UNAVAILABLE = 13
    const val CANNOT_CHECK_SUPPORT = 14
    const val CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15
}

/**
 * Classifies one error code into the shapes the session lifecycle cares about, so no branch
 * anywhere else in this module tests a code by hand. spec: dictation.md SS1, SS6.3, SS6.6.
 */
object DictationErrorClassifier {

    /** spec SS1: "Quiet error: the engine reporting 'no match' (code 7) or 'speech timeout' (code 6)." */
    fun isQuiet(code: Int): Boolean = code == DictationErrorCode.NO_MATCH || code == DictationErrorCode.SPEECH_TIMEOUT

    /** spec SS6.6: a busy engine ("recognizer busy") gets its own 300 ms retry, not an immediate re-listen. */
    fun isBusy(code: Int): Boolean = code == DictationErrorCode.RECOGNIZER_BUSY

    /** spec SS4.3: the language is not installed for the recognizer asked for (12) or not downloaded yet (13). */
    fun isLanguage(code: Int): Boolean = code == DictationErrorCode.LANGUAGE_NOT_SUPPORTED || code == DictationErrorCode.LANGUAGE_UNAVAILABLE

    /** spec SS6.6: the network and server family, the one family whose message names the network. */
    fun isNetwork(code: Int): Boolean =
        code == DictationErrorCode.NETWORK || code == DictationErrorCode.NETWORK_TIMEOUT || code == DictationErrorCode.SERVER || code == DictationErrorCode.SERVER_DISCONNECTED

    /**
     * spec SS6.3 "Refusal": code 5 (client), or any code not in {1,2,3,4,6,7,8,9,10,11,12,13}, i.e.
     * an unknown code. Busy, network, audio, permission, language and silence errors are listed
     * there as "never refusals: they would happen to a plain request too."
     */
    private val NEVER_A_REFUSAL = setOf(
        DictationErrorCode.NETWORK_TIMEOUT, DictationErrorCode.NETWORK, DictationErrorCode.AUDIO,
        DictationErrorCode.SERVER, DictationErrorCode.SPEECH_TIMEOUT, DictationErrorCode.NO_MATCH,
        DictationErrorCode.RECOGNIZER_BUSY, DictationErrorCode.INSUFFICIENT_PERMISSIONS,
        DictationErrorCode.TOO_MANY_REQUESTS, DictationErrorCode.SERVER_DISCONNECTED,
        DictationErrorCode.LANGUAGE_NOT_SUPPORTED, DictationErrorCode.LANGUAGE_UNAVAILABLE,
    )

    fun isSegmentedRefusal(code: Int): Boolean = code !in NEVER_A_REFUSAL

    /** spec SS6.6's message table for the errors that end a session. Quiet errors never reach this: they re-listen. */
    fun toastFor(code: Int): DictationMessage = when {
        code == DictationErrorCode.INSUFFICIENT_PERMISSIONS -> DictationMessage.MIC_PERMISSION_DENIED
        isNetwork(code) -> DictationMessage.NETWORK_ERROR
        isLanguage(code) -> DictationMessage.OFFLINE_LANGUAGE_MISSING
        else -> DictationMessage.SPEECH_RECOGNITION_ERROR
    }
}

/**
 * spec: dictation.md SS6.6's message text. [text] is the exact spec string so `:ime` never invents
 * its own wording for either surface (a toast via `DictationEffect.ShowMessage`, a log line via
 * `DictationEffect.LogMessage`).
 */
enum class DictationMessage(val text: String) {
    MIC_PERMISSION_DENIED("Microphone permission denied."),
    NETWORK_ERROR("Network error."),
    SPEECH_RECOGNITION_ERROR("Speech recognition error."),
    SPEECH_RECOGNITION_NOT_AVAILABLE("Speech recognition isn't available on this phone."),
    /** spec SS4.3: the on-device recognizer has no pack for the language and nothing else may be used. */
    OFFLINE_LANGUAGE_MISSING("The offline speech pack for this language isn't installed. Download it in Speech Services by Google, or turn off \"Keep speech on the phone\"."),
    /** spec SS4.3, app-shell.md SS31: the same, in private mode, where going online is not an option. */
    PRIVATE_MODE_NEEDS_OFFLINE_LANGUAGE("Private mode keeps speech on the phone, and the offline speech pack for this language isn't installed."),
    /** spec SS4.3: the log line for the one silent fallback, offline to online. */
    FELL_BACK_TO_ONLINE("offline recognizer has no pack for this language; this session goes online"),
    /** spec SS6.3: the log line for the other silent fallback, segmented to plain. */
    SEGMENTED_REFUSED("segmented session refused by the engine; falling back to one request per utterance"),
}

/**
 * spec SS2.6 steps 4 and 7: why the very first request of a session could not be issued, before
 * the engine ever got a chance to answer. `:ime` classifies the platform failure into one of these;
 * [DictationEngine] turns it into the matching [DictationMessage].
 */
enum class DictationStartFailureReason {
    /** step 4: no recognizer exists for the engine id and none could be created either. */
    RECOGNITION_UNAVAILABLE,
    /** step 7: "a security failure" starting the request (a permission race after the trigger checked it). */
    SECURITY_FAILURE,
    /** step 7: "any other failure" starting the request. */
    OTHER_FAILURE,
}

/** spec SS2.6 steps 4 and 7: which message each start-failure reason shows. */
object DictationStartFailureMessages {
    fun forReason(reason: DictationStartFailureReason): DictationMessage = when (reason) {
        DictationStartFailureReason.RECOGNITION_UNAVAILABLE -> DictationMessage.SPEECH_RECOGNITION_NOT_AVAILABLE
        DictationStartFailureReason.SECURITY_FAILURE -> DictationMessage.MIC_PERMISSION_DENIED
        DictationStartFailureReason.OTHER_FAILURE -> DictationMessage.SPEECH_RECOGNITION_ERROR
    }
}
