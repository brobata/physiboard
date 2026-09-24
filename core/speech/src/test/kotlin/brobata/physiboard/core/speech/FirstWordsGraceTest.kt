package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: dictation.md SS16, T1-T8. */
class FirstWordsGraceTest {

    private fun baseline(
        active: Boolean = true,
        stopRequested: Boolean = false,
        heardSpeech: Boolean = false,
        restartsUsed: Int = 0,
        sessionStartMs: Long = 0L,
    ) = DictationSession(
        ownerPackage = "app",
        sessionStartMs = sessionStartMs,
        active = active,
        stopRequested = stopRequested,
        heardSpeech = heardSpeech,
        restartsUsed = restartsUsed,
        mode = DictationMode.RESTART_LOOP,
        isContinuation = false,
        requestStartMs = sessionStartMs,
        segmentsSeen = 0,
        utterance = UtteranceState(UtteranceContext(null), PendingUtterance.None),
    )

    @Test
    fun `T1 code 7 within grace re-listens`() {
        assertTrue(FirstWordsGrace.canReListen(baseline(), now = 3000L, code = DictationErrorCode.NO_MATCH))
    }

    @Test
    fun `T2 code 6 within grace re-listens`() {
        assertTrue(FirstWordsGrace.canReListen(baseline(), now = 3000L, code = DictationErrorCode.SPEECH_TIMEOUT))
    }

    @Test
    fun `T3 code 8 within grace re-listens`() {
        assertTrue(FirstWordsGrace.canReListen(baseline(), now = 3000L, code = DictationErrorCode.RECOGNIZER_BUSY))
    }

    @Test
    fun `T4 real errors never re-listen`() {
        for (code in listOf(DictationErrorCode.AUDIO, DictationErrorCode.NETWORK, DictationErrorCode.INSUFFICIENT_PERMISSIONS, DictationErrorCode.CLIENT)) {
            assertFalse(FirstWordsGrace.canReListen(baseline(), now = 3000L, code = code), "code $code should not re-listen")
        }
    }

    @Test
    fun `T5 heard speech blocks the grace`() {
        assertFalse(FirstWordsGrace.canReListen(baseline(heardSpeech = true), now = 3000L, code = DictationErrorCode.NO_MATCH))
    }

    @Test
    fun `T6 stop requested or inactive blocks the grace`() {
        assertFalse(FirstWordsGrace.canReListen(baseline(stopRequested = true), now = 3000L, code = DictationErrorCode.NO_MATCH))
        assertFalse(FirstWordsGrace.canReListen(baseline(active = false), now = 3000L, code = DictationErrorCode.NO_MATCH))
    }

    @Test
    fun `T7 the ten second window is exclusive`() {
        assertTrue(FirstWordsGrace.canReListen(baseline(), now = 9_999L, code = DictationErrorCode.NO_MATCH))
        assertFalse(FirstWordsGrace.canReListen(baseline(), now = 10_000L, code = DictationErrorCode.NO_MATCH))
    }

    @Test
    fun `T8 the five restart cap is exclusive`() {
        assertTrue(FirstWordsGrace.canReListen(baseline(restartsUsed = 4), now = 3000L, code = DictationErrorCode.NO_MATCH))
        assertFalse(FirstWordsGrace.canReListen(baseline(restartsUsed = 5), now = 3000L, code = DictationErrorCode.NO_MATCH))
    }
}
