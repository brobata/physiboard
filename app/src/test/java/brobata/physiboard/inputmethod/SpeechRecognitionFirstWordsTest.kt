package brobata.physiboard.inputmethod

import android.speech.SpeechRecognizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the keyboard does when the engine gives up before the user has said a word.
 *
 * Google's engines close the microphone about two seconds after any sound, so a user who
 * presses the trigger, draws breath and then speaks gets "no text recognized" for the breath.
 * Seen on a Titan 2 in Teams: speech began ten milliseconds after the microphone closed.
 * The session must listen again instead — for a bounded time, and never once text has arrived,
 * because after that a quiet error really is the end of the utterance.
 */
class SpeechRecognitionFirstWordsTest {

    private fun relisten(
        error: Int = SpeechRecognizer.ERROR_NO_MATCH,
        sessionActive: Boolean = true,
        stopRequested: Boolean = false,
        heardSpeech: Boolean = false,
        elapsedMs: Long = 3_000L,
        restarts: Int = 0
    ) = SpeechRecognitionManager.shouldRelistenBeforeSpeech(
        error = error,
        sessionActive = sessionActive,
        stopRequested = stopRequested,
        heardSpeech = heardSpeech,
        elapsedMs = elapsedMs,
        restarts = restarts
    )

    @Test
    fun `listens again when the engine hears nothing before the user speaks`() {
        assertTrue(relisten(error = SpeechRecognizer.ERROR_NO_MATCH))
        assertTrue(relisten(error = SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }

    @Test
    fun `a busy engine is retried rather than reported`() {
        assertTrue(relisten(error = SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
    }

    @Test
    fun `real failures are still reported`() {
        assertFalse(relisten(error = SpeechRecognizer.ERROR_AUDIO))
        assertFalse(relisten(error = SpeechRecognizer.ERROR_NETWORK))
        assertFalse(relisten(error = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertFalse(relisten(error = SpeechRecognizer.ERROR_CLIENT))
    }

    @Test
    fun `once text has arrived a quiet error is the utterance ending`() {
        assertFalse(relisten(heardSpeech = true))
    }

    @Test
    fun `the user stopping the session wins`() {
        assertFalse(relisten(stopRequested = true))
        assertFalse(relisten(sessionActive = false))
    }

    @Test
    fun `gives up after the grace period`() {
        assertTrue(relisten(elapsedMs = SpeechRecognitionManager.START_GRACE_MS - 1))
        assertFalse(relisten(elapsedMs = SpeechRecognitionManager.START_GRACE_MS))
    }

    @Test
    fun `gives up after enough restarts even inside the grace period`() {
        assertTrue(relisten(restarts = SpeechRecognitionManager.MAX_QUIET_RESTARTS - 1))
        assertFalse(relisten(restarts = SpeechRecognitionManager.MAX_QUIET_RESTARTS))
    }

    @Test
    fun `only a client error counts as the engine refusing a segmented session`() {
        assertTrue(SpeechRecognitionManager.isSegmentRefusalError(SpeechRecognizer.ERROR_CLIENT))
        // An unknown code is not a recognition failure we can name, so treat it as a refusal.
        assertTrue(SpeechRecognitionManager.isSegmentRefusalError(99))
    }

    @Test
    fun `transient and environmental errors never switch segmented sessions off`() {
        listOf(
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
        ).forEach { error ->
            assertFalse("error $error", SpeechRecognitionManager.isSegmentRefusalError(error))
        }
    }
}
