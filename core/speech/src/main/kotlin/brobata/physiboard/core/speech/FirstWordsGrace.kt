package brobata.physiboard.core.speech

/**
 * spec: dictation.md SS6.2: whether a quiet or busy error should re-listen instead of ending the
 * session, because Google's engines close the microphone about 2 s after any sound (D2) and a user
 * who draws breath before speaking would otherwise be told nothing was heard.
 */
object FirstWordsGrace {
    fun canReListen(session: DictationSession, now: Long, code: Int): Boolean {
        if (!session.active) return false
        if (session.stopRequested) return false
        if (session.heardSpeech) return false
        if (!(DictationErrorClassifier.isQuiet(code) || DictationErrorClassifier.isBusy(code))) return false
        if (now - session.sessionStartMs >= DictationTiming.FIRST_WORDS_GRACE_MS) return false
        if (session.restartsUsed >= DictationTiming.FIRST_WORDS_GRACE_MAX_RESTARTS) return false
        return true
    }
}
