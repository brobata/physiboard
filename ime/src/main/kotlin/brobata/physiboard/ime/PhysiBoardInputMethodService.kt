package brobata.physiboard.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

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
 * keyboard: [onEvaluateInputViewShown] stays false and the only surface it puts
 * on screen is the strip, rendered in the candidates view ([onCreateCandidatesView]).
 */
class PhysiBoardInputMethodService : InputMethodService() {

    private val keyboard by lazy { KeyboardSession(this) }

    override fun onCreateInputView(): View? = null

    /**
     * A hardware keyboard is always present on this phone, so the answer is always no.
     * The platform still wants its own implementation called, because it records the
     * configuration it was asked about; the answer it returns is simply not ours.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return false
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateCandidatesView(): View = keyboard.onCreateCandidatesView()

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
        keyboard.onDictationServiceDestroyed()
        super.onDestroy()
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
        keyboard.onKeyEvent(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        keyboard.onKeyEvent(event) || super.onKeyUp(keyCode, event)
}
