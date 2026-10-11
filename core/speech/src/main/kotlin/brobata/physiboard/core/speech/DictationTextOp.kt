package brobata.physiboard.core.speech

/**
 * One primitive change for `:ime` to make on a real `InputConnection`. Deliberately a small,
 * self-contained vocabulary rather than a dependency on `:core:text`'s own `EditorOp`: dictation's
 * one extra need, composing text that keeps replacing itself in place while the engine is still
 * listening, has no counterpart in the ordinary typing pipeline, and giving this module its own
 * three primitives keeps it free of `:core:text`'s dictionaries and autocorrect machinery. `:ime`
 * applies these the same way it already applies `EditorOp` (a batch edit, in order); see
 * `SpeechEditorBridge` in that module. spec: dictation.md SS7.1, SS7.3.
 */
sealed class DictationTextOp {
    /**
     * Marks [text] as the composing region with the cursor placed after it, replacing whatever was
     * composing before. spec SS7.1: "Composing text replaces the previous composing region
     * whatever else is asked ... The cursor placement matters: placed at the start of the region, a
     * session that ends without a final would leave the cursor in front of the dictated words"
     * (D3; the 2.0.7 fix this type keeps in place by construction, since there is no case here that
     * places the cursor anywhere but after the text).
     */
    data class SetComposingText(val text: String) : DictationTextOp()

    /**
     * The utterance is over: what it settled on becomes ordinary text. On an editor that holds a
     * composing region this ends the region without changing it; [DirectCommit] reads it as
     * "write what was staged, once".
     */
    object FinishComposing : DictationTextOp()

    /**
     * Lets go of the composing region and writes nothing: the words already on screen stay as the
     * user's plain text, wherever the user has put the cursor (spec SS7.4). Applied like
     * [FinishComposing] on an editor that holds a region, but it is never a commit: in a
     * direct-commit field ([DirectCommit]) the staged words were never shown and the user has
     * just edited the field (an Enter in a shell), so writing them now would type them after it.
     */
    object ReleaseComposing : DictationTextOp()

    /** Deletes [count] characters before the cursor, so a direct commit can replace what the previous one left (see [DirectCommit]). */
    data class DeleteBeforeCursor(val count: Int) : DictationTextOp()

    /** Commits [text] at the cursor directly, for the rare case nothing was composing to replace. */
    data class CommitText(val text: String) : DictationTextOp()
}
