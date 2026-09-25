package brobata.physiboard.ime

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.text.EditorOp

/**
 * Pure bookkeeping over the [EditorOp]s one keystroke applied, answering the two questions only
 * the Android glue has to ask and that a JVM test must still be able to check:
 *
 * 1. Did the field's text change at all? Dictation's "words the user deleted are never typed
 *    back" rule (the c440844 invariant) is driven by a real edit the keyboard made, not by every
 *    key event: a modifier press, a key-up or the Fn repeats that keep arriving while the user is
 *    still holding Fn after the dictation trigger edit nothing, and must not invalidate the
 *    utterance the user is in the middle of dictating.
 * 2. Where does our own edit leave the cursor? spec: text-input.md SS2, "every cursor change that
 *    is not the one-character forward step caused by its own last commit" is external. The editor
 *    reports our edit back through `onUpdateSelection`; knowing the position it should report lets
 *    [OwnEditExpectation] tell that report apart from a genuine cursor move.
 *
 * No `android.*` import: this is exactly the arithmetic a device cannot be trusted to exercise
 * every branch of, so it lives where JUnit can reach it.
 */
internal object AppliedEditAccounting {

    /** True when applying [ops] changes the text of the field (not merely its selection, composing state or a haptic). */
    fun changesText(ops: List<EditorOp>): Boolean = ops.any { op ->
        when (op) {
            is EditorOp.CommitText, is EditorOp.ReplaceBeforeCursor, is EditorOp.DeleteSurrounding, EditorOp.SendSpaceKeyFallback -> true
            is EditorOp.SetComposingRegion, is EditorOp.SetSelection, EditorOp.FinishComposing, EditorOp.Haptic, EditorOp.PassThroughKey -> false
        }
    }

    /**
     * Whether a key-down handed to the app unconsumed will make the app edit the field: a plain
     * Backspace or Delete (text-input.md SS8's fall-through), or a Ctrl combo the app treats as
     * cut, paste, undo or redo. Ctrl+A, Ctrl+C and Ctrl+arrows change nothing and are not listed.
     */
    fun appEditsWithPassThrough(key: KeyId, ctrlActive: Boolean): Boolean = when (key) {
        is KeyId.Control -> key.key == ControlKey.BACKSPACE || key.key == ControlKey.FORWARD_DELETE || key.key == ControlKey.SWIPE_TO_DELETE
        is KeyId.Letter -> ctrlActive && key.qwertyLetter.uppercaseChar() in "XVZY"
        is KeyId.Digit, is KeyId.Punctuation, is KeyId.Modifier -> false
    }

    /** True when a stroke's result edits the field one way or the other: ops this keyboard applies, or a key the app will edit with. */
    fun editsField(result: PipelineResult): Boolean = changesText(result.ops) || result.appMayEditField

    /** True when applying [ops] leaves the cursor somewhere the editor may report: any text change, or a selection op. */
    fun movesCursor(ops: List<EditorOp>): Boolean = changesText(ops) || ops.any { it is EditorOp.SetSelection }

    /**
     * The document offset the selection start should sit at once [ops] have been applied, given
     * the cursor was at [cursorAbsolute] and the ops' window-relative positions start at
     * [windowStartOffset]. Mirrors how `InputConnection` treats each op: a commit lands after its
     * text, replacing the composing region when one is set (or the selection otherwise); a delete
     * before the cursor pulls it back, never below zero; a selection op places it outright.
     */
    fun expectedCursorAfter(cursorAbsolute: Int, windowStartOffset: Int, ops: List<EditorOp>): Int {
        var cursor = cursorAbsolute.coerceAtLeast(0)
        var composingStart: Int? = null
        for (op in ops) {
            when (op) {
                is EditorOp.CommitText -> {
                    cursor = (composingStart ?: cursor) + op.text.length
                    composingStart = null
                }
                is EditorOp.ReplaceBeforeCursor -> cursor = (cursor - op.count).coerceAtLeast(0) + op.text.length
                is EditorOp.DeleteSurrounding -> cursor = (cursor - op.before).coerceAtLeast(0)
                is EditorOp.SetComposingRegion -> composingStart = (cursor - op.charsBeforeCursor).coerceAtLeast(0)
                EditorOp.FinishComposing -> composingStart = null
                is EditorOp.SetSelection -> cursor = windowStartOffset + op.start
                EditorOp.SendSpaceKeyFallback -> cursor += 1
                EditorOp.Haptic, EditorOp.PassThroughKey -> Unit
            }
        }
        return cursor
    }
}

/**
 * What the keyboard expects the editor to report after its own last edit: the selection start
 * that edit should produce, and a deadline past which the expectation is worthless. Replaces a
 * single unbounded "suppress the next selection update" flag, which an editor that never
 * reported the edit (asynchronous web and Compose fields) left armed to swallow the user's next
 * real tap, and which a batch reporting twice consumed on the first report so the second was
 * mistaken for a tap and wiped the state right after a correction.
 */
/**
 * [matched] is set once the app has reported the cursor where the edit put it. The expectation
 * is then kept, not dropped, until [expiresAtMs]: a web field on the Titan (2026-09-25) followed
 * every own-edit report with a second one moving the cursor to 0 and reading "", which, taken as
 * an external move, wiped the tracked word and re-armed the start-of-text capital on every
 * letter. Anything the app says inside the settle window after it already agreed is noise.
 */
internal data class OwnEditExpectation(val selStart: Int, val expiresAtMs: Long, val matched: Boolean = false) {

    enum class Verdict {
        /** The editor reported exactly the position our edit produces: the expectation is met. */
        OWN_EDIT,

        /** Not our position yet, but the edit is fresh: an intermediate report from the same batch. */
        STILL_SETTLING,

        /** The window has passed and the position is not ours: a genuine cursor move. */
        EXTERNAL,
    }

    fun classify(newSelStart: Int, nowMs: Long): Verdict = when {
        newSelStart == selStart -> Verdict.OWN_EDIT
        nowMs < expiresAtMs -> Verdict.STILL_SETTLING
        else -> Verdict.EXTERNAL
    }

    companion object {
        /** How long an unconfirmed own edit may still claim the editor's reports. Long enough for a slow field's callback, short enough that a real tap after typing is never eaten. */
        const val SETTLE_WINDOW_MS = 300L
    }
}
