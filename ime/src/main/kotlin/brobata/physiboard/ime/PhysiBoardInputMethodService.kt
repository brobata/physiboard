package brobata.physiboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import brobata.physiboard.design.PhysiFonts
import brobata.physiboard.device.privileged.PrivilegedServices
import brobata.physiboard.device.privileged.setup.SetupReasons
import brobata.physiboard.ime.access.AccessibilityBridge

/**
 * The keyboard, as Android sees it.
 *
 * It holds no typing rules. Its whole job is to be the adapter at two edges: it
 * pulls the plain integer fields off a real key event and hands them to the
 * pure pipeline, then applies the operations the pipeline returns to the
 * editor. Everything that decides what SHOULD happen lives in `:core` and
 * `:device:titan`, where it can be driven by a JVM test.
 *
 * PhysiBoard is a physical-keyboard keyboard, so it never offers a software
 * keyboard: [onEvaluateInputViewShown] stays false (the one exception is an empty
 * input view of no height for the experimental inline suggestions, off by default)
 * and the only surface it puts on screen is the strip, rendered in the candidates
 * view ([onCreateCandidatesView]).
 */
class PhysiBoardInputMethodService : InputMethodService() {

    /**
     * The settings store lives in `:app` (see [SettingsSourceOwner]); a host without one leaves
     * the session on its shipped defaults. spec: per-app-behavior.md SS2.2, the WebAPK host
     * lookup: without [WebApkHostLookup] a WebAPK like PersaLink can only be told apart from its
     * host browser by turning the whole browser raw.
     */
    private val keyboard by lazy {
        KeyboardSession(
            this,
            settingsSource = (applicationContext as? SettingsSourceOwner)?.settingsSource,
            webApkHost = WebApkHostLookup.forContext(this),
            // app-shell.md SS10.2, SS10.7: a host without :app's wiring (a JVM test) simply reports nothing.
            rawDebugCaptureSink = (applicationContext as? DebugCaptureSinkOwner)?.debugCaptureSink,
        )
    }

    /**
     * spec: broker-privileged-toolbox.md SS7: the privileged setup pass runs at every IME start
     * (reason `ime_start`), off the main thread, because the IME is the process that survives
     * boot on this ROM (D17: a foreground service from BOOT_COMPLETED crashes). Guarded like
     * every other entry point Android calls here; a host without `:app`'s wiring gets null and
     * nothing runs.
     */
    override fun onCreate() {
        super.onCreate()
        // The panels' typefaces are read from disk once, here, off the main thread, so the first
        // Sym page never waits on them (docs/design/design-system.md, "Type").
        PhysiFonts.prewarm(this)
        runCatching { PrivilegedServices.from(this)?.runSetupAsync(SetupReasons.IME_START) }
            .onFailure { error -> Log.e(TAG, "privileged setup at IME start crashed", error) }
        // per-app-behavior.md SS16, keys-and-modifiers.md SS15.1: the accessibility service, when
        // the user has it on, hands this keyboard the keys Android does not send it.
        AccessibilityBridge.keyboard = accessibilityHook
    }

    private val accessibilityHook = AccessibilityBridge.KeyboardHook { event -> keyboard.onKeyEventFromAccessibility(event) }

    /**
     * No soft keyboard: the input view is an empty view of no height, and it is up only while
     * the Fill page's experimental password manager suggestions want it for the field
     * (layers-sym-alt.md SS4.7, D15: Android hands an input method those only while its input
     * view is up).
     */
    override fun onCreateInputView(): View = View(this).apply { minimumHeight = 0 }

    /**
     * A hardware keyboard is always present on this phone, so the answer is no, except while the
     * experimental inline suggestions want the empty input view up (`fill_inline_suggestions`,
     * off by default). The platform still wants its own implementation called, because it
     * records the configuration it was asked about; the answer it returns is simply not ours.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return keyboard.inlineInputViewWanted
    }

    /** layers-sym-alt.md SS4.7: null (the password manager keeps its drop-down) unless `fill_inline_suggestions` is on. */
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? = keyboard.onCreateInlineSuggestionsRequest()

    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean = keyboard.onInlineSuggestionsResponse(response)

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateCandidatesView(): View = keyboard.onCreateCandidatesView()

    /**
     * spec: status-bar.md SS3.2 and SS12.2. The platform's answer is kept (it is "no" on this
     * keyboard, see [onEvaluateInputViewShown]); what matters is that a refusal for a listed
     * app is the trigger of the per-app dip, and a configuration change never is.
     */
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        val shown = super.onShowInputRequested(flags, configChange)
        keyboard.onShowInputRequested(configurationChange = configChange, refused = !shown)
        return shown
    }

    /**
     * spec: status-bar.md SS12.2: "While the hold is on (the first 200 ms), every other request
     * to show the candidates view is refused, including PhysiBoard's own re-show", so that the
     * hide reaches the app as its own event (D5). A hide always goes through.
     */
    override fun setCandidatesViewShown(shown: Boolean) {
        if (shown && keyboard.refusesCandidatesShow()) return
        super.setCandidatesViewShown(shown)
    }

    /** spec: status-bar.md SS11: the inset policy is applied after the platform computed its own. */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        keyboard.onComputeInsets(outInsets)
    }

    /** spec: status-bar.md SS13: "When the window is shown again the strip is refreshed immediately." */
    override fun onWindowShown() {
        super.onWindowShown()
        keyboard.onKeyboardWindowShown()
    }

    /**
     * spec dictionaries-languages.md SS9.4: Android's own language-switch key or Settings >
     * Languages picking a different base subtype must resync the running keyboard, not just
     * PhysiBoard's own in-app cycle.
     */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        keyboard.onCurrentInputMethodSubtypeChanged(newSubtype)
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        keyboard.onStartInput(info, restarting)
    }

    override fun onFinishInput() {
        keyboard.onFinishInput()
        super.onFinishInput()
    }

    /** spec: dictation.md SS3, "Keyboard service destroyed: ... no session-end bookkeeping." */
    override fun onDestroy() {
        if (AccessibilityBridge.keyboard === accessibilityHook) AccessibilityBridge.keyboard = null
        keyboard.onServiceDestroyed()
        super.onDestroy()
    }

    /** spec: status-bar.md SS13 (unless a dip is in flight, SS12.2) and trackpad-caret-nav.md SS2.4. */
    override fun onWindowHidden() {
        keyboard.onKeyboardWindowHidden()
        super.onWindowHidden()
    }

    /** spec: trackpad-caret-nav.md SS4.7, the cursor-anchor reports [KeyboardSession] requests for the caret badge. */
    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        keyboard.onUpdateCursorAnchorInfo(cursorAnchorInfo)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        keyboard.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        keyboard.onKeyEventFromWindow(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        keyboard.onKeyEventFromWindow(event) || super.onKeyUp(keyCode, event)

    private companion object {
        const val TAG = "PhysiBoardIme"
    }
}
