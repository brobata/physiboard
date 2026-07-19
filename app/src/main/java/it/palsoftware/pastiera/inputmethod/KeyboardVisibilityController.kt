package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.SymLayoutController

/**
 * Handles creation/show/hide of the IME status UI for both the full input view
 * and the candidate-only view exposed when the system hides the soft keyboard.
 */
class KeyboardVisibilityController(
    private val context: Context,
    private val candidatesBarController: CandidatesBarController,
    private val symLayoutController: SymLayoutController,
    private val isInputViewActive: () -> Boolean,
    private val hasActiveTextField: () -> Boolean,
    private val isNavModeLatched: () -> Boolean,
    private val currentInputConnection: () -> InputConnection?,
    private val isInputViewShown: () -> Boolean,
    private val attachInputView: (View) -> Unit,
    private val setCandidatesViewShown: (Boolean) -> Unit,
    private val requestShowInputView: () -> Unit,
    private val refreshStatusBar: () -> Unit
) {

    private var statusBarPresentationMode: SettingsManager.StatusBarPresentationMode =
        SettingsManager.getStatusBarPresentationMode(context)

    fun onCreateInputView(): View {
        val layout = candidatesBarController.getInputView(symLayoutController.emojiMapTextForLayout())
        detachFromParent(layout)
        refreshStatusBar()
        return layout
    }

    fun onCreateCandidatesView(): View {
        val layout = candidatesBarController.getCandidatesView(symLayoutController.emojiMapTextForLayout())
        detachFromParent(layout)
        refreshStatusBar()
        return layout
    }

    fun onEvaluateInputViewShown(shouldShowInputView: Boolean): Boolean {
        SoftwareKeyboardAutoDetector.updateSystemInputViewDecision(shouldShowInputView)
        refreshStatusBar()
        setCandidatesViewShown(false)
        return true
    }

    fun ensureInputViewCreated() {
        if (!isInputViewActive()) {
            return
        }
        if (currentInputConnection() == null) {
            return
        }

        val layout = candidatesBarController.getInputView(symLayoutController.emojiMapTextForLayout())
        refreshStatusBar()

        if (layout.parent == null) {
            attachInputView(layout)
        }

        if (!isInputViewShown() && !isNavModeLatched()) {
            try {
                requestShowInputView()
            } catch (_: Exception) {
                // Avoid crashing if the system rejects the request
            }
        }
    }

    fun togglePastierinaMode() {
        statusBarPresentationMode = when (statusBarPresentationMode) {
            SettingsManager.StatusBarPresentationMode.PASTIERINA ->
                SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR
            SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR ->
                SettingsManager.StatusBarPresentationMode.PASTIERINA
        }
        SettingsManager.setStatusBarPresentationMode(context, statusBarPresentationMode)
        applyStatusBarPresentationMode()
    }

    private fun applyStatusBarPresentationMode() {
        val pastierinaModeActive =
            statusBarPresentationMode == SettingsManager.StatusBarPresentationMode.PASTIERINA
        candidatesBarController.setPastierinaModeActive(pastierinaModeActive)
        SettingsManager.setPastierinaModeActive(context, pastierinaModeActive)
        refreshStatusBar()
    }

    fun syncStatusBarPresentationModeFromSettings() {
        statusBarPresentationMode = SettingsManager.getStatusBarPresentationMode(context)
        applyStatusBarPresentationMode()
    }

    fun onKeyboardSurfaceChanged(
        ensureInputViewShown: Boolean,
        requireActiveTextField: Boolean = false
    ) {
        refreshStatusBar()
        if (
            ensureInputViewShown &&
            (!requireActiveTextField || hasActiveTextField()) &&
            currentInputConnection() != null &&
            !isInputViewShown()
        ) {
            try {
                requestShowInputView()
            } catch (_: Exception) {
                // The editor may disappear during the same device transition.
            }
        }
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
