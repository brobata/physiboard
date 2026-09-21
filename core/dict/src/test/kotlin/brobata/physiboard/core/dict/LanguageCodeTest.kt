package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageCodeTest {

    @Test
    fun `T-a two-letter code parses and is lowercased`() {
        assertEquals("en", LanguageCode.of("EN")?.value)
        assertEquals("de", LanguageCode.of("de")?.value)
    }

    @Test
    fun `T-a code that is not exactly two letters is rejected`() {
        assertNull(LanguageCode.of("e"))
        assertNull(LanguageCode.of("eng"))
        assertNull(LanguageCode.of(""))
        assertNull(LanguageCode.of("e1"))
    }

    @Test
    fun `T-fromLocale strips a region joined by underscore or hyphen`() {
        assertEquals(LanguageCode.of("en"), LanguageCode.fromLocale("en_US"))
        assertEquals(LanguageCode.of("en"), LanguageCode.fromLocale("en-US"))
        assertEquals(LanguageCode.of("fr"), LanguageCode.fromLocale("fr_FR"))
        assertEquals(LanguageCode.of("en"), LanguageCode.fromLocale("en"))
    }

    @Test
    fun `T-en_US and en_GB share one language code`() {
        assertEquals(LanguageCode.fromLocale("en_US"), LanguageCode.fromLocale("en_GB"))
    }

    @Test
    fun `T-active languages drop x-pastiera, the primary, invalid tags and duplicates`() {
        val primary = LanguageCode.of("en")!!
        val active = ActiveLanguages.of(
            primary,
            listOf("fr-FR", "en_US", "x-pastiera", "not a code", "fr_FR", "it"),
        )

        assertEquals(primary, active.primary)
        assertEquals(listOf(LanguageCode.of("fr"), LanguageCode.of("it")), active.extra)
        assertEquals(listOf(primary, LanguageCode.of("fr")!!, LanguageCode.of("it")!!), active.all())
    }
}
