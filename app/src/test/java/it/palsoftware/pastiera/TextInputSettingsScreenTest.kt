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

    @Test
    fun closingBracketsAreVisibleWithoutOfferingOpeningBrackets() {
        val options = autoSpacePunctuationOptions()

        assertTrue(listOf(')', ']', '}').all { it in options })
        assertTrue(listOf('(', '[', '{').none { it in options })
    }
}
