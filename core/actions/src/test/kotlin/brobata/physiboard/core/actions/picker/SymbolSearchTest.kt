package brobata.physiboard.core.actions.picker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS4.8. */
class SymbolSearchTest {

    private val names = mapOf(
        0x2192 to "RIGHTWARDS ARROW",
        0x21D2 to "RIGHTWARDS DOUBLE ARROW",
        0x2190 to "LEFTWARDS ARROW",
        0x2605 to "BLACK STAR",
        0x2606 to "WHITE STAR",
        0x2211 to "N-ARY SUMMATION",
        0x20AC to "EURO SIGN",
        0xA3 to "POUND SIGN",
        0x266A to "EIGHTH NOTE",
        0x2500 to "BOX DRAWINGS LIGHT HORIZONTAL",
        0x2014 to "EM DASH",
        0xA7 to "SECTION SIGN",
    )
    private val catalog = UnicodeSymbols.build(names = { names[it] })
    private val index = SymbolSearchIndex(catalog)

    private fun top(query: String): List<String> = index.search(query).map { it.symbol.text }

    @Test
    fun `symbols land in their groups and a shared code point goes to the claiming group`() {
        assertEquals(listOf("→", "←", "⇒").sorted(), catalog.symbolsOf("ARROWS").map { it.text }.sorted())
        // £ is in Latin-1's punctuation range too; Currency claims it.
        assertEquals(listOf("£", "€"), catalog.symbolsOf("CURRENCY").map { it.text })
        // ♪ sits inside the Dingbats & symbols block; Music claims it.
        assertEquals(listOf("♪"), catalog.symbolsOf("MUSIC").map { it.text })
        assertEquals(listOf("★", "☆"), catalog.symbolsOf("DINGBATS").map { it.text })
        assertTrue(catalog.tabs.none { it.searchOnly })
    }

    @Test
    fun `an exact name outranks a name it starts, and every word matching outranks a substring`() {
        assertEquals(NameSearch.SCORE_EQUALS - 2, index.search("euro sign").first().score)
        assertEquals("€", top("euro").first())
        assertEquals("€", top("euro sign").first())
    }

    @Test
    fun `query words match whole words or their starts in any order, the shorter name first`() {
        assertEquals(listOf("→", "⇒"), top("right arrow"))
        assertEquals(listOf("→", "⇒"), top("arrow right"))
        assertEquals("★", top("star").first())
        assertEquals(listOf("★", "☆"), top("star"))
        assertEquals("★", top("black star").first())
    }

    @Test
    fun `a prefix finds the long name and a substring is the last resort`() {
        assertEquals("∑", top("sum").first())
        assertEquals("∑", top("summation").first())
        // "mati" appears only inside "summation".
        assertEquals(listOf("∑"), top("mati"))
    }

    @Test
    fun `the symbol itself and its code point find it`() {
        assertEquals("§", top("§").first())
        assertEquals(SymbolSearchIndex.SCORE_SYMBOL, index.search("§").first().score)
        assertEquals("→", top("U+2192").first())
        assertEquals("→", top("u2192").first())
        assertEquals(SymbolSearchIndex.SCORE_CODE_POINT, index.search("U+2192").first().score)
    }

    @Test
    fun `nothing matches nonsense and an empty query gives nothing`() {
        assertTrue(top("zzqx").isEmpty())
        assertTrue(top("   ").isEmpty())
    }

    @Test
    fun `the ranking table`() {
        val q = NameSearch.Query("right arrow")
        assertEquals(NameSearch.SCORE_ALL_WORDS + NameSearch.SCORE_WHOLE_WORD_BONUS - 2, NameSearch.score(q, NameSearch.Term.of("RIGHTWARDS ARROW")))
        assertEquals(NameSearch.SCORE_EQUALS - 2, NameSearch.score(NameSearch.Query("Em-Dash"), NameSearch.Term.of("EM DASH")))
        assertEquals(NameSearch.SCORE_PREFIX - 2, NameSearch.score(NameSearch.Query("em d"), NameSearch.Term.of("EM DASH")))
        assertNull(NameSearch.score(NameSearch.Query("x"), NameSearch.Term.of("EM DASH")))
    }

    @Test
    fun `the platform names cover the real catalogue and stay searchable`() {
        val real = UnicodeSymbols.build()
        assertTrue(real.size > 3000, "only ${real.size} symbols")
        val realIndex = SymbolSearchIndex(real)
        assertEquals("→", realIndex.search("rightwards arrow").first().symbol.text)
        assertEquals("€", realIndex.search("euro").first().symbol.text)
        assertEquals("♪", realIndex.search("eighth note").first().symbol.text)
        assertEquals("─", realIndex.search("box drawings light horizontal").first().symbol.text)
        // No emoji pictograph leaks into Other.
        assertTrue(real.symbolsOf("OTHER").none { it.codePoint in 0x1F300..0x1FAFF })
    }
}
