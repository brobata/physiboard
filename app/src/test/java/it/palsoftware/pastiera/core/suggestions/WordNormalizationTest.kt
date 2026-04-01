package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WordNormalizationTest {

    @Test
    fun testNormalizeForDictionary_FoldsCommonLigatures() {
        assertEquals("oeil", WordNormalization.normalizeForDictionary("œil", Locale.FRENCH))
        assertEquals("oeil", WordNormalization.normalizeForDictionary("oeil", Locale.FRENCH))
        assertEquals("aether", WordNormalization.normalizeForDictionary("Æther", Locale.GERMAN))
        assertEquals("strasse", WordNormalization.normalizeForDictionary("Straße", Locale.GERMAN))
    }

    @Test
    fun testNormalizeForSuggestion_KeepsApostrophes() {
        assertEquals("l'oeil", WordNormalization.normalizeForSuggestion("l’Œil", Locale.FRENCH))
    }
}
