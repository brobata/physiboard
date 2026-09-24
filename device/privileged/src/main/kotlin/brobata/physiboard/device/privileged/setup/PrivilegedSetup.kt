package brobata.physiboard.device.privileged.setup

import brobata.physiboard.device.privileged.DeviceStateStore
import brobata.physiboard.device.privileged.DiagnosticsStore
import brobata.physiboard.device.privileged.PrivilegedStep
import brobata.physiboard.device.privileged.StepOutcome
import brobata.physiboard.device.privileged.StepReasons
import brobata.physiboard.device.privileged.backlight.KeyboardBacklightController
import brobata.physiboard.device.privileged.broker.BrokerRules
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.broker.ShellRunner

/** Why the pass ran, for the record. spec: broker-privileged-toolbox.md SS7. */
object SetupReasons {
    const val PAIRING_SUCCEEDED = "pairing_succeeded"
    const val IME_START = "ime_start"
    const val BACKLIGHT_SCREEN = "backlight_screen"
}

/** One run's outcomes, keyed by step; every step always has one (the blocker short-circuit records all four). */
data class SetupReport(val reason: String, val outcomes: Map<PrivilegedStep, StepOutcome>)

/**
 * The idempotent pass that applies every privileged change the enabled features need. It runs
 * at pairing success, at every IME start and whenever the Smart backlight screen sees the
 * feature enabled with a key stored, so every step tolerates being run any number of times.
 *
 * Every step records its own outcome ([DiagnosticsStore]) rather than only logging: the
 * release build strips every log level below error, and 2.x recorded only two of the four steps
 * (SS7 "Only steps 1 and 4 record outcomes"; SS23 "keep, and fix"). The blocker short-circuit
 * records all four as failed with the blocker's reason, so a silent early return leaves a trace.
 *
 * spec: broker-privileged-toolbox.md SS7, SS8; device-backlight-ring.md SS3.2, SS5.9.
 */
class PrivilegedSetup(
    private val shell: ShellRunner,
    private val permissions: PermissionProbe,
    private val store: DeviceStateStore,
    private val diagnostics: DiagnosticsStore,
    private val backlight: KeyboardBacklightController,
    private val identity: AppIdentity,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Blocking, never throws, never on the main thread. spec: SS7. */
    fun run(reason: String): SetupReport {
        val outcomes = LinkedHashMap<PrivilegedStep, StepOutcome>()
        shell.blocker()?.let { blocker ->
            PrivilegedStep.entries.forEach { step -> outcomes[step] = record(step, ok = false, reason = blocker.reason) }
            return SetupReport(reason, outcomes)
        }
        val settings = store.snapshot()
        outcomes[PrivilegedStep.BACKLIGHT] = guarded(PrivilegedStep.BACKLIGHT) { backlightStep(settings.device.smartBacklightEnabled) }
        outcomes[PrivilegedStep.OVERLAY_GRANT] = guarded(PrivilegedStep.OVERLAY_GRANT) { overlayStep(settings.trackpad.enabled) }
        outcomes[PrivilegedStep.NOTIFICATION_RING] = guarded(PrivilegedStep.NOTIFICATION_RING) { ringGrantsStep(settings.device.ringEnabled) }
        outcomes[PrivilegedStep.RING_BACKLIGHT] = guarded(PrivilegedStep.RING_BACKLIGHT) { ringBacklightStep(settings.device.ringEnabled) }
        return SetupReport(reason, outcomes)
    }

    /** spec: SS7 step 1, "only if `smart_backlight_enabled` is true"; the controller records the outcome itself. */
    private fun backlightStep(smartBacklightEnabled: Boolean): StepOutcome {
        if (!smartBacklightEnabled) return record(PrivilegedStep.BACKLIGHT, ok = true, reason = SKIPPED_DISABLED)
        return backlight.applyNow(smartBacklightEnabled = true)
    }

    /** spec: SS7 step 2; the first successful grant while the trackpad is off switches it on. */
    private fun overlayStep(trackpadEnabled: Boolean): StepOutcome {
        if (permissions.canDrawOverlays()) return record(PrivilegedStep.OVERLAY_GRANT, ok = true, reason = StepReasons.OK)
        val result = shell.run(ShellLines.overlayGrant(identity))
        if (!permissions.canDrawOverlays()) return record(PrivilegedStep.OVERLAY_GRANT, ok = false, reason = failureText(result))
        if (!trackpadEnabled) store.update { s -> s.copy(trackpad = s.trackpad.copy(enabled = true)) }
        return record(PrivilegedStep.OVERLAY_GRANT, ok = true, reason = StepReasons.OK)
    }

    /** spec: SS7 step 3, ring SS5.9: no-op when all three are in place, else one joined line, judged by re-checking. */
    private fun ringGrantsStep(ringEnabled: Boolean): StepOutcome {
        if (!ringEnabled) return record(PrivilegedStep.NOTIFICATION_RING, ok = true, reason = SKIPPED_DISABLED)
        if (ringGrantsInPlace()) return record(PrivilegedStep.NOTIFICATION_RING, ok = true, reason = StepReasons.OK)
        val result = shell.run(ShellLines.ringGrants(identity))
        return if (ringGrantsInPlace()) {
            record(PrivilegedStep.NOTIFICATION_RING, ok = true, reason = StepReasons.OK)
        } else {
            record(PrivilegedStep.NOTIFICATION_RING, ok = false, reason = failureText(result))
        }
    }

    /** spec: SS7 step 4: the outcome is the permission re-check, not the shell's exit status. */
    private fun ringBacklightStep(ringEnabled: Boolean): StepOutcome {
        if (!ringEnabled) return record(PrivilegedStep.RING_BACKLIGHT, ok = true, reason = SKIPPED_DISABLED)
        if (permissions.hasWriteSecureSettings()) return record(PrivilegedStep.RING_BACKLIGHT, ok = true, reason = StepReasons.OK)
        val result = shell.run(ShellLines.secureSettingsGrant(identity))
        return if (permissions.hasWriteSecureSettings()) {
            record(PrivilegedStep.RING_BACKLIGHT, ok = true, reason = StepReasons.OK)
        } else {
            record(PrivilegedStep.RING_BACKLIGHT, ok = false, reason = failureText(result))
        }
    }

    private fun ringGrantsInPlace(): Boolean =
        permissions.isNotificationListenerGranted() && permissions.canUseFullScreenIntent() && permissions.areNotificationsEnabled()

    /** spec: SS7 step 1's failure text ("the broker's last error (or `shell_failed`)"), applied to every step. */
    private fun failureText(result: ShellResult): String = when (result) {
        is ShellResult.Failed -> result.message.ifBlank { StepReasons.SHELL_FAILED }
        is ShellResult.Ok -> shell.lastError ?: StepReasons.SHELL_FAILED
    }

    /** A step that throws still records ("never throws, so the pass can run any number of times"). */
    private fun guarded(step: PrivilegedStep, body: () -> StepOutcome): StepOutcome = try {
        body()
    } catch (error: Exception) {
        record(step, ok = false, reason = BrokerRules.errorText(error))
    }

    private fun record(step: PrivilegedStep, ok: Boolean, reason: String): StepOutcome {
        val outcome = StepOutcome(ok, reason, clock())
        diagnostics.recordStep(step, outcome)
        return outcome
    }

    companion object {
        /**
         * SPEC GAP: the spec skips a step whose feature is off but says nothing about what its
         * row should then hold; recording a distinct "skipped" reason keeps "never ran" and
         * "ran and skipped" apart in the export.
         */
        const val SKIPPED_DISABLED = "skipped_feature_disabled"
    }
}
