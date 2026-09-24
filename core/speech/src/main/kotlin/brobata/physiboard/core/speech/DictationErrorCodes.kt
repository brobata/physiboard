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
}

/**
 * Classifies one error code into the three shapes the session lifecycle cares about, so no branch
 * anywhere else in this module tests a code by hand. spec: dictation.md SS1, SS6.2, SS6.3, SS6.6.
 */
object DictationErrorClassifier {

    /** spec SS1: "Quiet error: the engine reporting 'no match' (code 7) or 'speech timeout' (code 6)." */
    fun isQuiet(code: Int): Boolean = code == DictationErrorCode.NO_MATCH || code == DictationErrorCode.SPEECH_TIMEOUT

    /** spec SS6.2: a busy engine ("recognizer busy") gets its own 300 ms retry, not an immediate re-listen. */
    fun isBusy(code: Int): Boolean = code == DictationErrorCode.RECOGNIZER_BUSY

    /** spec SS1: "Every other engine error code is a real error." */
    fun isReal(code: Int): Boolean = !isQuiet(code)

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

    /** spec SS6.6 rule 8's toast table. */
    fun toastFor(code: Int): DictationMessage = when (code) {
        DictationErrorCode.NO_MATCH -> DictationMessage.NO_TEXT_RECOGNIZED
        DictationErrorCode.SPEECH_TIMEOUT -> DictationMessage.NO_SPEECH_INPUT_DETECTED
        DictationErrorCode.INSUFFICIENT_PERMISSIONS -> DictationMessage.MIC_PERMISSION_DENIED
        DictationErrorCode.NETWORK -> DictationMessage.NETWORK_ERROR
        else -> DictationMessage.SPEECH_RECOGNITION_ERROR
    }
}

/** spec: dictation.md SS6.6's toast text and SS2.6's log-only messages that reach the user. */
enum class DictationMessage {
    NO_TEXT_RECOGNIZED,
    NO_SPEECH_INPUT_DETECTED,
    MIC_PERMISSION_DENIED,
    NETWORK_ERROR,
    SPEECH_RECOGNITION_ERROR,
}
