package brobata.physiboard.core.actions.launcher

import brobata.physiboard.core.keys.KeyId

/**
 * One press of a key the launcher paths took. spec: expansion-clipboard-pickers-launcher.md
 * SS6.2 D, "Tap or hold".
 *
 * An assigned key does nothing on its down. Released before [thresholdMs] (the same
 * `long_press_threshold` every other hold in PhysiBoard uses) it runs its assignment, on the
 * release itself, with no timer in the way, so a tap is as quick as it was when the key fired on
 * its down. Still held at the threshold, it opens the assignment sheet for that key instead, to
 * reassign or remove it. An unassigned key opens the sheet on its down, as it always did.
 *
 * Either way the press's auto-repeats and its release belong to this feature: none of them reaches
 * the app or the Sym chord lookup, so nothing is typed into a field and no app sees a release
 * whose press it never saw.
 */
data class LauncherPress(
    val key: KeyId,
    val keycode: Int,
    /** The assignment a tap runs; null once the press has done its one thing (the sheet opened). */
    val pendingRun: ShortcutEntry?,
    val downAtMs: Long,
    val thresholdMs: Long,
) {
    /** When a still-held key stops being a tap and opens the sheet. */
    val deadlineMs: Long get() = downAtMs + thresholdMs

    /** The press already acted; what is left of it (repeats, the release) is only swallowed. */
    val settled: Boolean get() = pendingRun == null
}

/** What one event did to a tracked press: the press as it now stands (null once released) and what to perform, if anything. */
data class LauncherPressStep(val press: LauncherPress?, val decision: LauncherKeyDecision?)

/** spec SS6.2 D: tap or hold on a launcher key, as a `(press, event) -> (press, decision)` machine. */
object LauncherPressTiming {

    /**
     * The down the router just decided on ([LauncherKeyRouter]). An assignment waits for the
     * release or the threshold; the sheet for an unassigned key opens now. [LauncherKeyDecision.FallThrough]
     * is not this feature's key and starts nothing.
     */
    fun begin(key: KeyId, decision: LauncherKeyDecision, nowMs: Long, thresholdMs: Long): LauncherPressStep = when (decision) {
        is LauncherKeyDecision.Run -> LauncherPressStep(LauncherPress(key, decision.keycode, decision.entry, nowMs, thresholdMs), null)
        is LauncherKeyDecision.OpenAssignmentSheet -> LauncherPressStep(LauncherPress(key, decision.keycode, null, nowMs, thresholdMs), decision)
        LauncherKeyDecision.FallThrough -> LauncherPressStep(null, null)
    }

    /**
     * The hold timer ran, or an auto-repeat of the key arrived (the system starts repeating about
     * 400 ms in, so a repeat can beat a busy timer). Past the threshold an unsettled press opens the
     * sheet, once; a repeat never runs the assignment, and never runs anything twice.
     */
    fun onTick(press: LauncherPress, nowMs: Long): LauncherPressStep {
        if (press.settled || nowMs < press.deadlineMs) return LauncherPressStep(press, null)
        return LauncherPressStep(press.copy(pendingRun = null), LauncherKeyDecision.OpenAssignmentSheet(press.keycode, byHold = true))
    }

    /**
     * The release. Before the threshold it is a tap and the assignment runs now; at or after it
     * (the timer had not run yet) it was a hold and the sheet opens; a press that already acted
     * ends quietly. The release is consumed in every case.
     */
    fun onRelease(press: LauncherPress, nowMs: Long): LauncherPressStep {
        val entry = press.pendingRun ?: return LauncherPressStep(null, null)
        if (nowMs >= press.deadlineMs) return LauncherPressStep(null, LauncherKeyDecision.OpenAssignmentSheet(press.keycode, byHold = true))
        return LauncherPressStep(null, LauncherKeyDecision.Run(press.keycode, entry))
    }

    /**
     * The field changed while the key was down: whatever the press was going to do is dropped
     * (a release lost with the old window must not leave a timer that opens the sheet later),
     * but its repeats and release are still swallowed if they come.
     */
    fun abandon(press: LauncherPress): LauncherPress = press.copy(pendingRun = null)
}
