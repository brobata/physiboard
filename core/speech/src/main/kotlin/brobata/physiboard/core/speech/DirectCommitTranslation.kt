package brobata.physiboard.core.speech

/**
 * Rewrites dictation's text operations for a field that cannot be trusted with a composing
 * region.
 *
 * Dictation stages its words as composing text, replacing them as the recognizer changes its
 * mind, and turns them into ordinary text when the utterance finishes. Some editors draw that
 * staged text and never adopt it: a web terminal on the maintainer's Titan (2026-09-26) showed
 * the dictated sentence inverted at the caret and committed none of it, so a whole spoken
 * sentence was lost. The plan's "editor is not a reliable narrator" answers exactly this: where
 * a composing region is unsafe "the keyboard commits directly and loses only the underline".
 *
 * So this translates the same decisions into commits: each staged text replaces the previous one
 * by deleting exactly what was written last, and finishing an utterance becomes nothing at all,
 * because the words are already in the field. [written] carries how many characters the last
 * translation put there and must be handed back on the next call.
 */
data class DirectCommitState(val written: Int = 0) {
    init { require(written >= 0) { "written cannot be negative: $written" } }
}

/** The ops to apply, and the state to carry into the next translation. */
data class DirectCommitTranslation(val ops: List<DictationTextOp>, val state: DirectCommitState)

object DirectCommit {
    /**
     * [ops] as they would be applied to an editor that refuses a composing region. A staged text
     * becomes a delete of whatever the last one left plus a commit of the new one; an empty
     * staged text, which is how the engine clears an abandoned utterance, becomes the delete
     * alone; finishing becomes nothing, and an outright commit passes through and is not counted,
     * since the engine only commits text it will never revise.
     */
    fun translate(ops: List<DictationTextOp>, state: DirectCommitState): DirectCommitTranslation {
        var written = state.written
        val out = mutableListOf<DictationTextOp>()
        for (op in ops) {
            when (op) {
                is DictationTextOp.SetComposingText -> {
                    if (written > 0) out += DictationTextOp.DeleteBeforeCursor(written)
                    if (op.text.isNotEmpty()) out += DictationTextOp.CommitText(op.text)
                    written = op.text.length
                }
                DictationTextOp.FinishComposing -> written = 0
                is DictationTextOp.CommitText -> {
                    out += op
                    written = 0
                }
                // Already translated; the engine itself never produces one.
                is DictationTextOp.DeleteBeforeCursor -> out += op
            }
        }
        return DirectCommitTranslation(out, DirectCommitState(written))
    }
}
