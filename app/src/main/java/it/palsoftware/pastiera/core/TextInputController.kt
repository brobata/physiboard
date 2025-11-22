package it.palsoftware.pastiera.core

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AutoCapitalizeHelper

/**
 * Orchestrates text-level helpers such as double-space-to-period and
 * auto-capitalization triggers. Keeps state like double-space timing isolated
 * from the IME service.
 */
class TextInputController(
    private val context: Context,
    private val modifierStateController: ModifierStateController,
    private val doubleTapThreshold: Long
) {

    private var lastSpacePressTime: Long = 0L

    fun handleDoubleSpaceToPeriod(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onStatusBarUpdate: () -> Unit
    ): Boolean {
        if (keyCode != KeyEvent.KEYCODE_SPACE || shouldDisableSmartFeatures) {
            if (lastSpacePressTime > 0) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSpacePressTime >= doubleTapThreshold) {
                    lastSpacePressTime = 0
                }
            }
            return false
        }

        if (!SettingsManager.getDoubleSpaceToPeriod(context)) {
            lastSpacePressTime = 0
            return false
        }

        val currentTime = System.currentTimeMillis()
        val isDoubleTap = lastSpacePressTime > 0 &&
            (currentTime - lastSpacePressTime) < doubleTapThreshold

        if (!isDoubleTap || inputConnection == null) {
            lastSpacePressTime = currentTime
            return false
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0) ?: return false
        if (!textBeforeCursor.endsWith(" ") || 
            (textBeforeCursor.length >= 2 && textBeforeCursor[textBeforeCursor.length - 2] == ' ')) {
            lastSpacePressTime = currentTime
            return false
        }

        var lastCharIndex = textBeforeCursor.length - 2
        while (lastCharIndex >= 0 && textBeforeCursor[lastCharIndex].isWhitespace()) {
            lastCharIndex--
        }

        if (lastCharIndex < 0) {
            lastSpacePressTime = currentTime
            return false
        }

        val lastChar = textBeforeCursor[lastCharIndex]
        val isEndPunctuation = lastChar in ".!?"
        if (isEndPunctuation) {
            lastSpacePressTime = currentTime
            return false
        }

        inputConnection.deleteSurroundingText(1, 0)
        inputConnection.commitText(". ", 1)
        modifierStateController.shiftOneShot = true
        onStatusBarUpdate()
        lastSpacePressTime = 0
        return true
    }

    fun handleAutoCapAfterPeriod(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onStatusBarUpdate: () -> Unit
    ) {
        val autoCapitalizeAfterPeriodEnabled =
            SettingsManager.getAutoCapitalizeAfterPeriod(context) && !shouldDisableSmartFeatures
        if (autoCapitalizeAfterPeriodEnabled &&
            keyCode == KeyEvent.KEYCODE_SPACE &&
            !modifierStateController.shiftOneShot
        ) {
            AutoCapitalizeHelper.enableAfterPunctuation(
                inputConnection,
                onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                onUpdateStatusBar = onStatusBarUpdate
            )
        }
    }

    fun handleAutoCapAfterEnter(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onStatusBarUpdate: () -> Unit
    ) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.enableAfterEnter(
                context,
                inputConnection,
                shouldDisableSmartFeatures,
                onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                onUpdateStatusBar = onStatusBarUpdate
            )
        }
    }
}

