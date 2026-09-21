package brobata.physiboard.core.text

/** Left or right, for every selection and cursor primitive below. */
enum class MoveDirection { LEFT, RIGHT }

/**
 * The remembered "which end is the anchor" for an in-progress selection built by repeated
 * expand-selection presses. spec: text-input.md SS10 ("The anchor of a selection is remembered
 * while the selection matches what was last set; reversing direction shrinks the selection back
 * toward the anchor... If the app changes the selection itself the remembered anchor is dropped").
 * [anchor] and [movingEdge] are unordered (either may be the smaller offset); a caller never needs
 * to sort them, since [SelectionExpansion] always does before producing an [EditorOp.SetSelection].
 */
data class SelectionAnchorState(val anchor: Int, val movingEdge: Int)

/**
 * Word and character selection expansion (the Ctrl-layer `expand_selection_*` mappings) and the
 * anchor-tracking rules that go with them. spec: text-input.md SS10.
 */
object SelectionExpansion {

    data class Result(val op: EditorOp.SetSelection, val newState: SelectionAnchorState?)

    /** Character-wise expand-left/right (W/R): moves the moving edge one character, clamped to the text bounds. */
    fun expandCharacter(state: SelectionAnchorState?, window: TextWindow, direction: MoveDirection): Result {
        val current = reconcile(state, window, direction)
        val newMovingEdge = when (direction) {
            MoveDirection.LEFT -> (current.movingEdge - 1).coerceAtLeast(0)
            MoveDirection.RIGHT -> (current.movingEdge + 1).coerceAtMost(window.text.length)
        }
        return buildResult(current.anchor, newMovingEdge)
    }

    /**
     * Word-wise expand-left/right (U/I): moves the moving edge to the previous word start (left)
     * or the next word end (right). Requires the caller to have read the whole document (or at
     * least everything the expansion could reach); without it, word-wise expansion does nothing
     * (spec: SS10), which this function cannot detect on its own, so the caller should simply not
     * call it when only a fallback window was read.
     */
    fun expandWord(state: SelectionAnchorState?, window: TextWindow, direction: MoveDirection): Result {
        val current = reconcile(state, window, direction)
        val newMovingEdge = when (direction) {
            MoveDirection.LEFT -> WordMotion.previousWordStart(window.text, current.movingEdge)
            MoveDirection.RIGHT -> WordMotion.nextWordEnd(window.text, current.movingEdge)
        }
        return buildResult(current.anchor, newMovingEdge)
    }

    private fun reconcile(remembered: SelectionAnchorState?, window: TextWindow, direction: MoveDirection): SelectionAnchorState {
        val low = window.selectionLow
        val high = window.selectionHigh
        if (remembered != null &&
            minOf(remembered.anchor, remembered.movingEdge) == low &&
            maxOf(remembered.anchor, remembered.movingEdge) == high
        ) {
            return remembered
        }

        // The remembered anchor was dropped (no prior state, or the app changed the selection):
        // a fresh expansion anchors at the cursor, or, over an existing selection, at the far edge
        // in the direction of travel.
        return when {
            !window.hasSelection -> SelectionAnchorState(anchor = window.cursorOrSelectionStart, movingEdge = window.cursorOrSelectionStart)
            direction == MoveDirection.LEFT -> SelectionAnchorState(anchor = high, movingEdge = low)
            else -> SelectionAnchorState(anchor = low, movingEdge = high)
        }
    }

    private fun buildResult(anchor: Int, movingEdge: Int): Result =
        if (movingEdge == anchor) {
            Result(EditorOp.SetSelection(anchor, anchor), newState = null)
        } else {
            Result(EditorOp.SetSelection(minOf(anchor, movingEdge), maxOf(anchor, movingEdge)), SelectionAnchorState(anchor, movingEdge))
        }
}

/**
 * Word motions (N/M, `move_word_left` / `move_word_right`) and the word-boundary primitives they
 * and [SelectionExpansion] share. Word boundaries here are whitespace only, unlike the fourteen-
 * character boundary punctuation set used for composition tracking. spec: text-input.md SS10.
 */
object WordMotion {

    /** Collapses (per "moving the cursor left with a selection collapses to the selection start") and moves to the previous word start. */
    fun moveLeft(window: TextWindow): EditorOp.SetSelection {
        val from = if (window.hasSelection) window.selectionLow else window.cursorOrSelectionStart
        val newPos = previousWordStart(window.text, from)
        return EditorOp.SetSelection(newPos, newPos)
    }

    /** Collapses to the selection end and moves to the next word start. */
    fun moveRight(window: TextWindow): EditorOp.SetSelection {
        val from = if (window.hasSelection) window.selectionHigh else window.cursorOrSelectionStart
        val newPos = nextWordStart(window.text, from)
        return EditorOp.SetSelection(newPos, newPos)
    }

    /** Skips trailing whitespace backward from [pos], then the word before that. */
    fun previousWordStart(text: String, pos: Int): Int {
        var i = pos
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return i
    }

    /** Skips the word forward from [pos], then trailing whitespace after it. */
    fun nextWordStart(text: String, pos: Int): Int {
        var i = pos
        while (i < text.length && !text[i].isWhitespace()) i++
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    /** Skips whitespace forward from [pos], then the word after it. */
    fun nextWordEnd(text: String, pos: Int): Int {
        var i = pos
        while (i < text.length && text[i].isWhitespace()) i++
        while (i < text.length && !text[i].isWhitespace()) i++
        return i
    }
}

/** `select_all`: selects the whole document. spec: text-input.md SS10. */
object SelectAll {
    fun apply(text: String): EditorOp.SetSelection = EditorOp.SetSelection(0, text.length)
}
