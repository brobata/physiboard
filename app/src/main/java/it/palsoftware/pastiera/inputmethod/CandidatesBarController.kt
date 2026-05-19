package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.view.KeyEvent
import android.widget.LinearLayout
import android.view.inputmethod.InputConnection

/**
 * Coordinates the two StatusBarController instances (full input view vs
 * candidates-only view) so the IME service can treat them as a single surface.
 */
class CandidatesBarController(
    context: Context,
    clipboardHistoryManager: it.palsoftware.pastiera.clipboard.ClipboardHistoryManager? = null,
    assets: AssetManager? = null,
    imeServiceClass: Class<*>? = null
) {

    private val inputStatusBar = StatusBarController(context, StatusBarController.Mode.FULL, clipboardHistoryManager, assets, imeServiceClass)
    private val candidatesStatusBar = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY, clipboardHistoryManager, assets, imeServiceClass)

    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
        set(value) {
            field = value
            inputStatusBar.onVariationSelectedListener = value
            candidatesStatusBar.onVariationSelectedListener = value
        }

    var onCursorMovedListener: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onCursorMovedListener = value
            candidatesStatusBar.onCursorMovedListener = value
        }

    var onSpeechRecognitionRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSpeechRecognitionRequested = value
            candidatesStatusBar.onSpeechRecognitionRequested = value
        }

    var onAddUserWord: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onAddUserWord = value
            candidatesStatusBar.onAddUserWord = value
        }

    var onLanguageSwitchRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onLanguageSwitchRequested = value
            candidatesStatusBar.onLanguageSwitchRequested = value
        }
    
    var onClipboardRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onClipboardRequested = value
            candidatesStatusBar.onClipboardRequested = value
        }
    
    var onEmojiPickerRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiPickerRequested = value
            candidatesStatusBar.onEmojiPickerRequested = value
        }

    var onEmojiPageRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiPageRequested = value
            candidatesStatusBar.onEmojiPageRequested = value
        }
    
    var onSymbolsPageRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSymbolsPageRequested = value
            candidatesStatusBar.onSymbolsPageRequested = value
        }

    var onSymCloseRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSymCloseRequested = value
            candidatesStatusBar.onSymCloseRequested = value
        }

    var onUndoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onUndoRequested = value
            candidatesStatusBar.onUndoRequested = value
        }

    var onRedoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onRedoRequested = value
            candidatesStatusBar.onRedoRequested = value
        }

    var onSoftwareKeyboardKeyPressed: ((Int) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardKeyPressed = value
            candidatesStatusBar.onSoftwareKeyboardKeyPressed = value
        }

    var onSoftwareKeyboardShiftTapped: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardShiftTapped = value
            candidatesStatusBar.onSoftwareKeyboardShiftTapped = value
        }

    var onSoftwareKeyboardNonShiftInteraction: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardNonShiftInteraction = value
            candidatesStatusBar.onSoftwareKeyboardNonShiftInteraction = value
        }

    var onSoftwareKeyboardTextInput: ((String, InputConnection?, StatusBarController.StatusSnapshot) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardTextInput = value
            candidatesStatusBar.onSoftwareKeyboardTextInput = value
        }

    var onMinimalUiToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onMinimalUiToggleRequested = value
            candidatesStatusBar.onMinimalUiToggleRequested = value
        }

    fun getInputView(emojiMapText: String = ""): LinearLayout {
        return inputStatusBar.getOrCreateLayout(emojiMapText)
    }

    fun getCandidatesView(emojiMapText: String = ""): LinearLayout {
        return candidatesStatusBar.getOrCreateLayout(emojiMapText)
    }

    fun setForceMinimalUi(force: Boolean) {
        inputStatusBar.setForceMinimalUi(force)
        candidatesStatusBar.setForceMinimalUi(force)
    }

    fun handleBackPressed(): Boolean {
        return inputStatusBar.handleBackPressed() || candidatesStatusBar.handleBackPressed()
    }

    fun handleEmojiPickerSearchKeyDown(event: KeyEvent?, ctrlActive: Boolean): Boolean {
        return inputStatusBar.handleEmojiPickerSearchKeyDown(event, ctrlActive) ||
            candidatesStatusBar.handleEmojiPickerSearchKeyDown(event, ctrlActive)
    }

    fun shouldConsumeEmojiPickerSearchKeyUp(event: KeyEvent?, ctrlActive: Boolean): Boolean {
        return inputStatusBar.shouldConsumeEmojiPickerSearchKeyUp(event, ctrlActive) ||
            candidatesStatusBar.shouldConsumeEmojiPickerSearchKeyUp(event, ctrlActive)
    }

    fun disableEmojiPickerSearchInputCapture() {
        inputStatusBar.disableEmojiPickerSearchInputCapture()
        candidatesStatusBar.disableEmojiPickerSearchInputCapture()
    }

    fun isEmojiPickerSearchInputActive(): Boolean {
        return inputStatusBar.isEmojiPickerSearchInputActive() ||
            candidatesStatusBar.isEmojiPickerSearchInputActive()
    }

    fun createEmojiPickerSearchInputConnection(): InputConnection? {
        return inputStatusBar.createEmojiPickerSearchInputConnection()
            ?: candidatesStatusBar.createEmojiPickerSearchInputConnection()
    }

    fun isMinimalUiActive(): Boolean = inputStatusBar.isMinimalUiActive()
    
    fun invalidateStaticVariations() {
        inputStatusBar.invalidateStaticVariations()
        candidatesStatusBar.invalidateStaticVariations()
    }

    fun setMicrophoneButtonActive(isActive: Boolean) {
        inputStatusBar.setMicrophoneButtonActive(isActive)
        candidatesStatusBar.setMicrophoneButtonActive(isActive)
    }
    
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        inputStatusBar.updateMicrophoneAudioLevel(rmsdB)
        candidatesStatusBar.updateMicrophoneAudioLevel(rmsdB)
    }
    
    fun showSpeechRecognitionHint(show: Boolean) {
        inputStatusBar.showSpeechRecognitionHint(show)
        candidatesStatusBar.showSpeechRecognitionHint(show)
    }

    fun updateStatusBars(
        snapshot: StatusBarController.StatusSnapshot,
        emojiMapText: String,
        inputConnection: InputConnection?,
        symMappings: Map<Int, String>?
    ) {
        inputStatusBar.update(snapshot, emojiMapText, inputConnection, symMappings)
        candidatesStatusBar.update(snapshot, emojiMapText, inputConnection, symMappings)
    }

    fun updateClipboardCount(count: Int) {
        inputStatusBar.updateClipboardCount(count)
        candidatesStatusBar.updateClipboardCount(count)
    }

    fun flashSuggestionSlot(suggestionIndex: Int) {
        inputStatusBar.flashSuggestionSlot(suggestionIndex)
        candidatesStatusBar.flashSuggestionSlot(suggestionIndex)
    }
}
