package brobata.physiboard.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent

/**
 * Where one key event meets the pipeline.
 *
 * Not yet wired: milestone 3 fills this in. It will normalise the event through
 * `:device:titan`, resolve it through `:core:keys`, run it through the
 * `:core:text` pipeline and apply the resulting operations to the editor.
 */
internal class KeyboardSession(@Suppress("unused") private val service: InputMethodService) {

    /** True when PhysiBoard consumed the event and the app must not see it. */
    fun onKeyEvent(@Suppress("unused") event: KeyEvent): Boolean = false
}
