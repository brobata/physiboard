package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardThemeColorTest {

    @Test
    fun parsesSixDigitRgbWithOptionalHash() {
        assertEquals(0xFF12ABEF.toInt(), parseKeyboardThemeHexColor("#12abef"))
        assertEquals(0xFF12ABEF.toInt(), parseKeyboardThemeHexColor("12ABEF"))
    }

    @Test
    fun rejectsIncompleteAndInvalidValues() {
        assertNull(parseKeyboardThemeHexColor("#12345"))
        assertNull(parseKeyboardThemeHexColor("#12345G"))
        assertNull(parseKeyboardThemeHexColor("#00112233"))
    }
}
