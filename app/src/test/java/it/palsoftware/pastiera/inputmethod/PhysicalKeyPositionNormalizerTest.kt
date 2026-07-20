package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalKeyPositionNormalizerTest {
    @Test
    fun standardAlphabeticScanCodes_coverEveryCanonicalLetterPosition() {
        val expected = listOf(
            16 to KeyEvent.KEYCODE_Q,
            17 to KeyEvent.KEYCODE_W,
            18 to KeyEvent.KEYCODE_E,
            19 to KeyEvent.KEYCODE_R,
            20 to KeyEvent.KEYCODE_T,
            21 to KeyEvent.KEYCODE_Y,
            22 to KeyEvent.KEYCODE_U,
            23 to KeyEvent.KEYCODE_I,
            24 to KeyEvent.KEYCODE_O,
            25 to KeyEvent.KEYCODE_P,
            30 to KeyEvent.KEYCODE_A,
            31 to KeyEvent.KEYCODE_S,
            32 to KeyEvent.KEYCODE_D,
            33 to KeyEvent.KEYCODE_F,
            34 to KeyEvent.KEYCODE_G,
            35 to KeyEvent.KEYCODE_H,
            36 to KeyEvent.KEYCODE_J,
            37 to KeyEvent.KEYCODE_K,
            38 to KeyEvent.KEYCODE_L,
            44 to KeyEvent.KEYCODE_Z,
            45 to KeyEvent.KEYCODE_X,
            46 to KeyEvent.KEYCODE_C,
            47 to KeyEvent.KEYCODE_V,
            48 to KeyEvent.KEYCODE_B,
            49 to KeyEvent.KEYCODE_N,
            50 to KeyEvent.KEYCODE_M
        )

        expected.forEach { (scanCode, keyCode) ->
            assertEquals(
                keyCode,
                PhysicalKeyPositionNormalizer.canonicalAlphabeticKeyCode(scanCode)
            )
        }
    }

    @Test
    fun nonAlphabeticScanCode_isNotClaimed() {
        assertNull(PhysicalKeyPositionNormalizer.canonicalAlphabeticKeyCode(53))
    }
}
