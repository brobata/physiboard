package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SpeechRecognitionManagerLanguageTagTest {

    @Test
    fun `uses ime subtype locale when available`() {
        val tag = SpeechRecognitionManager.buildRecognitionLanguageTag(
            imeSubtypeLocale = "fr_FR",
            deviceLocale = Locale.US
        )

        assertEquals("fr-FR", tag)
    }

    @Test
    fun `falls back to device locale when subtype locale missing`() {
        val tag = SpeechRecognitionManager.buildRecognitionLanguageTag(
            imeSubtypeLocale = null,
            deviceLocale = Locale.UK
        )

        assertEquals("en-GB", tag)
    }

    @Test
    fun `normalizes subtype locale with underscore`() {
        assertEquals(
            "it-IT",
            SpeechRecognitionManager.normalizeSubtypeLocaleToLanguageTag("it_IT")
        )
    }

    @Test
    fun `supports multiple common subtype locales`() {
        assertEquals("de-DE", SpeechRecognitionManager.normalizeSubtypeLocaleToLanguageTag("de_DE"))
        assertEquals("pt-BR", SpeechRecognitionManager.normalizeSubtypeLocaleToLanguageTag("pt_BR"))
        assertEquals("fr", SpeechRecognitionManager.normalizeSubtypeLocaleToLanguageTag("fr"))
        assertEquals("es-ES", SpeechRecognitionManager.normalizeSubtypeLocaleToLanguageTag("es-ES"))
    }

    @Test
    fun `supports script locales from subtype`() {
        assertEquals(
            "sr-Latn-RS",
            SpeechRecognitionManager.normalizeSubtypeLocaleToLanguageTag("sr_Latn_RS")
        )
    }

    @Test
    fun `returns null for invalid subtype locale`() {
        assertNull(SpeechRecognitionManager.normalizeSubtypeLocaleToLanguageTag("___"))
    }
}
