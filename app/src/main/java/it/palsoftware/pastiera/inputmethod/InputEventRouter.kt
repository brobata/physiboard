package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.inputmethod.AltSymManager
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.core.SymLayoutController.SymKeyResult
import it.palsoftware.pastiera.core.TextInputController
import it.palsoftware.pastiera.core.AutoCorrectionManager
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import it.palsoftware.pastiera.inputmethod.TextSelectionHelper
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest

/**
 * Routes IME key events to the appropriate handlers so that the service can
 * focus on lifecycle wiring.
 */
class InputEventRouter(
    private val context: Context,
    private val navModeController: NavModeController
) {

    sealed class EditableFieldRoutingResult {
        object Continue : EditableFieldRoutingResult()
        object Consume : EditableFieldRoutingResult()
        object CallSuper : EditableFieldRoutingResult()
    }

    data class NoEditableFieldCallbacks(
        val isAlphabeticKey: (Int) -> Boolean,
        val isLauncherPackage: (String?) -> Boolean,
        val handleLauncherShortcut: (Int) -> Boolean,
        val callSuper: () -> Boolean,
        val currentInputConnection: () -> InputConnection?
    )

    fun handleKeyDownWithNoEditableField(
        keyCode: Int,
        event: KeyEvent?,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        callbacks: NoEditableFieldCallbacks,
        ctrlLatchActive: Boolean,
        editorInfo: EditorInfo?,
        currentPackageName: String?
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (navModeController.isNavModeActive()) {
                navModeController.exitNavMode()
                return false
            }
            return callbacks.callSuper()
        }

        if (navModeController.isNavModeKey(keyCode)) {
            return navModeController.handleNavModeKey(
                keyCode,
                event,
                isKeyDown = true,
                ctrlKeyMap = ctrlKeyMap,
                inputConnectionProvider = callbacks.currentInputConnection
            )
        }

        if (!ctrlLatchActive && SettingsManager.getLauncherShortcutsEnabled(context)) {
            val packageName = editorInfo?.packageName ?: currentPackageName
            if (callbacks.isLauncherPackage(packageName) && callbacks.isAlphabeticKey(keyCode)) {
                if (callbacks.handleLauncherShortcut(keyCode)) {
                    return true
                }
            }
        }

        return callbacks.callSuper()
    }

    fun handleKeyUpWithNoEditableField(
        keyCode: Int,
        event: KeyEvent?,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        callbacks: NoEditableFieldCallbacks
    ): Boolean {
        if (navModeController.isNavModeKey(keyCode)) {
            return navModeController.handleNavModeKey(
                keyCode,
                event,
                isKeyDown = false,
                ctrlKeyMap = ctrlKeyMap,
                inputConnectionProvider = callbacks.currentInputConnection
            )
        }
        return callbacks.callSuper()
    }

    data class EditableFieldKeyDownParams(
        val ctrlLatchFromNavMode: Boolean,
        val ctrlLatchActive: Boolean,
        val isInputViewActive: Boolean,
        val isInputViewShown: Boolean,
        val hasInputConnection: Boolean
    )

    data class EditableFieldKeyDownCallbacks(
        val exitNavMode: () -> Unit,
        val ensureInputViewCreated: () -> Unit,
        val callSuper: () -> Boolean
    )

    fun handleEditableFieldKeyDownPrelude(
        keyCode: Int,
        params: EditableFieldKeyDownParams,
        callbacks: EditableFieldKeyDownCallbacks
    ): EditableFieldRoutingResult {
        if (params.ctrlLatchFromNavMode && params.ctrlLatchActive) {
            callbacks.exitNavMode()
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return EditableFieldRoutingResult.CallSuper
        }

        if (params.hasInputConnection && params.isInputViewActive && !params.isInputViewShown) {
            callbacks.ensureInputViewCreated()
        }

        return EditableFieldRoutingResult.Continue
    }

    fun handleTextInputPipeline(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        isAutoCorrectEnabled: Boolean,
        textInputController: TextInputController,
        autoCorrectionManager: AutoCorrectionManager,
        updateStatusBar: () -> Unit
    ): Boolean {
        if (
            autoCorrectionManager.handleBackspaceUndo(
                keyCode,
                inputConnection,
                isAutoCorrectEnabled,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            return true
        }

        if (
            textInputController.handleDoubleSpaceToPeriod(
                keyCode,
                inputConnection,
                shouldDisableSmartFeatures,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            return true
        }

        textInputController.handleAutoCapAfterPeriod(
            keyCode,
            inputConnection,
            shouldDisableSmartFeatures,
            onStatusBarUpdate = updateStatusBar
        )

        textInputController.handleAutoCapAfterEnter(
            keyCode,
            inputConnection,
            shouldDisableSmartFeatures,
            onStatusBarUpdate = updateStatusBar
        )

        if (
            autoCorrectionManager.handleSpaceOrPunctuation(
                keyCode,
                event,
                inputConnection,
                isAutoCorrectEnabled,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            return true
        }

        autoCorrectionManager.handleAcceptOrResetOnOtherKeys(
            keyCode,
            event,
            isAutoCorrectEnabled
        )
        return false
    }

    fun handleNumericAndSym(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        isNumericField: Boolean,
        altSymManager: AltSymManager,
        symLayoutController: SymLayoutController,
        ctrlLatchActive: Boolean,
        ctrlOneShot: Boolean,
        altLatchActive: Boolean,
        cursorUpdateDelayMs: Long,
        updateStatusBar: () -> Unit,
        callSuper: () -> Boolean
    ): Boolean {
        val ic = inputConnection ?: return false

        // Numeric fields always use the Alt mapping for every key press (short press included).
        if (isNumericField) {
            val altChar = altSymManager.getAltMappings()[keyCode]
            if (altChar != null) {
                ic.commitText(altChar, 1)
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBar()
                }, cursorUpdateDelayMs)
                return true
            }
        }

        // If SYM is active, check SYM mappings first (they take precedence over Alt and Ctrl)
        // When SYM is active, all other modifiers are bypassed
        val shouldBypassSymForCtrl = event?.isCtrlPressed == true || ctrlLatchActive || ctrlOneShot
        if (!shouldBypassSymForCtrl && symLayoutController.isSymActive()) {
            return when (
                symLayoutController.handleKeyWhenActive(
                    keyCode,
                    event,
                    ic,
                    ctrlLatchActive = ctrlLatchActive,
                    altLatchActive = altLatchActive,
                    updateStatusBar = updateStatusBar
                )
            ) {
                SymKeyResult.CONSUME -> true
                SymKeyResult.CALL_SUPER -> callSuper()
                SymKeyResult.NOT_HANDLED -> false
            }
        }

        return false
    }

    /**
     * Handles Alt-modified key presses once Alt is considered active
     * (physical Alt, latch or one-shot). The caller is responsible for
     * managing Alt latch/one-shot state.
     */
    fun handleAltModifiedKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        altSymManager: AltSymManager,
        updateStatusBar: () -> Unit,
        callSuperWithKey: (Int, KeyEvent?) -> Boolean
    ): Boolean {
        val ic = inputConnection ?: return false

        // Consume Alt+Space to avoid Android's symbol picker and just insert a space.
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            ic.commitText(" ", 1)
            updateStatusBar()
            return true
        }

        val result = altSymManager.handleAltCombination(
            keyCode,
            ic,
            event
        ) { defaultKeyCode, defaultEvent ->
            // Fallback: delegate to caller (typically super.onKeyDown)
            callSuperWithKey(defaultKeyCode, defaultEvent)
        }

        if (result) {
            updateStatusBar()
        }
        return result
    }

    /**
     * Handles Ctrl-modified shortcuts in editable fields (copy/paste/cut/undo/select_all,
     * expand selection, DPAD/TAB/PAGE/ESC mappings and Ctrl+Backspace behaviour).
     * The caller is responsible for setting/clearing Ctrl latch and one-shot flags.
     */
    fun handleCtrlModifiedKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        ctrlLatchFromNavMode: Boolean,
        ctrlOneShot: Boolean,
        clearCtrlOneShot: () -> Unit,
        updateStatusBar: () -> Unit,
        callSuper: () -> Boolean
    ): Boolean {
        val ic = inputConnection ?: return false

        if (ctrlOneShot && !ctrlLatchFromNavMode) {
            clearCtrlOneShot()
            updateStatusBar()
        }

        val ctrlMapping = ctrlKeyMap[keyCode]
        if (ctrlMapping != null) {
            when (ctrlMapping.type) {
                "action" -> {
                    when (ctrlMapping.value) {
                        "expand_selection_left" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                outputKeyCode = null,
                                outputKeyCodeName = "expand_selection_left"
                            )
                            TextSelectionHelper.expandSelectionLeft(ic)
                            return true
                        }
                        "expand_selection_right" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                outputKeyCode = null,
                                outputKeyCodeName = "expand_selection_right"
                            )
                            TextSelectionHelper.expandSelectionRight(ic)
                            return true
                        }
                        else -> {
                            val actionId = when (ctrlMapping.value) {
                                "copy" -> android.R.id.copy
                                "paste" -> android.R.id.paste
                                "cut" -> android.R.id.cut
                                "undo" -> android.R.id.undo
                                "select_all" -> android.R.id.selectAll
                                else -> null
                            }
                            if (actionId != null) {
                                KeyboardEventTracker.notifyKeyEvent(
                                    keyCode,
                                    event,
                                    "KEY_DOWN",
                                    outputKeyCode = null,
                                    outputKeyCodeName = ctrlMapping.value
                                )
                                ic.performContextMenuAction(actionId)
                                return true
                            }
                            return true
                        }
                    }
                }
                "keycode" -> {
                    val mappedKeyCode = when (ctrlMapping.value) {
                        "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                        "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                        "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                        "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                        "TAB" -> KeyEvent.KEYCODE_TAB
                        "PAGE_UP" -> KeyEvent.KEYCODE_PAGE_UP
                        "PAGE_DOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
                        "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
                        else -> null
                    }
                    if (mappedKeyCode != null) {
                        KeyboardEventTracker.notifyKeyEvent(
                            keyCode,
                            event,
                            "KEY_DOWN",
                            outputKeyCode = mappedKeyCode,
                            outputKeyCodeName = KeyboardEventTracker.getOutputKeyCodeName(mappedKeyCode)
                        )
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, mappedKeyCode))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, mappedKeyCode))

                        if (mappedKeyCode in listOf(
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_PAGE_UP,
                                KeyEvent.KEYCODE_PAGE_DOWN
                            )
                        ) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                updateStatusBar()
                            }, 50)
                        }

                        return true
                    }
                    return true
                }
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                val extractedText: ExtractedText? = ic.getExtractedText(
                    ExtractedTextRequest().apply {
                        flags = ExtractedText.FLAG_SELECTING
                    },
                    0
                )

                val hasSelection = extractedText?.let {
                    it.selectionStart >= 0 && it.selectionEnd >= 0 && it.selectionStart != it.selectionEnd
                } ?: false

                if (hasSelection) {
                    KeyboardEventTracker.notifyKeyEvent(
                        keyCode,
                        event,
                        "KEY_DOWN",
                        outputKeyCode = null,
                        outputKeyCodeName = "delete_selection"
                    )
                    ic.commitText("", 0)
                    return true
                } else {
                    KeyboardEventTracker.notifyKeyEvent(
                        keyCode,
                        event,
                        "KEY_DOWN",
                        outputKeyCode = null,
                        outputKeyCodeName = "delete_last_word"
                    )
                    TextSelectionHelper.deleteLastWord(ic)
                    return true
                }
            }

            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BACK) {
                return callSuper()
            }

            return true
        }

        return false
    }
}

