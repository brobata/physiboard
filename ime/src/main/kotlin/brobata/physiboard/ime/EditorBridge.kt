package brobata.physiboard.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.EnterIntent
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
 * Reads the unified window text-input.md SS19 calls for, plus, only when [wholeDocument] is set,
 * the whole document via extracted text for the selection/word-motion primitives (SS19: "drop"
 * the 1000-character fallback reads; an app that refuses the extracted-text request simply gets
 * no selection helpers, spec SS2). Extracting the document is an O(document) IPC, so
 * [KeyboardPipeline.needsWholeDocument] decides per stroke whether it is worth paying for.
 * [fallbackCursorAbsolute] is the caller's best knowledge of the cursor (the editor's last
 * selection report) for when the document is not read.
 */
internal fun InputConnection.readEditorState(nowMs: Long, wholeDocument: Boolean, fallbackCursorAbsolute: Int): EditorReadout {
    val before = runCatching { getTextBeforeCursor(TEXT_BEFORE_CURSOR_WINDOW, 0)?.toString() }.getOrNull()
    val extracted = if (wholeDocument) {
        runCatching { getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 0 }, 0) }.getOrNull()
    } else {
        null
    }

    val fullText = extracted?.text?.let { charSequence ->
        val text = charSequence.toString()
        val start = extracted.selectionStart.coerceIn(0, text.length)
        val end = extracted.selectionEnd.coerceIn(0, text.length)
        TextWindow(text, start, end)
    }
    val documentStartOffset = extracted?.startOffset ?: 0
    val cursorAbsolute = if (extracted != null) documentStartOffset + extracted.selectionStart else fallbackCursorAbsolute.coerceAtLeast(0)

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
                    // spec: Composition.replaceVariation KDoc, "If the app refuses to set the
                    // composing region, nothing is changed": that refusal can only be observed
                    // here, so the ops that assumed it worked (the replacement text, the restoring
                    // FinishComposing/SetSelection) must not run either.
                    if (!setComposingRegion(start, start + op.length)) return
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
 * Performs the real `InputConnection` call an [EnterIntent] `:core:text` handed back describes,
 * and answers whether it was delivered. spec: per-app-behavior.md SS3.4: this return value is
 * exactly the hard-won fact the whole feature is built around, "Android reports only that the
 * connection was alive, never whether the app acted on it" -- [InputConnection.performEditorAction]
 * and [InputConnection.sendKeyEvent] both only ever report that. [EnterIntent.Decline] and
 * [EnterIntent.InsertNewline] never reach here: `:core:text` settles both itself (see
 * [brobata.physiboard.core.text.TextInputResult.enterDelivery]'s own KDoc), so this only performs
 * the four cases SS3.4 calls "delivery mechanisms" plus swallow.
 */
internal fun InputConnection.performEnterDelivery(intent: EnterIntent, nowMs: Long): Boolean = when (intent) {
    is EnterIntent.RequestEditorAction -> performEditorAction(intent.actionId)
    EnterIntent.SendPlainEnter -> sendEnterKeyEvent(nowMs, metaState = 0)
    EnterIntent.SendCtrlEnter -> sendEnterKeyEvent(nowMs, metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
    // spec SS3.4 "Unsupported send": "Reported as handled" unconditionally; nothing is actually
    // sent, so there is no real delivery outcome to ask the connection about.
    is EnterIntent.Swallow -> true
    EnterIntent.Decline, EnterIntent.InsertNewline -> true
}

/** spec: per-app-behavior.md SS3.4 "Plain Enter"/"Ctrl+Enter": keycode 66, key down then key up, [metaState] 0 or META_CTRL_ON|META_CTRL_LEFT_ON. */
private fun InputConnection.sendEnterKeyEvent(nowMs: Long, metaState: Int): Boolean {
    val down = sendKeyEvent(KeyEvent(nowMs, nowMs, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, metaState))
    val up = sendKeyEvent(KeyEvent(nowMs, nowMs, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, metaState))
    return down && up
}

/**
 * Whether a delivered [EnterIntent] should clear the Ctrl state (latch, one-shot, nav-mode latch).
 * spec: SS3.4: an editor-action send clears it only when this particular send was Ctrl-triggered;
 * Plain Enter and Ctrl+Enter always clear it once delivered ("always, not only for Ctrl-triggered
 * sends"); a swallow clears it immediately, not conditioned on any delivery outcome ("if any Ctrl
 * state was active it is cleared"), which [performEnterDelivery] already always reports delivered
 * for, so gating on [delivered] here still matches SS3.4 exactly for every case.
 */
internal fun EnterIntent.clearsCtrlState(delivered: Boolean): Boolean = delivered && when (this) {
    is EnterIntent.RequestEditorAction -> clearCtrlIfDelivered
    EnterIntent.SendPlainEnter, EnterIntent.SendCtrlEnter -> true
    is EnterIntent.Swallow -> clearCtrlNow
    EnterIntent.Decline, EnterIntent.InsertNewline -> false
}

/**
 * Classifies the focused field from Android's own [EditorInfo] into the one value `:core:text`
 * ever sees. spec: text-input.md SS3.
 *
 * [profile] supplies the two facts that [EditorInfo] alone cannot answer, closing the SPEC GAPs
 * this function used to record: [AppProfile.exactTypingEnabled] escalates an otherwise-unrestricted
 * text field to [FieldKind.RAW_MODE_APP] (per-app-behavior.md SS4.1, "every field that has no
 * field-type restriction of its own"; SS4.2, "Raw mode is the reason only when none of those
 * [variations] match" -- so it never overrides a variation already classified above), and
 * [AppProfile.unclassifiedFieldsAreEditable] turns an unclassified field (`TYPE_NULL`, the only
 * value `TYPE_MASK_CLASS` leaves once TEXT, NUMBER, PHONE and DATETIME are accounted for) into a
 * [FieldKind.NORMAL] field with [FieldContext.isEditableButNotReallyEditable] set, matching
 * text-input.md SS3's "some custom views" case, instead of the blanket [FieldKind.NOT_EDITABLE]
 * every such field got before a profile could say otherwise (see [AppProfile]'s own KDoc for why
 * this remains a caller-supplied flag rather than a heuristic).
 */
internal fun classifyField(info: EditorInfo?, profile: AppProfile = AppProfile.default(null)): FieldContext {
    if (info == null) return FieldContext(FieldKind.NOT_EDITABLE)

    val inputType = info.inputType
    val fieldClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION

    var isEditableButNotReallyEditable = false
    val kind = when (fieldClass) {
        InputType.TYPE_CLASS_TEXT -> when (variation) {
            InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            -> FieldKind.PASSWORD
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> FieldKind.EMAIL
            InputType.TYPE_TEXT_VARIATION_FILTER -> FieldKind.FILTER
            else -> if (profile.exactTypingEnabled) FieldKind.RAW_MODE_APP else FieldKind.NORMAL
        }
        InputType.TYPE_CLASS_NUMBER ->
            if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) FieldKind.PASSWORD else FieldKind.NUMBER_OR_PHONE
        InputType.TYPE_CLASS_PHONE -> FieldKind.NUMBER_OR_PHONE
        InputType.TYPE_CLASS_DATETIME -> FieldKind.DATE_TIME
        else -> if (profile.unclassifiedFieldsAreEditable) {
            isEditableButNotReallyEditable = true
            FieldKind.NORMAL
        } else {
            FieldKind.NOT_EDITABLE
        }
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
        isEditableButNotReallyEditable = isEditableButNotReallyEditable,
        // Read before the keyboard sets the flag itself (text-input.md SS3 has the keyboard set
        // it on every editable field it starts, and never read its own).
        appDisablesSuggestions = inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0,
    )
}

/**
 * spec: per-app-behavior.md SS3.9: "if the field's imeOptions has the 'no Enter action' flag,
 * none; else the field's explicit actionId if non-zero, else the action bits of imeOptions; the
 * result counts only if it is one of Go, Search, Send, Next, Done, Previous. Unspecified and None
 * give 'none'." The no-Enter-action flag is checked first and wins outright (T28: a field with
 * both that flag and Search bits set resolves to none, not Search); a non-empty [EditorInfo.actionLabel]
 * (a custom action label, text-input.md's own broader vocabulary, not one SS3.9 names) is checked
 * next, before the actionId/options precedence, since a field that bothers to set a label is
 * declaring an action of its own even when [EditorInfo.actionId] is left at 0.
 */
private fun imeActionOf(info: EditorInfo): ImeAction {
    if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return ImeAction.NONE
    if (!info.actionLabel.isNullOrEmpty()) return ImeAction.CUSTOM
    val resolvedActionId = if (info.actionId != 0) info.actionId else (info.imeOptions and EditorInfo.IME_MASK_ACTION)
    return when (resolvedActionId) {
        EditorInfo.IME_ACTION_GO -> ImeAction.GO
        EditorInfo.IME_ACTION_SEARCH -> ImeAction.SEARCH
        EditorInfo.IME_ACTION_SEND -> ImeAction.SEND
        EditorInfo.IME_ACTION_NEXT -> ImeAction.NEXT
        EditorInfo.IME_ACTION_DONE -> ImeAction.DONE
        EditorInfo.IME_ACTION_PREVIOUS -> ImeAction.PREVIOUS
        else -> ImeAction.NONE
    }
}
