package brobata.physiboard.core.text

/**
 * What Backspace should do, in the order text-input.md SS8 lists: forward-delete alternatives
 * first (only without a selection), then undo, then the ordinary fall-through. The caller is
 * responsible for step 1 regardless of the outcome (cancel the deferred-space debt and clear the
 * auto-space flag) and, on [Decision.FallThrough], for dropping the tracked word's last character.
 */
object Backspace {

    sealed class Decision {
        /** Deletes one character *after* the cursor instead of before it, and consumes the key. spec: SS8 steps 3, 4. */
        object DeleteForward : Decision()

        /** Restores the word a boundary replaced. spec: SS8 step 5/6; autocorrect-suggestions.md SS7.5. */
        data class Undo(val result: AutocorrectUndo.Result) : Decision()

        /** Nothing special: the key falls through to the system. spec: SS8 step 7. */
        object FallThrough : Decision()
    }

    /**
     * [charsBeforeCursor] only needs to be exact when it might be zero; any positive count is
     * enough for [BackspaceSettings.backspaceAtStartDeletesForward] to know it does not apply.
     * [undoTextBeforeCursor], when [autocorrectMemory] holds a pending replacement, should be that
     * replacement's length plus 2 characters (see [AutocorrectUndo.attempt]); pass null when it
     * was not read (for instance because there is nothing to undo).
     */
    fun decide(
        hasSelection: Boolean,
        shiftHeld: Boolean,
        altActive: Boolean,
        settings: BackspaceSettings,
        charsBeforeCursor: Int,
        autocorrectMemory: AutocorrectMemory,
        undoTextBeforeCursor: String?,
    ): Decision {
        if (!hasSelection) {
            if (shiftHeld && settings.shiftBackspaceDeletesForward) return Decision.DeleteForward
            if (altActive && settings.altBackspaceDeletesForward) return Decision.DeleteForward
            if (settings.backspaceAtStartDeletesForward && charsBeforeCursor == 0 && !shiftHeld && !altActive) {
                return Decision.DeleteForward
            }
        }
        if (undoTextBeforeCursor != null) {
            // spec: autocorrect-suggestions.md SS7.5 step 3, "the original word, lowercased, and
            // its apostrophe root if any, are added to the rejected set" (SS3.3's split, e.g.
            // `dell'amivo` rejected once must also reject `amivo`'s own correction path).
            val apostropheRoot = autocorrectMemory.lastReplacement?.original?.let { ApostropheSplit.split(it)?.root }
            val undo = AutocorrectUndo.attempt(autocorrectMemory, undoTextBeforeCursor, apostropheRoot)
            if (undo != null) return Decision.Undo(undo)
        }
        return Decision.FallThrough
    }
}

/**
 * Ctrl+Backspace without a selection: deletes the trailing whitespace before the cursor, then the
 * run of non-whitespace before that. spec: text-input.md SS8 ("the whitespace after it, then the
 * run of non-whitespace before that, within 100 characters").
 */
object DeleteWordBackward {
    /** [textBeforeCursor] up to 100 characters. Returns how many characters to delete before the cursor. */
    fun countToDelete(textBeforeCursor: String): Int {
        var i = textBeforeCursor.length
        while (i > 0 && textBeforeCursor[i - 1].isWhitespace()) i--
        while (i > 0 && !textBeforeCursor[i - 1].isWhitespace()) i--
        return textBeforeCursor.length - i
    }
}
