package brobata.physiboard.ime

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.strip.DipEffect
import brobata.physiboard.core.strip.SlotKind
import brobata.physiboard.core.strip.StripButton
import brobata.physiboard.core.strip.StripDip
import brobata.physiboard.core.strip.StripFootprint
import brobata.physiboard.core.strip.StripSettings
import brobata.physiboard.core.strip.StripVisibilityMode
import brobata.physiboard.core.strip.SuggestionRow
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.TextInputResources
import brobata.physiboard.core.text.TextWindow
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The strip as [KeyboardPipeline] feeds it: the refresh snapshot, and the per-app dip's promise
 * that a window-hide during the blink resets nothing. spec: status-bar.md SS12.2, SS13, SS17
 * ("Dip in flight and the app hides the window for real: modifiers and suggestion context are
 * not reset"). No `android.*` type here, like `KeyboardPipelineTest`.
 */
class KeyboardPipelineStripTest {

    private val layout = TitanLayouts.titan2EliteQwerty()
    private val teams = AppProfile.default("com.microsoft.teams")

    private val english = DictionaryIndex.build(
        LanguageCode.of("en")!!,
        listOf(WordFrequency("hello", 100), WordFrequency("help", 90), WordFrequency("held", 80)),
    )

    private fun pipeline(settings: KeyboardSettings = KeyboardSettings()) =
        KeyboardPipeline(layout = layout, resources = TextInputResources(dictionaries = listOf(english)), settings = settings)

    private fun snapshot(text: String, nowMs: Long) = EditorSnapshot(textBeforeCursor = text, fullText = TextWindow(text, text.length, text.length), nowMs = nowMs)

    private fun press(pipeline: KeyboardPipeline, key: KeyId, text: String, nowMs: Long): String {
        val result = pipeline.onKeyStroke(KeyStroke(key, KeyEdge.DOWN, 0, nowMs), snapshot(text, nowMs))
        val committed = result.ops.filterIsInstance<brobata.physiboard.core.text.EditorOp.CommitText>().joinToString("") { it.text }
        pipeline.onKeyStroke(KeyStroke(key, KeyEdge.UP, 0, nowMs + 10), snapshot(text + committed, nowMs + 10))
        return text + committed
    }

    /** Types "hel" with a Shift one-shot armed afterwards, so both the modifier and the suggestion context have something to lose. */
    private fun typeHelWithShiftArmed(pipeline: KeyboardPipeline): String {
        var text = ""
        text = press(pipeline, KeyId.Letter('H'), text, 100)
        text = press(pipeline, KeyId.Letter('E'), text, 200)
        text = press(pipeline, KeyId.Letter('L'), text, 300)
        val shift = KeyId.Modifier(ModifierKey.SHIFT)
        pipeline.onKeyStroke(KeyStroke(shift, KeyEdge.DOWN, 0, 400), snapshot(text, 400))
        pipeline.onKeyStroke(KeyStroke(shift, KeyEdge.UP, 0, 450), snapshot(text, 450))
        return text
    }

    private fun model(pipeline: KeyboardPipeline) = pipeline.stripModel(clipboardCount = 0, dictationActive = false, dictionaryInstalled = true, subtypeLocale = "en_US")

    @Test
    fun `SS17 a window hide during the dip resets neither the modifiers nor the suggestion context`() {
        val pipeline = pipeline()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = teams)
        typeHelWithShiftArmed(pipeline)
        val before = pipeline.modifierGlyphInput()
        assertTrue(before.shiftOneShotArmed, "precondition: Shift one-shot armed")
        val suggestionsBefore = pipeline.suggestions().map { it.word }
        assertTrue(suggestionsBefore.isNotEmpty(), "precondition: suggestions for 'hel'")

        val decision = pipeline.onShowRequestRefused(nowMs = 1_000, stripRendered = true, configurationChange = false)
        assertTrue(decision.started)
        assertEquals(listOf(DipEffect.HIDE_STRIP), decision.effects)

        assertFalse(pipeline.onWindowHidden(nowMs = 1_050), "the real hide is treated as the blink")
        val refreshed = model(pipeline)
        assertEquals(before, pipeline.modifierGlyphInput(), "modifiers survive the blink")
        assertEquals(suggestionsBefore, pipeline.suggestions().map { it.word }, "suggestion context survives the blink")
        assertIs<SuggestionRow.Slots>(refreshed.row)

        assertEquals(listOf(DipEffect.SHOW_STRIP), pipeline.onDipHoldElapsed(nowMs = 1_200))
        assertFalse(pipeline.onWindowHidden(nowMs = 1_400), "still in flight after the re-show")
        assertEquals(before, pipeline.modifierGlyphInput())
    }

    @Test
    fun `SS13 a window hide with no dip in flight resets the modifiers and the suggestion context`() {
        val pipeline = pipeline()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = teams)
        typeHelWithShiftArmed(pipeline)
        assertTrue(pipeline.onWindowHidden(nowMs = 1_000))
        assertFalse(pipeline.modifierGlyphInput().shiftOneShotArmed)
        assertTrue(pipeline.suggestions().isEmpty())
    }

    @Test
    fun `SS12_2 the hold refuses PhysiBoard's own re-show for 200 ms and the dip is over at 500 ms`() {
        val pipeline = pipeline()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = teams)
        pipeline.onShowRequestRefused(nowMs = 0, stripRendered = true, configurationChange = false)
        assertTrue(pipeline.refusesCandidatesShow(nowMs = 199))
        assertFalse(pipeline.refusesCandidatesShow(nowMs = 200))
        assertTrue(pipeline.isDipInFlight(nowMs = 499))
        assertFalse(pipeline.isDipInFlight(nowMs = 500))
        assertTrue(pipeline.onWindowHidden(nowMs = 500), "after the dip, a hide is a real hide again")
    }

    @Test
    fun `SS12_2 an app off the dip list never dips`() {
        val pipeline = pipeline()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = AppProfile.default("com.example.other"))
        val decision = pipeline.onShowRequestRefused(nowMs = 0, stripRendered = true, configurationChange = false)
        assertFalse(decision.started)
        assertFalse(pipeline.refusesCandidatesShow(nowMs = 10))
    }

    @Test
    fun `SS12_2 the dip list is a setting`() {
        val pipeline = pipeline(KeyboardSettings(statusBar = StripSettings(dipApps = setOf("com.example.chat"))))
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = AppProfile.default("com.example.chat"))
        assertTrue(pipeline.onShowRequestRefused(nowMs = 0, stripRendered = true, configurationChange = false).started)
        assertEquals(StripDip.SEEDED_APPS, KeyboardSettings().statusBar.dipApps, "shipped default: Teams")
    }

    @Test
    fun `SS1 the refresh snapshot carries the suggestions, the buttons and the language`() {
        val pipeline = pipeline()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = teams)
        typeHelWithShiftArmed(pipeline)
        val model = model(pipeline)
        assertEquals(StripFootprint.SHOWN, model.footprint)
        val row = assertIs<SuggestionRow.Slots>(model.row)
        assertEquals("hello", row.center.text)
        assertEquals(SlotKind.SUGGESTION, row.center.kind)
        assertEquals(listOf(StripButton.CLIPBOARD), model.leftButtons)
        assertEquals(listOf(StripButton.MICROPHONE), model.rightButtons)
        assertEquals("EN", model.languageLabel)
        assertFalse(model.quickActionsOpen)
    }

    @Test
    fun `SS3_5 mode APPS with the field's app off the list collapses the strip`() {
        val pipeline = pipeline(KeyboardSettings(statusBar = StripSettings(visibility = StripVisibilityMode.APPS, apps = setOf("com.example.listed"))))
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = AppProfile.default("com.android.launcher3"))
        assertEquals(StripFootprint.COLLAPSED, model(pipeline).footprint)
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = AppProfile.default("com.example.listed"))
        assertEquals(StripFootprint.SHOWN, model(pipeline).footprint)
    }

    @Test
    fun `SS5_2 a field that disallows suggestions hides the row and keeps the buttons`() {
        val pipeline = pipeline()
        pipeline.onStartInput(FieldContext(FieldKind.PASSWORD), appProfile = teams)
        val model = model(pipeline)
        assertEquals(SuggestionRow.Hidden, model.row)
        assertEquals(listOf(StripButton.CLIPBOARD), model.leftButtons)
    }

    @Test
    fun `SS6_1 the clipboard and microphone taps release a latched Alt layer, and nothing else`() {
        val pipeline = pipeline()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = teams)
        typeHelWithShiftArmed(pipeline)
        pipeline.releaseLatchedLayersForStripButton()
        assertTrue(pipeline.modifierGlyphInput().shiftOneShotArmed, "a one-shot is not a layer latch")
        assertFalse(pipeline.modifierGlyphInput().altLatched)
    }
}
