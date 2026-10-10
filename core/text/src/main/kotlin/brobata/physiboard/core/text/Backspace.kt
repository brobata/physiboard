package brobata.physiboard.core.text

import brobata.physiboard.core.keys.AltBackspaceAction

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
            if (altActive && settings.altBackspace == AltBackspaceAction.DELETE_FORWARD) return Decision.DeleteForward
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

/**
 * Alt+Backspace with `alt_backspace_delete` = `line`: how many characters before the cursor to
 * delete so the cursor ends at the start of its line. spec: keys-and-modifiers.md SS7.7.
 *
 * - Text on the line before the cursor: all of it, back to the last line break (kept).
 * - The cursor already at a line start: the line break before it, so the line joins the one
 *   above (a Windows "\r\n" goes as one). Android's own text fields do the same when the line
 *   holds nothing to delete.
 * - No line break in [textBeforeCursor]: all of it. The caller reads a bounded window, so on a
 *   line longer than that window this deletes what was read and the next press deletes more.
 *   A window that begins in the middle of a surrogate pair keeps that half rather than
 *   leaving half an emoji behind.
 * - Empty: 0, nothing to delete on this side.
 *
 * A line break is never part of a longer grapheme (apart from "\r\n"), so stopping just after
 * one never splits an emoji or an accented letter.
 */
object DeleteToLineStart {
    fun countToDelete(textBeforeCursor: String): Int {
        if (textBeforeCursor.isEmpty()) return 0
        if (textBeforeCursor.last() == '\n') {
            return if (textBeforeCursor.length >= 2 && textBeforeCursor[textBeforeCursor.length - 2] == '\r') 2 else 1
        }
        val lineBreak = textBeforeCursor.lastIndexOf('\n')
        if (lineBreak >= 0) return textBeforeCursor.length - lineBreak - 1
        return if (textBeforeCursor[0].isLowSurrogate()) textBeforeCursor.length - 1 else textBeforeCursor.length
    }
}
