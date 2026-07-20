package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent

/**
 * Resolves stable Linux scan codes to canonical alphabetic key positions.
 *
 * A canonical key code describes the physical QWERTY position. Input-language
 * mappings such as QWERTZ or AZERTY are deliberately applied later.
 */
internal object PhysicalKeyPositionNormalizer {
    fun canonicalAlphabeticKeyCode(scanCode: Int): Int? = when (scanCode) {
        16 -> KeyEvent.KEYCODE_Q
        17 -> KeyEvent.KEYCODE_W
        18 -> KeyEvent.KEYCODE_E
        19 -> KeyEvent.KEYCODE_R
        20 -> KeyEvent.KEYCODE_T
        21 -> KeyEvent.KEYCODE_Y
        22 -> KeyEvent.KEYCODE_U
        23 -> KeyEvent.KEYCODE_I
        24 -> KeyEvent.KEYCODE_O
        25 -> KeyEvent.KEYCODE_P
        30 -> KeyEvent.KEYCODE_A
        31 -> KeyEvent.KEYCODE_S
        32 -> KeyEvent.KEYCODE_D
        33 -> KeyEvent.KEYCODE_F
        34 -> KeyEvent.KEYCODE_G
        35 -> KeyEvent.KEYCODE_H
        36 -> KeyEvent.KEYCODE_J
        37 -> KeyEvent.KEYCODE_K
        38 -> KeyEvent.KEYCODE_L
        44 -> KeyEvent.KEYCODE_Z
        45 -> KeyEvent.KEYCODE_X
        46 -> KeyEvent.KEYCODE_C
        47 -> KeyEvent.KEYCODE_V
        48 -> KeyEvent.KEYCODE_B
        49 -> KeyEvent.KEYCODE_N
        50 -> KeyEvent.KEYCODE_M
        else -> null
    }
}
