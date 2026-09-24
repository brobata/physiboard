package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: status-bar.md SS1, SS4, SS5, SS6.2, SS7, SS8, SS17: the snapshot the strip draws, region by region. */
class StripModelTest {

    private val settings = StripSettings()
    private fun inputs(vararg suggestions: String, packageName: String? = "com.example.app") = StripInputs(packageName = packageName, suggestions = suggestions.toList())

    @Test
    fun `a fresh install shows clipboard left, microphone right, the row mapped, EN for the language`() {
        val model = StripModel.build(inputs("hello", "help", "held").copy(subtypeLocale = "en_US"), settings)
        assertEquals(StripFootprint.SHOWN, model.footprint)
        assertEquals(listOf(StripButton.CLIPBOARD), model.leftButtons)
        assertEquals(listOf(StripButton.MICROPHONE), model.rightButtons, "SS6.3: the `none` entry draws nothing")
        assertEquals(SuggestionRow.Slots(Slot("held", SlotKind.SUGGESTION), Slot("hello", SlotKind.SUGGESTION), Slot("help", SlotKind.SUGGESTION)), model.row)
        assertEquals("EN", model.languageLabel)
        assertNull(model.leds, "Slate Dark ships with LEDs off")
        assertFalse(model.quickActionsOpen)
    }

    @Test
    fun `SS3_5 mode NEVER collapses the strip but the model still carries the buttons`() {
        val model = StripModel.build(inputs("a"), settings.copy(visibility = StripVisibilityMode.NEVER))
        assertEquals(StripFootprint.COLLAPSED, model.footprint)
        assertEquals(listOf(StripButton.CLIPBOARD), model.leftButtons)
    }

    @Test
    fun `SS17 a Sym page open hides the row but shows the strip even when the mode hides it`() {
        val model = StripModel.build(inputs("a").copy(modifiers = ModifierIndicatorInput(symPage = 2)), settings.copy(visibility = StripVisibilityMode.NEVER))
        assertEquals(StripFootprint.SHOWN, model.footprint)
        assertEquals(SuggestionRow.Hidden, model.row)
    }

    @Test
    fun `SS17 no dictionary for the language removes the slots and keeps the buttons`() {
        val model = StripModel.build(inputs("a").copy(dictionaryInstalled = false), settings)
        assertEquals(SuggestionRow.Hidden, model.row)
        assertTrue(model.leftButtons.isNotEmpty())
    }

    @Test
    fun `SS5_2 expansion suggestions beat a missing dictionary and a restricted field`() {
        val model = StripModel.build(inputs("a").copy(dictionaryInstalled = false, fieldAllowsSuggestions = false, expansionSuggestions = listOf("x", "y")), settings)
        assertEquals(SuggestionRow.Slots(Slot.EMPTY, Slot("x", SlotKind.EXPANSION), Slot("y", SlotKind.EXPANSION)), model.row)
    }

    @Test
    fun `SS17 suggestions off leaves the buttons at full height`() {
        val model = StripModel.build(inputs("a").copy(suggestionsEnabled = false), settings)
        assertEquals(SuggestionRow.Hidden, model.row)
        assertEquals(StripFootprint.SHOWN, model.footprint)
    }

    @Test
    fun `SS7 the LED row follows the theme's show_leds and the modifier table`() {
        val leds = settings.copy(theme = settings.theme.copy(showLeds = true))
        val model = StripModel.build(inputs().copy(modifiers = ModifierIndicatorInput(capsLockOn = true, ctrlOneShotArmed = true, altLatched = true, symPage = 1)), leds)
        assertEquals(LedRow(shift = LedLevel.LOCKED, sym = LedLevel.ACTIVE, ctrl = LedLevel.ACTIVE, alt = LedLevel.LOCKED), model.leds)
        assertEquals(listOf(LedLevel.LOCKED, LedLevel.ACTIVE, null, null, LedLevel.ACTIVE, LedLevel.LOCKED), model.leds?.positions)
    }

    @Test
    fun `SS7 a Shift one-shot lights active, the symbols page locked, nothing lights for a plain hold`() {
        val row = LedRow.from(ModifierIndicatorInput(shiftOneShotArmed = true, symPage = 2))
        assertEquals(LedLevel.ACTIVE, row.shift)
        assertEquals(LedLevel.LOCKED, row.sym)
        assertEquals(LedLevel.INACTIVE, row.ctrl)
        assertEquals(LedRow(LedLevel.INACTIVE, LedLevel.INACTIVE, LedLevel.INACTIVE, LedLevel.INACTIVE), LedRow.from(ModifierIndicatorInput()))
    }

    @Test
    fun `SS7 Ctrl latched including nav mode lights locked`() {
        assertEquals(LedLevel.LOCKED, LedRow.from(ModifierIndicatorInput(ctrlLatched = true, ctrlOneShotArmed = true)).ctrl)
    }

    @Test
    fun `SS8 the live strip never shows modifier chips and the preview chips Shift and Alt only`() {
        assertFalse(ModifierChips.VISIBLE_IN_LIVE_STRIP)
        val chips = ModifierChips.forPreview(ModifierIndicatorInput(capsLockOn = true, altOneShotArmed = true, ctrlLatched = true, symPage = 2))
        assertEquals(listOf(ModifierChip(ModifierChip.ChipModifier.SHIFT, LedLevel.LOCKED), ModifierChip(ModifierChip.ChipModifier.ALT, LedLevel.ACTIVE)), chips)
        assertTrue(ModifierChips.forPreview(ModifierIndicatorInput(shiftOneShotArmed = false)).isEmpty())
    }

    @Test
    fun `SS17 modifier held (Shift down) changes nothing in the strip`() {
        val idle = StripModel.build(inputs("a"), settings)
        val heldIsNotAnInput = StripModel.build(inputs("a").copy(modifiers = ModifierIndicatorInput()), settings)
        assertEquals(idle, heldIsNotAnInput)
    }

    @Test
    fun `SS17 the hamburger overlay is closed by every refresh in hardware mode`() {
        assertFalse(StripModel.build(inputs("a"), settings.copy(slots = ButtonSlots.DEFAULT)).quickActionsOpen)
    }

    @Test
    fun `SS6_2 the first left and the last right buttons are edge buttons only with rounded corners`() {
        val model = StripModel.build(inputs(), settings.copy(slots = ButtonSlots.DEFAULT))
        assertTrue(model.isEdgeButton(StripSide.LEFT, 0, roundedCorners = true))
        assertFalse(model.isEdgeButton(StripSide.RIGHT, 0, roundedCorners = true))
        assertTrue(model.isEdgeButton(StripSide.RIGHT, 1, roundedCorners = true))
        assertFalse(model.isEdgeButton(StripSide.LEFT, 0, roundedCorners = false))
    }

    @Test
    fun `SS1 the strip is redrawn only when the snapshot changed`() {
        val a = StripModel.build(inputs("a"), settings)
        val same = StripModel.build(inputs("a"), settings)
        val different = StripModel.build(inputs("a", "b"), settings)
        assertEquals(a, same)
        assertNotEquals(a, different)
    }

    @Test
    fun `SS6_1 dictation active and the clipboard count travel with the snapshot`() {
        val model = StripModel.build(inputs().copy(dictationActive = true, clipboardCount = 4), settings)
        assertTrue(model.dictationActive)
        assertEquals(4, model.clipboardCount)
    }

    @Test
    fun `SS15 the shipped defaults are the spec's`() {
        assertEquals(StripVisibilityMode.ALWAYS, settings.visibility, "D9: the shipped first-run default is Always")
        assertEquals(56, settings.barHeightDp)
        assertEquals(StripDip.SEEDED_APPS, settings.dipApps)
        assertTrue(settings.roundedCorners, "D3")
        assertEquals(StripTheme.SLATE_DARK, settings.theme)
        assertEquals(1.4, settings.theme.suggestionsHeightScale, "D4")
    }
}
