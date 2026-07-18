package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInputSettingsScreenTest {

    @Test
    fun quoteIsVisibleAndToggleableAsAutoSpacePunctuation() {
        assertTrue('"' in autoSpacePunctuationOptions())
        assertEquals("\"", autoSpacePunctuationLabel('"'))
        assertEquals("\"", toggleAutoSpacePunctuation("", '"'))
        assertEquals("", toggleAutoSpacePunctuation("\"", '"'))
    }
}
