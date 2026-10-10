package brobata.physiboard.core.text

import brobata.physiboard.core.keys.EditEffect

/**
 * One primitive change for the `:ime` module to make on a real `InputConnection`, or a request
 * that a physical key be sent through unchanged. This module never touches an editor itself: every
 * function here takes the current state plus whatever text the caller read around the cursor, and
 * returns a `List<EditorOp>` as data, so every rule in the spec is a JVM-testable pure function.
 *
 * Positions carried by [SetSelection] and [SetComposingRegion] are offsets into whatever text
 * window the caller supplied to the function that produced them (see the KDoc of that function),
 * never an absolute document offset; this module has no notion of one. Translating a window
 * offset to a real document position is `:ime`'s job, since only it knows where that window
 * started.
 */
sealed class EditorOp {

    /** Commits [text] at the cursor, replacing any current selection. */
    data class CommitText(val text: String) : EditorOp()

    /**
     * Deletes [count] characters immediately before the cursor, then commits [text], as one batch
     * edit so the app never observes the intermediate state. spec: text-input.md SS9 (multi-tap
     * cycling "is done as one batch edit so apps such as Messages do not flicker") and the several
     * word/rule replacements in SS6 and autocorrect-suggestions.md SS7.2 that delete-then-insert.
     */
    data class ReplaceBeforeCursor(val count: Int, val text: String) : EditorOp()

    /**
     * Deletes [before] characters before the cursor and [after] characters after it, as one batch
     * edit. A plain Backspace or forward-delete is expressed with one of the two counts zero.
     */
    data class DeleteSurrounding(val before: Int, val after: Int) : EditorOp()

    /**
     * Ends whatever composing region the app may be holding, without changing the text. spec:
     * text-input.md SS4 item 3: run before every boundary, multi-tap replace, text expansion and
     * Telex rewrite, "so the reads that follow see committed text".
     */
    object FinishComposing : EditorOp()

    /**
     * Marks the [length] characters ending [charsBeforeCursor] characters before the cursor as the
     * composing region, so a following [CommitText] replaces exactly that span without touching
     * the rest of the field. spec: text-input.md SS4 item 1 (variations replacement): finish
     * composition, mark the target character's span, commit the replacement over it, finish
     * composition again, restore the selection.
     */
    data class SetComposingRegion(val charsBeforeCursor: Int, val length: Int) : EditorOp()

    /**
     * Sets the selection to [start]..[end] (a collapsed range when equal), in the coordinate space
     * of the [TextWindow] the caller supplied to whichever selection or cursor function produced
     * this op.
     */
    data class SetSelection(val start: Int, val end: Int) : EditorOp()

    /**
     * A last resort: sends a Space key down/up pair to the app, for when a committed space did not
     * verifiably land (some apps intercept `commitText` for a lone space). spec: text-input.md
     * SS6.1 step 5; autocorrect-suggestions.md SS7.3.
     */
    object SendSpaceKeyFallback : EditorOp()

    /** Fires the keyboard's correction haptic: every autocorrect commit and strip tap does this (keys-and-modifiers.md SS13.5, `CORRECTION`). */
    object Haptic : EditorOp()

    /** Fires the haptic for a correction Backspace just undid, distinct from [Haptic] (keys-and-modifiers.md SS13.5, `CORRECTION_UNDONE`). */
    object HapticUndo : EditorOp()

    /**
     * Deliberately does nothing to the field: the physical key (or its software equivalent) should
     * be delivered to the app or the system exactly as it arrived. spec: text-input.md SS6.2 ("the
     * key falls through to the system, which delivers a normal Space key event to the app") and
     * SS8 (a plain Backspace with nothing special pending). Distinct from an empty op list, which
     * means "nothing needs to change" without saying whether the key itself should still reach
     * the app.
     */
    object PassThroughKey : EditorOp()

    /**
     * Sends [effect]'s keycode to the app as a real key down/up pair, for a Ctrl mapping or nav
     * mode keycode with no text-pipeline meaning of its own (a DPAD move, Tab, Escape, forward
     * delete, or a page/line jump). spec: keys-and-modifiers.md SS7.3, SS12.2; trackpad-caret-nav.md
     * SS5.5. [withShift] is "Shift meta is added when Shift is active" for the eight
     * selection-aware navigation keys; [withCtrl] is [EditEffect.PAGE_START]/[EditEffect.PAGE_END]'s
     * own "Ctrl+Home / Ctrl+End", distinct from [EditEffect.LINE_HOME]/[EditEffect.LINE_END]'s plain
     * Home/End. [withAlt] is Alt+Backspace's line delete in a Terminal mode app, where the app
     * gets the real Alt+Backspace key ([EditEffect.DELETE_TO_LINE_START] names the Backspace key).
     */
    data class SendKey(val effect: EditEffect, val withShift: Boolean = false, val withCtrl: Boolean = false, val withAlt: Boolean = false) : EditorOp()

    /** Performs the editor's own copy/paste/cut/undo, the same action a long-press toolbar offers. spec: keys-and-modifiers.md SS7.3. */
    data class PerformEditorAction(val effect: EditEffect) : EditorOp()

    /** Dispatches a media key through the audio service rather than the text editor. spec: keys-and-modifiers.md SS7.3. */
    data class DispatchMediaKey(val effect: EditEffect) : EditorOp()
}

/**
 * A snapshot of text the caller read around the cursor, for the one function that needs to reason
 * over a selection or move the cursor by more than a few characters (word motions, select-all,
 * expand-selection). [text] is whatever window the caller read (ideally the whole document, per
 * the SS19 "unify" decision in text-input.md, but a fallback window works identically);
 * [cursorOrSelectionStart] and [selectionEnd] are offsets into that same [text], with
 * [selectionEnd] equal to [cursorOrSelectionStart] when nothing is selected. A caller that could
 * not read the field at all should not call the function rather than fabricate an empty window,
 * since "no text" and "empty field" are different facts (spec: text-input.md SS2, "no word-wise
 * moves" when the read fails).
 */
data class TextWindow(
    val text: String,
    val cursorOrSelectionStart: Int,
    val selectionEnd: Int = cursorOrSelectionStart,
) {
    init {
        require(cursorOrSelectionStart in 0..text.length) { "cursorOrSelectionStart out of range" }
        require(selectionEnd in 0..text.length) { "selectionEnd out of range" }
    }

    val hasSelection: Boolean get() = selectionEnd != cursorOrSelectionStart
    val selectionLow: Int get() = minOf(cursorOrSelectionStart, selectionEnd)
    val selectionHigh: Int get() = maxOf(cursorOrSelectionStart, selectionEnd)
}
