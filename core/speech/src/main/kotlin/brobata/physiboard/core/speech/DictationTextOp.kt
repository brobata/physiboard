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

    /** Ends the current composing region, turning it into ordinary text without changing it. */
    object FinishComposing : DictationTextOp()

    /** Deletes [count] characters before the cursor, so a direct commit can replace what the previous one left (see [DirectCommit]). */
    data class DeleteBeforeCursor(val count: Int) : DictationTextOp()

    /** Commits [text] at the cursor directly, for the rare case nothing was composing to replace. */
    data class CommitText(val text: String) : DictationTextOp()
}
