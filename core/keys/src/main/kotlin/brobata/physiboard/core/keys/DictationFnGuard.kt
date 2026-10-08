package brobata.physiboard.core.keys

/**
 * spec: dictation.md SS2: while a dictation session runs, a PRESS of Fn stops it, and nothing
 * the same press goes on to produce (its later repeats, the burst command they add up to) may
 * start another. Pure, so the two mistakes it exists to prevent can be tested on the JVM:
 *
 * - The hold that just STARTED a session on its fifth repeat must not stop it on its sixth
 *   (Titan, 2026-10-07: every normal hold produced a session that died within a frame).
 * - A stop press's trailing repeats can arrive after the session has already ended, or with a
 *   gap wide enough to look like a new press; the guard is therefore time-based from the stop,
 *   independent of any session state (Titan, 2026-10-07 19:59: a session restarted 0.6 s after
 *   the press that stopped the previous one).
 *
 * Fn on this phone delivers no press of its own, only repeats about 50 ms apart from ~400 ms
 * into a hold (keys document, D4/D5); a press is told from the next repeat of the same hold by a
 * gap of the burst's own reset time.
 */
class DictationFnGuard(
    private val newPressGapMs: Long = 200L,
    private val stopWindowMs: Long = 1_500L,
) {
    private var lastFnEventAtMs: Long? = null
    private var stopAtMs: Long? = null

    /** Every Fn-origin event, in order. True when this event is the press that stops the running session. */
    fun onFnEvent(timeMs: Long, sessionActive: Boolean): Boolean {
        val newPress = lastFnEventAtMs?.let { timeMs - it >= newPressGapMs } ?: true
        lastFnEventAtMs = timeMs
        if (!newPress || !sessionActive) return false
        stopAtMs = timeMs
        return true
    }

    /** The session ended for a reason a late Fn press would have been meant to cause (the app emptied the field, a typing key): that press must not start a new one. */
    fun noteStoppedByOther(timeMs: Long) {
        stopAtMs = timeMs
    }

    /** Whether a start (the burst command, the catalog command) at [timeMs] is a real request and not the tail of the press that stopped the last session. */
    fun allowsStart(timeMs: Long): Boolean = stopAtMs?.let { timeMs - it >= stopWindowMs } ?: true
}
