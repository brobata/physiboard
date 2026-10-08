package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: layers-sym-alt.md SS8 (3.0): the built-in table, the language order, the user's lists, the accent chooser; SS14 cases 68-80. */
class VariationsTest {

    @Test
    fun `case 68 - Polish puts its own letters first`() {
        val pl = Variations.defaults("pl_PL")
        assertEquals("ą", pl.getValue('a').first())
        assertEquals("ć", pl.getValue('c').first())
        assertEquals("ę", pl.getValue('e').first())
        assertEquals("ł", pl.getValue('l').first())
        assertEquals("ń", pl.getValue('n').first())
        assertEquals("ó", pl.getValue('o').first())
        assertEquals("ś", pl.getValue('s').first())
        assertEquals(listOf("ź", "ż"), pl.getValue('z').take(2))
        assertEquals("Ą", pl.getValue('A').first())
        assertEquals(listOf("Ź", "Ż"), pl.getValue('Z').take(2))
    }

    @Test
    fun `case 69 - each language's own letters lead, and nothing is lost by reordering`() {
        val neutral = Variations.defaults(null)
        val expectedFirst = mapOf(
            "fr_FR" to mapOf('e' to "é", 'c' to "ç", 'o' to "ô"),
            "de_DE" to mapOf('a' to "ä", 'o' to "ö", 'u' to "ü", 's' to "ß"),
            "es_ES" to mapOf('n' to "ñ", 'a' to "á"),
            "pt_PT" to mapOf('a' to "ã", 'o' to "õ"),
            "it_IT" to mapOf('e' to "è", 'o' to "ò"),
            "cs" to mapOf('r' to "ř", 'u' to "ú"),
            "ro" to mapOf('a' to "ă", 's' to "ș", 't' to "ț"),
            "tr" to mapOf('i' to "ı", 'g' to "ğ"),
            "sv" to mapOf('a' to "å"),
            "da_DK" to mapOf('o' to "ø"),
            "nb_NO" to mapOf('a' to "å", 'o' to "ø"),
            "hu" to mapOf('o' to "ó", 'u' to "ú"),
        )
        for ((locale, firsts) in expectedFirst) {
            val table = Variations.defaults(locale)
            for ((base, first) in firsts) assertEquals(first, table.getValue(base).first(), "$locale '$base'")
            for ((base, list) in neutral) assertEquals(list.sorted(), table.getValue(base).sorted(), "$locale '$base' keeps every entry")
        }
        assertEquals("İ", Variations.defaults("tr_TR").getValue('I').first(), "Turkish capital I offers the dotted capital first")
    }

    @Test
    fun `case 70 - a language with no letters of its own, or none at all, gets the neutral order`() {
        assertEquals(Variations.defaults(null), Variations.defaults("en_US"))
        assertEquals(Variations.defaults(""), Variations.defaults("xx"))
        assertEquals(listOf("à", "á", "â", "ä", "ã", "å", "ā", "ą", "ă", "æ"), Variations.defaults(null).getValue('a'))
    }

    @Test
    fun `case 71 - no list is longer than the ten pick keys, and capitals match their small letters`() {
        for (locale in listOf(null) + Variations.languagesWithOwnOrder) {
            val table = Variations.defaults(locale)
            assertTrue(table.values.all { it.size <= Variations.MAX_PER_CHARACTER }, "$locale")
            for (base in Variations.latinBases) {
                assertEquals(table.getValue(base).size, table.getValue(base.uppercaseChar()).size, "$locale '$base'")
            }
        }
        val neutral = Variations.defaults(null)
        assertEquals(listOf("ẞ", "Ś", "Š", "Ş", "Ș", "$"), neutral.getValue('S'))
        assertEquals("€", neutral.getValue('E').last(), "a currency sign stays as it is on the capital")
    }

    @Test
    fun `case 72 - language tags read leniently`() {
        assertEquals("pl", Variations.languageOf("pl_PL"))
        assertEquals("pt", Variations.languageOf("pt-BR"))
        assertEquals("no", Variations.languageOf("nb"))
        assertEquals("no", Variations.languageOf("nn_NO"))
        assertEquals("", Variations.languageOf(null))
    }

    @Test
    fun `case 73 - the user's list replaces the built-in one for that character only, as saved`() {
        val table = Variations.effective("pl_PL", mapOf('a' to listOf("à", "ą", "中"), 'q' to listOf("¿")))
        assertEquals(listOf("à", "ą", "中"), table.listFor('a'))
        assertEquals(listOf("¿"), table.listFor('q'))
        assertEquals("ę", table.listFor('e').first(), "a character the user left alone keeps the language order")
        assertEquals("Ą", table.listFor('A').first(), "the capital key has its own list")
    }

    @Test
    fun `case 74 - an empty saved list means no variations for that character`() {
        val table = Variations.effective(null, mapOf('a' to emptyList()))
        assertEquals(emptyList(), table.listFor('a'))
        assertTrue(table.listFor('e').isNotEmpty())
    }

    @Test
    fun `case 75 - a saved list is cleaned - blanks, duplicates, overlong entries and extras dropped`() {
        val long = "x".repeat(40)
        val cleaned = Variations.clean(listOf("a", " ", "", "a", long) + (1..20).map { "$it" })
        assertEquals("a", cleaned[0])
        assertEquals(Variations.MAX_ENTRY_LENGTH, cleaned[1].length)
        assertEquals(Variations.MAX_PER_CHARACTER, cleaned.size)
        assertEquals(mapOf('a' to listOf("ą")), Variations.overridesFromStored(mapOf("a" to listOf("ą"), "ab" to listOf("x"), "" to listOf("y"))))
    }

    // The accent chooser, SS8.4 ---------------------------------------------------------------

    private val a = KeyId.Letter('A')
    private val w = KeyId.Letter('W')
    private val open = VariationChooser.State(heldKey = a, choices = listOf("ą", "à", "á"), committed = "ą")

    @Test
    fun `case 76 - the chooser opens only when there is more than one variation`() {
        assertFalse(VariationChooser.opens(listOf("ü")))
        assertTrue(VariationChooser.opens(listOf("ü", "ú")))
    }

    @Test
    fun `case 77 - choices are labelled 1 to 9 then 0, and a key picks by the digit printed on it`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0), (0..9).map(VariationChooser::digitForIndex))
        assertEquals((0..9).toList(), listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0).map(VariationChooser::indexForDigit))
        assertEquals(1, VariationChooser.digitFor("1", null))
        assertEquals(null, VariationChooser.digitFor("@", null))
        assertEquals(7, VariationChooser.digitFor(null, '7'))
    }

    @Test
    fun `case 78 - a pick key picks while the bar is open, held or not, and Alt still arms`() {
        assertEquals(VariationChooser.KeyOutcome.Pick(0), VariationChooser.onKeyDown(open, w, 0, digit = 1, altHeld = false))
        val released = VariationChooser.onKeyUp(open, a)
        assertFalse(released.heldKeyDown)
        assertEquals(VariationChooser.KeyOutcome.Pick(0), VariationChooser.onKeyDown(released, w, 0, digit = 1, altHeld = false), "let go, then W: picks")
        assertEquals(VariationChooser.KeyOutcome.Pick(2), VariationChooser.onKeyDown(released, KeyId.Letter('R'), 0, digit = 3, altHeld = false), "let go, then R: the third")
        assertEquals(VariationChooser.KeyOutcome.Pick(1), VariationChooser.onKeyDown(released, KeyId.Letter('E'), 0, digit = 2, altHeld = true))
        assertEquals(VariationChooser.KeyOutcome.ArmAlt, VariationChooser.onKeyDown(released, KeyId.Modifier(ModifierKey.ALT), 0, digit = null, altHeld = false))
        assertEquals(VariationChooser.KeyOutcome.Pick(2), VariationChooser.onKeyDown(released.copy(altArmed = true), KeyId.Letter('R'), 0, digit = 3, altHeld = false))
        assertEquals(VariationChooser.KeyOutcome.CloseAndPassOn, VariationChooser.onKeyDown(open, KeyId.Letter('S'), 0, digit = 4, altHeld = false), "no fourth choice")
        assertEquals(open, VariationChooser.onKeyUp(open, w), "another key's release changes nothing")
    }

    @Test
    fun `after the release, the same letter again cycles to the next accent and any other letter types on`() {
        val released = VariationChooser.onKeyUp(open, a)
        assertEquals(VariationChooser.KeyOutcome.Cycle, VariationChooser.onKeyDown(released, a, 0, digit = null, altHeld = false))
        assertEquals(VariationChooser.KeyOutcome.CloseAndPassOn, VariationChooser.onKeyDown(released, KeyId.Letter('G'), 0, digit = null, altHeld = false), "a letter with no listed digit types on")
        assertEquals(VariationChooser.KeyOutcome.Swallow, VariationChooser.onKeyDown(open, a, 3, digit = null, altHeld = false), "the hold's own repeat is not a cycle")
        assertEquals(1, VariationChooser.nextIndex(released))
        val last = released.copy(committed = released.choices.last())
        assertEquals(0, VariationChooser.nextIndex(last), "wraps round to the first")
    }

    @Test
    fun `case 79 - repeats, Back and other keys`() {
        assertEquals(VariationChooser.KeyOutcome.Swallow, VariationChooser.onKeyDown(open, a, 3, digit = null, altHeld = false))
        assertEquals(VariationChooser.KeyOutcome.PassOnKeepOpen, VariationChooser.onKeyDown(open, KeyId.Modifier(ModifierKey.SHIFT), 2, digit = null, altHeld = false))
        assertEquals(VariationChooser.KeyOutcome.Dismiss, VariationChooser.onKeyDown(open, KeyId.Control(ControlKey.BACK), 0, digit = null, altHeld = false))
        assertEquals(VariationChooser.KeyOutcome.CloseAndPassOn, VariationChooser.onKeyDown(open, KeyId.Control(ControlKey.SPACE), 0, digit = null, altHeld = false))
        assertEquals(VariationChooser.KeyOutcome.CloseAndPassOn, VariationChooser.onKeyDown(open, KeyId.Letter('H'), 0, digit = null, altHeld = false))
    }

    @Test
    fun `case 80 - a pick replaces only while the text before the caret still ends with the last choice`() {
        assertTrue(VariationChooser.canReplace("zażółć ą", "ą", terminalMode = false))
        assertFalse(VariationChooser.canReplace("zażółć a", "ą", terminalMode = false))
        assertTrue(VariationChooser.canReplace(null, "ą", terminalMode = false), "an unreadable field is trusted")
        assertTrue(VariationChooser.canReplace("", "ą", terminalMode = false), "an empty read (a web field) is trusted")
        assertTrue(VariationChooser.canReplace("", "ą", terminalMode = true), "a terminal empties its text box after every key")
    }
}
