package brobata.physiboard.ime

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.keys.PunctuationKey
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorReadTrust
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.EditorTrust
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.EnterIntent
import brobata.physiboard.core.text.FieldCapFlags
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.ImeAction
import brobata.physiboard.core.text.SpacingSettings
import brobata.physiboard.core.text.TextInputResources
import brobata.physiboard.core.text.TextInputSettingsBundle
import brobata.physiboard.core.text.TextWindow
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

        /** Advances the clock without a keystroke, so a test can put two real key events on either side of the 500 ms double-space-to-period window (spec: text-input.md SS6.7, SS13). */
        fun advanceClock(ms: Long) {
            clock += ms
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

    /**
     * spec: text-input.md SS9.3, "if the user taps Shift while auto-cap has it armed... the
     * keyboard records the current cursor context... as suppressed". `:core:text`'s own
     * AutoCapitalizationTest proves the pure decision; this proves the running keyboard actually
     * calls it, which it did not before this test was added (the running pipeline never passed a
     * suppression context to `AutoCapitalization.evaluate`, nor called `onUserDisarmed`).
     */
    @Test
    fun `tapping Shift to cancel auto-cap keeps it cancelled through the next selection update`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        // "Capitalize at text start" (default on) arms a one-shot at this empty field.
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), textBeforeCursor = "")

        // The user taps Shift, turning the auto-armed one-shot back off.
        step(pipeline, editor, modifier(ModifierKey.SHIFT))

        // The exact same context (still an empty field) recurs, as a selection update the app
        // fires right after; without the suppression this would re-arm the one-shot.
        pipeline.onExternalSelectionChange(textBeforeCursor = "")
        step(pipeline, editor, letter('H'))

        assertEquals("h", editor.text, "the user's cancel of auto-cap must survive the very next selection update")
    }

    // -----------------------------------------------------------------------------------------
    // The Space/Enter/Backspace baseline (see LayerResolver.withBaselineControlAction's KDoc):
    // without it these three keys would resolve to Action.PassThrough and never reach
    // `:core:text`'s smart-space/backspace-undo logic at all. `:ime` no longer does anything to
    // make this true; it is `:core:keys` answering these three ordinary presses correctly in the
    // first place, and this pipeline just carries the answer through.
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

    // -----------------------------------------------------------------------------------------
    // Regression (device report, 2.x daily-driver keyboard): pressing the real Space key twice
    // only ever produced one space, because `:core:text`'s trailing-space guarantee treated any
    // pre-existing trailing space as one it had already supplied, including the user's own
    // previous, separate Space keystroke. Every existing rule-level test only ever exercised
    // `DoubleSpacePeriod.apply` directly with a hand-picked `isSecondPressWithinWindow`, so none of
    // them ever drove two real Space key-downs through the pipeline the way a device does; these
    // do, through the same `KeyboardPipeline.onKeyStroke` entry point a physical key event reaches.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `pressing the real Space key twice, slowly, inserts two spaces`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('H'))
        step(pipeline, editor, letter('I'))

        step(pipeline, editor, KeyId.Control(ControlKey.SPACE))
        assertEquals("hi ", editor.text)

        editor.advanceClock(600) // outside the 500ms double-space-to-period window
        val result = step(pipeline, editor, KeyId.Control(ControlKey.SPACE))

        assertTrue(result.consumed)
        assertEquals("hi  ", editor.text, "a second, deliberate Space press must still insert its own space")
    }

    @Test
    fun `pressing the real Space key twice, quickly, still converts to a period`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('H'))
        step(pipeline, editor, letter('I'))

        step(pipeline, editor, KeyId.Control(ControlKey.SPACE))
        step(pipeline, editor, KeyId.Control(ControlKey.SPACE)) // FakeEditor.nextSnapshot() only advances 50ms per step, well inside the window

        assertEquals("hi. ", editor.text)
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
    // Defect 4: the same family as the Space/Enter/Backspace bridge above, but for a punctuation
    // or digit key struck directly (a real physical period/comma/digit key, as an external or
    // emulator keyboard sends, rather than the Titan's own Alt-layer route to the same
    // characters). CharacterResolution.layoutOrDefaultCharacter used to fall back to the key's
    // own glyph only for letters, so these fell out as Action.PassThrough and never reached
    // `:core:text`: no boundary hand-off, no auto-cap re-evaluation, and the tracked word drifted
    // out of step with text the app received but this pipeline never saw.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a period key struck directly still reaches the app as committed text`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('H'))
        step(pipeline, editor, letter('I'))

        val result = step(pipeline, editor, KeyId.Punctuation(PunctuationKey.PERIOD))

        assertTrue(result.consumed, "an ordinary period must run through TextInputPipeline, not fall through untouched")
        assertEquals("hi.", editor.text)
    }

    @Test
    fun `a sentence ended with a struck period key still capitalises the next letter`() {
        // This is the exact sequence that typed correctly in every unit test before defect 4 was
        // found (because those tests only ever produced a period through the Alt layer) yet
        // failed on the device: "hi" + a real period key + Space + a letter must capitalise it,
        // the same as "hi" + Alt-period + Space + a letter already did.
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('H'))
        step(pipeline, editor, letter('I'))
        step(pipeline, editor, KeyId.Punctuation(PunctuationKey.PERIOD))
        step(pipeline, editor, KeyId.Control(ControlKey.SPACE))

        step(pipeline, editor, letter('T'))

        assertEquals("hi. T", editor.text)
    }

    @Test
    fun `a sentence ended with Alt-layer period still capitalises the next letter after Space`() {
        // The Titan 2 Elite has no physical period key at all (D1): every real keystroke that
        // types "." goes through Alt+M, never the direct Punctuation(PERIOD) route the test above
        // exercises. Diagnostic probe for the reported device failure.
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('H'))
        step(pipeline, editor, letter('I'))
        step(pipeline, editor, modifier(ModifierKey.ALT))
        step(pipeline, editor, letter('M')) // Alt+M -> "." on the Titan Elite bottom row
        step(pipeline, editor, KeyId.Control(ControlKey.SPACE))

        step(pipeline, editor, letter('T'))

        assertEquals("hi. T", editor.text)
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

    // -----------------------------------------------------------------------------------------
    // Failure 2 (device report): "wierd" typed into a plain field stayed "wierd". `:core:text`'s
    // own BoundaryEngine/EnglishDictionaryInvariantTest already prove the correction fires when
    // `auto_replace_on_space_enter` is on; what they cannot see is whether the shipped keyboard
    // ever turns it on at all. settings-catalog.md SS4.1: every real Titan 2 Elite applies the
    // factory baseline asset before the keyboard first runs, and that baseline's
    // `auto_replace_on_space_enter` is true, not `:core:text`'s own bare "key absent" default of
    // false. `KeyboardSettings()` is the shipped stand-in for that baseline (no `:settings`
    // module exists yet), so this drives the pipeline with its production default settings
    // (nothing passed in), exactly like a fresh install, rather than opting a test setting in.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a genuine typo is corrected on Space using the shipped default settings, not a test-only override`() {
        val dictionary = DictionaryIndex.build(LanguageCode.of("en")!!, listOf(WordFrequency("weird", 200)))
        val pipeline = KeyboardPipeline(layout = layout, resources = TextInputResources(dictionaries = listOf(dictionary)))
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        for (c in "wierd") step(pipeline, editor, letter(c.uppercaseChar()))
        step(pipeline, editor, KeyId.Control(ControlKey.SPACE))

        assertEquals("weird ", editor.text)
    }

    @Test
    fun `a genuine typo correction on Space is forwarded as a commit for the debug capture`() {
        // spec app-shell.md SS11, autocorrect-suggestions.md SS7.2: "each attempt is recorded in
        // the debug capture with its outcome"; this is the seam KeyboardSession reads to do that.
        val dictionary = DictionaryIndex.build(LanguageCode.of("en")!!, listOf(WordFrequency("weird", 200)))
        val pipeline = KeyboardPipeline(layout = layout, resources = TextInputResources(dictionaries = listOf(dictionary)))
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        for (c in "wierd") step(pipeline, editor, letter(c.uppercaseChar()))
        val result = step(pipeline, editor, KeyId.Control(ControlKey.SPACE))

        val debug = assertNotNull(result.autocorrectDebug)
        assertEquals("commit", debug.type)
        assertEquals("space", debug.trigger)
        assertEquals("applied", debug.outcome)
        assertEquals("wierd", debug.before)
        assertEquals("weird", debug.after)
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

    // -----------------------------------------------------------------------------------------
    // Per-app Enter behaviour: per-app-behavior.md SS3.5 step 4. `:core:text`'s own
    // EnterDecisionTest/TextInputPipelineEnterDeliveryTest cover the decision and delivery shapes;
    // these prove the `:ime`-side wiring that only [KeyboardPipeline] can: the app profile actually
    // reaches [TextInputPipeline], and a Ctrl-active Enter with a per-app opinion is redirected
    // into the pipeline instead of being left as a raw pass-through/forwarded combo.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a send-on-Enter profile asks for the field's editor action instead of a newline`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        val profile = AppProfile(packageName = "com.whatsapp", enterBehavior = EnterBehavior.SEND_SHIFT_NEWLINE, enterActionAllowed = true)
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL, imeAction = ImeAction.SEND), appProfile = profile)

        val result = step(pipeline, editor, KeyId.Control(ControlKey.ENTER))

        assertTrue(result.consumed)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = false), result.enterDelivery)
        assertEquals("", editor.text, "no newline should have been committed")
    }

    @Test
    fun `Ctrl+Enter with a per-app opinion is redirected into the pipeline instead of passing through raw`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        val profile = AppProfile(packageName = "com.whatsapp", enterBehavior = EnterBehavior.SEND_SHIFT_NEWLINE, enterActionAllowed = true)
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL, imeAction = ImeAction.SEND), appProfile = profile)

        step(pipeline, editor, modifier(ModifierKey.CTRL))
        val result = step(pipeline, editor, KeyId.Control(ControlKey.ENTER))

        assertTrue(result.consumed, "a Ctrl-triggered send must be consumed, not left for the app's own raw Ctrl+Enter handling")
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = true), result.enterDelivery)
    }

    @Test
    fun `Ctrl+Enter with no per-app opinion still passes through raw, unchanged from today`() {
        // Regression guard for LayerResolverTest's "Ctrl held with no mapping still leaves Enter
        // passed through": the redirect must never fire for an app with no wanted behaviour.
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL, imeAction = ImeAction.SEND))

        step(pipeline, editor, modifier(ModifierKey.CTRL))
        val result = step(pipeline, editor, KeyId.Control(ControlKey.ENTER))

        assertFalse(result.consumed)
        assertNull(result.enterDelivery)
        assertEquals(emptyList(), result.ops)
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

    // -----------------------------------------------------------------------------------------
    // Dictation's c440844 invariant from the glue side: only a stroke that actually changed the
    // field's text may invalidate the utterance. A modifier press, a key-up and a Fn repeat all
    // reach the pipeline while dictation is listening (the user is still holding Fn after the
    // burst that started it), and none of them edits the field.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a modifier-only stroke during dictation edits no text and so must not invalidate the utterance`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        val shiftDown = step(pipeline, editor, modifier(ModifierKey.SHIFT))
        assertFalse(AppliedEditAccounting.changesText(shiftDown.ops))

        val fnRepeat = pipeline.onKeyStroke(KeyStroke(modifier(ModifierKey.FN), KeyEdge.DOWN, 3, 100), editor.nextSnapshot())
        assertFalse(AppliedEditAccounting.changesText(fnRepeat.ops))

        val letterUp = pipeline.onKeyStroke(KeyStroke(letter('H'), KeyEdge.UP, 0, 200), editor.nextSnapshot())
        assertFalse(AppliedEditAccounting.changesText(letterUp.ops))
    }

    @Test
    fun `a letter or a backspace during dictation does edit the text`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        assertTrue(AppliedEditAccounting.editsField(step(pipeline, editor, letter('H'))))
        // A plain Backspace is a pass-through: the app deletes, not this keyboard, but the field
        // still changed under the utterance and the deleted words must never be typed back.
        val backspace = step(pipeline, editor, KeyId.Control(ControlKey.BACKSPACE))
        assertFalse(backspace.consumed)
        assertTrue(AppliedEditAccounting.editsField(backspace))
    }

    @Test
    fun `Ctrl+Backspace under reduced trust passes through and still counts as editing the field`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL), trust = EditorTrust(reads = EditorReadTrust.UNAVAILABLE))
        step(pipeline, editor, modifier(ModifierKey.CTRL))

        val result = pipeline.onKeyStroke(KeyStroke(KeyId.Control(ControlKey.BACKSPACE), KeyEdge.DOWN, 0, 100), EditorSnapshot(textBeforeCursor = null, nowMs = 100))
        assertFalse(result.consumed)
        assertTrue(AppliedEditAccounting.editsField(result))
    }

    @Test
    fun `a Ctrl+A or a key-up handed to the app does not count as editing the field`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        val up = pipeline.onKeyStroke(KeyStroke(KeyId.Control(ControlKey.BACKSPACE), KeyEdge.UP, 0, 100), editor.nextSnapshot())
        assertFalse(AppliedEditAccounting.editsField(up))
    }

    // -----------------------------------------------------------------------------------------
    // Restarting input (text-input.md line 85: reclassify; line 463: reset a Shift one-shot and
    // re-evaluate auto-cap; nothing says the tracked word is wiped). Chrome and WebViews restart
    // input mid-word, so wiping it there killed autocorrect and Backspace-undo in web fields.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a restart mid-word keeps the tracked word so the boundary can still correct it`() {
        val dictionary = DictionaryIndex.build(LanguageCode.of("en")!!, listOf(WordFrequency("weird", 200)))
        val pipeline = KeyboardPipeline(layout = layout, resources = TextInputResources(dictionaries = listOf(dictionary)))
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        for (c in "wie") step(pipeline, editor, letter(c.uppercaseChar()))

        pipeline.onRestartInput(FieldContext(FieldKind.NORMAL), textBeforeCursor = editor.text)
        for (c in "rd") step(pipeline, editor, letter(c.uppercaseChar()))
        step(pipeline, editor, KeyId.Control(ControlKey.SPACE))

        assertEquals("weird ", editor.text)
    }

    @Test
    fun `a restart drops a Shift one-shot but a fresh field start still wipes the word`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        for (c in "wie") step(pipeline, editor, letter(c.uppercaseChar()))
        step(pipeline, editor, modifier(ModifierKey.SHIFT))

        pipeline.onRestartInput(FieldContext(FieldKind.NORMAL), textBeforeCursor = editor.text)
        step(pipeline, editor, letter('R'))
        assertEquals("wier", editor.text, "the one-shot must not survive a restart")

        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        assertTrue(pipeline.suggestions().isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // Whole-document reads (text-input.md SS19 "unify"): only the strokes that can produce a
    // selection or word-motion op need the extracted text; a plain letter, Space, Enter, a
    // modifier press or a key-up must not cost an O(document) IPC each.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `plain letters, Space, Enter, modifiers and key-ups do not need the whole document`() {
        val pipeline = KeyboardPipeline(layout = layout)
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        assertFalse(pipeline.needsWholeDocument(KeyStroke(letter('H'), KeyEdge.DOWN, 0, 0)))
        assertFalse(pipeline.needsWholeDocument(KeyStroke(KeyId.Control(ControlKey.SPACE), KeyEdge.DOWN, 0, 0)))
        assertFalse(pipeline.needsWholeDocument(KeyStroke(KeyId.Control(ControlKey.ENTER), KeyEdge.DOWN, 0, 0)))
        assertFalse(pipeline.needsWholeDocument(KeyStroke(modifier(ModifierKey.CTRL), KeyEdge.DOWN, 0, 0)))
        assertFalse(pipeline.needsWholeDocument(KeyStroke(KeyId.Control(ControlKey.BACKSPACE), KeyEdge.UP, 0, 0)))
    }

    @Test
    fun `Backspace, arrows and any Ctrl or Alt stroke read the whole document`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        assertTrue(pipeline.needsWholeDocument(KeyStroke(KeyId.Control(ControlKey.BACKSPACE), KeyEdge.DOWN, 0, 0)))
        assertTrue(pipeline.needsWholeDocument(KeyStroke(KeyId.Control(ControlKey.DPAD_LEFT), KeyEdge.DOWN, 0, 0)))
        assertTrue(pipeline.needsWholeDocument(KeyStroke(letter('A'), KeyEdge.DOWN, 0, 0, ModifierFlags(ctrl = true))))

        // A Ctrl one-shot armed by a previous press makes the next letter a Ctrl combo too.
        step(pipeline, editor, modifier(ModifierKey.CTRL))
        assertTrue(pipeline.needsWholeDocument(KeyStroke(letter('A'), KeyEdge.DOWN, 0, 0)))
    }

    @Test
    fun `a letter under a held Sym is a chord (Sym+A selects all) and reads the whole document`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, modifier(ModifierKey.SYM)) // Sym down and still held: a chord is pending
        assertTrue(pipeline.needsWholeDocument(KeyStroke(letter('A'), KeyEdge.DOWN, 0, 50)))
    }

    // -----------------------------------------------------------------------------------------
    // Holding Space. LayerResolver answers every Space repeat with Commit(" "), and the repeat
    // onset (about 400 ms) is inside the double-space window (500 ms), so without a gate the
    // first repeat of a held Space typed ". ". spec text-input.md SS6.7 is about two presses.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a Space auto-repeat inside the double-space window commits a space, not a full stop`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('H'))
        step(pipeline, editor, letter('I'))
        step(pipeline, editor, KeyId.Control(ControlKey.SPACE))

        val snapshot = editor.nextSnapshot() // 50 ms later, well inside the window
        val repeat = pipeline.onKeyStroke(KeyStroke(KeyId.Control(ControlKey.SPACE), KeyEdge.DOWN, 1, snapshot.nowMs), snapshot)
        editor.apply(repeat.ops)

        assertEquals("hi  ", editor.text)
    }

    @Test
    fun `a pending long press survives a restart so the session can reschedule its timer`() {
        val pipeline = KeyboardPipeline(layout = layout)
        val editor = FakeEditor()
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))
        step(pipeline, editor, letter('Q'))
        val deadline = pipeline.pendingLongPressDeadlineMs
        assertNotNull(deadline)

        pipeline.onRestartInput(FieldContext(FieldKind.NORMAL), textBeforeCursor = editor.text)
        assertEquals(deadline, pipeline.pendingLongPressDeadlineMs)
    }
}
