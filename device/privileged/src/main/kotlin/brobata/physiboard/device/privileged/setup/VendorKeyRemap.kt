package brobata.physiboard.device.privileged.setup

import brobata.physiboard.device.privileged.DeviceStateStore
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import brobata.physiboard.device.privileged.broker.ShellRunner
import brobata.physiboard.device.privileged.updateCaptures

/**
 * "Set Fn key to Ctrl" (the Fn Layer screen's card): writes the two vendor rows that make the
 * vendor layer synthesize Ctrl out of the Fn key, capturing the phone's own values once so
 * "Reset Fn key to default" can put them back. [ResetToStock.run] performs the same revert as
 * part of a full reset; this class is the forward direction plus the same revert on its own, for
 * the Fn Layer screen's card.
 *
 * 3.0 declares no `WRITE_SETTINGS` (see [ResetToStock]'s own note), so unlike the spec's "direct,
 * else broker" route this always goes through the broker; a missing pairing key is reported as
 * needing permission, and wireless debugging being off surfaces as an ordinary failure once the
 * shell line itself cannot reach the phone.
 *
 * spec: keys-and-modifiers.md SS3.6 ("Enabling the vendor Fn-to-Ctrl remap"); broker-privileged-
 * toolbox.md SS9, SS10 step 1.
 */
class FnCtrlRemap(
    private val shell: ShellRunner,
    private val settings: SystemSettingsAccess,
    private val store: DeviceStateStore,
) {
    /** spec: SS3.6 ("shows 'Fn is set to Ctrl (check)' when both keys already read 1"). */
    fun isEnabled(): Boolean = settings.getInt(VendorKeyRows.FN_ENABLE) == 1 && settings.getInt(VendorKeyRows.FN_FUNCTION) == 1

    /** spec: SS3.6 ("Before the first write, the original values are captured... Success is confirmed by reading both keys back as 1"). */
    fun apply(): RevertOutcome {
        if (isEnabled()) return RevertOutcome.SUCCESS
        captureIfNeeded()
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return RevertOutcome.NEEDS_PERMISSION
        val line = ShellLines.joined(ShellLines.systemPut(VendorKeyRows.FN_ENABLE, "1"), ShellLines.systemPut(VendorKeyRows.FN_FUNCTION, "1"))
        if (!shell.run(line).isOk) return RevertOutcome.FAILED
        return if (isEnabled()) RevertOutcome.SUCCESS else RevertOutcome.FAILED
    }

    /** spec: SS3.6 ("'Reset Fn key to default' writes the captured values back (or 0/0 when nothing was captured) and clears the capture"). */
    fun resetToDefault(): RevertOutcome {
        val captures = store.snapshot().captures
        val (enable, function) = RevertValues.fnCtrlTargets(captures)
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return RevertOutcome.NEEDS_PERMISSION
        val line = ShellLines.joined(
            ShellLines.systemPut(VendorKeyRows.FN_ENABLE, enable.toString()),
            ShellLines.systemPut(VendorKeyRows.FN_FUNCTION, function.toString()),
        )
        if (!shell.run(line).isOk) return RevertOutcome.FAILED
        store.updateCaptures { it.copy(fnCtrlPrevCaptured = false, fnCtrlPrevEnable = null, fnCtrlPrevFunction = null) }
        return RevertOutcome.SUCCESS
    }

    private fun captureIfNeeded() {
        val captures = store.snapshot().captures
        if (captures.fnCtrlPrevCaptured) return
        store.updateCaptures {
            it.copy(
                fnCtrlPrevCaptured = true,
                fnCtrlPrevEnable = settings.getInt(VendorKeyRows.FN_ENABLE),
                fnCtrlPrevFunction = settings.getInt(VendorKeyRows.FN_FUNCTION),
            )
        }
    }
}

/**
 * The orange side key's long-press vendor slot (dictation.md SS11.3): redirecting it to
 * PhysiBoard's own trigger and putting it back. [assistantTriggerActivity] is the fully-qualified
 * class name of the app's own trampoline activity; that activity is dictation.md's own feature
 * and does not exist in this module, so [bind] only renders and sends the three-key write a
 * caller who owns that activity can drive. Nothing in this module calls [bind] yet.
 *
 * spec: broker-privileged-toolbox.md SS9 (`func1_long_press_package`, `func1_long_press_activity`,
 * `func1_shortcut_key_enable`), SS10 step 4; dictation.md SS11.3.
 */
class SideKeyAssistantRemap(
    private val shell: ShellRunner,
    private val settings: SystemSettingsAccess,
    private val store: DeviceStateStore,
    private val identity: AppIdentity,
) {
    /** spec: dictation.md SS11.3 ("shows 'on' only when they point at PhysiBoard"). */
    fun isBoundToThisApp(): Boolean = settings.getString(VendorKeyRows.SIDE_KEY_PACKAGE) == identity.packageName

    /** spec: dictation.md SS11.3 ("Binding writes three `Settings.System` keys..."). */
    fun bind(assistantTriggerActivity: String): RevertOutcome {
        captureIfNeeded()
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return RevertOutcome.NEEDS_PERMISSION
        val line = ShellLines.joined(
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_PACKAGE, identity.packageName),
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_ACTIVITY, assistantTriggerActivity),
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_SHORTCUT_ENABLE, "1"),
        )
        if (!shell.run(line).isOk) return RevertOutcome.FAILED
        return if (isBoundToThisApp()) RevertOutcome.SUCCESS else RevertOutcome.FAILED
    }

    /** spec: dictation.md SS11.3 ("Unbinding restores the captured pair and clears the capture"); same shape as [ResetToStock]'s side-key revert. */
    fun unbind(): RevertOutcome {
        val captures = store.snapshot().captures
        val targets = RevertValues.sideKeyTargets(captures)
        if (targets == null) {
            store.updateCaptures { it.copy(sideKeyOriginalCaptured = false, sideKeyOriginalPackage = "", sideKeyOriginalActivity = "") }
            return RevertOutcome.SUCCESS
        }
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return RevertOutcome.NEEDS_PERMISSION
        val (pkg, activity) = targets
        val line = ShellLines.joined(
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_PACKAGE, pkg),
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_ACTIVITY, activity),
        )
        if (!shell.run(line).isOk) return RevertOutcome.FAILED
        store.updateCaptures { it.copy(sideKeyOriginalCaptured = false, sideKeyOriginalPackage = "", sideKeyOriginalActivity = "") }
        return RevertOutcome.SUCCESS
    }

    /** spec: dictation.md SS11.3 ("captured once... but only if both values are well-formed... Any value that fails is neither recorded nor ever written"). */
    private fun captureIfNeeded() {
        val captures = store.snapshot().captures
        if (captures.sideKeyOriginalCaptured) return
        val currentPackage = settings.getString(VendorKeyRows.SIDE_KEY_PACKAGE)
        val currentActivity = settings.getString(VendorKeyRows.SIDE_KEY_ACTIVITY)
        if (currentPackage == null || currentActivity == null) return
        if (!SideKeyValue.isSafe(currentPackage) || !SideKeyValue.isSafe(currentActivity)) return
        store.updateCaptures { it.copy(sideKeyOriginalCaptured = true, sideKeyOriginalPackage = currentPackage, sideKeyOriginalActivity = currentActivity) }
    }
}
