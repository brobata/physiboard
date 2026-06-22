package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.TextInputController

object SoftwareKeyboardTextInputHandler {
    fun handleSpaceInput(
        textInputController: TextInputController,
        inputConnection: InputConnection,
        shouldDisableDoubleSpaceToPeriod: Boolean,
        shouldDisableAutoCapitalize: Boolean,
        shouldDisableSuggestions: Boolean,
        onDoubleSpaceHandled: () -> Unit,
        onNormalBoundary: () -> Boolean,
        onCommitSpace: () -> Unit,
        onStatusBarUpdate: () -> Unit
    ): Boolean {
        if (
            textInputController.handleDoubleSpaceToPeriod(
                keyCode = KeyEvent.KEYCODE_SPACE,
                inputConnection = inputConnection,
                shouldDisableDoubleSpaceToPeriod = shouldDisableDoubleSpaceToPeriod,
                shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
                onStatusBarUpdate = onStatusBarUpdate
            )
        ) {
            onDoubleSpaceHandled()
            onStatusBarUpdate()
            return true
        }

        val boundaryCommitted = !shouldDisableSuggestions && onNormalBoundary()
        if (!boundaryCommitted) {
            onCommitSpace()
        }
        onStatusBarUpdate()
        return true
    }
}
