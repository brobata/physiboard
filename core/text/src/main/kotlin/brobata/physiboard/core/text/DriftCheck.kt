package brobata.physiboard.core.text

/**
 * Whether the editor's own account of the text before the cursor still agrees with the word this
 * pipeline believes it just committed ([CurrentWordTracker], "the keyboard's own record"). spec:
 * rebuild-from-scratch.md "The editor is not a reliable narrator" point 1: "Never depend on
 * reading back what you just wrote ... An editor read is a hint used to detect drift, not the
 * source of truth for the keyboard's own recent output." A caller that needs more context than
 * the tracked word alone (the boundary engine's hard-boundary scan) uses this to decide whether
 * the extra characters the editor reports ahead of that word are safe to use at all, rather than
 * trusting a read that may already have stopped matching what is actually on screen.
 */
sealed class DriftCheck {
    /**
     * The editor's window ends with the word the keyboard itself tracked; [contextBeforeWord] is
     * whatever the editor reports further back, safe to use for a rule that needs it (that part was
     * never something this pipeline wrote, so there is nothing of "its own output" to read back).
     */
    data class Agreed(val contextBeforeWord: String) : DriftCheck()

    /** No read was available to check against: trust already withheld it, or the field refused it. */
    object Unavailable : DriftCheck()

    /**
     * The editor's window does not end with the word the keyboard believes it just committed: the
     * two have drifted apart (the dictation and deferred-space-capital bugs the plan cites are both
     * this same mistake, made without ever checking). The caller must not attempt a correction
     * against this window; it should also resync its own record from the editor's window instead of
     * carrying the disagreement forward.
     */
    object Disagreed : DriftCheck()

    companion object {
        /**
         * [trackedWord] is the keyboard's own record ([CurrentWordTracker.word]); [editorWindow] is
         * the corresponding read, already limited to whatever [EditorTrust] allows context rules to
         * see (null when trust withheld it or the field returned nothing). The comparison folds
         * apostrophe variants on both sides ([WordChars.straightenAll]) since [CurrentWordTracker]
         * folds them in its own record while a real commit types back exactly the key that was
         * pressed; that intentional difference must never look like drift.
         */
        fun evaluate(trackedWord: String, editorWindow: String?): DriftCheck {
            if (editorWindow == null) return Unavailable
            if (trackedWord.isEmpty()) return Agreed(editorWindow)
            val folded = WordChars.straightenAll(editorWindow)
            if (!folded.endsWith(trackedWord)) return Disagreed
            return Agreed(editorWindow.substring(0, editorWindow.length - trackedWord.length))
        }
    }
}
