package it.palsoftware.pastiera.inputmethod

import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager

/**
 * Helper for handling auto-capitalization logic.
 * Each entry point toggles Shift through the provided callbacks so only one
 * source of truth tracks the modifier state.
 */
object AutoCapitalizeHelper {

    fun checkAndEnableAutoCapitalize(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!SettingsManager.getAutoCapitalizeFirstLetter(context)) return
        if (shouldDisableSmartFeatures) return
        val ic = inputConnection ?: return
        val textBeforeCursor = ic.getTextBeforeCursor(1, 0)

        val isCursorAtStart = textBeforeCursor.isNullOrEmpty()
        val isAfterNewline = textBeforeCursor?.lastOrNull() == '\n'
        if ((isCursorAtStart || isAfterNewline) && enableShift()) {
            onUpdateStatusBar()
        }
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
        if (!SettingsManager.getAutoCapitalizeFirstLetter(context)) {
            if (disableShift()) onUpdateStatusBar()
            return
        }

        if (newSelStart != newSelEnd || shouldDisableSmartFeatures || inputConnection == null) {
            if (disableShift()) onUpdateStatusBar()
            return
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(1000, 0) ?: ""
        val textAfterCursor = inputConnection.getTextAfterCursor(1000, 0) ?: ""
        val isCursorAtStart = textBeforeCursor.isEmpty()
        val isAfterNewline = textBeforeCursor.lastOrNull() == '\n'
        val isFieldEmpty = isCursorAtStart && textAfterCursor.isEmpty()

        if ((isCursorAtStart && isFieldEmpty) || isAfterNewline) {
            if (enableShift()) onUpdateStatusBar()
        }
    }

    fun checkAutoCapitalizeOnRestart(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!SettingsManager.getAutoCapitalizeFirstLetter(context)) return
        if (shouldDisableSmartFeatures) return
        val ic = inputConnection ?: return
        val textBeforeCursor = ic.getTextBeforeCursor(1000, 0)
        val textAfterCursor = ic.getTextAfterCursor(1000, 0) ?: ""
        val isCursorAtStart = textBeforeCursor.isNullOrEmpty()
        val isFieldEmpty = isCursorAtStart && textAfterCursor.isEmpty()
        if (isCursorAtStart && isFieldEmpty && enableShift()) {
            onUpdateStatusBar()
        }
    }

    fun enableAfterPunctuation(
        inputConnection: InputConnection?,
        onEnableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ): Boolean {
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(100, 0) ?: return false
        if (textBeforeCursor.isEmpty()) return false

        val lastChar = textBeforeCursor.last()
        val shouldCapitalize = when (lastChar) {
            '.' -> textBeforeCursor.length >= 2 && textBeforeCursor[textBeforeCursor.length - 2] != '.'
            '!', '?' -> true
            else -> false
        }

        if (shouldCapitalize && onEnableShift()) {
            onUpdateStatusBar()
            return true
        }
        return false
    }

    fun enableAfterEnter(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onEnableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!SettingsManager.getAutoCapitalizeFirstLetter(context)) return
        if (shouldDisableSmartFeatures) return

        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1, 0) ?: return
        val isAfterNewline = textBeforeCursor.lastOrNull() == '\n'
        if (isAfterNewline && onEnableShift()) {
            onUpdateStatusBar()
        }
    }
}

