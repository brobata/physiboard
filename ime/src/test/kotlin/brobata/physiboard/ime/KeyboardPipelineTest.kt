package brobata.physiboard.ime

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldCapFlags
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.SpacingSettings
import brobata.physiboard.core.text.TextInputResources
import brobata.physiboard.core.text.TextInputSettingsBundle
import brobata.physiboard.core.text.TextWindow
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM tests for [KeyboardPipeline]. No `android.*` type appears here or in the class under test,
 * so these run the same way `:core:keys` and `:core:text`'s own tests do, with no device or
 * Robolectric shim.
 */
class KeyboardPipelineTest {

    private val layout = TitanLayouts.titan2EliteQwerty()

    /** Replays the [EditorOp]s a step produced against a plain string, standing in for a real `InputConnection` in these tests. */
    private class FakeEditor {
        var text: String = ""
            private set
        private var clock = 0L

        fun nextSnapshot(): EditorSnapshot {
            clock += 50
            return EditorSnapshot(textBeforeCursor = text, fullText = TextWindow(text, text.length, text.length), nowMs = clock)
        }

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

    private fun step(pipeline: KeyboardPipeline, editor: FakeEditor, key: KeyId): PipelineResult {
        val snapshot = editor.nextSnapshot()
        val stroke = KeyStroke(key, KeyEdge.DOWN, 0, snapshot.nowMs)
        val result = pipeline.onKeyStroke(stroke, snapshot)
        editor.apply(result.ops)
        return result
    }

    private fun modifier(key: ModifierKey) = KeyId.Modifier(key)
    private fun letter(c: Char) = KeyId.Letter(c)

    // -----------------------------------------------------------------------------------------
    // The milestone-2 sequencing rule: docs/plans/rebuild-from-scratch.md, "What milestone 2
    // established". Get this wrong and the first letter after a deferred space loses its capital.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a deferred space forces the next letter uppercase on the same keystroke`() {
        val settings = KeyboardSettings(textInput = TextInputSettingsBundle(spacing = SpacingSettings(beforeNextTextList = "?")))
        val pipeline = KeyboardPipeline(layout = layout, settings = settings)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        // Alt one-shot, then V: the Titan Elite's Alt layer maps V to "?" (TitanLayouts.ALT_LAYER).
        step(pipeline, editor, modifier(ModifierKey.ALT))
        step(pipeline, editor, letter('V'))
        assertEquals("?", editor.text, "the Alt-layer '?' should have committed and armed the deferred-space debt")

        // A plain 'w' key-down would resolve lowercase on its own; the pending debt must force it
        // uppercase on this exact keystroke, not the one after.
        step(pipeline, editor, letter('W'))
        assertEquals("? W", editor.text)
    }

    @Test
    fun `with no deferred space owed a letter after punctuation stays lowercase`() {
        val pipeline = KeyboardPipeline(layout = layout) // default settings: space_after_punctuation ships empty
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        step(pipeline, editor, modifier(ModifierKey.ALT))
        step(pipeline, editor, letter('V')) // commits "?", no debt armed with the default empty list
        step(pipeline, editor, letter('W'))

        assertEquals("?w", editor.text)
    }

    // -----------------------------------------------------------------------------------------
    // Plain typing and modifiers
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a plain letter commits lowercase with no modifier active`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        val result = step(pipeline, editor, letter('H'))

        assertTrue(result.consumed)
        assertEquals("h", editor.text)
    }

    @Test
    fun `a Shift one-shot capitalises exactly the next letter`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        step(pipeline, editor, modifier(ModifierKey.SHIFT))
        step(pipeline, editor, letter('H'))
        step(pipeline, editor, letter('I'))

        assertEquals("Hi", editor.text)
    }

    // -----------------------------------------------------------------------------------------
    // The Space/Enter/Backspace bridge (see KeyboardPipeline.withBaselineControlAction's SPEC GAP
    // note): without it these three keys would resolve to Action.PassThrough and never reach
    // `:core:text`'s smart-space/backspace-undo logic at all.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a plain unmodified space is not a raw pass-through`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('H'))
        step(pipeline, editor, letter('I'))

        val result = step(pipeline, editor, KeyId.Control(ControlKey.SPACE))

        assertTrue(result.consumed, "a plain Space must run through TextInputPipeline.handleSpace, not fall through untouched")
        assertEquals("hi ", editor.text)
    }

    @Test
    fun `a plain unmodified enter commits a newline through the pipeline`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('H'))

        val result = step(pipeline, editor, KeyId.Control(ControlKey.ENTER))

        assertTrue(result.consumed)
        assertEquals("h\n", editor.text)
    }

    // -----------------------------------------------------------------------------------------
    // Field lifecycle
    // -----------------------------------------------------------------------------------------

    @Test
    fun `CAP_CHARACTERS at field start capitalises every letter until the field changes`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL, capFlags = FieldCapFlags(capCharacters = true)))

        step(pipeline, editor, letter('H'))
        step(pipeline, editor, letter('I'))

        assertEquals("HI", editor.text)
    }

    @Test
    fun `a restricted field never surfaces suggestions`() {
        val dictionary = DictionaryIndex.build(LanguageCode.of("en")!!, listOf(WordFrequency("hello", 200)))
        val pipeline = KeyboardPipeline(layout = layout, resources = TextInputResources(dictionaries = listOf(dictionary)))
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.PASSWORD))

        step(pipeline, editor, letter('H'))

        assertTrue(pipeline.suggestions().isEmpty())
    }

    @Test
    fun `a normal field can surface a ranked suggestion for the word in progress`() {
        val dictionary = DictionaryIndex.build(LanguageCode.of("en")!!, listOf(WordFrequency("hello", 200)))
        val pipeline = KeyboardPipeline(layout = layout, resources = TextInputResources(dictionaries = listOf(dictionary)))
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        for (c in "hel") step(pipeline, editor, letter(c.uppercaseChar()))

        assertEquals(listOf("hello"), pipeline.suggestions().map { it.word })
    }

    @Test
    fun `accepting a suggestion goes through the pipeline, not a direct editor write`() {
        val dictionary = DictionaryIndex.build(LanguageCode.of("en")!!, listOf(WordFrequency("hello", 200)))
        val pipeline = KeyboardPipeline(layout = layout, resources = TextInputResources(dictionaries = listOf(dictionary)))
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        for (c in "hel") step(pipeline, editor, letter(c.uppercaseChar()))

        val snapshot = editor.nextSnapshot()
        val result = pipeline.onAcceptSuggestion("hello", snapshot)
        editor.apply(result.ops)

        // Capitalised, not because the accept path decided so on its own, but because
        // text-input.md SS9's "capitalize at text start" rule applies to the very first word of
        // the document; TextInputPipeline.handleAcceptSuggestion re-evaluates it at the accepted
        // span's own start, exactly as it does for every other commit path.
        assertEquals("Hello ", editor.text)
    }

    @Test
    fun `finishing input resets modifier state so a stale one-shot cannot leak into the next field`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, modifier(ModifierKey.SHIFT))

        pipeline.onFinishInput()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('H'))

        assertEquals("h", editor.text)
        assertFalse(editor.text == "H")
    }
}
