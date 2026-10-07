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
    fun `T49 the message for an error that ends the session`() {
        assertEquals(DictationMessage.MIC_PERMISSION_DENIED, DictationErrorClassifier.toastFor(DictationErrorCode.INSUFFICIENT_PERMISSIONS))
        assertEquals(DictationMessage.NETWORK_ERROR, DictationErrorClassifier.toastFor(DictationErrorCode.NETWORK))
        assertEquals(DictationMessage.NETWORK_ERROR, DictationErrorClassifier.toastFor(DictationErrorCode.SERVER))
        assertEquals(DictationMessage.NETWORK_ERROR, DictationErrorClassifier.toastFor(DictationErrorCode.SERVER_DISCONNECTED))
        assertEquals(DictationMessage.OFFLINE_LANGUAGE_MISSING, DictationErrorClassifier.toastFor(DictationErrorCode.LANGUAGE_UNAVAILABLE))
        assertEquals(DictationMessage.SPEECH_RECOGNITION_ERROR, DictationErrorClassifier.toastFor(DictationErrorCode.AUDIO))
        assertEquals(DictationMessage.SPEECH_RECOGNITION_ERROR, DictationErrorClassifier.toastFor(DictationErrorCode.CANNOT_CHECK_SUPPORT))
    }

    @Test
    fun `the quiet, busy, language and network families`() {
        assertTrue(DictationErrorClassifier.isQuiet(DictationErrorCode.NO_MATCH))
        assertTrue(DictationErrorClassifier.isQuiet(DictationErrorCode.SPEECH_TIMEOUT))
        assertTrue(DictationErrorClassifier.isBusy(DictationErrorCode.RECOGNIZER_BUSY))
        assertTrue(DictationErrorClassifier.isLanguage(DictationErrorCode.LANGUAGE_NOT_SUPPORTED))
        assertTrue(DictationErrorClassifier.isLanguage(DictationErrorCode.LANGUAGE_UNAVAILABLE))
        assertTrue(DictationErrorClassifier.isNetwork(DictationErrorCode.NETWORK_TIMEOUT))
        assertFalse(DictationErrorClassifier.isNetwork(DictationErrorCode.CLIENT))
        assertTrue(DictationErrorClassifier.isSegmentedRefusal(DictationErrorCode.CANNOT_CHECK_SUPPORT), "an unknown-to-SS1 code counts as a refusal early on")
    }
}
