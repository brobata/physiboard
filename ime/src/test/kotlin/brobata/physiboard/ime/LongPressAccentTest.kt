package brobata.physiboard.ime

import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.LongPressMode
import brobata.physiboard.core.keys.SymPageId
import brobata.physiboard.core.settings.CustomSymPage
import brobata.physiboard.core.settings.KeyPrefs
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.TextWindow
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * spec: layers-sym-alt.md SS7.4, SS8.2 to SS8.4 (3.0): a long press in Accent mode through the
 * whole pipeline, the accent chooser's hand-off and its pick, and the user's own pages and lists
 * reaching the layout.
 */
class LongPressAccentTest {

    private class Editor {
        // Mid-sentence, so auto-capitalisation leaves the letters small.
        var text = "ok "
        var clock = 0L
        fun snapshot() = EditorSnapshot(textBeforeCursor = text, fullText = TextWindow(text, text.length, text.length), nowMs = clock)
        fun apply(ops: List<EditorOp>) {
            for (op in ops) {
                text = when (op) {
                    is EditorOp.CommitText -> text + op.text
                    is EditorOp.DeleteSurrounding -> text.dropLast(op.before.coerceAtMost(text.length))
                    is EditorOp.ReplaceBeforeCursor -> text.dropLast(op.count.coerceAtMost(text.length)) + op.text
                    else -> text
                }
            }
        }
    }

    private fun accentSettings(lists: Map<String, List<String>> = emptyMap()) =
        Settings(keys = KeyPrefs(longPressMode = LongPressMode.VARIATIONS, longPressThresholdMs = 300, customVariations = lists))

    private fun pipeline(settings: Settings, locale: String, field: FieldContext = FieldContext(FieldKind.NORMAL)): KeyboardPipeline {
        val pipeline = KeyboardPipeline(layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), settings, subtypeLocale = locale))
        pipeline.onStartInput(field, textBeforeCursor = "ok ")
        return pipeline
    }

    private fun press(pipeline: KeyboardPipeline, editor: Editor, letter: Char) {
        val result = pipeline.onKeyStroke(KeyStroke(KeyId.Letter(letter), KeyEdge.DOWN, 0, editor.clock), editor.snapshot())
        editor.apply(result.ops)
    }

    @Test
    fun `holding a in Polish types ą and offers the other accents`() {
        val p = pipeline(accentSettings(), "pl_PL")
        val editor = Editor()
        press(p, editor, 'A')
        assertEquals("ok a", editor.text)
        editor.clock = 400
        val tick = assertNotNull(p.checkLongPressTick(400, editor.snapshot()))
        editor.apply(tick.ops)
        assertEquals("ok ą", editor.text)
        val choice = assertNotNull(tick.variationChoice)
        assertEquals(KeyId.Letter('A'), choice.key)
        assertEquals("ą", choice.committed)
        assertEquals("ą", choice.choices.first())

        val pick = assertNotNull(p.replaceVariation("ą", "à", editor.snapshot()))
        editor.apply(pick.ops)
        assertEquals("ok à", editor.text)
    }

    @Test
    fun `a pick changes nothing once the text before the caret no longer ends with the accent`() {
        val p = pipeline(accentSettings(), "pl_PL")
        assertNull(p.replaceVariation("ą", "à", EditorSnapshot(textBeforeCursor = "ab", nowMs = 0)))
    }

    @Test
    fun `the user's own list wins, and a single accent opens no chooser`() {
        val p = pipeline(accentSettings(mapOf("e" to listOf("€"))), "pl_PL")
        val editor = Editor()
        press(p, editor, 'E')
        editor.clock = 400
        val tick = assertNotNull(p.checkLongPressTick(400, editor.snapshot()))
        editor.apply(tick.ops)
        assertEquals("ok €", editor.text)
        assertNull(tick.variationChoice)
    }

    @Test
    fun `the long press leaves a letter alone when the app changed it before the timer`() {
        val p = pipeline(accentSettings(), "de_DE")
        val editor = Editor()
        press(p, editor, 'A')
        editor.text = "x" // the app rewrote the field
        editor.clock = 400
        val tick = p.checkLongPressTick(400, editor.snapshot())
        assertEquals(emptyList(), tick?.ops.orEmpty())
        assertEquals("x", editor.text)
        assertNull(tick?.variationChoice)
    }

    @Test
    fun `an email field takes no accents, so a held letter never arms`() {
        val p = pipeline(accentSettings(), "pl_PL", FieldContext(FieldKind.EMAIL))
        val editor = Editor()
        press(p, editor, 'A')
        assertNull(p.pendingLongPressDeadlineMs)
    }

    @Test
    fun `in a terminal the accent replaces the letter without asking the emptied text box`() {
        val p = pipeline(accentSettings(), "pl_PL", FieldContext(FieldKind.RAW_MODE_APP, isMultiLine = true, appDisablesSuggestions = true))
        val editor = Editor()
        press(p, editor, 'A')
        editor.clock = 400
        val tick = assertNotNull(p.checkLongPressTick(400, EditorSnapshot(textBeforeCursor = "", nowMs = 400)))
        assertEquals(listOf(EditorOp.ReplaceBeforeCursor(1, "ą")), tick.ops.filterIsInstance<EditorOp.ReplaceBeforeCursor>())
    }

    @Test
    fun `the user's own pages reach the layout, and only set-up pages get a chooser name`() {
        val base = Settings()
        val s = base.copy(
            symPages = base.symPages.copy(
                pages = base.symPages.pages.copy(custom3Enabled = true),
                customPages = listOf(CustomSymPage("Polski", mapOf("KEYCODE_A" to "ą")), CustomSymPage(), CustomSymPage()),
            ),
        )
        val layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), s)
        assertEquals("ą", layout.customPages[SymPageId.CUSTOM_1]?.get(KeyId.Letter('A'))?.lowercase)
        assertNull(layout.customPages[SymPageId.CUSTOM_2])
        assertEquals(true, layout.symPagesConfig.custom3Enabled)
        assertEquals(mapOf(SymPageId.CUSTOM_1 to "Polski", SymPageId.CUSTOM_3 to ""), ImeSettings.customPageNames(s))
    }
}
