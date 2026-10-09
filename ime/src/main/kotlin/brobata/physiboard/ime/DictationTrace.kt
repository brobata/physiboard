package brobata.physiboard.ime

import android.util.Log
import brobata.physiboard.core.speech.DictationEffect
import brobata.physiboard.core.speech.DictationEvent
import brobata.physiboard.core.speech.DictationPhase
import brobata.physiboard.core.speech.DictationSession
import brobata.physiboard.core.speech.DictationTextOp

/**
 * spec: dictation.md SS6.9: one compact line per recognizer callback and per clock event, always
 * on, in every build. The release rules strip `Log.v/d/i/w` calls, so this goes through
 * `Log.println` at info level, which they leave alone. No word of the user's ever appears: a
 * result is traced as its length and a short hash, so two callbacks carrying the same text can be
 * told apart from two carrying different text, which is what the 2026-10-07 investigations
 * needed and could not get from the engine's own log. The cost is one string per callback,
 * nothing on the key path.
 */
internal object DictationTrace {
    private const val TAG = "PhysiBoardDictationTrace"

    fun dispatched(event: DictationEvent, ops: List<DictationTextOp>, effects: List<DictationEffect>, session: DictationSession?) {
        val ev = when (event) {
            is DictationEvent.PartialResult -> "partial ${describe(event.text)}"
            is DictationEvent.FinalResult -> "final ${describe(event.text)}"
            is DictationEvent.SegmentResult -> "segment ${describe(event.text)}"
            is DictationEvent.Error -> "error ${event.code}"
            is DictationEvent.Trigger -> "trigger"
            is DictationEvent.EditorFieldOpened -> "fieldOpened"
            is DictationEvent.StartFailed -> "startFailed ${event.reason}"
            DictationEvent.ClockTick -> "tick"
            else -> event::class.simpleName ?: "event"
        }
        if (ev == "tick" && ops.isEmpty() && effects.isEmpty()) return
        val written = ops.joinToString(",") { op ->
            when (op) {
                is DictationTextOp.SetComposingText -> "compose${op.text.length}"
                DictationTextOp.FinishComposing -> "finish"
                is DictationTextOp.CommitText -> "commit${op.text.length}"
                is DictationTextOp.DeleteBeforeCursor -> "del${op.count}"
            }
        }
        val fx = effects.joinToString(",") { it::class.simpleName ?: "?" }
        val phase = session?.phase?.let { p -> if (p == DictationPhase.LISTENING && session.continuationProbeDeadlineMs != null) "LISTENING+probe" else p.name } ?: "ended"
        Log.println(Log.INFO, TAG, "$ev | ops=[$written] | effects=[$fx] | $phase")
    }

    /** spec SS6.9, SS6.10: one line about the audio around the session (routes, focus, recordings, playback, mode). Types and counts only. */
    fun audio(line: String) {
        Log.println(Log.INFO, TAG, line)
    }

    /** A per-process salt, so the hash says "same text as that other line" and nothing else: an unsalted hash of a short utterance could be looked up. */
    private val salt: String = java.util.UUID.randomUUID().toString()

    private fun describe(text: String?): String = if (text == null) "null" else "len=${text.length} h=${Integer.toHexString((salt + text.trim().lowercase()).hashCode())}"
}
