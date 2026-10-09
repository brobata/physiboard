package brobata.physiboard.device.privileged.setup

import brobata.physiboard.core.settings.DeviceCaptures
import brobata.physiboard.core.toolbox.AccessibilityServiceList
import brobata.physiboard.core.toolbox.SpellCheckerReading
import brobata.physiboard.core.toolbox.SpellCheckerSelection
import brobata.physiboard.device.privileged.DeviceStateStore
import brobata.physiboard.device.privileged.backlight.KeyboardBacklightController
import brobata.physiboard.device.privileged.backlight.MasterSwitchAccess
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.broker.ShellRunner
import brobata.physiboard.device.privileged.updateCaptures
import brobata.physiboard.device.titan.KeyboardBacklight

/** How one revert ended. spec: broker-privileged-toolbox.md SS10. */
enum class RevertOutcome { SUCCESS, FAILED, NEEDS_PERMISSION }

/** The seven reverts, in the spec's order. spec: SS10. */
enum class RevertStep { FN_CTRL, BACKLIGHT, QS_BACKLIGHT, SIDE_KEY, NOTIFICATION_RING, SPELL_CHECKER, ACCESSIBILITY_SERVICE }

/** The whole reset's result, plus the one snackbar line for it. spec: SS10 ("Result snackbar"); T44 to T46. */
data class ResetReport(val outcomes: Map<RevertStep, RevertOutcome>) {
    val message: String get() = ResetMessages.forOutcomes(outcomes.values)
}

/** spec: SS10, the three snackbar texts and their precedence. */
object ResetMessages {
    const val ALL_RESTORED = "Device settings restored to stock."
    const val NEEDS_PERMISSION = "Grant PhysiBoard \"Modify system settings\", or pair wireless debugging, then try again."
    const val PARTIAL = "Some settings were restored. A reboot may be needed for changes to fully apply."

    fun forOutcomes(outcomes: Collection<RevertOutcome>): String = when {
        outcomes.all { it == RevertOutcome.SUCCESS } -> ALL_RESTORED
        outcomes.any { it == RevertOutcome.NEEDS_PERMISSION } -> NEEDS_PERMISSION
        else -> PARTIAL
    }
}

/** The vendor rows the Fn to Ctrl remap and the orange key write. spec: SS9 table, D4. */
object VendorKeyRows {
    const val FN_ENABLE = "fn_programmable_key_enable"
    const val FN_FUNCTION = "fn_programmable_key_function"
    const val SIDE_KEY_PACKAGE = "func1_long_press_package"
    const val SIDE_KEY_ACTIVITY = "func1_long_press_activity"

    /** spec: SS9 ("the vendor ignores every slot unless this is on; stock already has it on"); dictation.md SS11.3. */
    const val SIDE_KEY_SHORTCUT_ENABLE = "func1_shortcut_key_enable"
}

/**
 * The pure parts of the reverts: which values go back. spec: SS10 steps 1 and 4; T47, T48.
 */
object RevertValues {
    /** spec: SS10 step 1 ("an unset or never-captured original becomes 0"). */
    fun fnCtrlTargets(captures: DeviceCaptures): Pair<Int, Int> =
        if (!captures.fnCtrlPrevCaptured) 0 to 0 else (captures.fnCtrlPrevEnable ?: 0) to (captures.fnCtrlPrevFunction ?: 0)

    /**
     * spec: SS10 step 4: nothing captured, or a malformed capture, restores nothing. Returns the
     * package and activity to write, or null for "nothing to write" (which is still SUCCESS).
     */
    fun sideKeyTargets(captures: DeviceCaptures): Pair<String, String>? {
        if (!captures.sideKeyOriginalCaptured) return null
        val pkg = captures.sideKeyOriginalPackage
        val activity = captures.sideKeyOriginalActivity
        if (pkg.isBlank() || activity.isBlank()) return null
        if (!SideKeyValue.isSafe(pkg) || !SideKeyValue.isSafe(activity)) return null
        return pkg to activity
    }
}

/**
 * "Reset device settings to stock": reverts exactly what the app wrote at the OS or vendor
 * level, since Android gives an app no uninstall hook. Seven reverts run independently (one
 * failing never skips the others), off the main thread, each never throwing.
 *
 * Differences from 2.x that the spec's Keep/Drop decides for 3.0: the backlight write is
 * awaited and its truth reported (SS23 "drop: Backlight step reporting SUCCESS on a queued
 * write"); a never-captured tile value is put back to unset, not 0 (ring document SS11). And
 * one deliberate narrowing: the Fn and side-key rows go through the broker only. 3.0 declares
 * no `WRITE_SETTINGS`, so the spec's "direct" route for `Settings.System` does not exist here,
 * and every device write of this module goes through the broker except the master switch
 * ([MasterSwitchAccess], the ring's time-critical exception).
 *
 * spec: broker-privileged-toolbox.md SS10; device-backlight-ring.md SS8.
 */
class ResetToStock(
    private val shell: ShellRunner,
    private val permissions: PermissionProbe,
    private val masterSwitch: MasterSwitchAccess,
    private val store: DeviceStateStore,
    private val backlight: KeyboardBacklightController,
    private val identity: AppIdentity,
) {

    /** Blocking, never throws, never on the main thread. spec: SS10. */
    fun run(): ResetReport {
        val outcomes = LinkedHashMap<RevertStep, RevertOutcome>()
        outcomes[RevertStep.FN_CTRL] = guarded(::revertFnCtrl)
        outcomes[RevertStep.BACKLIGHT] = guarded(::revertBacklight)
        outcomes[RevertStep.QS_BACKLIGHT] = guarded(::revertQsBacklight)
        outcomes[RevertStep.SIDE_KEY] = guarded(::revertSideKey)
        outcomes[RevertStep.NOTIFICATION_RING] = guarded(::revertNotificationRing)
        outcomes[RevertStep.SPELL_CHECKER] = guarded(::revertSpellChecker)
        outcomes[RevertStep.ACCESSIBILITY_SERVICE] = guarded(::revertAccessibilityService)
        return ResetReport(outcomes)
    }

    /** spec: SS10 step 1; the capture is cleared only on success. */
    private fun revertFnCtrl(): RevertOutcome {
        val captures = store.snapshot().captures
        val (enable, function) = RevertValues.fnCtrlTargets(captures)
        if (!shell.isPaired()) return RevertOutcome.NEEDS_PERMISSION
        val line = ShellLines.joined(
            ShellLines.systemPut(VendorKeyRows.FN_ENABLE, enable.toString()),
            ShellLines.systemPut(VendorKeyRows.FN_FUNCTION, function.toString()),
        )
        if (!shell.run(line).isOk) return RevertOutcome.FAILED
        store.updateCaptures { it.copy(fnCtrlPrevCaptured = false, fnCtrlPrevEnable = null, fnCtrlPrevFunction = null) }
        return RevertOutcome.SUCCESS
    }

    /** spec: SS10 step 2 as 3.0 runs it (awaited); ring document T36. */
    private fun revertBacklight(): RevertOutcome {
        val settings = store.snapshot()
        val hadAnything = settings.device.smartBacklightEnabled || settings.captures.smartBacklightApplied
        store.update { it.copy(device = it.device.copy(smartBacklightEnabled = false), captures = it.captures.copy(smartBacklightApplied = false)) }
        if (!hadAnything) return RevertOutcome.SUCCESS
        if (!shell.isPaired()) return RevertOutcome.NEEDS_PERMISSION
        val outcome = backlight.applyNow(smartBacklightEnabled = false)
        return if (outcome.ok) RevertOutcome.SUCCESS else RevertOutcome.FAILED
    }

    /** spec: SS10 step 3 with the 3.0 "never captured means unset" rule; ring document T34, T35. */
    private fun revertQsBacklight(): RevertOutcome {
        val captures = store.snapshot().captures
        val target: Int? = if (captures.qsBacklightPrevCaptured) captures.qsBacklightPrev else null
        val outcome = when {
            target != null && permissions.hasWriteSecureSettings() ->
                if (masterSwitch.write(target)) RevertOutcome.SUCCESS else RevertOutcome.FAILED
            shell.isPaired() -> {
                val line = if (target != null) ShellLines.masterSwitchPut(target) else ShellLines.masterSwitchDelete
                if (shell.run(line).isOk) RevertOutcome.SUCCESS else RevertOutcome.FAILED
            }
            // SPEC GAP: unset cannot be written in-process; with only the permission and no
            // key, the vendor's own default for an unset key (on) is the nearest thing to stock.
            target == null && permissions.hasWriteSecureSettings() ->
                if (masterSwitch.write(KeyboardBacklight.MASTER_SWITCH_UNSET_VALUE)) RevertOutcome.SUCCESS else RevertOutcome.FAILED
            else -> RevertOutcome.NEEDS_PERMISSION
        }
        if (outcome == RevertOutcome.SUCCESS) store.updateCaptures { it.copy(qsBacklightPrevCaptured = false, qsBacklightPrev = null) }
        return outcome
    }

    /** spec: SS10 step 4. */
    private fun revertSideKey(): RevertOutcome {
        store.update { it.copy(dictation = it.dictation.copy(sideKeyAssistant = false)) }
        val captures = store.snapshot().captures
        val targets = RevertValues.sideKeyTargets(captures)
        if (targets == null) {
            // Nothing captured, or a malformed capture dropped: SUCCESS without writing.
            store.updateCaptures { it.copy(sideKeyOriginalCaptured = false, sideKeyOriginalPackage = "", sideKeyOriginalActivity = "") }
            return RevertOutcome.SUCCESS
        }
        if (!shell.isPaired()) return RevertOutcome.NEEDS_PERMISSION
        val (pkg, activity) = targets
        val line = ShellLines.joined(
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_PACKAGE, pkg),
            ShellLines.systemPut(VendorKeyRows.SIDE_KEY_ACTIVITY, activity),
        )
        if (!shell.run(line).isOk) return RevertOutcome.FAILED
        store.updateCaptures { it.copy(sideKeyOriginalCaptured = false, sideKeyOriginalPackage = "", sideKeyOriginalActivity = "") }
        return RevertOutcome.SUCCESS
    }

    /** spec: SS10 step 5; ring document SS8 row 3. */
    private fun revertNotificationRing(): RevertOutcome {
        store.update { it.copy(device = it.device.copy(ringEnabled = false)) }
        if (!permissions.isNotificationListenerGranted() && !permissions.canUseFullScreenIntent()) return RevertOutcome.SUCCESS
        if (!shell.isPaired()) return RevertOutcome.NEEDS_PERMISSION
        shell.run(ShellLines.ringRevoke(identity))
        return if (permissions.isNotificationListenerGranted()) RevertOutcome.FAILED else RevertOutcome.SUCCESS
    }

    /**
     * spec: SS10 step 6: the spell checker rows go back to what the setup pass recorded, but only
     * while PhysiBoard is still the one selected; a spell checker the user picked since is theirs.
     * Like the backlight and the ring, the feature is switched off first, so the next keyboard
     * start does not choose PhysiBoard again; the "decided" marker is cleared with the record, so
     * switching the setting back on makes the setup pass decide afresh.
     */
    private fun revertSpellChecker(): RevertOutcome {
        store.update { it.copy(device = it.device.copy(autoSelectSpellChecker = false)) }
        val captures = store.snapshot().captures
        if (!captures.spellCheckerPrevCaptured) {
            store.updateCaptures { it.copy(spellCheckerDecided = false) }
            return RevertOutcome.SUCCESS
        }
        if (!shell.isPaired()) return RevertOutcome.NEEDS_PERMISSION
        val current = (shell.run(SpellCheckerSelection.READ_LINE) as? ShellResult.Ok)?.output?.let(SpellCheckerSelection::parse)
            ?: return RevertOutcome.FAILED
        val previous = SpellCheckerReading(captures.spellCheckerPrevSelected, captures.spellCheckerPrevEnabled, captures.spellCheckerPrevSubtype)
        val line = SpellCheckerSelection.revertLine(previous, current, identity.spellCheckerComponent)
        if (line != null && !shell.run(line).isOk) return RevertOutcome.FAILED
        store.updateCaptures {
            it.copy(
                spellCheckerDecided = false,
                spellCheckerPrevCaptured = false,
                spellCheckerPrevSelected = null,
                spellCheckerPrevEnabled = null,
                spellCheckerPrevSubtype = null,
            )
        }
        return RevertOutcome.SUCCESS
    }

    /**
     * spec: SS10 step 7: PhysiBoard's accessibility service comes out of Android's list, however it
     * was turned on (the user in Android's settings, or "Turn on with pairing"); every other app's
     * service stays exactly as it was. Not on: nothing to do.
     */
    private fun revertAccessibilityService(): RevertOutcome {
        // Listed with accessibility switched off still counts: the next service to switch it on
        // would bring PhysiBoard's back with it.
        if (!permissions.isAccessibilityServiceListed()) return RevertOutcome.SUCCESS
        if (!shell.isPaired()) return RevertOutcome.NEEDS_PERMISSION
        val reading = (shell.run(AccessibilityServiceList.READ_LINE) as? ShellResult.Ok)?.output?.let(AccessibilityServiceList::parse)
            ?: return RevertOutcome.FAILED
        val line = AccessibilityServiceList.disableLine(reading, identity.packageName) ?: return RevertOutcome.FAILED
        if (!shell.run(line).isOk) return RevertOutcome.FAILED
        return if (permissions.isAccessibilityServiceListed()) RevertOutcome.FAILED else RevertOutcome.SUCCESS
    }

    private fun guarded(body: () -> RevertOutcome): RevertOutcome = try {
        body()
    } catch (_: Exception) {
        RevertOutcome.FAILED
    }

    /** "A key is stored": debugging being off still counts as paired, so the write is attempted and its failure reported as FAILED (SS21). */
    private fun ShellRunner.isPaired(): Boolean = blocker() != BrokerBlocker.NOT_PAIRED
}
