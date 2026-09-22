package brobata.physiboard.core.text

/**
 * What is composing versus committed. spec: text-input.md SS4.
 *
 * Ordinary typing on the Titan never composes: every character is committed the moment its key
 * goes down, and the current word (see [CurrentWordTracker]) is tracked from committed characters,
 * never from a composing span. Composition exists only for the three narrow cases SS4 names:
 * variations replacement ([replaceVariation] below), dictation (out of this module's scope; see
 * the dictation spec), and finishing whatever composition an app may be holding of its own accord
 * before every boundary, multi-tap replace, text expansion or Telex rewrite ([finishBeforeBoundary]).
 * Vietnamese Telex itself is dropped for 3.0 (spec SS19), so no rewrite function for it exists here.
 */
object Composition {

    /**
     * Finishes whatever composition the app may be holding, so the reads that follow see
     * committed text. spec: SS4 item 3. A caller runs this before Space, Enter, boundary
     * punctuation, a multi-tap replace, or a text expansion; every one of those already commits
     * its own text right afterward, so this is always the first op in whatever list follows.
     */
    fun finishBeforeBoundary(): EditorOp = EditorOp.FinishComposing

    /**
     * Swaps an already-committed character for an accented variant (a long-press pick or a strip
     * pick), without disturbing anything else in the field. spec: SS4 item 1: finish composition,
     * mark the target character's span as composing, commit the replacement over it, finish
     * composition again, and restore the selection adjusted by the length change.
     *
     * [charsBeforeCursor] and [targetLength] locate the span to replace, relative to the cursor at
     * the moment the picker was shown (in the ordinary long-press case, one character immediately
     * before the cursor: `charsBeforeCursor = 1, targetLength = 1`). [selectionBeforeReplacement],
     * when the field held a selection when the picker was shown, is restored afterward shifted by
     * the length difference between [targetLength] and [replacement]; pass null when there was no
     * selection (the common case), and this returns no selection-restoring op at all.
     *
     * If the app refuses to set the composing region, none of this should be applied at all (spec:
     * "If the app refuses to set the composing region, nothing is changed"); that refusal can only
     * be observed by `:ime` actually calling the `InputConnection`, so it is on `:ime` to check the
     * result of [EditorOp.SetComposingRegion] and discard the rest of this list if it failed,
     * rather than something this pure function could ever detect on its own.
     *
     * [composingSafety], when [ComposingSafety.UNSAFE], drops the whole composing dance in favour
     * of a direct delete-and-commit (spec: rebuild-from-scratch.md "The editor is not a reliable
     * narrator" point 3, "the keyboard commits directly and loses only the underline"): the app is
     * still asked to finish whatever composition it may already be holding of its own accord, but
     * this function never asks it to start one. That only works when the span to replace is
     * anchored at the cursor ([charsBeforeCursor] equal to [targetLength], the ordinary long-press
     * shape); for a span further back, a direct commit has no way to reach it without a composing
     * region or an absolute document offset this module never has, so nothing is changed at all
     * rather than guess at one (same "nothing is changed" rule as an outright refusal above).
     */
    fun replaceVariation(
        charsBeforeCursor: Int,
        targetLength: Int,
        replacement: String,
        selectionBeforeReplacement: TextWindow? = null,
        composingSafety: ComposingSafety = ComposingSafety.SAFE,
    ): List<EditorOp> {
        if (composingSafety == ComposingSafety.UNSAFE) {
            return replaceVariationWithoutComposing(charsBeforeCursor, targetLength, replacement, selectionBeforeReplacement)
        }
        return buildList {
            add(EditorOp.FinishComposing)
            add(EditorOp.SetComposingRegion(charsBeforeCursor, targetLength))
            add(EditorOp.CommitText(replacement))
            add(EditorOp.FinishComposing)
            if (selectionBeforeReplacement != null) {
                val shift = replacement.length - targetLength
                add(EditorOp.SetSelection(selectionBeforeReplacement.cursorOrSelectionStart + shift, selectionBeforeReplacement.selectionEnd + shift))
            }
        }
    }

    private fun replaceVariationWithoutComposing(
        charsBeforeCursor: Int,
        targetLength: Int,
        replacement: String,
        selectionBeforeReplacement: TextWindow?,
    ): List<EditorOp> {
        if (charsBeforeCursor != targetLength) return emptyList()
        return buildList {
            add(EditorOp.FinishComposing)
            add(EditorOp.DeleteSurrounding(charsBeforeCursor, 0))
            add(EditorOp.CommitText(replacement))
            if (selectionBeforeReplacement != null) {
                val shift = replacement.length - targetLength
                add(EditorOp.SetSelection(selectionBeforeReplacement.cursorOrSelectionStart + shift, selectionBeforeReplacement.selectionEnd + shift))
            }
        }
    }
}
