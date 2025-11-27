package it.palsoftware.pastiera.inputmethod

import android.text.TextUtils
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager

/**
 * Central helper for smart auto-capitalization rules.
 * Provides a single decision path and a single entry point to enable/clear smart Shift.
 */
object AutoCapitalizeHelper {
    private var smartShiftRequested = false

    private data class AutoCapSettings(
        val autoCapFirstLetter: Boolean,
        val autoCapAfterPeriod: Boolean
    )

    data class CursorContext(
        val before: CharSequence,
        val after: CharSequence
    )

    private fun resolveAutoCapSettings(
        context: android.content.Context,
        inputConnection: InputConnection
    ): AutoCapSettings {
        val userAutoCapFirstLetter = SettingsManager.getAutoCapitalizeFirstLetter(context)
        val userAutoCapAfterPeriod = SettingsManager.getAutoCapitalizeAfterPeriod(context)

        val capsMode = inputConnection.getCursorCapsMode(
            TextUtils.CAP_MODE_SENTENCES or TextUtils.CAP_MODE_WORDS
        )
        val capsSentences = capsMode and TextUtils.CAP_MODE_SENTENCES != 0
        val capsWords = capsMode and TextUtils.CAP_MODE_WORDS != 0

        val autoCapFirstLetter = userAutoCapFirstLetter || capsSentences || capsWords
        val autoCapAfterPeriod = userAutoCapAfterPeriod || capsSentences

        return AutoCapSettings(autoCapFirstLetter, autoCapAfterPeriod)
    }

    /**
     * Reads the cursor context, ignoring any selected text (treated as removed/replaced).
     * Prefers ExtractedText; falls back to surrounding text APIs.
     */
    private fun readContext(inputConnection: InputConnection): CursorContext? {
        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val text = extracted.text
            val selStart = extracted.selectionStart
            val selEnd = extracted.selectionEnd
            if (selStart >= 0 && selEnd >= selStart && selEnd <= text.length) {
                val before = text.subSequence(0, selStart)
                val after = text.subSequence(selEnd, text.length)
                return CursorContext(before, after)
            }
        }

        val before = inputConnection.getTextBeforeCursor(200, 0) ?: return null
        val after = inputConnection.getTextAfterCursor(200, 0) ?: ""
        val selected = inputConnection.getSelectedText(0) ?: ""

        val beforeEffective = when {
            selected.isEmpty() -> before
            before.length >= selected.length && before.endsWith(selected) ->
                before.dropLast(selected.length)
            else -> before
        }
        val afterEffective = when {
            selected.isEmpty() -> after
            after.length >= selected.length && after.startsWith(selected) ->
                after.drop(selected.length)
            else -> after
        }

        return CursorContext(beforeEffective, afterEffective)
    }

    /**
     * Pure decision: should smart auto-cap request Shift given before/after?
     */
    private fun shouldAutoCap(
        settings: AutoCapSettings,
        before: CharSequence,
        after: CharSequence
    ): Boolean {
        val isCursorAtStart = before.isEmpty()
        val isAfterNewline = before.lastOrNull() == '\n'

        if (settings.autoCapFirstLetter && (isCursorAtStart || isAfterNewline)) {
            return true
        }

        if (settings.autoCapAfterPeriod && before.isNotEmpty()) {
            val lastNonWhitespaceIndex = before.indexOfLast { !it.isWhitespace() }
            if (lastNonWhitespaceIndex >= 0) {
                val lastNonWhitespaceChar = before[lastNonWhitespaceIndex]
                val isSentencePunctuation = when (lastNonWhitespaceChar) {
                    '.' -> {
                        val prevIndex = lastNonWhitespaceIndex - 1
                        !(prevIndex >= 0 && before[prevIndex] == '.')
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

    /**
     * Single entry point to evaluate and set/clear smart Shift.
     */
    fun maybeEnableSmartShift(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit
    ) {
        if (shouldDisableSmartFeatures) {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        val ic = inputConnection ?: run {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        val settings = resolveAutoCapSettings(context, ic)
        if (!settings.autoCapFirstLetter && !settings.autoCapAfterPeriod) {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        val cursorContext = readContext(ic) ?: run {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        val shouldCapitalize = shouldAutoCap(settings, cursorContext.before, cursorContext.after)
        if (shouldCapitalize) {
            if (enableShift()) {
                smartShiftRequested = true
                onUpdateStatusBar()
            }
        } else {
            clearSmartShift(disableShift, onUpdateStatusBar)
        }
    }

    fun shouldAutoCapitalizeAtCursor(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean
    ): Boolean {
        if (inputConnection == null || shouldDisableSmartFeatures) return false
        val settings = resolveAutoCapSettings(context, inputConnection)
        if (!settings.autoCapFirstLetter && !settings.autoCapAfterPeriod) {
            return false
        }
        val cursorContext = readContext(inputConnection) ?: return false
        return shouldAutoCap(settings, cursorContext.before, cursorContext.after)
    }

    // Thin wrappers kept for call sites; all delegate to maybeEnableSmartShift.
    fun checkAndEnableAutoCapitalize(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit
    ) {
        maybeEnableSmartShift(
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
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
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
        maybeEnableSmartShift(
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
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = onEnableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun enableAfterEnter(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onEnableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = onEnableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }
}
