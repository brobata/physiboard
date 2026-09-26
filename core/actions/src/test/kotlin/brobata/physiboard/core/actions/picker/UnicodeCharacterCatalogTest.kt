package brobata.physiboard.core.actions.picker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS5.2, the Unicode character dialog's seven chips. */
class UnicodeCharacterCatalogTest {

    @Test
    fun `every category parses and is non-empty`() {
        val categories = UnicodeCharacterCatalog.CATEGORIES
        assertEquals(7, categories.size)
        for (category in categories) {
            assertTrue(category.glyphs.isNotEmpty(), "${category.label} must not be empty")
            assertTrue(category.glyphs.all { it.isNotEmpty() }, "${category.label} must have no blank glyphs")
        }
    }

    @Test
    fun `chip order matches the spec table`() {
        assertEquals(
            listOf("Punctuation", "Mathematical Symbols", "Currencies", "Technical Symbols", "Arrows", "Variations", "Miscellaneous"),
            UnicodeCharacterCatalog.CATEGORIES.map { it.label },
        )
    }

    @Test
    fun `the fixed chips carry the shipped glyphs`() {
        assertEquals(listOf("„", "“", "”"), UnicodeCharacterCatalog.PUNCTUATION.take(3))
        assertTrue("$" in UnicodeCharacterCatalog.CURRENCIES)
        assertTrue("→" in UnicodeCharacterCatalog.ARROWS)
        assertTrue("≠" in UnicodeCharacterCatalog.MATHEMATICAL_SYMBOLS)
        assertTrue("©" in UnicodeCharacterCatalog.TECHNICAL_SYMBOLS)
    }

    @Test
    fun `miscellaneous is the two whole code point ranges the spec names`() {
        val misc = UnicodeCharacterCatalog.MISCELLANEOUS
        assertEquals((0x2205..0x22FF).count() + (0x2100..0x214F).count(), misc.size)
        assertTrue("∅" in misc)
        assertTrue("⊂" in misc)
        assertTrue("⋿" in misc)
        assertTrue("℀" in misc)
        assertTrue("⅏" in misc)
    }

    @Test
    fun `variations groups accented Latin letters by base letter, uppercase before lowercase`() {
        val variations = UnicodeCharacterCatalog.VARIATIONS
        assertTrue(variations.isNotEmpty())
        assertTrue("Á" in variations)
        assertTrue("á" in variations)
        assertTrue(variations.indexOf("Á") < variations.indexOf("á"), "uppercase A-with-acute must come before lowercase")
        // Ligatures fold into their nearest letter (SS5.2's own note on ligatures).
        assertTrue("Æ" in variations)
        assertTrue("ß" in variations)
        // "about 560 glyphs" (SS5.2): this is a from-first-principles reconstruction, not a copy,
        // so only the right order of magnitude is asserted.
        assertTrue(variations.size in 100..1200, "expected a few hundred glyphs, got ${variations.size}")
    }
}
