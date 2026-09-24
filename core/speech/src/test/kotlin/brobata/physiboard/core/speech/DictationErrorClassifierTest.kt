package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: dictation.md SS16, T9, T49. */
class DictationErrorClassifierTest {

    @Test
    fun `T9 refusal classification`() {
        assertTrue(DictationErrorClassifier.isSegmentedRefusal(DictationErrorCode.CLIENT))
        assertTrue(DictationErrorClassifier.isSegmentedRefusal(99)) // unknown code
        val neverRefusals = listOf(
            DictationErrorCode.RECOGNIZER_BUSY, DictationErrorCode.NETWORK, DictationErrorCode.NETWORK_TIMEOUT,
            DictationErrorCode.SERVER, DictationErrorCode.SERVER_DISCONNECTED, DictationErrorCode.TOO_MANY_REQUESTS,
            DictationErrorCode.AUDIO, DictationErrorCode.INSUFFICIENT_PERMISSIONS, DictationErrorCode.NO_MATCH,
            DictationErrorCode.SPEECH_TIMEOUT, DictationErrorCode.LANGUAGE_NOT_SUPPORTED, DictationErrorCode.LANGUAGE_UNAVAILABLE,
        )
        for (code in neverRefusals) {
            assertFalse(DictationErrorClassifier.isSegmentedRefusal(code), "code $code must not be a refusal")
        }
    }

    @Test
    fun `T49 rule 8 toast text`() {
        assertEquals(DictationMessage.NO_TEXT_RECOGNIZED, DictationErrorClassifier.toastFor(DictationErrorCode.NO_MATCH))
        assertEquals(DictationMessage.NO_SPEECH_INPUT_DETECTED, DictationErrorClassifier.toastFor(DictationErrorCode.SPEECH_TIMEOUT))
        assertEquals(DictationMessage.MIC_PERMISSION_DENIED, DictationErrorClassifier.toastFor(DictationErrorCode.INSUFFICIENT_PERMISSIONS))
        assertEquals(DictationMessage.NETWORK_ERROR, DictationErrorClassifier.toastFor(DictationErrorCode.NETWORK))
        assertEquals(DictationMessage.SPEECH_RECOGNITION_ERROR, DictationErrorClassifier.toastFor(DictationErrorCode.SERVER))
    }
}
