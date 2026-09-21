package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.RuleSet
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.EditEffect
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Whole-sequence tests for [TextInputPipeline]. Every rule chained inside it already has its own
 * primitive test elsewhere in this module (see [PunctuationRulesTest], [AutoCapitalizationTest],
 * [BoundaryEngineTest], [BackspaceTest], and so on); what those cannot catch is the *order*, so
 * these drive a virtual field through several keystrokes and assert the final text, including the
 * places two rules interact within or across a boundary.
 */
class TextInputPipelineTest {

    private val en = LanguageCode.of("en")!!
    private fun dict(vararg entries: Pair<String, Int>): DictionaryIndex = DictionaryIndex.build(en, entries.map { WordFrequency(it.first, it.second) })

    private val normalField = FieldContext(FieldKind.NORMAL)

    /** Replays [EditorOp]s against an in-memory field, so a whole keystroke sequence can be asserted as one string. */
    private class VirtualField {
        var text: String = ""
            private set
        var cursor: Int = 0
            private set

        fun apply(ops: List<EditorOp>) {
            for (op in ops) {
                when (op) {
                    is EditorOp.CommitText -> {
                        text = text.substring(0, cursor) + op.text + text.substring(cursor)
                        cursor += op.text.length
                    }
                    is EditorOp.DeleteSurrounding -> {
                        val before = minOf(op.before, cursor)
                        val after = minOf(op.after, text.length - cursor)
                        text = text.substring(0, cursor - before) + text.substring(cursor + after)
                        cursor -= before
                    }
                    is EditorOp.ReplaceBeforeCursor -> {
                        val count = minOf(op.count, cursor)
                        text = text.substring(0, cursor - count) + op.text + text.substring(cursor)
                        cursor = cursor - count + op.text.length
                    }
                    is EditorOp.SetSelection -> cursor = op.end
                    else -> Unit
                }
            }
        }

        fun snapshot(limit: Int = 240): EditorSnapshot = EditorSnapshot(textBeforeCursor = text.substring(maxOf(0, cursor - limit), cursor))
    }

    /**
     * Drives [TextInputPipeline] the way `:ime` must: [pendingCapital] simulates `:ime` consulting
     * the previous call's [CapDecision] and, when it says to arm a one-shot, capitalizing the
     * *next* keystroke's letter before ever asking `:core:keys` to resolve it (see this class's
     * KDoc and the class-level SPEC GAP note in [TextInputPipeline.handleLetter] about why the
     * pipeline itself cannot do this for the same keystroke that triggers the arm).
     */
    private class Session(
        val field: FieldContext = FieldContext(FieldKind.NORMAL),
        val settings: TextInputSettingsBundle = TextInputSettingsBundle(),
        val resources: TextInputResources = TextInputResources(),
    ) {
        val virtualField = VirtualField()
        var state = TextInputState()
        var pendingCapital = false

        fun type(text: String) {
            for (ch in text) type(ch)
        }

        fun type(ch: Char) {
            val cased = if (pendingCapital && ch.isLetter()) ch.uppercaseChar() else ch
            pendingCapital = false
            key(Action.Commit(cased.toString()))
        }

        fun space() = key(Action.Commit(" "))
        fun enter() = key(Action.Edit(EditEffect.NEWLINE))
        fun backspace() = key(Action.Edit(EditEffect.DELETE_CHAR_BACKWARD))
        fun altChar(ch: Char) = key(Action.Commit(ch.toString()))

        private fun key(action: Action) {
            val result = TextInputPipeline.handle(TextInputRequest.Key(action), field, settings, resources, state, virtualField.snapshot())
            virtualField.apply(result.ops)
            state = result.state
            when (result.capDecision) {
                CapDecision.ArmOneShot -> pendingCapital = true
                CapDecision.ClearOneShot, null -> if (result.capDecision == CapDecision.ClearOneShot) pendingCapital = false
                CapDecision.Leave -> Unit
                CapDecision.EnableCapsLock -> Unit
            }
        }
    }

    @Test
    fun `a plain sentence types and spaces normally`() {
        val session = Session()
        session.type("hello")
        session.space()
        session.type("world")
        assertEquals("hello world", session.virtualField.text)
    }

    @Test
    fun `SS9 field-start capital plus SS6 double-space-to-period feed each other across a whole sentence`() {
        val session = Session()
        session.pendingCapital = true // simulates the field-start arm (field-start itself is outside this pipeline's "at minimum" scope)

        session.type("hello")
        assertEquals("Hello", session.virtualField.text)

        session.space()
        session.space() // second Space within the double-space window: converts the trailing space to ". " and re-arms the next capital
        assertEquals("Hello. ", session.virtualField.text)

        session.type("world")
        assertEquals("Hello. World", session.virtualField.text)
    }

    @Test
    fun `a sentence with two boundaries chains a text replacement then an automatic correction`() {
        val ruleSet = RuleSet("en", null, mapOf("dont" to "don't"))
        val dictionary = dict("world" to 200)
        val resources = TextInputResources(ruleSets = listOf(ruleSet), dictionaries = listOf(dictionary))
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))
        val session = Session(settings = settings, resources = resources)

        session.type("dont")
        session.space() // SS7.2 step 6: a text-replacement rule fires before automatic correction is even considered
        assertEquals("don't ", session.virtualField.text)

        session.type("worlx")
        session.space() // SS7.2 step 9: no rule matches "worlx", so the automatic correction gate runs and fixes the typo
        assertEquals("don't world ", session.virtualField.text)
    }

    @Test
    fun `a boundary punctuation mark both corrects a misspelling and reapplies itself`() {
        // Alt-layer punctuation follow-up (SS5.4): the period is already committed when the
        // boundary hand-off runs, so a correction must delete the word plus that period, commit
        // the fix, then re-commit the period (SS14).
        val dictionary = dict("hello" to 200)
        val resources = TextInputResources(dictionaries = listOf(dictionary))
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))
        val session = Session(settings = settings, resources = resources)

        session.type("hellp")
        session.altChar('.')
        assertEquals("hello.", session.virtualField.text)
    }

    @Test
    fun `backspace undoes a correction and a retype after undo is not corrected again`() {
        val dictionary = dict("the" to 220)
        val resources = TextInputResources(dictionaries = listOf(dictionary))
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))
        val session = Session(settings = settings, resources = resources)

        session.type("teh")
        session.space()
        assertEquals("the ", session.virtualField.text)

        session.backspace() // undo: restores "teh" and rejects it (autocorrect-suggestions.md SS7.5)
        assertEquals("teh", session.virtualField.text)

        session.space() // rejected: committed as typed, not corrected a second time
        assertEquals("teh ", session.virtualField.text)
    }

    @Test
    fun `a deferred space debt still pays out through a full sentence`() {
        val settings = TextInputSettingsBundle(spacing = SpacingSettings(beforeNextTextList = "?!"))
        val session = Session(settings = settings)

        session.altChar('?') // "?" is in the "Before next text" list: a space is owed but withheld
        assertEquals("?", session.virtualField.text)

        session.type("W")
        assertEquals("? W", session.virtualField.text)
    }

    @Test
    fun `the one-shot a deferred-space payout arms is consumed by that letter, not the next one`() {
        // Defect 2: `:ime` resolves a letter's case before `:core:text` ever sees it (the
        // milestone-2 sequencing rule; `session.type("W")` below stands in for that already-
        // resolved capital, exactly like the existing "pays out through a full sentence" test
        // above). But handleLetter's InsertSpaceBefore branch *also* re-evaluates auto-cap
        // against the text as it will read once the withheld space lands ("? "), and that
        // second evaluation arms the very same one-shot again with nothing to consume it. Through
        // Session's pendingCapital plumbing (which mirrors `:ime`'s applyCapDecision), that stray
        // arm survives to the next keystroke and capitalizes it too: "? WX" instead of "? Wx".
        val settings = TextInputSettingsBundle(spacing = SpacingSettings(beforeNextTextList = "?"))
        val session = Session(settings = settings)

        session.altChar('?') // a space is owed but withheld
        session.type("W") // already-resolved capital, as `:core:keys` would hand it over
        assertEquals("? W", session.virtualField.text)

        session.type("x")
        assertEquals("? Wx", session.virtualField.text)
    }

    @Test
    fun `enter runs the same boundary hand-off as space before committing the newline`() {
        val dictionary = dict("hello" to 200)
        val resources = TextInputResources(dictionaries = listOf(dictionary))
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))
        val session = Session(settings = settings, resources = resources)

        session.type("hellp")
        session.enter()
        assertEquals("hello\n", session.virtualField.text)
    }
}
