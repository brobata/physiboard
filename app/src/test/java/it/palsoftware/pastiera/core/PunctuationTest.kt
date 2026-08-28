package it.palsoftware.pastiera.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationTest {

    @Test
    fun testIsWordBoundary_Whitespace() {
        assertTrue(Punctuation.isWordBoundary(' '))
        assertTrue(Punctuation.isWordBoundary('\n'))
        assertTrue(Punctuation.isWordBoundary('\t'))
    }

    @Test
    fun testIsWordBoundary_StandardPunctuation() {
        assertTrue(Punctuation.isWordBoundary('.'))
        assertTrue(Punctuation.isWordBoundary(','))
        assertTrue(Punctuation.isWordBoundary('!'))
        assertTrue(Punctuation.isWordBoundary('?'))
        assertTrue(Punctuation.isWordBoundary(';'))
        assertTrue(Punctuation.isWordBoundary(':'))
    }

    @Test
    fun testIsWordBoundary_LettersAndDigits() {
        assertFalse(Punctuation.isWordBoundary('a'))
        assertFalse(Punctuation.isWordBoundary('Z'))
        assertFalse(Punctuation.isWordBoundary('5'))
        assertFalse(Punctuation.isWordBoundary('ü')) // Unicode letters
    }

    @Test
    fun testIsWordBoundary_ApostropheLogic() {
        // Apostrophe in the middle of a word (e.g. l'amico)
        // ch='\'', prev='l' -> prevIsWord=true -> returns false (kein Boundary)
        assertFalse("An apostrophe after a letter should not be a boundary", 
            Punctuation.isWordBoundary('\'', prev = 'l'))

        // Apostrophe at the start of a word (e.g. 'hallo)
        // ch='\'', prev=' ' -> prevIsWord=false -> returns true (Boundary)
        assertTrue("An apostrophe after a space should be a boundary", 
            Punctuation.isWordBoundary('\'', prev = ' '))
        
        // Different apostrophe types
        assertFalse("A typographic apostrophe should be treated like the standard one",
            Punctuation.isWordBoundary('’', prev = 'd'))
    }

    @Test
    fun testIsWordBoundary_BracketsAndSymbols() {
        assertTrue(Punctuation.isWordBoundary('('))
        assertTrue(Punctuation.isWordBoundary(']'))
        assertTrue(Punctuation.isWordBoundary('/'))
        assertTrue(Punctuation.isWordBoundary('\\'))
        assertTrue(Punctuation.isWordBoundary('"'))
    }
}



