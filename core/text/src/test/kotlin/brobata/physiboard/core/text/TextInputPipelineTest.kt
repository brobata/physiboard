package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.RuleSet
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.EditEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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

        fun snapshot(limit: Int = 240, nowMs: Long = 0L): EditorSnapshot =
            EditorSnapshot(textBeforeCursor = text.substring(maxOf(0, cursor - limit), cursor), nowMs = nowMs)
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

        /** The ops the last request produced, for a test that needs to assert what the pipeline asked for rather than only the text. */
        var lastOps: List<EditorOp> = emptyList()

        /**
         * The wall clock [DoubleSpaceTimer] measures Space key-downs against. Real Space presses
         * always advance it (see [space]'s own call below); a test that also needs to simulate the
         * user pausing between two presses calls this directly first, so a "slowly apart" pair
         * exercises the same 500 ms window a real device clock would (spec: text-input.md SS6.7,
         * SS13).
         */
        var clockMs = 0L

        fun type(text: String) {
            for (ch in text) type(ch)
        }

        fun type(ch: Char) {
            if (ch == ' ') return space()
            val cased = if (pendingCapital && ch.isLetter()) ch.uppercaseChar() else ch
            pendingCapital = false
            key(Action.Commit(cased.toString()))
        }

        /** Space as the user experiences it: in a restricted field the key falls through (text-input.md SS6.2) and the system delivers the space, replayed here. */
        fun space() {
            key(Action.Commit(" "))
            if (lastOps == listOf(EditorOp.PassThroughKey)) virtualField.apply(listOf(EditorOp.CommitText(" ")))
        }

        fun enter() = key(Action.Edit(EditEffect.NEWLINE))
        fun altChar(ch: Char) = key(Action.Commit(ch.toString()))

        /** A multi-character commit (a Sym-page string, an expansion, a paste). spec: text-input.md SS5.3. */
        fun commit(text: String) = key(Action.Commit(text))

        /**
         * Backspace as the user experiences it: when the pipeline lets the key fall through
         * (text-input.md SS8 step 7) the system delivers DEL to the app, which deletes one
         * character; that system-side deletion is replayed here so a sequence can continue past it.
         */
        fun backspace() {
            key(Action.Edit(EditEffect.DELETE_CHAR_BACKWARD))
            if (lastOps == listOf(EditorOp.PassThroughKey)) virtualField.apply(listOf(EditorOp.DeleteSurrounding(1, 0)))
        }

        /**
         * A letter key held past the long-press threshold in the default "alt" mode: the letter
         * was committed on key-down and is now deleted and replaced by its Alt-layer character in
         * one batch edit (text-input.md SS5.2; `:core:keys` hands this over as [Action.ReplaceRecent]).
         */
        fun longPressAlt(letter: Char, alt: Char) {
            type(letter)
            key(Action.ReplaceRecent(1, alt.toString()))
        }

        fun acceptSuggestion(word: String) =
            apply(TextInputPipeline.handle(TextInputRequest.AcceptSuggestion(word), field, settings, resources, state, virtualField.snapshot(nowMs = clockMs)))

        private fun key(action: Action) {
            apply(TextInputPipeline.handle(TextInputRequest.Key(action), field, settings, resources, state, virtualField.snapshot(nowMs = clockMs)))
        }

        private fun apply(result: TextInputResult) {
            lastOps = result.ops
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

    // -----------------------------------------------------------------------------------------
    // Regression: pressing Space more than once used to always produce exactly one space, because
    // the trailing-space guarantee (SS6.1 step 5) treated ANY pre-existing trailing space as one
    // it had already supplied, including a space the user had just typed with a previous, separate
    // Space press. Fixed per spec Keep/Drop SS19 ("make a typed space after a typed space insert
    // one"); these pin the fix and the cases the original guard was legitimately protecting.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `two deliberate Space presses more than 500ms apart each insert their own space`() {
        // T2's "only one space ends up in the field" outcome is the 2.x bug this rewrite fixes
        // (spec Keep/Drop SS19); outside the double-space window, two presses must give two spaces.
        val session = Session()
        session.type("hello")

        session.space()
        assertEquals("hello ", session.virtualField.text, "the first Space should land normally")

        session.clockMs += 600 // outside the 500ms double-space-to-period window
        session.space()
        assertEquals("hello  ", session.virtualField.text, "a second, deliberate Space press must still insert its own space, not find a trailing space and commit nothing")
    }

    @Test
    fun `two Space presses within 500ms still convert to a period, not two spaces`() {
        // The double-space-to-period rule must still fire for a fast pair (spec SS6.7, T1); the
        // fix above must not turn a fast double-space into two literal spaces.
        val session = Session()
        session.type("hello")

        session.space()
        session.space() // clockMs unchanged: both presses land in the same instant, well inside 500ms

        assertEquals("hello. ", session.virtualField.text)
    }

    @Test
    fun `a Space pressed right after accepting a suggestion does not double the auto-space, but a later press does`() {
        // What the guard was actually for (text-input.md SS6.3): a trailing space THIS module put
        // in the field on its own initiative (here, accepting a suggestion) must not be doubled by
        // the very next Space. That protection is a one-time credit, not a standing licence to
        // swallow every later press: once it has been spent, an ordinary second press behaves like
        // any other deliberate Space.
        val session = Session()

        // "Hello", not "hello": the span sits at text start, where auto-capitalization applies to
        // an accepted suggestion (autocorrect-suggestions.md SS5, "capitalized regardless").
        session.acceptSuggestion("hello")
        assertEquals("Hello ", session.virtualField.text, "accepting the suggestion should have appended its own trailing space")

        session.space() // deliberate, but the accepted suggestion already supplied this space
        assertEquals("Hello ", session.virtualField.text, "a Space right after an accepted suggestion must not double it")

        session.clockMs += 600
        session.space() // the one-time credit is spent; this is now an ordinary deliberate press
        assertEquals("Hello  ", session.virtualField.text, "once the auto-space credit is spent, the next deliberate Space must still land")
    }

    @Test
    fun `a Space pressed right after an automatic correction does not double it either`() {
        val dictionary = dict("the" to 220)
        val resources = TextInputResources(dictionaries = listOf(dictionary))
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))
        val session = Session(settings = settings, resources = resources)

        session.type("teh")
        session.space() // corrects to "the" and supplies the boundary's own trailing space
        assertEquals("the ", session.virtualField.text)

        session.clockMs += 600
        session.space() // absorbs the correction's own auto-space rather than doubling it
        assertEquals("the ", session.virtualField.text, "a Space right after an automatic correction must not double it")

        session.clockMs += 600
        session.space() // the credit is spent; a further deliberate press lands normally
        assertEquals("the  ", session.virtualField.text)
    }

    // -----------------------------------------------------------------------------------------
    // B1: a long-press Alt punctuation mark must run the follow-up on the word as it stands once
    // the letter is gone, not on the word with the deleted letter still counted. spec: text-
    // input.md SS5.2 ("the letter is deleted and the Alt-layer character is committed with exactly
    // the same three checks and the same follow-up"); autocorrect-suggestions.md SS10.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `SS5-2 a long-press period after a correctly spelled word leaves the word and its space alone`() {
        val dictionary = dict("cats" to 250, "cat" to 100, "have" to 200, "i" to 200, "a" to 200)
        val resources = TextInputResources(dictionaries = listOf(dictionary))
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1), lengthChangeAllowance = 2)
        val session = Session(settings = settings, resources = resources)

        session.type("I have a cat")
        session.longPressAlt('m', '.') // the held key's letter lands first, then the long press swaps it for "."
        assertEquals("I have a cat.", session.virtualField.text, "the boundary engine must see \"cat\", not \"catm\": a known word is never overwritten")
    }

    @Test
    fun `SS5-2 a long-press period still corrects the misspelled word before it`() {
        val dictionary = dict("hello" to 200)
        val resources = TextInputResources(dictionaries = listOf(dictionary))
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))
        val session = Session(settings = settings, resources = resources)

        session.type("hellp")
        session.longPressAlt('m', '.')
        assertEquals("hello.", session.virtualField.text)
    }

    // -----------------------------------------------------------------------------------------
    // B2: a restricted field gets no rule, no case repair and no correction at any boundary.
    // spec: autocorrect-suggestions.md SS7.4; text-input.md SS3 ("Autocorrect" column), SS6.2.
    // -----------------------------------------------------------------------------------------

    private fun correctingSettings() = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoCorrectEnabled = true, autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1))

    @Test
    fun `SS7-4 a URL bar gets no case repair from boundary punctuation`() {
        val session = Session(field = FieldContext(FieldKind.URL), settings = correctingSettings(), resources = TextInputResources(dictionaries = listOf(dict("Paris" to 200))))
        session.type("wiki/paris")
        session.altChar('/')
        assertEquals("wiki/paris/", session.virtualField.text)
    }

    @Test
    fun `SS7-4 a raw-mode app gets no correction on Space, the key falls through`() {
        val session = Session(field = FieldContext(FieldKind.RAW_MODE_APP), settings = correctingSettings(), resources = TextInputResources(dictionaries = listOf(dict("commit" to 200))))
        session.type("git comit")
        session.space()
        assertEquals(listOf(EditorOp.PassThroughKey), session.lastOps)
        assertEquals("git comit ", session.virtualField.text)
    }

    @Test
    fun `SS7-4 a password field gets no correction from boundary punctuation`() {
        val session = Session(field = FieldContext(FieldKind.PASSWORD), settings = correctingSettings(), resources = TextInputResources(dictionaries = listOf(dict("the" to 200))))
        session.type("teh")
        session.altChar('.')
        session.type("x")
        assertEquals("teh.x", session.virtualField.text)
    }

    @Test
    fun `SS7-4 an email field gets no text replacement on Enter`() {
        val ruleSet = RuleSet("en", null, mapOf("dont" to "don't"))
        val session = Session(field = FieldContext(FieldKind.EMAIL, isMultiLine = true), settings = correctingSettings(), resources = TextInputResources(ruleSets = listOf(ruleSet)))
        session.type("dont")
        session.enter()
        assertEquals("dont\n", session.virtualField.text)
    }

    // -----------------------------------------------------------------------------------------
    // B3: the undo memory is cleared when any character is typed and when a boundary passes
    // without a replacement; the rejected set is cleared by the next letter or digit. spec:
    // autocorrect-suggestions.md SS7.5.
    // -----------------------------------------------------------------------------------------

    private fun theSession(): Session = Session(
        settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1)),
        resources = TextInputResources(dictionaries = listOf(dict("the" to 220))),
    )

    @Test
    fun `SS7-5 Enter after a corrected word is a boundary without a replacement, so Backspace no longer undoes it`() {
        val session = theSession()
        session.type("teh")
        session.space()
        session.enter()
        assertEquals("the \n", session.virtualField.text)

        session.backspace() // deletes the newline like any other character; the correction was accepted
        assertEquals("the ", session.virtualField.text)
    }

    @Test
    fun `SS7-5 typing a character after a correction clears the undo memory`() {
        val session = theSession()
        session.type("teh")
        session.space()
        session.type("a")
        session.backspace() // deletes "a"
        session.backspace() // deletes the space; must not resurrect "teh"
        assertEquals("the", session.virtualField.text)
    }

    @Test
    fun `SS7-5 a rejection survives only until the next letter is typed`() {
        val session = theSession()
        session.type("teh")
        session.space()
        session.backspace() // undo: "teh", rejected
        assertEquals("teh", session.virtualField.text)
        repeat(3) { session.backspace() }
        assertEquals("", session.virtualField.text)

        session.type("teh") // a new word starts: the rejection is gone
        session.space()
        assertEquals("the ", session.virtualField.text)
    }

    // -----------------------------------------------------------------------------------------
    // B4: accepting a suggestion without a whole-document read replaces the tracked word rather
    // than appending to it. spec: autocorrect-suggestions.md SS5 ("deleted and the suggestion
    // committed in its place"); rebuild-from-scratch.md "The editor is not a reliable narrator"
    // point 1 (the pipeline's own record of what it committed).
    // -----------------------------------------------------------------------------------------

    @Test
    fun `SS5 accepting a suggestion with no document read replaces the tracked word`() {
        val session = Session()
        session.type("hel")
        session.acceptSuggestion("hello")
        // Capitalised because the span sits at text start, where auto-capitalization applies
        // (SS5, "the first letter is capitalized regardless"); the point here is that "hel" is gone.
        assertEquals("Hello ", session.virtualField.text)
    }

    // -----------------------------------------------------------------------------------------
    // B6: the double-space timer is reset by any other key. spec: text-input.md SS6.7.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `SS6-7 a letter and a Backspace between two quick Spaces reset the double-space window`() {
        val session = Session()
        session.type("hello")
        session.space()
        session.type("x")
        session.backspace()
        session.clockMs += 100
        session.space()
        assertEquals("hello  ", session.virtualField.text, "the second Space is not the second press of a pair once another key intervened")
    }

    // -----------------------------------------------------------------------------------------
    // B7: a multi-character commit clears the just-committed-sentence-end fact. spec: text-
    // input.md SS5.3 (no capitalization logic at all) and TextInputState.justCommittedSentenceEnd's
    // own contract ("cleared the instant anything else is committed").
    // -----------------------------------------------------------------------------------------

    @Test
    fun `SS5-3 a multi-character commit after a sentence end does not carry the sentence end to the next Space`() {
        val session = Session()
        session.altChar('!')
        session.commit("abc")
        assertEquals(false, session.state.justCommittedSentenceEnd)
        session.space()
        session.type("w")
        assertEquals("!abc w", session.virtualField.text)
    }

    // -----------------------------------------------------------------------------------------
    // B8: an external cursor move drops every fact that was about "the text right before the
    // cursor as this pipeline left it". spec: text-input.md SS2 ("every cursor change that is not
    // the one-character forward step caused by its own last commit clears the deferred-space
    // state ..."), SS6.3 (the auto-space flag names a specific space), autocorrect-suggestions.md
    // SS7.5 (undo checks the text right before the cursor).
    // -----------------------------------------------------------------------------------------

    @Test
    fun `SS2 an external cursor move clears the auto-space flag and the undo memory and resyncs the word`() {
        val before = TextInputState(
            currentWord = CurrentWordTracker.empty().onCharacterCommitted('x'),
            deferredSpace = DeferredSpace.onPunctuationInList(),
            autoSpacePending = true,
            autocorrectMemory = AutocorrectMemory().afterReplacement("teh", "the"),
            justCommittedSentenceEnd = true,
        )
        val after = before.afterExternalCursorMove("some wor")
        assertEquals("wor", after.currentWord.word)
        assertEquals(DeferredSpaceDebt.none(), after.deferredSpace)
        assertEquals(false, after.autoSpacePending)
        assertEquals(null, after.autocorrectMemory.lastReplacement)
        assertEquals(false, after.justCommittedSentenceEnd)

        assertEquals("", before.afterExternalCursorMove(null).currentWord.word, "an unreadable field resets the word rather than keeping a stale one")
    }

    /**
     * Titan, 2026-09-25, a web chat field in Chrome: after every committed letter the editor
     * answered "" for the text before the cursor. Read literally that is "start of text", so
     * every letter of the first word was capitalised ("TESTING"), and because the editor never
     * showed the period, nothing was capitalised after one. The read disagrees with the word
     * this pipeline typed, so no context rule may act on it; the sentence end is still known
     * from the pipeline's own record.
     */
    @Test
    fun `an editor that answers empty after every committed letter capitalises only the first letter and after a sentence end`() {
        val lying = EditorSnapshot(textBeforeCursor = "")
        var state = TextInputState()
        fun press(ch: Char): CapDecision? {
            val result = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(ch.toString())), normalField, TextInputSettingsBundle(), TextInputResources(), state, lying)
            state = result.state
            return result.capDecision
        }
        assertNotEquals(CapDecision.ArmOneShot, press('T'), "the letter just typed is not a new start of text")
        assertNotEquals(CapDecision.ArmOneShot, press('e'), "the empty read disagrees with the tracked word: no capital")
        assertEquals("Te", state.currentWord.word, "the tracked word survives the lying read")
        press('.')
        assertEquals(CapDecision.ArmOneShot, press(' '), "the sentence end is known from the pipeline's own record, not the read")
    }
}
