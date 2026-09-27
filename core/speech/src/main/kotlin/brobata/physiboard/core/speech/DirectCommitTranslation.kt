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
 * So this holds the words back until the utterance is finished and then commits them once.
 * [DirectCommitState] carries the staged text between calls and must be handed back on the next.
 */
data class DirectCommitState(val pending: String = "")

/** The ops to apply, and the state to carry into the next translation. */
data class DirectCommitTranslation(val ops: List<DictationTextOp>, val state: DirectCommitState)

object DirectCommit {
    /**
     * [ops] rewritten for an editor that will not hold a staged region, where NOTHING is written
     * until the sentence is final.
     *
     * The obvious translation, replacing each staged text by deleting the one before it, is
     * wrong here and was shipped once: the maintainer dictated a sentence and it arrived seven
     * times over, each copy a word or two longer than the last (2026-09-27). A terminal consumes
     * the characters it is sent, so deleting them afterwards cannot work, and every partial the
     * recognizer produced simply piled up. These editors get no running preview at all; the
     * words appear when the utterance ends, which is the only behaviour that can be correct in
     * a field that cannot take anything back.
     *
     * [DirectCommitState.pending] carries the latest staged text between calls, because a
     * partial and the final that supersedes it arrive in separate dispatches.
     */
    fun translate(ops: List<DictationTextOp>, state: DirectCommitState): DirectCommitTranslation {
        var pending = state.pending
        val out = mutableListOf<DictationTextOp>()
        for (op in ops) {
            when (op) {
                // Remembered, never written: the next one supersedes it and this field cannot
                // take back what it has already been sent.
                is DictationTextOp.SetComposingText -> pending = op.text
                // The utterance is over, so what it settled on is worth writing, once.
                DictationTextOp.FinishComposing -> {
                    if (pending.isNotEmpty()) out += DictationTextOp.CommitText(pending)
                    pending = ""
                }
                is DictationTextOp.CommitText -> {
                    out += op
                    pending = ""
                }
                // Never produced by the engine, and nothing here asks for one any more.
                is DictationTextOp.DeleteBeforeCursor -> out += op
            }
        }
        return DirectCommitTranslation(out, DirectCommitState(pending))
    }
}
