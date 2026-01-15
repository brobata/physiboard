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
    private val isNavModeLatched: () -> Boolean,
    private val currentInputConnection: () -> InputConnection?,
    private val isInputViewShown: () -> Boolean,
    private val attachInputView: (View) -> Unit,
    private val setCandidatesViewShown: (Boolean) -> Unit,
    private val requestShowInputView: () -> Unit,
    private val refreshStatusBar: () -> Unit
) {

    private enum class MinimalUiOverride {
        FOLLOW_SYSTEM,
        FORCE_MINIMAL,
        FORCE_FULL
    }

    private var systemRequestsMinimalUi: Boolean = false
    private var minimalUiOverride: MinimalUiOverride = MinimalUiOverride.FOLLOW_SYSTEM

    init {
        minimalUiOverride = resolveOverrideFromSettings()
    }
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
        systemRequestsMinimalUi = !shouldShowInputView
        if (systemRequestsMinimalUi && minimalUiOverride != MinimalUiOverride.FORCE_MINIMAL) {
            minimalUiOverride = MinimalUiOverride.FORCE_MINIMAL
            persistOverride(minimalUiOverride)
        }
        applyMinimalUiState()
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

    fun toggleUserMinimalUi() {
        minimalUiOverride = when (minimalUiOverride) {
            MinimalUiOverride.FORCE_MINIMAL,
            MinimalUiOverride.FORCE_FULL -> MinimalUiOverride.FOLLOW_SYSTEM
            MinimalUiOverride.FOLLOW_SYSTEM -> {
                if (isMinimalUiActive()) {
                    MinimalUiOverride.FORCE_FULL
                } else {
                    MinimalUiOverride.FORCE_MINIMAL
                }
            }
        }
        persistOverride(minimalUiOverride)
        applyMinimalUiState()
    }

    private fun applyMinimalUiState() {
        candidatesBarController.setForceMinimalUi(isMinimalUiActive())
        SettingsManager.setPastierinaModeActive(context, isMinimalUiActive())
        refreshStatusBar()
    }

    fun syncMinimalUiOverrideFromSettings() {
        minimalUiOverride = resolveOverrideFromSettings()
        applyMinimalUiState()
    }

    private fun isMinimalUiActive(): Boolean {
        return when (minimalUiOverride) {
            MinimalUiOverride.FORCE_MINIMAL -> true
            MinimalUiOverride.FORCE_FULL -> false
            MinimalUiOverride.FOLLOW_SYSTEM -> systemRequestsMinimalUi
        }
    }

    private fun resolveOverrideFromSettings(): MinimalUiOverride {
        return when (SettingsManager.getPastierinaModeOverride(context)) {
            SettingsManager.PastierinaModeOverride.FORCE_MINIMAL -> MinimalUiOverride.FORCE_MINIMAL
            SettingsManager.PastierinaModeOverride.FORCE_FULL -> MinimalUiOverride.FORCE_FULL
            SettingsManager.PastierinaModeOverride.FOLLOW_SYSTEM -> MinimalUiOverride.FOLLOW_SYSTEM
        }
    }

    private fun persistOverride(override: MinimalUiOverride) {
        val mapped = when (override) {
            MinimalUiOverride.FORCE_MINIMAL -> SettingsManager.PastierinaModeOverride.FORCE_MINIMAL
            MinimalUiOverride.FORCE_FULL -> SettingsManager.PastierinaModeOverride.FORCE_FULL
            MinimalUiOverride.FOLLOW_SYSTEM -> SettingsManager.PastierinaModeOverride.FOLLOW_SYSTEM
        }
        SettingsManager.setPastierinaModeOverride(context, mapped)
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
