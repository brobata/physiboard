package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.EditEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deliverable 4 of the "editor is not a reliable narrator" work: a test double for the editor
 * that can be told to refuse reads, answer with stale text, or drop a composing region, and the
 * tests proving the four guarantees the plan section demands. spec: rebuild-from-scratch.md
 * "The editor is not a reliable narrator".
 */
class MisbehavingEditorTest {

    private val en = LanguageCode.of("en")!!
    private fun dict(vararg entries: Pair<String, Int>): DictionaryIndex = DictionaryIndex.build(en, entries.map { WordFrequency(it.first, it.second) })

    /**
     * A field the pipeline can type into, whose read behaviour is misconfigured independently of
     * how it applies edits: [refuseReads] makes every read come back null (spec's "will not
     * answer"); [staleLag], when greater than zero, reports the text as it stood that many
     * batches ago instead of the true, current text (spec's "answers with stale text");
     * [dropsComposingRegion] makes [EditorOp.SetComposingRegion] a silent no-op, so a
     * [EditorOp.CommitText] that follows one lands at the plain cursor instead of over the marked
     * span (spec's "mishandles what is written").
     */
    private class MisbehavingEditor(
        private val refuseReads: Boolean = false,
        private val staleLag: Int = 0,
        private val dropsComposingRegion: Boolean = false,
    ) {
        var text: String = ""
            private set
        var cursor: Int = 0
            private set

        private var composingStart: Int? = null
        private var composingEnd: Int? = null

        /** One snapshot of [text] after every batch, oldest first, for [staleLag] to look back into. */
        private val history = mutableListOf("")

        fun apply(ops: List<EditorOp>) {
            for (op in ops) {
                when (op) {
                    is EditorOp.CommitText -> commit(op.text)
                    is EditorOp.ReplaceBeforeCursor -> {
                        val count = minOf(op.count, cursor)
                        text = text.substring(0, cursor - count) + text.substring(cursor)
                        cursor -= count
                        commit(op.text)
                    }
                    is EditorOp.DeleteSurrounding -> {
                        val before = minOf(op.before, cursor)
                        val after = minOf(op.after, text.length - cursor)
                        text = text.substring(0, cursor - before) + text.substring(cursor + after)
                        cursor -= before
                        clearComposing()
                    }
                    is EditorOp.SetComposingRegion -> {
                        if (!dropsComposingRegion) {
                            composingStart = (cursor - op.charsBeforeCursor).coerceAtLeast(0)
                            composingEnd = composingStart!! + op.length
                        }
                        // Dropped: exactly what a misbehaving app does with the request, per spec's
                        // "mishandles what is written" -- it neither applies it nor reports failure.
                    }
                    EditorOp.FinishComposing -> clearComposing()
                    is EditorOp.SetSelection -> cursor = op.end
                    else -> Unit
                }
            }
            history += text
        }

        /** A real `commitText` replaces the composing region when one is marked, not the plain cursor. */
        private fun commit(inserted: String) {
            val start = composingStart
            val end = composingEnd
            if (start != null && end != null) {
                text = text.substring(0, start) + inserted + text.substring(end)
                cursor = start + inserted.length
            } else {
                text = text.substring(0, cursor) + inserted + text.substring(cursor)
                cursor += inserted.length
            }
            clearComposing()
        }

        private fun clearComposing() {
            composingStart = null
            composingEnd = null
        }

        fun snapshot(): EditorSnapshot {
            if (refuseReads) return EditorSnapshot(textBeforeCursor = null)
            val reported = if (staleLag > 0 && history.size > staleLag) history[history.size - 1 - staleLag] else text
            return EditorSnapshot(textBeforeCursor = reported)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Guarantee 1: the keyboard still types the right characters under every failure mode.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `refused reads still type every character correctly`() {
        val editor = MisbehavingEditor(refuseReads = true)
        val trust = EditorTrust(reads = EditorReadTrust.UNAVAILABLE)
        var state = TextInputState()
        val field = FieldContext(FieldKind.NORMAL)

        for (ch in "Hello, world.") {
            val action = if (ch == ' ') Action.Commit(" ") else Action.Commit(ch.toString())
            val result = TextInputPipeline.handle(TextInputRequest.Key(action), field, TextInputSettingsBundle(), TextInputResources(), state, editor.snapshot(), trust)
            editor.apply(result.ops)
            state = result.state
        }

        assertEquals("Hello, world.", editor.text)
    }

    @Test
    fun `stale reads still type every character correctly`() {
        val editor = MisbehavingEditor(staleLag = 3)
        val trust = EditorTrust(reads = EditorReadTrust.POSSIBLY_STALE)
        var state = TextInputState()
        val field = FieldContext(FieldKind.NORMAL)

        for (ch in "typed plainly") {
            val action = if (ch == ' ') Action.Commit(" ") else Action.Commit(ch.toString())
            val result = TextInputPipeline.handle(TextInputRequest.Key(action), field, TextInputSettingsBundle(), TextInputResources(), state, editor.snapshot(), trust)
            editor.apply(result.ops)
            state = result.state
        }

        assertEquals("typed plainly", editor.text)
    }

    // -----------------------------------------------------------------------------------------
    // Guarantee 2: no rule that needs context fires when context is unavailable.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `sentence-end capitalisation does not arm a one-shot when reads are possibly stale`() {
        val settings = TextInputSettingsBundle()
        val resources = TextInputResources()
        val field = FieldContext(FieldKind.NORMAL)

        fun capDecisionAfter(trust: EditorTrust): CapDecision? {
            val editor = MisbehavingEditor()
            var state = TextInputState()
            var lastDecision: CapDecision? = null
            for (ch in "Hello. ") {
                val action = if (ch == ' ') Action.Commit(" ") else Action.Commit(ch.toString())
                val result = TextInputPipeline.handle(TextInputRequest.Key(action), field, settings, resources, state, editor.snapshot(), trust)
                editor.apply(result.ops)
                state = result.state
                lastDecision = result.capDecision
            }
            return lastDecision
        }

        // A reliable field arms a Shift one-shot on the space that follows "Hello.", so the next
        // letter would be capitalized; under possibly-stale trust the same sequence must not.
        assertEquals(CapDecision.ArmOneShot, capDecisionAfter(EditorTrust.FULL))
        assertEquals(CapDecision.Leave, capDecisionAfter(EditorTrust(reads = EditorReadTrust.POSSIBLY_STALE)))
    }

    @Test
    fun `boundary correction does not fire when reads are unavailable`() {
        val dictionary = dict("the" to 220)
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))
        val resources = TextInputResources(dictionaries = listOf(dictionary))
        val editor = MisbehavingEditor(refuseReads = true)
        val trust = EditorTrust(reads = EditorReadTrust.UNAVAILABLE)
        var state = TextInputState()
        val field = FieldContext(FieldKind.NORMAL)

        for (ch in "teh") {
            val result = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(ch.toString())), field, settings, resources, state, editor.snapshot(), trust)
            editor.apply(result.ops)
            state = result.state
        }
        val spaceResult = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(" ")), field, settings, resources, state, editor.snapshot(), trust)
        editor.apply(spaceResult.ops)

        // A reliable field would correct "teh" to "the" on this space; unavailable reads must
        // switch the rule off entirely rather than still run it from the tracked word alone.
        assertEquals("teh ", editor.text)
    }

    // -----------------------------------------------------------------------------------------
    // Guarantee 3: nothing sets a composing region when composing is unsafe, and the result is
    // still correct even against a field that would otherwise mishandle one.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `composing-unsafe never emits a composing region and lands correctly on a field that drops one`() {
        val editor = MisbehavingEditor(dropsComposingRegion = true)
        editor.apply(listOf(EditorOp.CommitText("cafe")))

        val ops = Composition.replaceVariation(charsBeforeCursor = 1, targetLength = 1, replacement = "é", composingSafety = ComposingSafety.UNSAFE)
        assertTrue(ops.none { it is EditorOp.SetComposingRegion })
        editor.apply(ops)

        assertEquals("café", editor.text)
    }

    @Test
    fun `the composing-safe path corrupts text on a field that drops the composing region, which is why unsafe exists`() {
        val editor = MisbehavingEditor(dropsComposingRegion = true)
        editor.apply(listOf(EditorOp.CommitText("cafe")))

        // Same replacement, but asked the ordinary (composing-safe) way: the field ignores
        // SetComposingRegion, so CommitText lands at the plain cursor instead of over "e",
        // duplicating text instead of replacing it. This is exactly the failure the plan's
        // "mishandles what is written" row describes, and the reason a caller must know to ask
        // for ComposingSafety.UNSAFE on such a field instead.
        val ops = Composition.replaceVariation(charsBeforeCursor = 1, targetLength = 1, replacement = "é")
        editor.apply(ops)

        assertFalse(editor.text == "café", "a field that drops the composing region must not be asked to use one")
    }

    // -----------------------------------------------------------------------------------------
    // Guarantee 4: drift is detected and recovered rather than compounding.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a stale read cannot cause a wrong correction or a doubled character`() {
        val dictionary = dict("world" to 200)
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))
        val resources = TextInputResources(dictionaries = listOf(dictionary))
        val field = FieldContext(FieldKind.NORMAL)

        // The editor answers reliably for reads (full trust), but this particular read disagrees
        // with what the pipeline itself just tracked as the current word: exactly what an
        // asynchronous editor applying edits on its own schedule can produce for one keystroke.
        var state = TextInputState(currentWord = CurrentWordTracker.empty().let { var t = it; for (c in "worlx") t = t.onCharacterCommitted(c); t })
        val staleSnapshot = EditorSnapshot(textBeforeCursor = "something else entirely")

        val result = TextInputPipeline.handle(
            TextInputRequest.Key(Action.Commit(" ")), field, settings, resources, state, staleSnapshot,
        )

        // No correction was attempted against the mismatched window: the space commits plainly,
        // with no delete-and-replace that could otherwise land on the wrong characters or double
        // one up.
        assertEquals(listOf(EditorOp.CommitText(" ")), result.ops)
        state = result.state
        assertEquals("", state.currentWord.word)
    }

    @Test
    fun `after a drifted boundary, typing resumes normally once the editor agrees again`() {
        val dictionary = dict("the" to 220)
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))
        val resources = TextInputResources(dictionaries = listOf(dictionary))
        val field = FieldContext(FieldKind.NORMAL)
        val editor = MisbehavingEditor()

        for (ch in "teh") editor.apply(listOf(EditorOp.CommitText(ch.toString())))
        var state = TextInputState(currentWord = CurrentWordTracker.empty().let { var t = it; for (c in "teh") t = t.onCharacterCommitted(c); t })

        // A drifted read at the boundary: skip the correction, but do not corrupt the field or the
        // tracker state.
        val drifted = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(" ")), field, settings, resources, state, EditorSnapshot(textBeforeCursor = "nothing like it"))
        editor.apply(drifted.ops)
        state = drifted.state
        assertEquals("teh ", editor.text)

        // The next word, typed and boundary-checked against a now-agreeing editor, corrects
        // normally: the earlier disagreement did not compound into this one.
        for (ch in "teh") {
            val result = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(ch.toString())), field, settings, resources, state, editor.snapshot())
            editor.apply(result.ops)
            state = result.state
        }
        val second = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(" ")), field, settings, resources, state, editor.snapshot())
        editor.apply(second.ops)

        assertEquals("teh the ", editor.text)
    }

    // -----------------------------------------------------------------------------------------
    // Guarantee 5: a sentence-ending mark this pipeline just committed itself still arms
    // "capitalize after sentence end" on the following Space even when the field's own read of
    // that same keystroke has not caught up with that commit yet. Device report: "hi. there" typed
    // into a plain field (no per-app profile in this milestone ever requests anything but
    // [EditorTrust.FULL], so this is the only trust level a real device field ever runs under)
    // came out "hi. there", not "hi. There" -- the field-start capital fired but the one after ". "
    // did not. [TextInputPipelineTest]'s and [KeyboardPipelineTest]'s own replays of the same
    // keystroke sequence against a fake editor that is always perfectly up to date already pass, so
    // the gap is not in the rule; this reproduces the one thing that idealised fake editor cannot
    // model: a field whose answer to a read genuinely lags its own most recent commit
    // ([MisbehavingEditor]'s own `staleLag`), under full trust rather than
    // [EditorReadTrust.POSSIBLY_STALE].
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a sentence-ending mark this pipeline just committed still arms capitalisation even if the next read has not caught up`() {
        val settings = TextInputSettingsBundle()
        val resources = TextInputResources()
        val field = FieldContext(FieldKind.NORMAL)
        // staleLag = 1: every read answers with the text as it stood one commit ago, so the read
        // taken for the Space keystroke reports "hi", not "hi." -- the "." this same pipeline
        // committed one keystroke earlier has not shown up in a read yet.
        val editor = MisbehavingEditor(staleLag = 1)
        var state = TextInputState()
        var lastDecision: CapDecision? = null

        for (ch in "hi. ") {
            val result = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(ch.toString())), field, settings, resources, state, editor.snapshot())
            editor.apply(result.ops)
            state = result.state
            lastDecision = result.capDecision
        }

        assertEquals(CapDecision.ArmOneShot, lastDecision)
    }

    @Test
    fun `the same lagging read still respects possibly-stale trust and does not arm`() {
        // Companion to the guarantee above: knowing our own last commit must not become a back door
        // around a field the caller has explicitly decided not to trust (the existing "sentence-end
        // capitalisation does not arm a one-shot when reads are possibly stale" guarantee above).
        // Same lagging read, same keystrokes; only the trust level differs.
        val settings = TextInputSettingsBundle()
        val resources = TextInputResources()
        val field = FieldContext(FieldKind.NORMAL)
        val editor = MisbehavingEditor(staleLag = 1)
        val trust = EditorTrust(reads = EditorReadTrust.POSSIBLY_STALE)
        var state = TextInputState()
        var lastDecision: CapDecision? = null

        for (ch in "hi. ") {
            val result = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(ch.toString())), field, settings, resources, state, editor.snapshot(), trust)
            editor.apply(result.ops)
            state = result.state
            lastDecision = result.capDecision
        }

        assertEquals(CapDecision.Leave, lastDecision)
    }

    // -----------------------------------------------------------------------------------------
    // B5: under reduced trust a rule that needs surrounding context switches off, and a keystroke
    // is never swallowed. spec: rebuild-from-scratch.md "The editor is not a reliable narrator"
    // point 2 ("switched off rather than run on a guess, because typing plainly is always better
    // than corrupting the field"); text-input.md SS8.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `Ctrl+Backspace with an unreadable field passes the key through rather than deleting nothing`() {
        val trust = EditorTrust(reads = EditorReadTrust.UNAVAILABLE)
        val result = TextInputPipeline.handle(
            TextInputRequest.Key(Action.Edit(EditEffect.DELETE_WORD_BACKWARD)),
            FieldContext(FieldKind.NORMAL), TextInputSettingsBundle(), TextInputResources(), TextInputState(),
            EditorSnapshot(textBeforeCursor = null), trust,
        )
        assertEquals(listOf(EditorOp.PassThroughKey), result.ops)
    }

    @Test
    fun `Ctrl+arrow with an unreadable document passes the key through rather than moving nothing`() {
        val trust = EditorTrust(reads = EditorReadTrust.UNAVAILABLE)
        val result = TextInputPipeline.handle(
            TextInputRequest.Key(Action.Edit(EditEffect.MOVE_WORD_LEFT)),
            FieldContext(FieldKind.NORMAL), TextInputSettingsBundle(), TextInputResources(), TextInputState(),
            EditorSnapshot(textBeforeCursor = null, fullText = null), trust,
        )
        assertEquals(listOf(EditorOp.PassThroughKey), result.ops)
    }

    @Test
    fun `select-all with an unreadable document passes the key through rather than being eaten`() {
        val trust = EditorTrust(reads = EditorReadTrust.UNAVAILABLE)
        val result = TextInputPipeline.handle(
            TextInputRequest.Key(Action.Edit(EditEffect.SELECT_ALL)),
            FieldContext(FieldKind.NORMAL), TextInputSettingsBundle(), TextInputResources(), TextInputState(),
            EditorSnapshot(textBeforeCursor = "hello", fullText = null), trust,
        )
        assertEquals(listOf(EditorOp.PassThroughKey), result.ops)
    }

    @Test
    fun `a selection shortcut with an unreadable document passes the key through rather than selecting nothing`() {
        val trust = EditorTrust(reads = EditorReadTrust.UNAVAILABLE)
        val result = TextInputPipeline.handle(
            TextInputRequest.Key(Action.Edit(EditEffect.EXPAND_SELECTION_WORD_RIGHT)),
            FieldContext(FieldKind.NORMAL), TextInputSettingsBundle(), TextInputResources(), TextInputState(),
            EditorSnapshot(textBeforeCursor = "hello", fullText = null), trust,
        )
        assertEquals(listOf(EditorOp.PassThroughKey), result.ops)
    }

    @Test
    fun `Backspace at field start does not become a forward delete on a possibly stale empty read`() {
        // The stale read says the field is empty; the true field holds "ab". A forward delete here
        // would delete nothing and swallow the Backspace the user actually pressed.
        val settings = TextInputSettingsBundle(backspace = BackspaceSettings(backspaceAtStartDeletesForward = true))
        val trust = EditorTrust(reads = EditorReadTrust.POSSIBLY_STALE)
        val result = TextInputPipeline.handle(
            TextInputRequest.Key(Action.Edit(EditEffect.DELETE_CHAR_BACKWARD)),
            FieldContext(FieldKind.NORMAL), settings, TextInputResources(), TextInputState(),
            EditorSnapshot(textBeforeCursor = ""), trust,
        )
        assertEquals(listOf(EditorOp.PassThroughKey), result.ops)
    }

    @Test
    fun `an apostrophe continues the tracked word from the keyboard's own record when reads are refused`() {
        val editor = MisbehavingEditor(refuseReads = true)
        val trust = EditorTrust(reads = EditorReadTrust.UNAVAILABLE)
        var state = TextInputState()
        val field = FieldContext(FieldKind.NORMAL)

        for (ch in "we'll") {
            val result = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(ch.toString())), field, TextInputSettingsBundle(), TextInputResources(), state, editor.snapshot(), trust)
            editor.apply(result.ops)
            state = result.state
        }

        assertEquals("we'll", editor.text)
        assertEquals("we'll", state.currentWord.word, "spec autocorrect-suggestions.md SS1.1: \"we'\" can still become \"we'll\" without asking the editor what precedes")
    }
}
