package brobata.physiboard.core.actions.launcher

import brobata.physiboard.core.keys.KeyId

/**
 * The `launcher_shortcuts_enabled` / `power_shortcuts_enabled` pair as the router reads them.
 * spec: expansion-clipboard-pickers-launcher.md SS6.6.
 */
data class LauncherKeySettings(
    /** `power_shortcuts_enabled`: Sym-armed and Sym-held assigned keys fire, everywhere. */
    val symShortcutsEnabled: Boolean = true,
    /** `launcher_shortcuts_enabled`: bare assigned keys fire on the home screen. */
    val homeScreenShortcutsEnabled: Boolean = false,
)

/** The armed-Sym mode outside text fields. spec SS6.2 B. */
data class PowerShortcutState(
    val armedAtMs: Long? = null,
    /** Nav mode was on when the mode armed and is restored when it disarms. */
    val navModeSuspended: Boolean = false,
) {
    val isArmed: Boolean get() = armedAtMs != null

    companion object {
        val IDLE = PowerShortcutState()
    }
}

/** What the caller does after one input to the power shortcut mode. spec SS6.2 B. */
data class PowerShortcutEffect(
    val consumed: Boolean = false,
    val scheduleToast: Boolean = false,
    val restoreNavMode: Boolean = false,
    val suspendNavMode: Boolean = false,
    /** The assigned key to act on, as case A would: run its assignment or open the sheet. */
    val fireKey: KeyId? = null,
)

/** spec SS6.2 B: the Sym-armed mode as a `(state, input) -> (state, effect)` machine. */
object PowerShortcutMode {
    /** spec SS6.2 B: the toast "Press shortcut key to launch" shows 500 ms after arming if still armed. */
    const val TOAST_DELAY_MS: Long = 500
    const val TOAST_TEXT: String = "Press shortcut key to launch"

    /** spec SS6.2 B: "the mode disarms by itself after 5000 ms". */
    const val DISARM_AFTER_MS: Long = 5000

    /** spec SS6.2 B: "Toasts of the same text within 1000 ms are collapsed into one." */
    const val TOAST_COLLAPSE_MS: Long = 1000

    /**
     * Sym key down (repeat 0) with no editable field: arms the mode and consumes the key, or
     * disarms it when already armed ("Pressing Sym again while armed disarms it").
     */
    fun onSymDown(state: PowerShortcutState, nowMs: Long, enabled: Boolean, navModeActive: Boolean): Pair<PowerShortcutState, PowerShortcutEffect> {
        if (!enabled) return state to PowerShortcutEffect()
        if (state.isArmed) return disarm(state, consumed = true)
        return PowerShortcutState(armedAtMs = nowMs, navModeSuspended = navModeActive) to
            PowerShortcutEffect(consumed = true, scheduleToast = true, suspendNavMode = navModeActive)
    }

    /** spec: "the next key that is one of the 29 disarms the mode... and then behaves exactly like case A"; other keys leave it armed. */
    fun onKeyDown(state: PowerShortcutState, key: KeyId, nowMs: Long): Pair<PowerShortcutState, PowerShortcutEffect> {
        if (!state.isArmed) return state to PowerShortcutEffect()
        if (expired(state, nowMs)) return disarm(state, consumed = false)
        if (!AssignableKeys.isAssignable(key)) return state to PowerShortcutEffect()
        val (next, effect) = disarm(state, consumed = true)
        return next to effect.copy(fireKey = key)
    }

    /** The 5000 ms timer fired (or any later check found it elapsed). */
    fun onTimeout(state: PowerShortcutState, nowMs: Long): Pair<PowerShortcutState, PowerShortcutEffect> =
        if (state.isArmed && expired(state, nowMs)) disarm(state, consumed = false) else state to PowerShortcutEffect()

    /** Whether the delayed toast should still show: the mode is still armed by the same press (no immediate chord). */
    fun shouldShowToast(state: PowerShortcutState, armedAtMs: Long): Boolean = state.armedAtMs == armedAtMs

    private fun expired(state: PowerShortcutState, nowMs: Long): Boolean = nowMs - (state.armedAtMs ?: return false) >= DISARM_AFTER_MS

    private fun disarm(state: PowerShortcutState, consumed: Boolean): Pair<PowerShortcutState, PowerShortcutEffect> =
        PowerShortcutState.IDLE to PowerShortcutEffect(consumed = consumed, restoreNavMode = state.navModeSuspended)
}

/** How one key down should be routed through the launcher shortcut paths. spec SS6.2. */
sealed class LauncherKeyDecision {
    /**
     * The key holds an assignment: run it, the key is consumed (and, in a text field, the Sym chord
     * counts as used). From [LauncherKeyRouter] this means "the key is this feature's"; the run
     * itself waits for the release ([LauncherPressTiming], SS6.2 D).
     */
    data class Run(val keycode: Int, val entry: ShortcutEntry) : LauncherKeyDecision()

    /**
     * The assignment sheet opens for [keycode]; the key is consumed. Either the key has no
     * assignment (outside a text field, on its down; choosing a command then also runs it), or
     * [byHold]: an assigned key was held past `long_press_threshold` (SS6.2 D), and the sheet
     * opens to reassign or remove it, without running what is chosen.
     */
    data class OpenAssignmentSheet(val keycode: Int, val byHold: Boolean = false) : LauncherKeyDecision()

    /** Not this feature's key; the ordinary pipeline continues. */
    data object FallThrough : LauncherKeyDecision()
}

/** The three ways an assigned key fires. spec SS6.2 A (home screen), C (in a text field); B feeds A through [PowerShortcutEffect.fireKey]. */
object LauncherKeyRouter {

    /**
     * spec SS6.2 A: with no editable field, no Ctrl latch, the foreground package one that answers
     * HOME, `launcher_shortcuts_enabled` on, and the key one of the 29: an assignment runs, an
     * unassigned key opens the sheet. Also the second half of case B once the armed mode fires a
     * key ([fromArmedMode], which does not need the home screen or the home-screen switch), and
     * SS6.2 B's last paragraph: a Sym that is physically held when the key goes down
     * ([symPhysicallyHeld]) fires it the same way, even when the armed mode was never entered,
     * as long as `power_shortcuts_enabled` is on. This mirrors [inTextField]'s `symHeldOrPending`.
     */
    fun outsideTextField(
        key: KeyId,
        shortcuts: LauncherShortcuts,
        settings: LauncherKeySettings,
        ctrlLatchActive: Boolean,
        foregroundIsHome: Boolean,
        fromArmedMode: Boolean,
        symPhysicallyHeld: Boolean = false,
    ): LauncherKeyDecision {
        val keycode = AssignableKeys.keycodeOf(key) ?: return LauncherKeyDecision.FallThrough
        if (ctrlLatchActive) return LauncherKeyDecision.FallThrough
        val symChordFires = symPhysicallyHeld && settings.symShortcutsEnabled
        if (!fromArmedMode && !symChordFires && !(settings.homeScreenShortcutsEnabled && foregroundIsHome)) return LauncherKeyDecision.FallThrough
        val entry = shortcuts[keycode]
        return if (entry != null) LauncherKeyDecision.Run(keycode, entry) else LauncherKeyDecision.OpenAssignmentSheet(keycode)
    }

    /**
     * spec SS6.2 C: in an editable field, power shortcuts on, the key pressed with Sym held or a
     * Sym tap still pending release, repeat 0, and an assignment on that key: it runs. "Keys
     * without an assignment fall through to the Sym chord symbol lookup"; the sheet never opens
     * from a text field. A physically held Sym on the quick launcher's key fires it "even if the
     * mode had not been armed, provided no Ctrl latch is active" (SS6.2 B, last paragraph).
     */
    fun inTextField(
        key: KeyId,
        shortcuts: LauncherShortcuts,
        settings: LauncherKeySettings,
        symHeldOrPending: Boolean,
        isInitialPress: Boolean,
        ctrlLatchActive: Boolean,
    ): LauncherKeyDecision {
        if (!settings.symShortcutsEnabled || !symHeldOrPending || !isInitialPress || ctrlLatchActive) return LauncherKeyDecision.FallThrough
        val keycode = AssignableKeys.keycodeOf(key) ?: return LauncherKeyDecision.FallThrough
        val entry = shortcuts[keycode] ?: return LauncherKeyDecision.FallThrough
        return LauncherKeyDecision.Run(keycode, entry)
    }
}
