package brobata.physiboard.core.pointer.navmode

import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.CtrlMapping
import brobata.physiboard.core.keys.CtrlMappingTable
import brobata.physiboard.core.keys.EditEffect
import brobata.physiboard.core.keys.KeyId

/** One nav-mode key-down's answer: what to do, and whether nav mode claims the key. */
data class NavModeKeyDecision(val action: Action, val consumed: Boolean) {
    companion object {
        val NOT_HANDLED = NavModeKeyDecision(Action.PassThrough, consumed = false)
    }
}

/**
 * Resolves a key-down against the Fn Layer map while nav mode is active and no field is focused.
 *
 * spec: trackpad-caret-nav.md SS5.5 ("Applies both in nav mode with no field and, inside a field,
 * whenever the map is consulted"), restricted here to the "no field" column of that table; SS5.2
 * for Enter, which is not part of the 26-letter map. This reuses `:core:keys`' own
 * `CtrlMapping`/`CtrlMappingTable` (keys-and-modifiers.md SS12.2), the exact map
 * `LayerResolver.resolveCtrlActive` already consults for the in-field case, so a mapping means the
 * same thing on both surfaces; `LayerResolver` itself only resolves the in-field column
 * (its own KDoc: nav mode with no field is out of its scope), so there is no duplicate
 * implementation to drift out of sync with, only a shared vocabulary.
 *
 * SS5.5's own general rule for this table, "'ic' means the current input connection; without one
 * the keycode and action types do nothing and report not handled, except commands", is
 * [hasInputConnection] here. Media actions are the one exception even to that rule (SS5.5: "the
 * media key dispatched through the audio manager", never through `ic`), and `page_start`/`page_end`
 * are the opposite exception ("Only inside a field; with no field this type is not handled",
 * regardless of [hasInputConnection]).
 *
 * A Shift meta bit on the original stroke is deliberately not a parameter here: SS5.5's "outside a
 * field the original event's meta state is copied" describes the raw key event `:ime` sends on to
 * the app, which happens with no text-editing pipeline in between when there is no field (unlike
 * the in-field case, where `Action.Edit.extendSelection` feeds `:core:text`'s own selection
 * handling); carrying that meta bit onto the real synthetic `KeyEvent` is `:ime`'s job, not a fact
 * this module's decision needs.
 */
object NavModeMap {

    private val ORDINARY_NAMED_ACTIONS: Map<String, EditEffect> = mapOf(
        "copy" to EditEffect.COPY,
        "paste" to EditEffect.PASTE,
        "cut" to EditEffect.CUT,
        "undo" to EditEffect.UNDO,
        "select_all" to EditEffect.SELECT_ALL,
        "expand_selection_left" to EditEffect.EXPAND_SELECTION_LEFT,
        "expand_selection_right" to EditEffect.EXPAND_SELECTION_RIGHT,
        "expand_selection_word_left" to EditEffect.EXPAND_SELECTION_WORD_LEFT,
        "expand_selection_word_right" to EditEffect.EXPAND_SELECTION_WORD_RIGHT,
        "move_word_left" to EditEffect.MOVE_WORD_LEFT,
        "move_word_right" to EditEffect.MOVE_WORD_RIGHT,
    )

    private val MEDIA_ACTIONS: Map<String, EditEffect> = mapOf(
        "media_play_pause" to EditEffect.MEDIA_PLAY_PAUSE,
        "media_previous" to EditEffect.MEDIA_PREVIOUS,
        "media_next" to EditEffect.MEDIA_NEXT,
    )

    private val FIELD_ONLY_ACTIONS = setOf("page_start", "page_end")

    private val KEYCODE_EFFECTS: Map<ControlKey, EditEffect> = mapOf(
        ControlKey.DPAD_UP to EditEffect.CURSOR_UP,
        ControlKey.DPAD_DOWN to EditEffect.CURSOR_DOWN,
        ControlKey.DPAD_LEFT to EditEffect.CURSOR_LEFT,
        ControlKey.DPAD_RIGHT to EditEffect.CURSOR_RIGHT,
        ControlKey.DPAD_CENTER to EditEffect.CURSOR_CENTER,
        ControlKey.TAB to EditEffect.TAB,
        ControlKey.MOVE_HOME to EditEffect.LINE_HOME,
        ControlKey.MOVE_END to EditEffect.LINE_END,
        ControlKey.PAGE_UP to EditEffect.PAGE_UP,
        ControlKey.PAGE_DOWN to EditEffect.PAGE_DOWN,
        ControlKey.ESCAPE to EditEffect.ESCAPE,
        ControlKey.FORWARD_DELETE to EditEffect.DELETE_CHAR_FORWARD,
    )

    /** spec SS5.2: "Enter | KEYCODE_DPAD_CENTER down and up sent through the input connection, if any; otherwise not consumed." */
    fun resolveEnter(hasInputConnection: Boolean): NavModeKeyDecision =
        if (hasInputConnection) NavModeKeyDecision(Action.Edit(EditEffect.CURSOR_CENTER), consumed = true) else NavModeKeyDecision.NOT_HANDLED

    /** spec SS5.5, the "with no field" column, for one of the 26 mapped letter keys. */
    fun resolveLetterKeyDown(key: KeyId, mappings: CtrlMappingTable, hasInputConnection: Boolean): NavModeKeyDecision =
        when (val mapping = mappings.mappingFor(key)) {
            is CtrlMapping.Command -> NavModeKeyDecision(Action.RunCommand(mapping.commandId), consumed = true)
            is CtrlMapping.NamedAction -> resolveNamedAction(mapping.actionId, hasInputConnection)
            CtrlMapping.NativeCtrl -> if (hasInputConnection) NavModeKeyDecision(Action.ForwardAsCtrlCombo(key), consumed = true) else NavModeKeyDecision.NOT_HANDLED
            is CtrlMapping.Keycode -> resolveKeycode(mapping.key, hasInputConnection)
            CtrlMapping.None -> NavModeKeyDecision.NOT_HANDLED
        }

    private fun resolveNamedAction(actionId: String, hasInputConnection: Boolean): NavModeKeyDecision {
        MEDIA_ACTIONS[actionId]?.let { return NavModeKeyDecision(Action.Edit(it), consumed = true) }
        if (actionId in FIELD_ONLY_ACTIONS) return NavModeKeyDecision.NOT_HANDLED
        if (!hasInputConnection) return NavModeKeyDecision.NOT_HANDLED
        // toggle_minimal_ui (dead, SS5.5) and any unrecognised id both fall through here.
        val effect = ORDINARY_NAMED_ACTIONS[actionId] ?: return NavModeKeyDecision.NOT_HANDLED
        return NavModeKeyDecision(Action.Edit(effect), consumed = true)
    }

    private fun resolveKeycode(key: KeyId, hasInputConnection: Boolean): NavModeKeyDecision {
        if (!hasInputConnection) return NavModeKeyDecision.NOT_HANDLED
        val control = (key as? KeyId.Control)?.key ?: return NavModeKeyDecision.NOT_HANDLED
        val effect = KEYCODE_EFFECTS[control] ?: return NavModeKeyDecision.NOT_HANDLED
        return NavModeKeyDecision(Action.Edit(effect), consumed = true)
    }
}
