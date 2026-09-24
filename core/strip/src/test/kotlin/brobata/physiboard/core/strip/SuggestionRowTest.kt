package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: status-bar.md SS5, SS18 rows T18 to T23. */
class SuggestionRowTest {

    private fun slots(row: SuggestionRow): SuggestionRow.Slots = assertIs(row)

    @Test
    fun `T18 three suggestions and no add-word map to third, first, second`() {
        val row = slots(SuggestionRowRules.map(listOf("a", "b", "c"), addWordCandidate = null))
        assertEquals(listOf("c", "a", "b"), row.texts)
        assertEquals(SlotKind.SUGGESTION, row.left.kind)
    }

    @Test
    fun `T19 one suggestion and an add-word put the add-word left and leave the right empty`() {
        val row = slots(SuggestionRowRules.map(listOf("a"), addWordCandidate = "z"))
        assertEquals(listOf("z", "a", ""), row.texts)
        assertEquals(SlotKind.ADD_WORD, row.left.kind)
        assertEquals(Slot.EMPTY, row.right)
        assertFalse(row.right.isTappable, "SS5.1: empty slots are not clickable")
    }

    @Test
    fun `T20 an add-word that duplicates a suggestion case-insensitively is dropped`() {
        val row = slots(SuggestionRowRules.map(listOf("a", "B"), addWordCandidate = "b"))
        assertEquals(listOf("", "a", "B"), row.texts)
        assertEquals(SlotKind.EMPTY, row.left.kind)
    }

    @Test
    fun `T21 an add-word with no suggestions spans the row`() {
        assertEquals(SuggestionRow.AddWordOnly("z"), SuggestionRowRules.map(emptyList(), addWordCandidate = "z"))
    }

    @Test
    fun `no suggestions and no add-word are three empty slots, still drawn`() {
        val row = slots(SuggestionRowRules.map(emptyList(), addWordCandidate = null))
        assertEquals(listOf("", "", ""), row.texts)
    }

    @Test
    fun `SS14 more than three suggestions are cut to three`() {
        val row = slots(SuggestionRowRules.map(listOf("a", "b", "c", "d"), addWordCandidate = null))
        assertEquals(listOf("c", "a", "b"), row.texts)
    }

    @Test
    fun `T22 flash index 0, 1, 2, 5 maps to center, right, left, nothing`() {
        assertEquals(SlotPosition.CENTER, SuggestionRowRules.flashSlotFor(0))
        assertEquals(SlotPosition.RIGHT, SuggestionRowRules.flashSlotFor(1))
        assertEquals(SlotPosition.LEFT, SuggestionRowRules.flashSlotFor(2))
        assertNull(SuggestionRowRules.flashSlotFor(5))
    }

    @Test
    fun `T23 trackpad thirds 0, 1, 2 map to suggestion index 2, 0, 1`() {
        assertEquals(2, SuggestionRowRules.trackpadThirdToSuggestionIndex(0))
        assertEquals(0, SuggestionRowRules.trackpadThirdToSuggestionIndex(1))
        assertEquals(1, SuggestionRowRules.trackpadThirdToSuggestionIndex(2))
        assertNull(SuggestionRowRules.trackpadThirdToSuggestionIndex(3))
    }

    @Test
    fun `SS5_2 expansion suggestions take the same three slots with no add-word`() {
        val row = slots(SuggestionRowRules.mapExpansion(listOf("first", "second", "third")))
        assertEquals(listOf("third", "first", "second"), row.texts)
        assertTrue(row.texts.isNotEmpty())
        assertEquals(SlotKind.EXPANSION, row.center.kind)
    }

    @Test
    fun `SS5_2 the row hides for suggestions off, a restricted field, a Sym page, the clipboard overlay, or no dictionary`() {
        fun visible(enabled: Boolean = true, field: Boolean = true, sym: Boolean = false, clip: Boolean = false, dict: Boolean = true, expansion: Boolean = false) =
            SuggestionRowRules.rowVisible(enabled, field, sym, clip, dict, expansion)
        assertTrue(visible())
        assertFalse(visible(enabled = false))
        assertFalse(visible(field = false))
        assertFalse(visible(sym = true))
        assertFalse(visible(clip = true))
        assertFalse(visible(dict = false))
    }

    @Test
    fun `SS5_2 expansion suggestions show even in a restricted field without a dictionary`() {
        assertTrue(SuggestionRowRules.rowVisible(suggestionsEnabled = true, fieldAllowsSuggestions = false, symPageOpen = false, clipboardOverlayOpen = false, dictionaryInstalled = false, expansionActive = true))
    }

    @Test
    fun `SS5_3 action mode offers the eye always and the trash only for a personal word`() {
        val slot = Slot("word", SlotKind.SUGGESTION)
        assertEquals(listOf(SlotActionButton.HIDE_SUGGESTION), SuggestionRowRules.actionModeButtons(slot, wordInPersonalDictionary = false))
        assertEquals(listOf(SlotActionButton.HIDE_SUGGESTION, SlotActionButton.DELETE_FROM_PERSONAL_DICTIONARY), SuggestionRowRules.actionModeButtons(slot, wordInPersonalDictionary = true))
    }

    @Test
    fun `SS17 long press on an empty or expansion slot does nothing`() {
        assertTrue(SuggestionRowRules.actionModeButtons(Slot.EMPTY, wordInPersonalDictionary = true).isEmpty())
        assertTrue(SuggestionRowRules.actionModeButtons(Slot("x", SlotKind.EXPANSION), wordInPersonalDictionary = true).isEmpty())
    }

    @Test
    fun `SS14 the flash lasts 160 ms`() {
        assertEquals(160L, SuggestionRowRules.FLASH_MS)
    }
}
