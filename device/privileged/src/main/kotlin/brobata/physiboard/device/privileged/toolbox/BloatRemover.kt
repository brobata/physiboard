package brobata.physiboard.device.privileged.toolbox

import brobata.physiboard.core.toolbox.BloatCensus
import brobata.physiboard.core.toolbox.BloatCensusResult
import brobata.physiboard.core.toolbox.BloatMutationChecker
import brobata.physiboard.core.toolbox.BloatMutationGate
import brobata.physiboard.core.toolbox.BloatState
import brobata.physiboard.core.toolbox.JournalAction
import brobata.physiboard.core.toolbox.JournalPriorState
import brobata.physiboard.core.toolbox.JournalRecord
import brobata.physiboard.core.toolbox.RemovalJournalCodec
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.broker.ShellRunner
import brobata.physiboard.device.privileged.setup.ShellLines

/** One census read's outcome. spec: broker-privileged-toolbox.md SS12.1, SS12.5. */
sealed class BloatCensusOutcome {
    object NotPaired : BloatCensusOutcome()
    data class Failed(val reason: String) : BloatCensusOutcome()
    data class Loaded(val result: BloatCensusResult) : BloatCensusOutcome()
}

/** One mutation's outcome. spec: SS12.6 ("Outcomes: NotPaired..., Refused..., Failed..."). */
sealed class BloatMutationOutcome {
    object Success : BloatMutationOutcome()
    object NotPaired : BloatMutationOutcome()
    data class Refused(val reason: String) : BloatMutationOutcome()
    data class Failed(val reason: String) : BloatMutationOutcome()
}

private fun BloatState.toJournalPrior(): JournalPriorState = when (this) {
    BloatState.ACTIVE -> JournalPriorState.ACTIVE
    BloatState.DISABLED -> JournalPriorState.DISABLED
    BloatState.UNINSTALLED -> JournalPriorState.UNINSTALLED
    BloatState.ABSENT -> JournalPriorState.ABSENT
}

/**
 * Remove bloat's device layer: the one census read, and the disable/uninstall/restore mutations
 * with their journal bookkeeping. The catalogue rules ([BloatCatalog], [BloatMutationChecker],
 * [BloatCensus], [RemovalJournalCodec]) are `:core:toolbox`'s and are pure; this class only runs
 * them against the real broker and the toolbox preferences file.
 *
 * spec: broker-privileged-toolbox.md SS12.1, SS12.3, SS12.5, SS12.6; T9 to T16.
 */
class BloatRemover(
    private val shell: ShellRunner,
    private val toolboxStore: ToolboxStateStore,
    private val deviceProfile: DeviceProfile,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** spec: SS12.5 ("On entry and after every action, one broker line..."). */
    fun census(): BloatCensusOutcome {
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return BloatCensusOutcome.NotPaired
        return when (val result = shell.run(BloatCensus.COMMAND)) {
            is ShellResult.Ok -> BloatCensusOutcome.Loaded(BloatCensus.parse(result.output))
            is ShellResult.Failed -> BloatCensusOutcome.Failed(result.message)
        }
    }

    /** spec: SS12.6 ("Disable sends `pm disable-user --user 0 <pkg>`... Default action everywhere"). */
    fun disable(packageName: String, priorState: BloatState): BloatMutationOutcome =
        mutate(packageName, priorState, JournalAction.DISABLED, "pm disable-user --user 0 $packageName")

    /** spec: SS12.6 ("Uninstall... sends `pm uninstall --user 0 <pkg>`"); the confirmation dialog is the screen's job. */
    fun uninstall(packageName: String, priorState: BloatState): BloatMutationOutcome =
        mutate(packageName, priorState, JournalAction.UNINSTALLED, "pm uninstall --user 0 $packageName")

    private fun mutate(packageName: String, priorState: BloatState, action: JournalAction, command: String): BloatMutationOutcome {
        when (val gate = BloatMutationChecker.check(packageName, deviceProfile.isTitan2Elite())) {
            is BloatMutationGate.Refused -> return BloatMutationOutcome.Refused(gate.reason)
            BloatMutationGate.Allowed -> Unit
        }
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return BloatMutationOutcome.NotPaired
        // spec: SS12.6 ("Before either command runs, a journal record is written... If the
        // command fails the record is removed again: it never happened, so the journal must not
        // claim it did").
        val record = JournalRecord(packageName, priorState.toJournalPrior(), action, clock())
        toolboxStore.updateJournal { RemovalJournalCodec.upsert(it, record) }
        val result = shell.run(command)
        if (!result.isOk) {
            toolboxStore.updateJournal { RemovalJournalCodec.remove(it, packageName) }
            return BloatMutationOutcome.Failed(failureText(result, "Command failed"))
        }
        return BloatMutationOutcome.Success
    }

    /**
     * spec: SS12.6 ("Restore sends two lines, always both... Success if either line succeeded;
     * the journal record is then forgotten"). SPEC GAP: this broker's [ShellRunner] reports
     * whether the shell round trip itself succeeded, not the exit code of each joined command, so
     * "either line succeeded" is read here as "the round trip succeeded" (the closest available
     * signal); on that success the journal record is forgotten, on failure it is kept so "N
     * package(s) changed" still offers a retry.
     */
    fun restore(packageName: String): BloatMutationOutcome {
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return BloatMutationOutcome.NotPaired
        val line = ShellLines.joined("cmd package install-existing --user 0 $packageName", "pm enable --user 0 $packageName")
        val result = shell.run(line)
        if (!result.isOk) return BloatMutationOutcome.Failed(failureText(result, "Restore failed"))
        toolboxStore.updateJournal { RemovalJournalCodec.remove(it, packageName) }
        return BloatMutationOutcome.Success
    }

    /** spec: SS12.6 ("Restore all... restores each journal record in turn"); does not depend on the catalogue. */
    fun restoreAll(): Map<String, BloatMutationOutcome> = toolboxStore.journal().associate { it.packageName to restore(it.packageName) }

    /** spec: SS12.3 ("disables (never uninstalls) each active entry in turn, in one coroutine, with one broker round trip per package"). */
    fun disablePreset(activePackages: List<Pair<String, BloatState>>): Map<String, BloatMutationOutcome> =
        activePackages.associate { (pkg, prior) -> pkg to disable(pkg, prior) }

    private fun failureText(result: ShellResult, fallback: String): String =
        (result as? ShellResult.Failed)?.message?.ifBlank { fallback } ?: fallback
}
