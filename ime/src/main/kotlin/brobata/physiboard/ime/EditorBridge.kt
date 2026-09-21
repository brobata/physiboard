package brobata.physiboard.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldCapFlags
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.ImeAction
import brobata.physiboard.core.text.TextWindow

/** spec: text-input.md SS2, "unify": one 240-before read per keystroke replaces the eleven different window sizes `:core:text` used to ask for individually. */
private const val TEXT_BEFORE_CURSOR_WINDOW = 240

/**
 * What one read of the real editor produced: the [EditorSnapshot] `:core:text` understands, plus
 * the two document-relative facts only `:ime` can translate an `EditorOp`'s window-relative
 * offsets back into ([EditorOp] KDoc: "translating a window offset to a real document position is
 * `:ime`'s job, since only it knows where that window started").
 */
internal data class EditorReadout(
    val snapshot: EditorSnapshot,
    /** Absolute document offset the [EditorSnapshot.fullText] window starts at. */
    val documentStartOffset: Int,
    /** Absolute document offset of the cursor (or selection start) at read time. */
    val cursorAbsolute: Int,
)

/**
 * Reads the unified window text-input.md SS19 calls for, plus the whole document via extracted
 * text for the selection/word-motion primitives (SS19: "drop" the 1000-character fallback reads;
 * an app that refuses the extracted-text request simply gets no selection helpers, spec SS2).
 */
internal fun InputConnection.readEditorState(nowMs: Long): EditorReadout {
    val before = runCatching { getTextBeforeCursor(TEXT_BEFORE_CURSOR_WINDOW, 0)?.toString() }.getOrNull()
    val extracted = runCatching { getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 0 }, 0) }.getOrNull()

    val fullText = extracted?.text?.let { charSequence ->
        val text = charSequence.toString()
        val start = extracted.selectionStart.coerceIn(0, text.length)
        val end = extracted.selectionEnd.coerceIn(0, text.length)
        TextWindow(text, start, end)
    }
    val documentStartOffset = extracted?.startOffset ?: 0
    val cursorAbsolute = if (extracted != null) documentStartOffset + extracted.selectionStart else 0

    return EditorReadout(
        snapshot = EditorSnapshot(textBeforeCursor = before, fullText = fullText, nowMs = nowMs),
        documentStartOffset = documentStartOffset,
        cursorAbsolute = cursorAbsolute,
    )
}

/**
 * Applies every [EditorOp] `:core:text` returned, as one batch edit. [windowStartOffset] and
 * [cursorAbsolute] are the two facts from the [EditorReadout] the ops were computed against,
 * used to translate [EditorOp.SetSelection] and [EditorOp.SetComposingRegion]'s window-relative
 * offsets into real document positions.
 */
internal fun InputConnection.applyEditorOps(
    ops: List<EditorOp>,
    windowStartOffset: Int,
    cursorAbsolute: Int,
    sendSpaceKeyFallback: () -> Unit,
    haptic: () -> Unit,
) {
    if (ops.isEmpty()) return
    beginBatchEdit()
    try {
        for (op in ops) {
            when (op) {
                is EditorOp.CommitText -> commitText(op.text, 1)
                is EditorOp.ReplaceBeforeCursor -> {
                    deleteSurroundingText(op.count, 0)
                    commitText(op.text, 1)
                }
                is EditorOp.DeleteSurrounding -> deleteSurroundingText(op.before, op.after)
                EditorOp.FinishComposing -> finishComposingText()
                is EditorOp.SetComposingRegion -> {
                    val start = (cursorAbsolute - op.charsBeforeCursor).coerceAtLeast(0)
                    setComposingRegion(start, start + op.length)
                }
                is EditorOp.SetSelection -> setSelection(windowStartOffset + op.start, windowStartOffset + op.end)
                EditorOp.SendSpaceKeyFallback -> sendSpaceKeyFallback()
                EditorOp.Haptic -> haptic()
                EditorOp.PassThroughKey -> Unit // KeyboardPipeline never lets this reach here alone; see toPipelineResult.
            }
        }
    } finally {
        endBatchEdit()
    }
}

/** spec: text-input.md EditorOp.SendSpaceKeyFallback KDoc: "sends a Space key down/up pair". */
internal fun InputConnection.sendSpaceKeyFallback(nowMs: Long) {
    sendKeyEvent(KeyEvent(nowMs, nowMs, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 0))
    sendKeyEvent(KeyEvent(nowMs, nowMs, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE, 0))
}

/**
 * Classifies the focused field from Android's own [EditorInfo] into the one value `:core:text`
 * ever sees. spec: text-input.md SS3.
 *
 * SPEC GAP: raw-mode apps ([FieldKind.RAW_MODE_APP]) and "editable but not really editable"
 * fields ([FieldContext.isEditableButNotReallyEditable]) both depend on a per-app list or a
 * custom-view heuristic that lives in a settings/data module not yet built (rebuild-from-scratch
 * build order step 6); this function never produces either value. Every field classifies as one
 * of the other [FieldKind]s, which is the closest correct answer until that module exists rather
 * than a guess at its data.
 */
internal fun classifyField(info: EditorInfo?): FieldContext {
    if (info == null) return FieldContext(FieldKind.NOT_EDITABLE)

    val inputType = info.inputType
    val fieldClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION

    val kind = when (fieldClass) {
        InputType.TYPE_CLASS_TEXT -> when (variation) {
            InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            -> FieldKind.PASSWORD
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> FieldKind.EMAIL
            InputType.TYPE_TEXT_VARIATION_FILTER -> FieldKind.FILTER
            else -> FieldKind.NORMAL
        }
        InputType.TYPE_CLASS_NUMBER ->
            if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) FieldKind.PASSWORD else FieldKind.NUMBER_OR_PHONE
        InputType.TYPE_CLASS_PHONE -> FieldKind.NUMBER_OR_PHONE
        InputType.TYPE_CLASS_DATETIME -> FieldKind.DATE_TIME
        else -> FieldKind.NOT_EDITABLE
    }

    val capFlags = FieldCapFlags(
        capCharacters = inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0,
        capWords = inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0,
        capSentences = inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0,
    )

    return FieldContext(
        kind = kind,
        capFlags = capFlags,
        imeAction = imeActionOf(info),
        isMultiLine = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0,
    )
}

private fun imeActionOf(info: EditorInfo): ImeAction {
    if (!info.actionLabel.isNullOrEmpty()) return ImeAction.CUSTOM
    return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_GO -> ImeAction.GO
        EditorInfo.IME_ACTION_SEARCH -> ImeAction.SEARCH
        EditorInfo.IME_ACTION_SEND -> ImeAction.SEND
        EditorInfo.IME_ACTION_NEXT -> ImeAction.NEXT
        EditorInfo.IME_ACTION_DONE -> ImeAction.DONE
        EditorInfo.IME_ACTION_PREVIOUS -> ImeAction.PREVIOUS
        else -> ImeAction.NONE
    }
}
