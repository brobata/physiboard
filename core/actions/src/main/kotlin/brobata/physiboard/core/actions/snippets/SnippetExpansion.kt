package brobata.physiboard.core.actions.snippets

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId

/** `snippets_presentation`. spec: expansion-clipboard-pickers-launcher.md SS2.5; an unknown stored value falls back to the floating popup (T15). */
enum class SnippetPresentation(val storedValue: String) {
    OFF("off"), FLOATING_POPUP("floating_popup"), SUGGESTION_BAR("suggestion_bar");

    companion object {
        fun fromStored(value: String?): SnippetPresentation = entries.firstOrNull { it.storedValue == value } ?: FLOATING_POPUP
    }
}

/**
 * The eight `snippets_*` rows as the expansion engine reads them. spec SS2.8.
 *
 * SPEC GAP / project rule: SS2.8 lists `snippets_enabled` default `false` and the maintainer's
 * default-ON rule says nothing that intercepts Space, Enter, Shift or Backspace ships enabled
 * until it has survived real use on the Titan; expansion triggers on Space, so [enabled] is
 * false here regardless of what any 2.x baseline said.
 */
data class SnippetSettings(
    val enabled: Boolean = false,
    val prefix: Char = SnippetRules.DEFAULT_PREFIX,
    val snippets: List<Snippet> = emptyList(),
    val presentation: SnippetPresentation = SnippetPresentation.FLOATING_POPUP,
    val expandExactOnSpace: Boolean = true,
    val acceptPrefixWithSpace: Boolean = false,
    val acceptWithTab: Boolean = true,
    val acceptWithEnter: Boolean = false,
)

/**
 * The facts about the field the lookup is gated on. spec SS2.4, "Never when": snippets
 * disabled, the field not really editable, the field restricted (password, URI, e-mail, filter,
 * exact-typing app), a non-collapsed selection, or no connection to the editor.
 */
data class ExpansionGate(
    val fieldReallyEditable: Boolean,
    val fieldRestricted: Boolean,
    val selectionCollapsed: Boolean,
    val editorConnected: Boolean,
) {
    fun allows(settings: SnippetSettings): Boolean =
        settings.enabled && fieldReallyEditable && !fieldRestricted && selectionCollapsed && editorConnected
}

/** The open matches, or nothing. spec SS2.3: the highlight starts at the first entry and resets when the typed shortcut changes. */
data class ExpansionState(
    val token: SnippetToken? = null,
    val matches: List<SnippetMatch> = emptyList(),
    val highlight: Int = 0,
) {
    val hasMatches: Boolean get() = token != null && matches.isNotEmpty()
    val highlighted: SnippetMatch? get() = matches.getOrNull(highlight)

    companion object {
        val EMPTY = ExpansionState()
    }
}

/** What the one commit does to the editor: delete the token backwards, then insert the text, as finished text in one batch (SS2.6). */
data class ExpansionCommit(val deleteCount: Int, val text: String)

/** The answer to one key. spec SS2.6's table. */
sealed class ExpansionKeyResult {
    abstract val state: ExpansionState

    /** The key is consumed; [commit] is null for a highlight move or a clear. */
    data class Consumed(override val state: ExpansionState, val commit: ExpansionCommit? = null) : ExpansionKeyResult()

    /** The key goes on to normal typing. */
    data class NotConsumed(override val state: ExpansionState) : ExpansionKeyResult()
}

/**
 * The text expansion engine as a `(state, input) -> (state, output)` machine. spec SS2.2 to
 * SS2.6. It never reads the editor itself: the caller reads the text before the caret and hands
 * it in with each call, which is what lets T1 to T12 run on the JVM.
 */
object SnippetExpansion {

    private val SPACE = KeyId.Control(ControlKey.SPACE)
    private val TAB = KeyId.Control(ControlKey.TAB)
    private val ENTER = KeyId.Control(ControlKey.ENTER)
    private val DPAD_CENTER = KeyId.Control(ControlKey.DPAD_CENTER)
    private val UP = KeyId.Control(ControlKey.DPAD_UP)
    private val DOWN = KeyId.Control(ControlKey.DPAD_DOWN)
    private val ESCAPE = KeyId.Control(ControlKey.ESCAPE)

    /** spec SS2.4: the coalesced lookup runs 24 ms after the last request. */
    const val LOOKUP_DELAY_MS: Long = 24

    /** spec SS2.6: the keys the engine ever looks at; every other key is never intercepted. */
    fun isExpansionKey(key: KeyId): Boolean =
        key == SPACE || key == TAB || key == ENTER || key == DPAD_CENTER || key == UP || key == DOWN || key == ESCAPE

    /**
     * The lookup (SS2.2 to SS2.4): detects the token in [textBeforeCaret] and matches it. When the
     * gate refuses, or there is no token, the matches clear. The highlight is kept when the typed
     * shortcut is unchanged and reset to the first entry otherwise (SS2.3).
     */
    fun refresh(state: ExpansionState, textBeforeCaret: String?, settings: SnippetSettings, gate: ExpansionGate): ExpansionState {
        if (!gate.allows(settings) || textBeforeCaret == null) return ExpansionState.EMPTY
        val token = SnippetTrigger.detect(textBeforeCaret, settings.prefix) ?: return ExpansionState.EMPTY
        val matches = SnippetMatcher.matches(settings.snippets, token.typedShortcut)
        if (matches.isEmpty()) return ExpansionState.EMPTY
        val sameShortcut = state.token?.typedShortcut == token.typedShortcut
        val highlight = if (sameShortcut) state.highlight.coerceIn(0, matches.lastIndex) else 0
        return ExpansionState(token, matches, highlight)
    }

    /** spec SS2.5: what the chosen presentation draws; Off draws nothing while the matches still exist invisibly. */
    fun visibleRows(state: ExpansionState, presentation: SnippetPresentation): List<SnippetMatch> = when (presentation) {
        SnippetPresentation.OFF -> emptyList()
        SnippetPresentation.FLOATING_POPUP -> state.matches
        SnippetPresentation.SUGGESTION_BAR -> state.matches.take(SUGGESTION_BAR_ROWS)
    }

    /** spec SS2.5: "the first three matches replace the three suggestion slots". */
    const val SUGGESTION_BAR_ROWS: Int = 3

    /**
     * One key-down, evaluated "immediately, before the key is acted on" (SS2.4) against the
     * freshly re-read [textBeforeCaret] (T11). [anyModifierActive] is the caller's answer to
     * SS2.6's "no modifier active in any form (Ctrl, Alt, Shift or Meta held, latched, one-shot,
     * or reported by the event)"; Fn arrives as Ctrl on the Titan, so Fn plus a key never expands.
     */
    fun onKeyDown(
        state: ExpansionState,
        key: KeyId,
        anyModifierActive: Boolean,
        textBeforeCaret: String?,
        settings: SnippetSettings,
        gate: ExpansionGate,
    ): ExpansionKeyResult {
        val fresh = refresh(state, textBeforeCaret, settings, gate)
        if (anyModifierActive || !isExpansionKey(key) || !fresh.hasMatches) return ExpansionKeyResult.NotConsumed(fresh)
        val visible = visibleRows(fresh, settings.presentation).isNotEmpty()
        val exact = SnippetMatcher.exactMatch(fresh.matches, fresh.token!!.typedShortcut)
        return when (key) {
            SPACE -> when {
                settings.expandExactOnSpace && exact != null -> commit(fresh, exact, textBeforeCaret, suffix = " ")
                settings.acceptPrefixWithSpace && exact == null && visible -> commit(fresh, fresh.highlighted!!, textBeforeCaret, suffix = " ")
                else -> ExpansionKeyResult.NotConsumed(fresh)
            }
            TAB -> acceptHighlightedOrExact(fresh, settings.acceptWithTab, visible, exact, textBeforeCaret)
            ENTER, DPAD_CENTER -> acceptHighlightedOrExact(fresh, settings.acceptWithEnter, visible, exact, textBeforeCaret)
            UP -> if (visible) ExpansionKeyResult.Consumed(moveHighlight(fresh, -1, settings.presentation)) else ExpansionKeyResult.NotConsumed(fresh)
            DOWN -> if (visible) ExpansionKeyResult.Consumed(moveHighlight(fresh, +1, settings.presentation)) else ExpansionKeyResult.NotConsumed(fresh)
            ESCAPE -> if (visible) ExpansionKeyResult.Consumed(ExpansionState.EMPTY) else ExpansionKeyResult.NotConsumed(fresh)
            else -> ExpansionKeyResult.NotConsumed(fresh)
        }
    }

    /** A tap on a popup row or a bar slot: "commits that match with no trailing space" (SS2.5). */
    fun onRowTapped(state: ExpansionState, index: Int, textBeforeCaret: String?): ExpansionKeyResult {
        val match = state.matches.getOrNull(index) ?: return ExpansionKeyResult.NotConsumed(state)
        return commit(state, match, textBeforeCaret, suffix = "")
    }

    /** spec SS2.6, Tab row: "Commit the highlighted match if matches are visible, else the exact match if any, with no trailing space; consumed. Otherwise not consumed." */
    private fun acceptHighlightedOrExact(state: ExpansionState, enabled: Boolean, visible: Boolean, exact: SnippetMatch?, text: String?): ExpansionKeyResult {
        if (!enabled) return ExpansionKeyResult.NotConsumed(state)
        val chosen = if (visible) state.highlighted else exact
        return if (chosen != null) commit(state, chosen, text, suffix = "") else ExpansionKeyResult.NotConsumed(state)
    }

    /** spec SS2.5: the popup moves through every row; the bar moves "within the three visible entries only". */
    private fun moveHighlight(state: ExpansionState, delta: Int, presentation: SnippetPresentation): ExpansionState {
        val rows = visibleRows(state, presentation).size
        if (rows == 0) return state
        return state.copy(highlight = (state.highlight + delta).coerceIn(0, rows - 1))
    }

    /**
     * spec SS2.6 "Commit mechanics": the text before the caret must still end with the token,
     * otherwise the matches are just cleared and nothing is typed (SS11, "Text changes between
     * the key down and the commit").
     */
    private fun commit(state: ExpansionState, match: SnippetMatch, textBeforeCaret: String?, suffix: String): ExpansionKeyResult {
        val token = state.token ?: return ExpansionKeyResult.Consumed(ExpansionState.EMPTY)
        val stillThere = textBeforeCaret != null && textBeforeCaret.endsWith(token.text, ignoreCase = true)
        if (!stillThere) return ExpansionKeyResult.Consumed(ExpansionState.EMPTY)
        return ExpansionKeyResult.Consumed(ExpansionState.EMPTY, ExpansionCommit(token.length, match.snippet.replacement + suffix))
    }
}
