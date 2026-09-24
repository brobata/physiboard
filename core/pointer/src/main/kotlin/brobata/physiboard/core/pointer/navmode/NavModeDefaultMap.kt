package brobata.physiboard.core.pointer.navmode

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.CtrlMapping
import brobata.physiboard.core.keys.CtrlMappingTable
import brobata.physiboard.core.keys.KeyId

/**
 * The Fn Layer's shipped default map.
 *
 * spec: trackpad-caret-nav.md SS5.4's table. This keeps the 26-key map itself and the four mapping
 * types, and drops the version-patching migration (SS11 Keep/Drop: "keep, simplify: drop the
 * version patching by starting 3.0 at the current defaults"), so what follows is already the fully
 * migrated (version 3) shape, with no separate "version 1" table to patch forward.
 *
 * SPEC GAP: SS11's Keep/Drop table flags P's shipped default (`toggle_minimal_ui`) as dead code and
 * says to "give P a real default or none". No replacement action is named anywhere in the spec, so
 * P maps to [CtrlMapping.None] here rather than inventing one. B's default
 * default toggled a software keyboard, which 3.0 does not have, so B is unmapped here for the
 * same reason it is unmapped in the Titan layout: a key pointed at something that cannot happen
 * is worse than a key that plainly does nothing. The
 * command catalogue that would run it is out of this module's scope regardless (see
 * `:core:keys`' own `KeyCommands` KDoc).
 */
object NavModeDefaultMap {
    private fun letter(qwertyLetter: Char): KeyId = KeyId.Letter(qwertyLetter)
    private fun keycode(key: ControlKey): CtrlMapping = CtrlMapping.Keycode(KeyId.Control(key))
    private fun action(actionId: String): CtrlMapping = CtrlMapping.NamedAction(actionId)

    val TABLE: CtrlMappingTable = CtrlMappingTable(
        mapOf(
            letter('Q') to keycode(ControlKey.ESCAPE),
            letter('W') to action("expand_selection_left"),
            letter('E') to keycode(ControlKey.DPAD_UP),
            letter('R') to action("expand_selection_right"),
            letter('T') to keycode(ControlKey.TAB),
            letter('Y') to keycode(ControlKey.PAGE_UP),
            letter('U') to action("expand_selection_word_left"),
            letter('I') to action("expand_selection_word_right"),
            letter('O') to keycode(ControlKey.DPAD_CENTER),
            letter('P') to CtrlMapping.None,
            letter('A') to action("select_all"),
            letter('S') to keycode(ControlKey.DPAD_LEFT),
            letter('D') to keycode(ControlKey.DPAD_DOWN),
            letter('F') to keycode(ControlKey.DPAD_RIGHT),
            letter('G') to CtrlMapping.None,
            letter('H') to keycode(ControlKey.PAGE_DOWN),
            letter('J') to keycode(ControlKey.DPAD_LEFT),
            letter('K') to keycode(ControlKey.DPAD_DOWN),
            letter('L') to keycode(ControlKey.DPAD_RIGHT),
            letter('Z') to action("undo"),
            letter('X') to action("cut"),
            letter('C') to action("copy"),
            letter('V') to action("paste"),
            // B toggled the software keyboard in 2.x. 3.0 has none, so it stays unmapped, as in
            // TitanLayouts' Ctrl table. See docs/plans/rebuild-from-scratch.md, decision 2.
            letter('B') to CtrlMapping.None,
            letter('N') to action("move_word_left"),
            letter('M') to action("move_word_right"),
        ),
    )
}
