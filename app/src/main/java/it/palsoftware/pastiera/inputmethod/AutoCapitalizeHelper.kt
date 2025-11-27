package it.palsoftware.pastiera.inputmethod

import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import android.view.inputmethod.ExtractedTextRequest

/**
 * Central helper for all smart auto-capitalization rules.
 *
 * It never manipulates modifier state directly, but instead:
 * - asks the caller to enable Shift one-shot via callbacks, and
 * - tracks whether that Shift came from a "smart" rule so it can be cleared
 *   on selection / context changes without affecting manually pressed Shift.
 *
 * All auto-cap entry points (field start, after Enter, after punctuation,
 * selection changes, restarts) should be routed through this helper so
 * there is a single place that decides "should Shift one-shot be active?".
 */
object AutoCapitalizeHelper {
    /**
     * Tracks whether the current Shift one-shot was requested by a smart
     * auto-capitalization rule (first letter / after punctuation / double-space).
     * This allows selection/field changes to clear only smart one-shot Shift
     * without interfering with manually pressed Shift.
     */
    private var smartShiftRequested = false

    /**
     * Returns true if, given the current cursor position, smart auto-cap rules
     * (first-letter / after-period) suggest enabling Shift one-shot.
     *
     * This is the single place where we look at:
     * - user settings (first-letter / after-period),
     * - smart-features disabled flag,
     * - surrounding text (start of field, after newline, after ". ! ?").
     */
    private fun shouldApplySmartAutoCap(
        context: android.content.Context,
        inputConnection: InputConnection,
        shouldDisableSmartFeatures: Boolean
    ): Boolean {
        if (shouldDisableSmartFeatures) {
            return false
        }

        val autoCapFirstLetter = SettingsManager.getAutoCapitalizeFirstLetter(context)
        val autoCapAfterPeriod = SettingsManager.getAutoCapitalizeAfterPeriod(context)
        if (!autoCapFirstLetter && !autoCapAfterPeriod) {
            return false
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0) ?: return false
        val textAfterCursor = inputConnection.getTextAfterCursor(1, 0) ?: ""

        return shouldApplySmartAutoCap(
            autoCapFirstLetter = autoCapFirstLetter,
            autoCapAfterPeriod = autoCapAfterPeriod,
            textBeforeCursor = textBeforeCursor,
            textAfterCursor = textAfterCursor
        )
    }

    private fun shouldApplySmartAutoCap(
        autoCapFirstLetter: Boolean,
        autoCapAfterPeriod: Boolean,
        textBeforeCursor: CharSequence,
        textAfterCursor: CharSequence
    ): Boolean {
        val isCursorAtStart = textBeforeCursor.isEmpty()
        val isAfterNewline = textBeforeCursor.lastOrNull() == '\n'

        if (autoCapFirstLetter && (isCursorAtStart || isAfterNewline)) {
            return true
        }

        if (autoCapAfterPeriod && textBeforeCursor.isNotEmpty()) {
            // Look for the last non-whitespace character before the cursor
            val lastNonWhitespaceIndex = textBeforeCursor.indexOfLast { !it.isWhitespace() }
            if (lastNonWhitespaceIndex >= 0) {
                val lastNonWhitespaceChar = textBeforeCursor[lastNonWhitespaceIndex]
                val isSentencePunctuation = when (lastNonWhitespaceChar) {
                    '.' -> {
                        // Avoid treating "..." as end of sentence.
                        val prevIndex = lastNonWhitespaceIndex - 1
                        !(prevIndex >= 0 && textBeforeCursor[prevIndex] == '.')
                    }
                    '!', '?' -> true
                    else -> false
                }
                if (isSentencePunctuation) {
                    return true
                }
            }
        }

        return false
    }

    private fun shouldApplySmartAutoCapForSelection(
        context: android.content.Context,
        inputConnection: InputConnection,
        shouldDisableSmartFeatures: Boolean
    ): Boolean {
        if (shouldDisableSmartFeatures) {
            return false
        }

        val autoCapFirstLetter = SettingsManager.getAutoCapitalizeFirstLetter(context)
        val autoCapAfterPeriod = SettingsManager.getAutoCapitalizeAfterPeriod(context)
        if (!autoCapFirstLetter && !autoCapAfterPeriod) {
            return false
        }

        // Prefer a precise selection window via ExtractedText when available.
        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val text = extracted.text
            val selStart = extracted.selectionStart
            val selEnd = extracted.selectionEnd
            if (selStart >= 0 && selEnd >= 0 && selStart <= selEnd && selEnd <= text.length) {
                val before = text.subSequence(0, selStart)
                val after = text.subSequence(selEnd, text.length)
                return shouldApplySmartAutoCap(
                    autoCapFirstLetter = autoCapFirstLetter,
                    autoCapAfterPeriod = autoCapAfterPeriod,
                    textBeforeCursor = before,
                    textAfterCursor = after
                )
            }
        }

        // Fallback to a conservative heuristic around the cursor.
        val selectedText = inputConnection.getSelectedText(0) ?: ""
        val textBeforeCursor = inputConnection.getTextBeforeCursor(200, 0) ?: return false
        val textAfterCursor = inputConnection.getTextAfterCursor(200, 0) ?: ""

        val effectiveTextBefore = when {
            selectedText.isEmpty() -> textBeforeCursor
            textBeforeCursor.length >= selectedText.length && textBeforeCursor.endsWith(selectedText) ->
                textBeforeCursor.dropLast(selectedText.length)
            textAfterCursor.length >= selectedText.length && textAfterCursor.startsWith(selectedText) ->
                textBeforeCursor // selection on the right of cursor
            else -> textBeforeCursor
        }

        return shouldApplySmartAutoCap(
            autoCapFirstLetter = autoCapFirstLetter,
            autoCapAfterPeriod = autoCapAfterPeriod,
            textBeforeCursor = effectiveTextBefore,
            textAfterCursor = textAfterCursor
        )
    }

    private fun clearSmartShift(
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (smartShiftRequested) {
            val changed = disableShift()
            smartShiftRequested = false
            if (changed) onUpdateStatusBar()
        }
    }

    private fun applyAutoCapitalizeFirstLetter(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        val ic = inputConnection ?: run {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        val shouldCapitalize = shouldApplySmartAutoCap(context, ic, shouldDisableSmartFeatures)
        if (shouldCapitalize) {
            if (enableShift()) {
                smartShiftRequested = true
                onUpdateStatusBar()
            }
        } else {
            clearSmartShift(disableShift, onUpdateStatusBar)
        }
    }

    fun checkAndEnableAutoCapitalize(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit
    ) {
        applyAutoCapitalizeFirstLetter(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun checkAutoCapitalizeOnSelectionChange(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (newSelStart != newSelEnd) {
            val ic = inputConnection ?: run {
                clearSmartShift(disableShift, onUpdateStatusBar)
                return
            }

            val shouldCapitalize = shouldApplySmartAutoCapForSelection(
                context = context,
                inputConnection = ic,
                shouldDisableSmartFeatures = shouldDisableSmartFeatures
            )

            if (shouldCapitalize) {
                if (enableShift()) {
                    smartShiftRequested = true
                    onUpdateStatusBar()
                }
            } else {
                clearSmartShift(disableShift, onUpdateStatusBar)
            }
            return
        }
        applyAutoCapitalizeFirstLetter(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    /**
     * Returns whether smart auto-cap rules would enable Shift at the current cursor,
     * taking into account selection (the selected text is treated as replaced/absent).
     * Useful for actions that commit text directly (e.g., suggestion taps).
     */
    fun shouldAutoCapitalizeAtCursor(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean
    ): Boolean {
        val ic = inputConnection ?: return false
        return shouldApplySmartAutoCapForSelection(
            context = context,
            inputConnection = ic,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures
        )
    }

    fun checkAutoCapitalizeOnRestart(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit
    ) {
        applyAutoCapitalizeFirstLetter(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun enableAfterPunctuation(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onEnableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        val ic = inputConnection ?: run {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        val shouldCapitalize = shouldApplySmartAutoCap(context, ic, shouldDisableSmartFeatures)
        if (!shouldCapitalize) {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        if (onEnableShift()) {
            smartShiftRequested = true
            onUpdateStatusBar()
        }
    }

    fun enableAfterEnter(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onEnableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        applyAutoCapitalizeFirstLetter(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = onEnableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }
}
