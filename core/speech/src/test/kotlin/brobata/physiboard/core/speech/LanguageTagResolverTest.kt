package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: dictation.md SS5.1, SS16, T12-T16. */
class LanguageTagResolverTest {

    @Test
    fun `T12 subtype wins over device locale`() {
        assertEquals("fr-FR", LanguageTagResolver.resolve("fr_FR", "en-US"))
    }

    @Test
    fun `T13 missing subtype falls to device locale`() {
        assertEquals("en-GB", LanguageTagResolver.resolve(null, "en-GB"))
    }

    @Test
    fun `T14 underscore and hyphen tags normalize the same way`() {
        assertEquals("it-IT", LanguageTagResolver.resolve("it_IT", null))
        assertEquals("de-DE", LanguageTagResolver.resolve("de_DE", null))
        assertEquals("pt-BR", LanguageTagResolver.resolve("pt_BR", null))
        assertEquals("fr", LanguageTagResolver.resolve("fr", null))
        assertEquals("es-ES", LanguageTagResolver.resolve("es-ES", null))
        assertEquals("sr-Latn-RS", LanguageTagResolver.resolve("sr_Latn_RS", null))
    }

    @Test
    fun `T15 a subtype with no language falls through to the device locale`() {
        assertEquals("en-GB", LanguageTagResolver.resolve("___", "en-GB"))
    }

    @Test
    fun `T16 no subtype and no device locale falls to English, not 2x's Italian leftover`() {
        // spec: dictionaries-languages.md SS17 Keep/Drop: "Italian fallback when there is no
        // subtype: Replace with English, upstream leftover." T16 itself still names `it-IT` as
        // 2.0.7's own answer; this is the document's explicit 3.0 departure from it.
        assertEquals("en-US", LanguageTagResolver.resolve(null, null))
        assertEquals("en-US", LanguageTagResolver.resolve(null, ""))
    }
}
