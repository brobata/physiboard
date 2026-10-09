package brobata.physiboard.device.privileged

import brobata.physiboard.device.privileged.broker.BrokerVerdict

/**
 * The five privileged steps whose outcome is recorded. spec: broker-privileged-toolbox.md SS8.
 * [key] is the row suffix (`privileged_<key>_ok` / `_reason` / `_at`).
 */
enum class PrivilegedStep(val key: String) {
    BACKLIGHT("backlight"),
    OVERLAY_GRANT("overlay_grant"),
    NOTIFICATION_RING("notification_ring"),
    RING_BACKLIGHT("ring_backlight"),
    SPELL_CHECKER("spell_checker"),
}

/** One step's last outcome. [reason] is one of [StepReasons] or free text. spec: SS8. */
data class StepOutcome(val ok: Boolean, val reason: String, val atMs: Long)

/** The fixed reason strings the spec names; anything else recorded is an error's own text. spec: SS8. */
object StepReasons {
    const val OK = "ok"
    const val NOT_PAIRED = "not_paired"
    const val WIRELESS_DEBUGGING_OFF = "wireless_debugging_off"
    const val SHELL_FAILED = "shell_failed"
}

/** The timeout last read back from the phone; a null [value] means the parcel was unreadable. spec: SS8. */
data class DeviceValueRecord(val value: String?, val atMs: Long)

/** The last verified verdict, which seeds every screen so none opens blank. spec: SS5.2. */
data class VerdictRecord(val verdict: BrokerVerdict, val atMs: Long)

/**
 * Where step outcomes, the backlight read-back and the last verdict are kept.
 *
 * They are persisted, not just logged, because the release build strips every log level below
 * error, and the process that runs the pass (the IME) restarts constantly: without this a
 * silent early return leaves no trace (spec SS7, SS8). 2.x recorded only two of the four steps;
 * 3.0 records all four (SS23, "Record outcomes for the overlay and ring steps too: keep, and fix").
 */
interface DiagnosticsStore {
    fun recordStep(step: PrivilegedStep, outcome: StepOutcome)
    fun step(step: PrivilegedStep): StepOutcome?
    fun recordBacklightDeviceValue(record: DeviceValueRecord)
    fun backlightDeviceValue(): DeviceValueRecord?
    fun recordBrokerVerdict(record: VerdictRecord)
    fun brokerVerdict(): VerdictRecord?

    /**
     * spec: broker-privileged-toolbox.md SS4.1 step 3, the re-arm gap fix. Whether the pairing
     * watcher was armed the last time anything set this flag, surviving the process dying so the
     * next process start can decide whether to re-arm it (see
     * [brobata.physiboard.device.privileged.broker.BrokerRules.shouldRearmPairingWatcherAtProcessStart]).
     * Cleared on "Stop" and on a successful pairing; left alone on a failed attempt, because the
     * user has not given up and the watcher may still need discovering again after a process
     * death.
     */
    fun setPairingWatcherArmed(armed: Boolean)
    fun isPairingWatcherArmed(): Boolean
}

/** The JVM tests' store, and the fallback for a host that wires nothing. */
class InMemoryDiagnosticsStore : DiagnosticsStore {
    private val steps = java.util.concurrent.ConcurrentHashMap<PrivilegedStep, StepOutcome>()

    @Volatile
    private var deviceValue: DeviceValueRecord? = null

    @Volatile
    private var verdict: VerdictRecord? = null

    override fun recordStep(step: PrivilegedStep, outcome: StepOutcome) {
        steps[step] = outcome
    }

    override fun step(step: PrivilegedStep): StepOutcome? = steps[step]

    override fun recordBacklightDeviceValue(record: DeviceValueRecord) {
        deviceValue = record
    }

    override fun backlightDeviceValue(): DeviceValueRecord? = deviceValue

    override fun recordBrokerVerdict(record: VerdictRecord) {
        verdict = record
    }

    override fun brokerVerdict(): VerdictRecord? = verdict

    @Volatile
    private var pairingWatcherArmed = false

    override fun setPairingWatcherArmed(armed: Boolean) {
        pairingWatcherArmed = armed
    }

    override fun isPairingWatcherArmed(): Boolean = pairingWatcherArmed
}

/** Everything the debug export's `[privileged]` section needs that is not already in a [DiagnosticsStore]. spec: SS8. */
data class PrivilegedExportFacts(
    val brokerPaired: Boolean,
    val wirelessDebuggingEnabled: Boolean,
    val brokerBlocker: String?,
    val backlightEnabled: Boolean,
    val backlightAppliedFlag: Boolean,
    val overlayPermissionGranted: Boolean,
    val notificationListenerGranted: Boolean,
    val notificationRingEnabled: Boolean,
    val screenTrackpadEnabled: Boolean,
    val trackpadProvider: String,
    val imeEnabled: Boolean,
    val imeSelected: Boolean,
)

/**
 * The `[privileged]` section of the debug export, one `key=value` per line in the spec's order.
 * spec: broker-privileged-toolbox.md SS8 ("The debug export's `[privileged]` section prints...").
 */
object PrivilegedExport {
    fun lines(facts: PrivilegedExportFacts, diagnostics: DiagnosticsStore, formatTime: (Long) -> String = { it.toString() }): List<String> {
        val deviceValue = diagnostics.backlightDeviceValue()
        val head = listOf(
            "broker_paired=${facts.brokerPaired}",
            "wireless_debugging_enabled=${facts.wirelessDebuggingEnabled}",
            "broker_blocker=${facts.brokerBlocker ?: "none"}",
            "backlight_enabled=${facts.backlightEnabled}",
            "backlight_applied_flag=${facts.backlightAppliedFlag}",
            "backlight_device_value=${deviceValue?.value ?: "never read"}",
            "backlight_device_value_at=${deviceValue?.let { formatTime(it.atMs) } ?: "n/a"}",
            "overlay_permission_granted=${facts.overlayPermissionGranted}",
            "notification_listener_granted=${facts.notificationListenerGranted}",
            "notification_ring_enabled=${facts.notificationRingEnabled}",
            "screen_trackpad_enabled=${facts.screenTrackpadEnabled}",
            "trackpad_provider=${facts.trackpadProvider}",
            "ime_enabled=${facts.imeEnabled}",
            "ime_selected=${facts.imeSelected}",
        )
        val recorded = PrivilegedStep.entries.mapNotNull { step -> diagnostics.step(step)?.let { step to it } }
        val tail = if (recorded.isEmpty()) {
            listOf("last_outcomes=(no privileged step has run)")
        } else {
            recorded.map { (step, outcome) ->
                "last_${step.key}=${if (outcome.ok) "ok" else "failed"} reason='${outcome.reason}' at=${formatTime(outcome.atMs)}"
            }
        }
        return head + tail
    }
}
