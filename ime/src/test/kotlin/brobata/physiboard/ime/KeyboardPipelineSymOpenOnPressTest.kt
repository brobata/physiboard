package brobata.physiboard.ime

import brobata.physiboard.core.keys.KeyCommands
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.keys.SymPageId
import brobata.physiboard.core.keys.SymPagesConfig
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * layers-sym-alt.md SS5.11 through the whole pipeline: with `sym_open_on_press` the page is up at
 * the press; a Sym chord takes it back before the chord resolves, so the chord types exactly what
 * it types with the setting off; the double tap still opens the chooser.
 */
class KeyboardPipelineSymOpenOnPressTest {
    private val sym = KeyId.Modifier(ModifierKey.SYM)
    private val messages = AppProfile.default("com.google.android.apps.messaging")
    private val editable = FieldContext(FieldKind.NORMAL)
    private val picker = SymPageId.EMOJI_PICKER.pageNumber
    private val symbols = SymPageId.SYMBOLS.pageNumber
    private val pages = SymPagesConfig(
        emojiEnabled = false, symbolsEnabled = true, emojiPickerEnabled = true, gifEnabled = true,
        order = listOf(SymPageId.EMOJI_PICKER, SymPageId.SYMBOLS, SymPageId.GIF, SymPageId.CLIPBOARD, SymPageId.EMOJI),
    )
    private val nothing = EditorSnapshot(textBeforeCursor = "", fullText = null, nowMs = 0)
    private val commands = ArrayList<String>()

    private fun pipeline(onPress: Boolean): KeyboardPipeline {
        val base = KeyboardSettings()
        val p = KeyboardPipeline(
            layout = TitanLayouts.titan2EliteQwerty().copy(symPagesConfig = pages),
            settings = base.copy(modifier = base.modifier.copy(symOpenOnPress = onPress)),
            onCommand = { commands.add(it) },
        )
        p.onStartInput(editable, appProfile = messages, nowMs = 0)
        return p
    }

    private fun down(p: KeyboardPipeline, key: KeyId, at: Long) = p.onKeyStroke(KeyStroke(key, KeyEdge.DOWN, 0, at), nothing)
    private fun up(p: KeyboardPipeline, key: KeyId, at: Long) = p.onKeyStroke(KeyStroke(key, KeyEdge.UP, 0, at), nothing)

    @Test
    fun `the page is up at the press and stays at the release`() {
        val p = pipeline(onPress = true)
        assertTrue(down(p, sym, 1_000).consumed)
        assertEquals(picker, p.currentSymPage)
        up(p, sym, 1_120)
        assertEquals(picker, p.currentSymPage)
        down(p, sym, 2_000)
        assertEquals(symbols, p.currentSymPage, "the next press steps on its way down")
        up(p, sym, 2_100)
        assertEquals(symbols, p.currentSymPage)
    }

    @Test
    fun `off, nothing changes at the press`() {
        val p = pipeline(onPress = false)
        down(p, sym, 1_000)
        assertEquals(0, p.currentSymPage)
        up(p, sym, 1_120)
        assertEquals(picker, p.currentSymPage)
    }

    @Test
    fun `a chord takes the page back and types what it types with the setting off`() {
        val expected = pipeline(onPress = false).let { p ->
            down(p, sym, 1_000)
            down(p, KeyId.Letter('Q'), 1_050).ops
        }
        val p = pipeline(onPress = true)
        down(p, sym, 1_000)
        assertEquals(picker, p.currentSymPage)
        assertFalse(p.symPressReverted)
        assertTrue(p.symChordLive, "a page's search capture stands aside while Sym is held, so the key reaches the chord")
        val chord = down(p, KeyId.Letter('Q'), 1_050)
        assertEquals(expected, chord.ops)
        assertTrue(chord.ops.single() is EditorOp.CommitText)
        assertEquals(0, p.currentSymPage, "the page that flashed is gone")
        assertTrue(p.symPressReverted)
        up(p, KeyId.Letter('Q'), 1_090)
        up(p, sym, 1_150)
        assertEquals(0, p.currentSymPage, "the release opens nothing")
        assertFalse(p.symPressReverted)
    }

    @Test
    fun `an edit shortcut under Sym takes the page back too`() {
        val p = pipeline(onPress = true)
        down(p, sym, 1_000)
        down(p, KeyId.Letter('C'), 1_050)
        assertEquals(0, p.currentSymPage)
        up(p, sym, 1_200)
        assertEquals(0, p.currentSymPage)
    }

    @Test
    fun `a double tap still opens the chooser over the page that was open before`() {
        val p = pipeline(onPress = true)
        commands.clear()
        down(p, sym, 1_000)
        up(p, sym, 1_080)
        assertEquals(picker, p.currentSymPage)
        down(p, sym, 1_250)
        assertEquals(picker, p.currentSymPage, "the second press takes no step")
        up(p, sym, 1_320)
        assertEquals(listOf(KeyCommands.OPEN_SYM_PAGE_CHOOSER), commands)
        assertEquals(0, p.currentSymPage)
    }

    @Test
    fun `Shift under Sym is not a chord - the page stays`() {
        val p = pipeline(onPress = true)
        val shift = KeyId.Modifier(ModifierKey.SHIFT)
        down(p, sym, 1_000)
        down(p, shift, 1_040)
        up(p, shift, 1_080)
        up(p, sym, 1_150)
        assertEquals(picker, p.currentSymPage)
    }
}
