package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: layers-sym-alt.md SS3.1, SS4.4, SS5.7 (the on-screen grid's per-letter lookup). */
class CharacterResolutionTest {

    @Test
    fun `symPageEntryText picks the uppercase entry only when shift is effective and present`() {
        val entry = SymPageEntry(lowercase = "$", uppercase = "USD")
        assertEquals("$", CharacterResolution.symPageEntryText(entry, shiftEffective = false))
        assertEquals("USD", CharacterResolution.symPageEntryText(entry, shiftEffective = true))
        assertNull(CharacterResolution.symPageEntryText(null, shiftEffective = false))
    }

    @Test
    fun `symPageEntryText falls back to lowercase when shift is effective but there is no uppercase entry`() {
        val entry = SymPageEntry(lowercase = "$")
        assertEquals("$", CharacterResolution.symPageEntryText(entry, shiftEffective = true))
    }

    @Test
    fun `symPageCharacters answers every mapped letter and omits the rest`() {
        val page = SymPageMap(
            mapOf(
                KeyId.Letter('M') to SymPageEntry(lowercase = "$"),
                KeyId.Letter('Q') to SymPageEntry(lowercase = "~"),
            ),
        )
        val characters = CharacterResolution.symPageCharacters(page, shiftEffective = false)
        assertEquals(mapOf('M' to "$", 'Q' to "~"), characters)
        assertEquals(2, characters.size, "letters with no entry (SS5.7: 'not tappable') are absent, not blank")
    }

    @Test
    fun `symPageCharacters honours a custom page's uppercase entry under shift`() {
        val page = SymPageMap(mapOf(KeyId.Letter('A') to SymPageEntry(lowercase = "a", uppercase = "A-UP")))
        assertEquals(mapOf('A' to "A-UP"), CharacterResolution.symPageCharacters(page, shiftEffective = true))
    }
}
