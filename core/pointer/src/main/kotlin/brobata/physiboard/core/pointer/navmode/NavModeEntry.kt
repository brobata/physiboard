package brobata.physiboard.core.pointer.navmode

import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.KeyCommands
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierMachine
import brobata.physiboard.core.keys.ModifierSettings
import brobata.physiboard.core.keys.ModifierState

/** Whether one Ctrl event just latched, unlatched or left nav mode as it was. spec: trackpad-caret-nav.md SS5.2. */
enum class NavModeTransition { NONE, ENTERED, EXITED }

/** The answer to one Ctrl event handled while no field is focused. */
data class NavModeCtrlResult(val state: ModifierState, val consumed: Boolean, val transition: NavModeTransition)

/**
 * Nav mode's own entry/exit dance on top of `:core:keys`' generic Ctrl latch mechanics.
 *
 * spec: trackpad-caret-nav.md SS5.2 ("Entering and leaving"), the "no field" rows; keys-and-modifiers.md
 * SS15 point 3. [ModifierMachine.ctrlDown] already implements the *un*latch branch (a second Ctrl
 * down while a nav-mode latch is held): its `state.ctrl.latched && state.ctrl.latchFromNavMode &&
 * !navModeInputViewActive` branch fires exactly the way this object calls it (always with that flag
 * false, since there is no field, so there is no keyboard input view to be active). What
 * [ModifierMachine] does not do on its own is mark a *freshly created* latch (the ordinary
 * double-tap-to-latch transition any caller of `ctrlDown` can reach) as coming from nav mode,
 * since it has no idea whether a field is focused; `LayerResolver`'s own KDoc names this exact gap
 * ("Nav-mode behaviour with no editable field focused... is out of this module's scope, [it]
 * belongs with the trackpad surface"). This object is that missing half.
 *
 * A caller is only expected to reach these functions when keys-and-modifiers.md SS15 point 3's own
 * guard already holds ("`nav_mode_enabled` or nav mode is active"): with the setting off and nav
 * mode not already latched, a Ctrl key with no field is not this module's concern at all (SS15
 * routes it to the launcher-shortcut steps instead), so there is nothing to gate a second time
 * here.
 */
object NavModeEntry {

    /** spec SS15 point 3: "Ctrl down: consumed always." */
    fun onCtrlDown(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings): NavModeCtrlResult {
        val result = ModifierMachine.ctrlDown(state, stroke, settings, navModeInputViewActive = false)
        val freshlyLatched = result.state.ctrl.latched && !state.ctrl.latched
        val newState = if (freshlyLatched) {
            result.state.copy(ctrl = result.state.ctrl.copy(latchFromNavMode = true))
        } else {
            result.state
        }
        val transition = when {
            freshlyLatched -> NavModeTransition.ENTERED
            result.action.isExitNavModeCommand() -> NavModeTransition.EXITED
            else -> NavModeTransition.NONE
        }
        return NavModeCtrlResult(newState, consumed = true, transition = transition)
    }

    /** spec SS15 point 3: "Ctrl up: consumed; release time recorded" (already [ModifierMachine.ctrlUp]'s own bookkeeping). */
    fun onCtrlUp(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings): NavModeCtrlResult {
        val result = ModifierMachine.ctrlUp(state, stroke, settings)
        return NavModeCtrlResult(result.state, consumed = true, transition = NavModeTransition.NONE)
    }

    private fun Action.isExitNavModeCommand(): Boolean = this is Action.RunCommand && commandId == KeyCommands.EXIT_NAV_MODE
}
