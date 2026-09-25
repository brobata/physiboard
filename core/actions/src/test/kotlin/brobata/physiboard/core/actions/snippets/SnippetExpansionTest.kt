package brobata.physiboard.core.actions.snippets

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS12, T1 to T17 (T16 is the dropped colon machinery, SS13). */
class SnippetExpansionTest {

    private val space = KeyId.Control(ControlKey.SPACE)
    private val tab = KeyId.Control(ControlKey.TAB)
    private val enter = KeyId.Control(ControlKey.ENTER)
    private val up = KeyId.Control(ControlKey.DPAD_UP)
    private val escape = KeyId.Control(ControlKey.ESCAPE)

    private val gate = ExpansionGate(fieldReallyEditable = true, fieldRestricted = false, selectionCollapsed = true, editorConnected = true)
    private val base = SnippetSettings(enabled = true, prefix = '!', snippets = listOf(Snippet("sig", "Regards")))

    /** Runs a key against [text] and replays the commit on it, standing in for the editor. */
    private fun press(text: String, key: KeyId, settings: SnippetSettings = base, gate: ExpansionGate = this.gate, modifier: Boolean = false): Pair<ExpansionKeyResult, String> {
        val result = SnippetExpansion.onKeyDown(ExpansionState.EMPTY, key, modifier, text, settings, gate)
        val commit = (result as? ExpansionKeyResult.Consumed)?.commit
        val after = if (commit != null) text.dropLast(commit.deleteCount) + commit.text else text
        return result to after
    }

    @Test
    fun `T1 Space on an exact match deletes the token and commits the replacement plus a space`() {
        val (result, after) = press("Hello !sig", space)
        assertIs<ExpansionKeyResult.Consumed>(result)
        assertEquals(ExpansionCommit(4, "Regards "), result.commit)
        assertEquals("Hello Regards ", after)
    }

    @Test
    fun `T2 a prefix right after a word is not a trigger`() {
        val (result, after) = press("mail!sig", space)
        assertIs<ExpansionKeyResult.NotConsumed>(result)
        assertEquals("mail!sig", after)
    }

    @Test
    fun `T3 Space with only a prefix match and prefix-space off is not consumed`() {
        val (result, _) = press("!si", space, base.copy(presentation = SnippetPresentation.FLOATING_POPUP, expandExactOnSpace = true, acceptPrefixWithSpace = false))
        assertIs<ExpansionKeyResult.NotConsumed>(result)
    }

    @Test
    fun `T4 Space with prefix-space on in the suggestion bar accepts the highlighted match`() {
        val (result, after) = press("!si", space, base.copy(presentation = SnippetPresentation.SUGGESTION_BAR, acceptPrefixWithSpace = true))
        assertIs<ExpansionKeyResult.Consumed>(result)
        assertEquals("Regards ", after)
    }

    @Test
    fun `T5 Tab commits the exact match with no trailing space`() {
        val (result, after) = press("!sig", tab, base.copy(acceptWithTab = true))
        assertIs<ExpansionKeyResult.Consumed>(result)
        assertEquals("Regards", after)
    }

    @Test
    fun `T6 Enter with accept-with-enter off is never touched by expansion`() {
        val (result, _) = press("!sig", enter, base.copy(acceptWithEnter = false))
        assertIs<ExpansionKeyResult.NotConsumed>(result)
    }

    @Test
    fun `T7 a replacement with leading spaces and a trailing newline is committed verbatim`() {
        val value = "  first\nsecond\n  "
        val (result, after) = press("!sig", tab, base.copy(snippets = listOf(Snippet("sig", value))))
        assertIs<ExpansionKeyResult.Consumed>(result)
        assertEquals(value, after)
    }

    @Test
    fun `T8 snippets off means no expansion`() {
        val (result, _) = press("!sig", space, base.copy(enabled = false))
        assertIs<ExpansionKeyResult.NotConsumed>(result)
        assertFalse(result.state.hasMatches)
    }

    @Test
    fun `T9 a restricted field never expands`() {
        val (result, _) = press("!sig", space, gate = gate.copy(fieldRestricted = true))
        assertIs<ExpansionKeyResult.NotConsumed>(result)
        assertFalse(result.state.hasMatches)
    }

    @Test
    fun `T10 a non-collapsed selection never expands`() {
        val (result, _) = press("!sig", space, gate = gate.copy(selectionCollapsed = false))
        assertIs<ExpansionKeyResult.NotConsumed>(result)
        assertFalse(result.state.hasMatches)
    }

    @Test
    fun `T11 Space before the scheduled refresh still expands because the key re-reads the text itself`() {
        // The state is stale (no matches yet); the key evaluates the fresh text before acting.
        val result = SnippetExpansion.onKeyDown(ExpansionState.EMPTY, space, false, "!sig", base, gate)
        assertIs<ExpansionKeyResult.Consumed>(result)
        assertNotNull(result.commit)
    }

    @Test
    fun `T12 only the prefix typed matches every snippet sorted by length then name`() {
        val settings = base.copy(snippets = listOf(Snippet("zzz", "1"), Snippet("ab", "2"), Snippet("aa", "3"), Snippet("b", "4")))
        val state = SnippetExpansion.refresh(ExpansionState.EMPTY, "!", settings, gate)
        assertEquals(listOf("b", "aa", "ab", "zzz"), state.matches.map { it.snippet.shortcut })
    }

    @Test
    fun `T13 prefix validation`() {
        assertTrue(SnippetRules.isValidPrefix("!"))
        assertFalse(SnippetRules.isValidPrefix(":"))
        assertFalse(SnippetRules.isValidPrefix("aa"))
        assertFalse(SnippetRules.isValidPrefix(" "))
        assertFalse(SnippetRules.isValidPrefix("a"))
        assertEquals('!', SnippetRules.effectivePrefix(":"))
        assertEquals('#', SnippetRules.effectivePrefix("#"))
    }

    @Test
    fun `T14 shortcut validation`() {
        assertTrue(SnippetRules.isValidShortcut("sig"))
        assertTrue(SnippetRules.isValidShortcut("my_sig2"))
        assertFalse(SnippetRules.isValidShortcut("a".repeat(41)))
        assertFalse(SnippetRules.isValidShortcut("si-g"))
        assertFalse(SnippetRules.isValidShortcut(""))
    }

    @Test
    fun `T15 an unknown stored presentation reads as the floating popup`() {
        assertEquals(SnippetPresentation.FLOATING_POPUP, SnippetPresentation.fromStored("future-value"))
    }

    @Test
    fun `T17 two matches with the same shortcut give no exact match`() {
        val matches = listOf(SnippetMatch(Snippet("id", "a")), SnippetMatch(Snippet("id", "b")))
        assertNull(SnippetMatcher.exactMatch(matches, "id"))
        assertNotNull(SnippetMatcher.exactMatch(matches.take(1), "id"))
    }

    @Test
    fun `an uppercase shortcut expands and the replacement is inserted as stored`() {
        val (result, after) = press("!SIG", space)
        assertIs<ExpansionKeyResult.Consumed>(result)
        assertEquals("Regards ", after)
    }

    @Test
    fun `a prefix inside quotes is a trigger`() {
        assertNotNull(SnippetTrigger.detect("\"!sig", '!'))
        assertNull(SnippetTrigger.detect("mail!sig", '!'))
        assertNull(SnippetTrigger.detect("!sig ", '!'), "a space after the shortcut ends the candidacy")
    }

    @Test
    fun `presentation Off - Tab on an exact match still expands but Up and Escape are not consumed`() {
        val off = base.copy(presentation = SnippetPresentation.OFF)
        assertIs<ExpansionKeyResult.Consumed>(press("!sig", tab, off).first)
        assertIs<ExpansionKeyResult.NotConsumed>(press("!sig", up, off).first)
        assertIs<ExpansionKeyResult.NotConsumed>(press("!sig", escape, off).first)
    }

    @Test
    fun `Up and Down move the highlight only through the rows the presentation shows`() {
        val settings = base.copy(snippets = (1..5).map { Snippet("s$it", "$it") }, presentation = SnippetPresentation.SUGGESTION_BAR)
        var state = SnippetExpansion.refresh(ExpansionState.EMPTY, "!s", settings, gate)
        repeat(4) { state = SnippetExpansion.onKeyDown(state, KeyId.Control(ControlKey.DPAD_DOWN), false, "!s", settings, gate).state }
        assertEquals(2, state.highlight, "the bar moves within the three visible entries only")
        val popup = settings.copy(presentation = SnippetPresentation.FLOATING_POPUP)
        var popupState = SnippetExpansion.refresh(ExpansionState.EMPTY, "!s", popup, gate)
        repeat(6) { popupState = SnippetExpansion.onKeyDown(popupState, KeyId.Control(ControlKey.DPAD_DOWN), false, "!s", popup, gate).state }
        assertEquals(4, popupState.highlight)
    }

    @Test
    fun `the highlight resets when the typed shortcut changes`() {
        val settings = base.copy(snippets = listOf(Snippet("sa", "1"), Snippet("sb", "2")))
        var state = SnippetExpansion.refresh(ExpansionState.EMPTY, "!s", settings, gate)
        state = SnippetExpansion.onKeyDown(state, KeyId.Control(ControlKey.DPAD_DOWN), false, "!s", settings, gate).state
        assertEquals(1, state.highlight)
        state = SnippetExpansion.refresh(state, "!s", settings, gate)
        assertEquals(1, state.highlight, "kept while the shortcut is unchanged")
        state = SnippetExpansion.refresh(state, "!sb", settings, gate)
        assertEquals(0, state.highlight)
    }

    @Test
    fun `a modifier active in any form never expands`() {
        val (result, _) = press("!sig", space, modifier = true)
        assertIs<ExpansionKeyResult.NotConsumed>(result)
    }

    @Test
    fun `the text changing between the key down and the commit types nothing and clears`() {
        val state = SnippetExpansion.refresh(ExpansionState.EMPTY, "!sig", base, gate)
        val result = SnippetExpansion.onRowTapped(state, 0, "!sig x")
        assertIs<ExpansionKeyResult.Consumed>(result)
        assertNull(result.commit)
        assertFalse(result.state.hasMatches)
    }

    @Test
    fun `a row tap commits with no trailing space`() {
        val state = SnippetExpansion.refresh(ExpansionState.EMPTY, "!sig", base, gate)
        val result = SnippetExpansion.onRowTapped(state, 0, "!sig")
        assertEquals(ExpansionCommit(4, "Regards"), (result as ExpansionKeyResult.Consumed).commit)
    }

    @Test
    fun `the match label is shortcut arrow first line`() {
        assertEquals("sig → Regards,", SnippetMatch(Snippet("sig", "Regards,\nJeremiah")).label)
    }

    @Test
    fun `the store sanitizer lowercases and drops invalid rows with the later duplicate winning`() {
        val raw = linkedMapOf("Sig" to "a", "bad-one" to "b", "blank" to "  ", "SIG" to "c", "ok" to " kept ")
        assertEquals(mapOf("sig" to "c", "ok" to " kept "), SnippetRules.sanitize(raw))
    }

    @Test
    fun `the master switch ships off`() {
        assertFalse(SnippetSettings().enabled)
    }
}
