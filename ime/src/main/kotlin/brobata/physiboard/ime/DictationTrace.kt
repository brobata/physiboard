package brobata.physiboard.ime

import android.util.Log
import brobata.physiboard.core.speech.DictationEffect
import brobata.physiboard.core.speech.DictationEvent
import brobata.physiboard.core.speech.DictationPhase
import brobata.physiboard.core.speech.DictationSession
import brobata.physiboard.core.speech.DictationTextOp
import java.security.MessageDigest

/**
 * spec: dictation.md SS6.9: one compact line per recognizer callback and per clock event, in
 * every build. The release rules strip `Log.v/d/i/w` calls, so this goes through
 * `Log.println` at info level, which they leave alone.
 *
 * Every label is a string written here, never a class name: the release build is minified, so
 * `::class.simpleName` or a data class's `toString()` would print a shortened name like `a0`
 * there. The `when`s below have no `else`, so a new event or effect cannot ship without a label.
 * The labels are the names the debug builds' traces already printed, so old and new logs compare.
 *
 * No word of the user's ever appears: a result is traced as its length and a short keyed hash,
 * so two callbacks carrying the same text can be told apart from two carrying different text,
 * which is what the 2026-10-07 investigations needed. Nothing at all is written while learning
 * is off ([privateNow]: private mode, or a field that asks for no personalized learning, the
 * same gate as [DiagnosticLog]). The cost is one string per callback, nothing on the key path.
 */
internal object DictationTrace {
    const val TAG = "PhysiBoardDictationTrace"

    /** app-shell.md SS31: set with [DiagnosticLog.privateNow] from the keyboard's privacy state; while true nothing is logged. */
    @Volatile
    var privateNow: Boolean = false

    fun dispatched(event: DictationEvent, ops: List<DictationTextOp>, effects: List<DictationEffect>, session: DictationSession?) {
        if (privateNow) return
        val line = line(event, ops, effects, session) ?: return
        Log.println(Log.INFO, TAG, line)
    }

    /** spec SS6.9, SS6.10: one line about the audio around the session (routes, focus, recordings, playback, mode). Types and counts only. */
    fun audio(line: String) {
        if (privateNow) return
        Log.println(Log.INFO, TAG, line)
    }

    /** The line [dispatched] writes, or `null` for a clock tick that did nothing. */
    internal fun line(event: DictationEvent, ops: List<DictationTextOp>, effects: List<DictationEffect>, session: DictationSession?): String? {
        if (event == DictationEvent.ClockTick && ops.isEmpty() && effects.isEmpty()) return null
        val written = ops.joinToString(",", transform = ::opLabel)
        val fx = effects.joinToString(",", transform = ::effectLabel)
        val phase = session?.phase?.let { p -> if (p == DictationPhase.LISTENING && session.continuationProbeDeadlineMs != null) "LISTENING+probe" else p.name } ?: "ended"
        return "${eventLabel(event)} | ops=[$written] | effects=[$fx] | $phase"
    }

    internal fun eventLabel(event: DictationEvent): String = when (event) {
        is DictationEvent.Trigger -> "trigger route=${event.audioRoute.name}"
        DictationEvent.ReadyForSpeech -> "ReadyForSpeech"
        DictationEvent.FirstAudio -> "FirstAudio"
        DictationEvent.BeginningOfSpeech -> "BeginningOfSpeech"
        DictationEvent.EndOfSpeech -> "EndOfSpeech"
        is DictationEvent.PartialResult -> "partial ${describe(event.text)}"
        is DictationEvent.FinalResult -> "final ${describe(event.text)}"
        is DictationEvent.SegmentResult -> "segment ${describe(event.text)}"
        DictationEvent.SegmentedSessionEnded -> "SegmentedSessionEnded"
        is DictationEvent.Error -> "error ${event.code}"
        DictationEvent.KeyDown -> "KeyDown"
        DictationEvent.EngineActivity -> "EngineActivity"
        DictationEvent.FieldClearedByApp -> "FieldClearedByApp"
        DictationEvent.PrivateModeTurnedOn -> "PrivateModeTurnedOn"
        is DictationEvent.AudioFocusChanged -> "AudioFocusChanged ${event.change.name} call=${event.callActive}"
        DictationEvent.CallStarted -> "CallStarted"
        DictationEvent.InputRouteSettling -> "InputRouteSettling"
        DictationEvent.InputRouteSettled -> "InputRouteSettled"
        DictationEvent.EditorFieldClosed -> "EditorFieldClosed"
        is DictationEvent.EditorFieldOpened -> "fieldOpened"
        DictationEvent.UserEditedComposingText -> "UserEditedComposingText"
        DictationEvent.EditorRejectedInsert -> "EditorRejectedInsert"
        is DictationEvent.StartFailed -> "startFailed ${event.reason.name}"
        DictationEvent.ClockTick -> "tick"
    }

    internal fun opLabel(op: DictationTextOp): String = when (op) {
        is DictationTextOp.SetComposingText -> "compose${op.text.length}"
        DictationTextOp.FinishComposing -> "finish"
        is DictationTextOp.CommitText -> "commit${op.text.length}"
        is DictationTextOp.DeleteBeforeCursor -> "del${op.count}"
    }

    internal fun effectLabel(effect: DictationEffect): String = when (effect) {
        is DictationEffect.StartListening -> "StartListening"
        DictationEffect.StopListening -> "StopListening"
        DictationEffect.CancelListening -> "CancelListening"
        DictationEffect.HoldImeVisible -> "HoldImeVisible"
        DictationEffect.ReleaseImeVisible -> "ReleaseImeVisible"
        DictationEffect.AcquireAudioFocus -> "AcquireAudioFocus"
        DictationEffect.ReleaseAudioFocus -> "ReleaseAudioFocus"
        DictationEffect.PlayStartCue -> "PlayStartCue"
        DictationEffect.PlayStopCue -> "PlayStopCue"
        is DictationEffect.ShowMessage -> "ShowMessage:${effect.message.name}"
        is DictationEffect.LogMessage -> "LogMessage:${effect.message.name}"
    }

    /**
     * A per-process key, so the hash says "same text as that other line" and nothing else. The
     * hash is SHA-256, not `String.hashCode`: a salted `hashCode` is linear in the salt, so one
     * known utterance would reveal the salt's share of every other hash of the same length, and a
     * short utterance's plain `hashCode` can be looked up.
     */
    private val salt: ByteArray = java.util.UUID.randomUUID().toString().toByteArray()

    private fun describe(text: String?): String {
        if (text == null) return "null"
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hash = digest.digest(text.trim().lowercase().toByteArray())
        val h = (0 until 4).joinToString("") { "%02x".format(hash[it]) }
        return "len=${text.length} h=$h"
    }
}
