package brobata.physiboard.device.privileged.backlight

import brobata.physiboard.device.privileged.DeviceStateStore
import brobata.physiboard.device.privileged.DeviceValueRecord
import brobata.physiboard.device.privileged.DiagnosticsStore
import brobata.physiboard.device.privileged.PrivilegedStep
import brobata.physiboard.device.privileged.StepOutcome
import brobata.physiboard.device.privileged.StepReasons
import brobata.physiboard.device.privileged.broker.BrokerRules
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.broker.ShellRunner
import brobata.physiboard.device.privileged.setup.ShellLines
import brobata.physiboard.device.privileged.updateCaptures
import brobata.physiboard.device.titan.BacklightParcel
import brobata.physiboard.device.titan.BacklightWrite
import brobata.physiboard.device.titan.KeyboardBacklight
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Executes what [KeyboardBacklight] decides about the Smart backlight: the always-on or stock
 * timeout through the vendor transaction, the read-back, and the recorded outcome.
 *
 * Every write runs on one single-thread executor so calls never overlap ("queues behind any
 * earlier backlight write", spec SS7 step 1); the cheap gate runs on the calling thread so a
 * hopeless attempt never blocks for 8 s of discovery (ring document SS3.2). The feature has no
 * runtime component: the value persists in the vendor's store, so this is a one-time write plus
 * a read-back that can catch the phone losing it.
 *
 * spec: device-backlight-ring.md SS3.1, SS3.2, SS3.4 ("Apply again"); broker-privileged-toolbox.md
 * SS7 step 1, SS8.
 */
class KeyboardBacklightController(
    private val shell: ShellRunner,
    private val store: DeviceStateStore,
    private val diagnostics: DiagnosticsStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "physiboard-backlight") },
) {

    /** spec: SS3.2 events 1 and 5: queue the write and return at once. The gate still runs here. */
    fun applyAsync(smartBacklightEnabled: Boolean) {
        if (gate() != null) return
        executor.execute { write(KeyboardBacklight.timeoutWriteFor(smartBacklightEnabled)) }
    }

    /** spec: SS3.2 as the setup pass and the reset run it: queued behind earlier writes, awaited, the truth reported. */
    fun applyNow(smartBacklightEnabled: Boolean): StepOutcome {
        gate()?.let { return it }
        return executor.submit<StepOutcome> { write(KeyboardBacklight.timeoutWriteFor(smartBacklightEnabled)) }.get()
    }

    /**
     * The value the phone holds right now, parsed from the GET parcel, or null when unreadable.
     * Blocking (a broker round trip); the screen calls it only when the verdict is OK (SS3.4).
     */
    fun readDeviceTimeout(): String? {
        val result = shell.run(ShellLines.vendorTimeoutRead)
        val value = BacklightParcel.parse(result.outputOrNull)
        diagnostics.recordBacklightDeviceValue(DeviceValueRecord(value, clock()))
        return value
    }

    /** spec: SS3.4's "no longer holding the always-on setting" line. */
    fun isDeviceValueStale(smartBacklightEnabled: Boolean): Boolean =
        KeyboardBacklight.isTimeoutStale(smartBacklightEnabled, diagnostics.backlightDeviceValue()?.value)

    /** spec: SS3.2 ("Before every write, a cheap gate runs on the calling thread"); T37, T38. */
    private fun gate(): StepOutcome? {
        val blocker = shell.blocker() ?: return null
        return record(ok = false, reason = blocker.reason)
    }

    /** spec: SS3.2's outcome table; T39, T40. */
    private fun write(write: BacklightWrite.VendorTimeoutMs): StepOutcome = try {
        when (val result = shell.run(ShellLines.render(write))) {
            is ShellResult.Ok -> {
                val applied = write.valueMs == KeyboardBacklight.ALWAYS_ON_TIMEOUT_MS
                store.updateCaptures { it.copy(smartBacklightApplied = applied) }
                readDeviceTimeout()
                record(ok = true, reason = StepReasons.OK)
            }
            is ShellResult.Failed -> record(ok = false, reason = result.message.ifBlank { StepReasons.SHELL_FAILED })
        }
    } catch (error: Exception) {
        record(ok = false, reason = BrokerRules.errorText(error))
    }

    private fun record(ok: Boolean, reason: String): StepOutcome {
        val outcome = StepOutcome(ok, reason, clock())
        diagnostics.recordStep(PrivilegedStep.BACKLIGHT, outcome)
        return outcome
    }
}
