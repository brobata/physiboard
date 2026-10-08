package brobata.physiboard.ime

import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.keys.SymPageId
import brobata.physiboard.core.keys.SymPagesConfig
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * layers-sym-alt.md SS5.2: Sym steps the pages and is never mistaken for the launcher key when the
 * app's text box goes away under an open page (the screenshot in Messages, 2026-10-07).
 */
class KeyboardPipelineSymFieldTest {
    private val sym = KeyId.Modifier(ModifierKey.SYM)
    private val messages = AppProfile.default("com.google.android.apps.messaging")
    private val launcher = AppProfile.default("com.teslacoilsw.launcher")
    private val editable = FieldContext(FieldKind.NORMAL)
    private val noBox = FieldContext(FieldKind.NOT_EDITABLE)
    private val emoji = SymPageId.EMOJI_PICKER.pageNumber
    private val symbols = SymPageId.SYMBOLS.pageNumber
    private val gif = SymPageId.GIF.pageNumber

    /** Today's default list: Emoji, Symbols, GIFs. */
    private val pages = SymPagesConfig(
        emojiEnabled = false, symbolsEnabled = true, emojiPickerEnabled = true, gifEnabled = true,
        order = listOf(SymPageId.EMOJI_PICKER, SymPageId.SYMBOLS, SymPageId.GIF, SymPageId.CLIPBOARD, SymPageId.EMOJI),
    )

    private fun pipeline(): KeyboardPipeline =
        KeyboardPipeline(layout = TitanLayouts.titan2EliteQwerty().copy(symPagesConfig = pages))

    private val nothing = EditorSnapshot(textBeforeCursor = null, fullText = null, nowMs = 0)

    private fun tap(p: KeyboardPipeline, at: Long) = p.onKeyStroke(KeyStroke(sym, KeyEdge.DOWN, 0, at), nothing).also {
        p.onKeyStroke(KeyStroke(sym, KeyEdge.UP, 0, at + 100), nothing)
    }

    @Test
    fun `Sym steps Emoji, Symbols, GIFs, then closes`() {
        val p = pipeline()
        p.onStartInput(editable, appProfile = messages, nowMs = 0)
        val seen = (0 until 4).map { i -> tap(p, 1_000L + i * 1_000); p.currentSymPage }
        assertEquals(listOf(emoji, symbols, gif, 0), seen)
    }

    @Test
    fun `the screenshot case - the box goes with the emoji page open, Sym is not the launcher key, and the page comes back`() {
        val p = pipeline()
        p.onStartInput(editable, appProfile = messages, nowMs = 0)
        tap(p, 1_000)
        assertEquals(emoji, p.currentSymPage)
        // The screenshot window takes focus; Messages comes back with no focused box.
        p.onFinishInput(nowMs = 5_000)
        p.onStartInput(noBox, appProfile = messages, nowMs = 5_050)

        val press = tap(p, 6_400)
        assertTrue(press.consumed)
        assertTrue(press.symWantsTheField, "the keyboard says to tap the box")
        assertNull(press.powerModeArmedAtMs, "and does not arm the launcher shortcuts")
        assertNull(p.powerShortcutArmedAtMs)

        // The user taps the box: the emoji page is back, and Sym goes on from it.
        p.onStartInput(editable, appProfile = messages, nowMs = 9_000)
        assertEquals(emoji, p.currentSymPage)
        tap(p, 10_000)
        assertEquals(symbols, p.currentSymPage)
    }

    @Test
    fun `a restart into a box-less field closes the page, and a restart back into the box reopens it`() {
        val p = pipeline()
        p.onStartInput(editable, appProfile = messages, nowMs = 0)
        tap(p, 1_000)
        tap(p, 2_000)
        assertEquals(symbols, p.currentSymPage)
        p.onRestartInput(noBox, appProfile = messages, textBeforeCursor = null, nowMs = 3_000)
        assertEquals(0, p.currentSymPage)
        assertTrue(tap(p, 3_500).symWantsTheField)
        p.onRestartInput(editable, appProfile = messages, textBeforeCursor = "", nowMs = 4_000)
        assertEquals(symbols, p.currentSymPage)
    }

    @Test
    fun `with a page open and no box, Sym still steps the page instead of arming the launcher`() {
        val p = pipeline()
        p.onStartInput(noBox, appProfile = launcher, nowMs = 0)
        p.openSymPage(emoji)
        val press = tap(p, 1_000)
        assertNull(press.powerModeArmedAtMs)
        assertNull(p.powerShortcutArmedAtMs)
        assertFalse(press.symWantsTheField)
        assertEquals(symbols, p.currentSymPage)
    }

    @Test
    fun `on the home screen, or long after the box went, Sym is the launcher key as before`() {
        val p = pipeline()
        p.onStartInput(editable, appProfile = messages, nowMs = 0)
        p.onFinishInput(nowMs = 1_000)
        p.onStartInput(noBox, appProfile = launcher, nowMs = 1_050)
        assertEquals(2_000L, tap(p, 2_000).powerModeArmedAtMs)

        val q = pipeline()
        q.onStartInput(editable, appProfile = messages, nowMs = 0)
        q.onFinishInput(nowMs = 1_000)
        q.onStartInput(noBox, appProfile = messages, nowMs = 1_050)
        assertEquals(30_000L, tap(q, 30_000).powerModeArmedAtMs)
    }
}
