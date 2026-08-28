package brobata.physiboard.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CasingHelperTest {

    @Test
    fun testApplyCasing_StandardTransformations() {
        // Kleinschreibung beibehalten
        assertEquals("apple", CasingHelper.applyCasing("apple", "app"))
        
        // Title Case übernehmen
        assertEquals("Apple", CasingHelper.applyCasing("apple", "App"))
        
        // All Caps übernehmen
        assertEquals("APPLE", CasingHelper.applyCasing("apple", "APP"))
    }

    @Test
    fun testApplyCasing_DictionaryPriority() {
        // Binnengroßschreibung im Wörterbuch muss erhalten bleiben (z.B. McCartney)
        // User types lowercase but the dictionary entry has special casing
        assertEquals("McCartney", CasingHelper.applyCasing("McCartney", "mcc"))
        
        // Spezialfälle wie iPhone
        assertEquals("iPhone", CasingHelper.applyCasing("iPhone", "iph"))
        
        // When the user types in ALL CAPS, the dictionary word should be uppercased too
        assertEquals("MCCARTNEY", CasingHelper.applyCasing("McCartney", "MCC"))
    }

    @Test
    fun testApplyCasing_ForceLeadingCapital() {
        // Leading capital even when the user types lowercase (e.g. start of a sentence)
        assertEquals("Apple", CasingHelper.applyCasing("apple", "app", forceLeadingCapital = true))
        
        // Should also work for dictionary entries that are already capitalised
        assertEquals("McCartney", CasingHelper.applyCasing("McCartney", "mcc", forceLeadingCapital = true))
    }

    @Test
    fun testApplyCasing_EdgeCases() {
        // Leere Strings
        assertEquals("", CasingHelper.applyCasing("", "abc"))
        assertEquals("apple", CasingHelper.applyCasing("apple", ""))
        
        // No letters (digits/symbols)
        assertEquals("123", CasingHelper.applyCasing("123", "12"))
        
        // Single-letter words (checks the allUpper logic)
        assertEquals("A", CasingHelper.applyCasing("a", "A"))
        assertEquals("a", CasingHelper.applyCasing("a", "a"))
    }

    @Test
    fun testApplyCasing_PunctuationInOriginal() {
        // Apostrophes in the original are ignored when deciding the casing
        // l'am -> l'amico (klein)
        assertEquals("l'amico", CasingHelper.applyCasing("l'amico", "l'am"))
        
        // L'am -> L'amico (groß)
        assertEquals("L'amico", CasingHelper.applyCasing("l'amico", "L'am"))
    }
}

