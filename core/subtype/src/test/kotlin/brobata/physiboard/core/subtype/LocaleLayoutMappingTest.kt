package brobata.physiboard.core.subtype

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: dictionaries-languages.md SS10 and SS16's T20-T22. */
class LocaleLayoutMappingTest {

    @Test
    fun `T20 de, de_DE and de-AT all resolve to qwertz with no override`() {
        assertEquals("qwertz", LocaleLayoutMapping.resolve("de"))
        assertEquals("qwertz", LocaleLayoutMapping.resolve("de_DE"))
        assertEquals("qwertz", LocaleLayoutMapping.resolve("de-AT"))
    }

    @Test
    fun `T21 en_GB falls back to qwerty since neither en_GB nor en is mapped`() {
        assertEquals("qwerty", LocaleLayoutMapping.resolve("en_GB"))
    }

    @Test
    fun `T22 an override for en_US wins over the asset and the asset is not consulted`() {
        val resolved = LocaleLayoutMapping.resolve("en_US", override = mapOf("en_US" to "azerty"))
        assertEquals("azerty", resolved)
    }

    @Test
    fun `a blank override value falls through to the asset`() {
        val resolved = LocaleLayoutMapping.resolve("de_DE", override = mapOf("de_DE" to ""))
        assertEquals("qwertz", resolved)
    }

    @Test
    fun `an override keyed by language covers every region of that language`() {
        val resolved = LocaleLayoutMapping.resolve("fr_CA", override = mapOf("fr" to "azerty"))
        assertEquals("azerty", resolved)
    }

    /** spec SS10: `:ime`'s own decoder for the file `:app`'s `LocaleLayoutOverrideStore` writes. */
    @Test
    fun `decodeOverride reads the same flat JSON object the settings screen writes`() {
        val decoded = LocaleLayoutMapping.decodeOverride("""{"it_IT":"qwertz","fr_FR":"azerty"}""")
        assertEquals(mapOf("it_IT" to "qwertz", "fr_FR" to "azerty"), decoded)
        assertEquals("qwertz", LocaleLayoutMapping.resolve("it_IT", override = decoded))
    }

    @Test
    fun `decodeOverride never throws on missing, blank or malformed text`() {
        assertEquals(emptyMap(), LocaleLayoutMapping.decodeOverride(null))
        assertEquals(emptyMap(), LocaleLayoutMapping.decodeOverride(""))
        assertEquals(emptyMap(), LocaleLayoutMapping.decodeOverride("not json"))
        assertEquals(emptyMap(), LocaleLayoutMapping.decodeOverride("[1,2,3]"))
    }
}
