package brobata.physiboard.device.privileged.setup

import brobata.physiboard.core.toolbox.AccessibilityServiceList
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.broker.ShellRunner

/** How "Turn on with pairing" ended. */
enum class AccessibilityTurnOnOutcome {
    TURNED_ON,
    ALREADY_ON,
    NOT_PAIRED,

    /** The list could not be read, or the write did not take. */
    FAILED,

    /** Another app's entry is not a plain component, so the list is not rewritten (it could lose that app's service). */
    LIST_NOT_SAFE,
}

/**
 * Turns PhysiBoard's accessibility service on through the paired broker, for a phone where
 * Android's own screen will not let a sideloaded app's service be turned on ("restricted
 * setting"). Only ever on the user's tap: the app never does this by itself. It appends
 * PhysiBoard's entry to `enabled_accessibility_services`, keeping every other service, and sets
 * `accessibility_enabled` to 1. "Reset device settings to stock" takes the entry out again
 * ([ResetToStock], SS10 step 7).
 *
 * Blocking; call it on the privileged worker. spec: broker-privileged-toolbox.md SS9.
 */
class AccessibilityServiceSwitch(
    private val shell: ShellRunner,
    private val permissions: PermissionProbe,
    private val packageName: String,
) {
    fun turnOn(): AccessibilityTurnOnOutcome {
        if (permissions.isAccessibilityServiceEnabled()) return AccessibilityTurnOnOutcome.ALREADY_ON
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return AccessibilityTurnOnOutcome.NOT_PAIRED
        val reading = (shell.run(AccessibilityServiceList.READ_LINE) as? ShellResult.Ok)?.output?.let(AccessibilityServiceList::parse)
            ?: return AccessibilityTurnOnOutcome.FAILED
        val line = AccessibilityServiceList.enableLine(reading, packageName)
            ?: return if (reading.entries.any { AccessibilityServiceList.isOurs(it, packageName) } && reading.masterOn) {
                AccessibilityTurnOnOutcome.ALREADY_ON
            } else {
                AccessibilityTurnOnOutcome.LIST_NOT_SAFE
            }
        if (!shell.run(line).isOk) return AccessibilityTurnOnOutcome.FAILED
        // The outcome is the re-read, as for every grant (SS7): a write that "succeeded" but did not take is a failure.
        return if (permissions.isAccessibilityServiceEnabled()) AccessibilityTurnOnOutcome.TURNED_ON else AccessibilityTurnOnOutcome.FAILED
    }
}
