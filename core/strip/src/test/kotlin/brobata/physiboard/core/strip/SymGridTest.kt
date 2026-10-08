package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: layers-sym-alt.md SS5.7 (the on-screen grid for the Emoji and Symbols key layers). */
class SymGridTest {

    @Test
    fun `forPageNumber answers only the two key-layer pages`() {
        assertEquals(SymGridPage.EMOJI, SymGridPage.forPageNumber(SYM_PAGE_EMOJI))
        assertEquals(SymGridPage.SYMBOLS, SymGridPage.forPageNumber(SYM_PAGE_SYMBOLS))
        assertNull(SymGridPage.forPageNumber(0), "page 0 (closed) has no grid")
        assertNull(SymGridPage.forPageNumber(SYM_PAGE_CLIPBOARD), "panels (3, 4) have no grid")
        assertNull(SymGridPage.forPageNumber(SYM_PAGE_EMOJI_PICKER))
    }

    @Test
    fun `the three rows are Q-P, A-L plus a blank, and Z-C, pencil, globe, B-M plus a blank`() {
        val rows = SymGridLayout.rows
        assertEquals(3, rows.size)
        assertEquals("QWERTYUIOP", rows[0].joinToString("") { (it as SymGridSlot.LetterKey).letter.letter.toString() })
        assertEquals(10, rows[1].size, "row 2 gets one blank cell at the end (SS5.7)")
        assertEquals(SymGridSlot.Blank, rows[1].last())
        assertEquals("ASDFGHJKL", rows[1].dropLast(1).joinToString("") { (it as SymGridSlot.LetterKey).letter.letter.toString() })
        assertEquals(10, rows[2].size, "row 3 is Z X C V, pencil, globe, B N M, blank (SS5.7)")
        assertEquals(listOf('Z', 'X', 'C', 'V'), rows[2].take(4).map { (it as SymGridSlot.LetterKey).letter.letter })
        assertEquals(SymGridSlot.Pencil, rows[2][4])
        assertEquals(SymGridSlot.Globe, rows[2][5])
        assertEquals(listOf('B', 'N', 'M'), rows[2].subList(6, 9).map { (it as SymGridSlot.LetterKey).letter.letter })
        assertEquals(SymGridSlot.Blank, rows[2].last())
    }

    @Test
    fun `every row has 10 columns, the widest row's width`() {
        assertTrue(SymGridLayout.rows.all { it.size == SymGridLayout.COLUMNS })
    }

    @Test
    fun `the Titan's geometry matches the spec's SS5-7 numbers`() {
        val geometry = SymGridGeometry.forScreenWidth(1080, StripGeometry.TITAN_PX_PER_DP)
        assertEquals(105, geometry.keyHeightPx, "56 dp at 1.875 px per dp")
        assertEquals(7, geometry.spacingPx, "4 dp spacing")
        assertEquals(11, geometry.cornerPx, "6 dp corners")
        assertEquals(1, geometry.borderPx)
        // (1080 - 16*1.875 - 9*7) / 10 = (1080 - 30 - 63) / 10 = 98.7 -> 98
        assertEquals(98, geometry.keyWidthPx)
        assertEquals(78, geometry.emojiCharacterPx, "0.75 of the 105 px key height")
        assertEquals(52, geometry.symbolsCharacterPx, "0.5 of the 105 px key height")
        assertEquals(329, geometry.contentHeightPx, "3 rows of 105 px keys plus 2 gaps of 7 px")
        assertEquals(15, geometry.sideInsetPx, "half of 16 dp at 1.875 px per dp")
    }

    @Test
    fun `characterPx picks the ratio for the open page`() {
        val geometry = SymGridGeometry.forScreenWidth(1080, StripGeometry.TITAN_PX_PER_DP)
        assertEquals(geometry.emojiCharacterPx, geometry.characterPx(SymGridPage.EMOJI))
        assertEquals(geometry.symbolsCharacterPx, geometry.characterPx(SymGridPage.SYMBOLS))
    }

    @Test
    fun `a key with a character is tappable, one with none is not`() {
        val rows = SymGridModel.rows(mapOf(SymGridLetter.M to "$"))
        val keyM = rows.flatten().filterIsInstance<SymGridCell.Key>().first { it.letter == SymGridLetter.M }
        val keyQ = rows.flatten().filterIsInstance<SymGridCell.Key>().first { it.letter == SymGridLetter.Q }
        assertEquals("$", keyM.character)
        assertTrue(keyM.isTappable)
        assertNull(keyQ.character)
        assertFalse(keyQ.isTappable, "a key with no character is not tappable (SS5.7)")
        assertEquals('M', keyM.label)
    }

    @Test
    fun `blank, pencil and globe slots carry through as their own cells`() {
        val rows = SymGridModel.rows(emptyMap())
        assertTrue(rows[1].last() is SymGridCell.Blank)
        assertTrue(rows[2][4] is SymGridCell.Pencil)
        assertTrue(rows[2][5] is SymGridCell.Globe)
    }

    @Test
    fun `the Symbols page puts its search in row 2's spare cell, and corner insets narrow the keys`() {
        val rows = SymGridModel.rows(emptyMap(), withSearch = true)
        assertEquals(SymGridCell.Search, rows[1].last())
        assertEquals(SymGridCell.Blank, rows[2].last())
        assertEquals(SymGridCell.Blank, SymGridModel.rows(emptyMap())[1].last())
        val plain = SymGridGeometry.forScreenWidth(1076, 1.875f)
        val inset = SymGridGeometry.forScreenWidth(1076, 1.875f, cornerSideInsetPx = 33)
        assertTrue(inset.keyWidthPx * 10 + 9 * inset.spacingPx + 2 * inset.sideInsetPx <= 1076 - 66)
        assertTrue(inset.keyWidthPx < plain.keyWidthPx)
    }
}
