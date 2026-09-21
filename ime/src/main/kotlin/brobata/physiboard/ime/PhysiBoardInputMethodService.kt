package brobata.physiboard.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View

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
 * on screen is the strip.
 */
class PhysiBoardInputMethodService : InputMethodService() {

    private val keyboard by lazy { KeyboardSession(this) }

    override fun onCreateInputView(): View? = null

    /** A hardware keyboard is always present on this phone; never take the screen. */
    override fun onEvaluateInputViewShown(): Boolean = false

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        keyboard.onKeyEvent(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        keyboard.onKeyEvent(event) || super.onKeyUp(keyCode, event)
}
