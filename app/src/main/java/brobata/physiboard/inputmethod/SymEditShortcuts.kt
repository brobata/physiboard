package brobata.physiboard.inputmethod

import android.view.KeyEvent

/**
 * Sym + C / V / X / A as copy, paste, cut and select-all inside a text field.
 *
 * Copy and paste otherwise live on Ctrl, which on the Titan is the Fn key only once the user
 * has remapped it; people reach for Sym first and got an emoji or an app launch instead.
 */
object SymEditShortcuts {
    /** The context-menu action for [keyCode], or null when the key is not an edit shortcut. */
    fun actionFor(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_C -> android.R.id.copy
        KeyEvent.KEYCODE_V -> android.R.id.paste
        KeyEvent.KEYCODE_X -> android.R.id.cut
        KeyEvent.KEYCODE_A -> android.R.id.selectAll
        else -> null
    }
}
