package brobata.physiboard.device.privileged.toolbox

import brobata.physiboard.core.toolbox.DensityApplyPlan
import brobata.physiboard.core.toolbox.DensityReading
import brobata.physiboard.core.toolbox.DensityShellLines
import brobata.physiboard.core.toolbox.DisplayDensity
import brobata.physiboard.core.toolbox.PendingRevertRecord
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.broker.ShellRunner

/** One read's outcome. spec: broker-privileged-toolbox.md SS13 ("Read"). */
sealed class DensityReadOutcome {
    object NotPaired : DensityReadOutcome()
    object Unreadable : DensityReadOutcome()
    data class Loaded(val reading: DensityReading) : DensityReadOutcome()
}

/** spec: SS13 ("Apply"). */
sealed class DensityApplyOutcome {
    object Applied : DensityApplyOutcome()
    data class Refused(val reason: String) : DensityApplyOutcome()
    data class Failed(val reason: String) : DensityApplyOutcome()
}

/**
 * Screen density's device layer: reading `wm density`, arming the pending revert record before
 * the change lands, and the countdown's three exits (keep, undo now, reaching zero) plus the
 * always-safe "Back to stock". [checkPendingRevertAtStart] is the fix SS23 Keep/Drop asks for: a
 * pending record survives a crash or a reboot inside the 15 s window, and today nothing reads it
 * back except the density screen's own countdown; calling this once at IME start closes that gap.
 *
 * spec: broker-privileged-toolbox.md SS13; T17 to T25.
 */
class DisplayDensityController(
    private val shell: ShellRunner,
    private val store: ToolboxStateStore,
) {
    fun read(): DensityReadOutcome {
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return DensityReadOutcome.NotPaired
        val result = shell.run(DensityShellLines.READ)
        val output = (result as? ShellResult.Ok)?.output ?: return DensityReadOutcome.Unreadable
        val reading = DisplayDensity.parse(output) ?: return DensityReadOutcome.Unreadable
        return DensityReadOutcome.Loaded(reading)
    }

    /** spec: SS13 ("Apply"); T20 to T22. */
    fun apply(physicalDpi: Int, candidateDpi: Int): DensityApplyOutcome {
        when (val plan = DisplayDensity.planApply(physicalDpi, candidateDpi)) {
            is DensityApplyPlan.Refused -> return DensityApplyOutcome.Refused(plan.reason)
            is DensityApplyPlan.Plan -> {
                // "the revert is armed BEFORE the command lands".
                store.setPendingRevert(plan.pendingRevert)
                val result = shell.run(plan.shellLine)
                if (!result.isOk) {
                    store.setPendingRevert(null)
                    return DensityApplyOutcome.Failed(failureText(result))
                }
                return DensityApplyOutcome.Applied
            }
        }
    }

    /** spec: SS13 ("'Keep it' removes the pending record"); T23. */
    fun keep() = store.setPendingRevert(null)

    /** spec: SS13 ("'Undo now', or reaching zero, sends the recorded revert and removes the record only if that succeeded"); T24, T25. */
    fun revertNow(): Boolean {
        val pending = store.pendingRevert() ?: return true
        val line = DisplayDensity.revertLine(pending) ?: return true
        if (!shell.run(line).isOk) return false
        store.setPendingRevert(null)
        return true
    }

    /** spec: SS13 ("Back to stock: `wm density reset` with no countdown (always safe)"). */
    fun backToStock(): Boolean {
        val ok = shell.run(DensityShellLines.RESET).isOk
        if (ok) store.setPendingRevert(null)
        return ok
    }

    /** spec: SS13 ("Pending revert after process death") and SS23 ("checked at IME start... keep, and fix"). */
    fun checkPendingRevertAtStart() {
        val pending = store.pendingRevert()?.takeIf { it.id == PendingRevertRecord.DISPLAY_DENSITY_ID } ?: return
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return
        if (shell.run(pending.revert).isOk) store.setPendingRevert(null)
    }

    private fun failureText(result: ShellResult): String = (result as? ShellResult.Failed)?.message?.ifBlank { "Command failed" } ?: "Command failed"
}
