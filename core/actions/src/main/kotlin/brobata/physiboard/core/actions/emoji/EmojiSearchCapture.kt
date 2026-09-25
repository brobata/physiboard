package brobata.physiboard.core.actions.emoji

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId

/**
 * The search field's own text and selection, as the capture path edits it. spec: expansion-
 * clipboard-pickers-launcher.md SS4.5. [pendingSelectAll] is the "remembered as a pending
 * replacement range" of Ctrl+A: the next inserted text or Backspace replaces the whole field.
 */
data class SearchFieldState(
    val text: String = "",
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val pendingSelectAll: Boolean = false,
) {
    val hasSelection: Boolean get() = selectionEnd != selectionStart || pendingSelectAll
    val selectionLow: Int get() = if (pendingSelectAll) 0 else minOf(selectionStart, selectionEnd)
    val selectionHigh: Int get() = if (pendingSelectAll) text.length else maxOf(selectionStart, selectionEnd)

    companion object {
        val EMPTY = SearchFieldState()
    }
}

/** What one captured key did. spec SS4.5's table. */
sealed class CaptureResult {
    abstract val state: SearchFieldState

    /** The key (and its matching release) is consumed; the field changed or was left as is. */
    data class Consumed(override val state: SearchFieldState, val queryChanged: Boolean) : CaptureResult()

    /** The key was not captured and reaches the app (Alt or Meta combinations, Back, Sym, pure modifiers). */
    data class NotCaptured(override val state: SearchFieldState) : CaptureResult()

    /** A Ctrl combination other than A/C/X/V, or a control character: handed to the field as a key event by the caller. */
    data class HandToField(override val state: SearchFieldState) : CaptureResult()

    /** Ctrl+C or Ctrl+X: the caller copies [text] to the system clipboard; cut also cleared the selection. */
    data class CopyToClipboard(override val state: SearchFieldState, val text: String) : CaptureResult()

    /** Ctrl+V: the caller pastes whatever the system clipboard holds through [SearchCapture.insert]. */
    data class RequestPaste(override val state: SearchFieldState) : CaptureResult()
}

/**
 * The hardware-key capture of SS4.5 as a pure model: "hardware keys are typed into the search
 * field instead of the app while page 4 is open, capture is on, and the key is not Back, not Sym,
 * and not a pure modifier". The caller resolves the layout character ([layoutText]) and the
 * event's own character ([eventChar]) since only it has the layout and the event (T31, T32).
 */
object SearchCapture {

    fun onKeyDown(
        state: SearchFieldState,
        key: KeyId,
        ctrl: Boolean,
        altOrMeta: Boolean,
        layoutText: String?,
        eventChar: Char?,
    ): CaptureResult {
        if (altOrMeta) return CaptureResult.NotCaptured(state)
        if (key is KeyId.Modifier) return CaptureResult.NotCaptured(state)
        if (key == KeyId.Control(ControlKey.BACK)) return CaptureResult.NotCaptured(state)

        if (ctrl) {
            return when (key) {
                KeyId.Letter('A') -> CaptureResult.Consumed(state.copy(selectionStart = 0, selectionEnd = state.text.length, pendingSelectAll = true), queryChanged = false)
                KeyId.Letter('C') -> CaptureResult.CopyToClipboard(state, selectedText(state))
                KeyId.Letter('X') -> {
                    val selected = selectedText(state)
                    CaptureResult.CopyToClipboard(deleteSelection(state), selected)
                }
                KeyId.Letter('V') -> CaptureResult.RequestPaste(state)
                else -> CaptureResult.HandToField(state)
            }
        }

        return when (key) {
            KeyId.Control(ControlKey.SPACE) -> CaptureResult.Consumed(insert(state, " "), queryChanged = true)
            KeyId.Control(ControlKey.BACKSPACE) -> CaptureResult.Consumed(backspace(state), queryChanged = true)
            KeyId.Control(ControlKey.ENTER), KeyId.Control(ControlKey.DPAD_CENTER) -> CaptureResult.Consumed(state, queryChanged = false)
            KeyId.Control(ControlKey.DPAD_UP), KeyId.Control(ControlKey.PAGE_UP), KeyId.Control(ControlKey.MOVE_HOME) -> CaptureResult.Consumed(caretTo(state, 0), queryChanged = false)
            KeyId.Control(ControlKey.DPAD_DOWN), KeyId.Control(ControlKey.PAGE_DOWN), KeyId.Control(ControlKey.MOVE_END) -> CaptureResult.Consumed(caretTo(state, state.text.length), queryChanged = false)
            KeyId.Control(ControlKey.DPAD_LEFT) -> CaptureResult.Consumed(caretTo(state, (state.selectionLow - 1).coerceAtLeast(0)), queryChanged = false)
            KeyId.Control(ControlKey.DPAD_RIGHT) -> CaptureResult.Consumed(caretTo(state, (state.selectionHigh + 1).coerceAtMost(state.text.length)), queryChanged = false)
            else -> {
                val text = layoutText ?: eventChar?.takeUnless { it.isISOControl() || it == '\u0000' }?.toString()
                when {
                    text != null -> CaptureResult.Consumed(insert(state, text), queryChanged = true)
                    eventChar != null -> CaptureResult.HandToField(state)
                    else -> CaptureResult.Consumed(state, queryChanged = false)
                }
            }
        }
    }

    /** spec SS4.5: "Inserted text replaces a selection (or a pending select-all range) when there is one, otherwise appends at the end" (T34). */
    fun insert(state: SearchFieldState, text: String): SearchFieldState {
        if (state.hasSelection) {
            val low = state.selectionLow
            val high = state.selectionHigh
            val newText = state.text.substring(0, low) + text + state.text.substring(high)
            val caret = low + text.length
            return SearchFieldState(newText, caret, caret)
        }
        val newText = state.text + text
        return SearchFieldState(newText, newText.length, newText.length)
    }

    /** spec SS4.5: "Deletes the selection (or the pending select-all range) if any, else the last character; consumed even when empty" (T35). */
    private fun backspace(state: SearchFieldState): SearchFieldState {
        if (state.hasSelection) return deleteSelection(state)
        if (state.text.isEmpty()) return state
        val newText = state.text.dropLast(1)
        return SearchFieldState(newText, newText.length, newText.length)
    }

    private fun deleteSelection(state: SearchFieldState): SearchFieldState {
        if (!state.hasSelection) return state
        val low = state.selectionLow
        val newText = state.text.substring(0, low) + state.text.substring(state.selectionHigh)
        return SearchFieldState(newText, low, low)
    }

    private fun selectedText(state: SearchFieldState): String =
        if (state.hasSelection) state.text.substring(state.selectionLow, state.selectionHigh) else state.text

    private fun caretTo(state: SearchFieldState, index: Int): SearchFieldState =
        state.copy(selectionStart = index, selectionEnd = index, pendingSelectAll = false)
}
