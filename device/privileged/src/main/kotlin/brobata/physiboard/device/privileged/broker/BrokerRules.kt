package brobata.physiboard.device.privileged.broker

/**
 * The five verified states of the pairing. spec: broker-privileged-toolbox.md SS5.1. Only a
 * real connection attempt produces one; "a key is stored" alone is never shown as ready (SS4.3).
 *
 * [WIRELESS_DEBUGGING_OFF] is deliberately distinct from [NOT_PAIRED]: Android turns Wireless
 * debugging off across every reboot, so a paired phone is routinely unreachable, and the user
 * must be sent to the switch, not told to pair again (SS5.3, "Wireless debugging being off does
 * not invalidate a pairing").
 */
enum class BrokerVerdict {
    NOT_PAIRED,
    WIRELESS_DEBUGGING_OFF,
    NO_SERVICE,
    REJECTED,
    OK;

    /** True when the pairing itself is intact and only the switch or the advertisement is missing. spec: SS5.3. */
    val isPairedButUnreachable: Boolean
        get() = this == WIRELESS_DEBUGGING_OFF || this == NO_SERVICE
}

/** The cheap pre-flight that fails without a broker round trip. spec: SS7 ("Before any step"). */
enum class BrokerBlocker(val reason: String) {
    NOT_PAIRED("not_paired"),
    WIRELESS_DEBUGGING_OFF("wireless_debugging_off"),
}

/** The cached verdict as the freshness rule sees it. spec: SS5.2. */
data class VerdictCache(val verdict: BrokerVerdict?, val recordedAtMs: Long, val invalidated: Boolean)

/**
 * The broker's timing constants and the pure parts of its decisions, so the JVM tests can pin
 * them without a socket.
 *
 * spec: broker-privileged-toolbox.md SS5 (verdicts, freshness), SS6 (the run sequence and its
 * messages), SS2 (the vendored client's timeouts, enforced inside it and recorded here as facts).
 */
object BrokerRules {
    /** spec: SS5.2 ("A verdict is treated as fresh for 10 000 ms"). */
    const val VERDICT_FRESH_MS = 10_000L

    /** spec: SS5.1 step 3, SS6 step 3 ("give up after 8000 ms"). */
    const val CONNECT_DISCOVERY_TIMEOUT_MS = 8_000L

    /** spec: SS2, SS6 step 4; enforced by the vendored `AdbClient`, recorded here so the number has one home. */
    const val TCP_CONNECT_TIMEOUT_MS = 5_000L
    const val READ_TIMEOUT_MS = 10_000L

    /** spec: SS5.3 ("that global is polled every 1500 ms while such a screen is visible"). */
    const val WIRELESS_DEBUGGING_POLL_MS = 1_500L

    /** spec: SS5.1 step 4. */
    const val VERIFY_LINE = "echo physiboard_verify"

    /** spec: SS6 step 2. */
    const val NOT_PAIRED_MESSAGE = "Not paired yet — set up wireless debugging first."

    /** spec: SS6 step 3. */
    const val NO_SERVICE_MESSAGE = "No adb-tls-connect service found. Is wireless debugging on?"

    /** spec: SS7, SS3.2 of the ring document (the gate); T50. */
    fun blocker(hasStoredKey: Boolean, wirelessDebuggingOn: Boolean): BrokerBlocker? = when {
        !hasStoredKey -> BrokerBlocker.NOT_PAIRED
        !wirelessDebuggingOn -> BrokerBlocker.WIRELESS_DEBUGGING_OFF
        else -> null
    }

    /** spec: SS5.2; T39 (5 s old: cached), T40 (11 s old: new check), T41 (invalidated: new check regardless of age). */
    fun needsFreshCheck(cache: VerdictCache, nowMs: Long, forced: Boolean): Boolean =
        forced || cache.invalidated || cache.verdict == null || nowMs - cache.recordedAtMs >= VERDICT_FRESH_MS

    /** spec: SS6 step 5 ("<exception simple name>: <message>"). */
    fun errorText(error: Throwable): String = "${error::class.simpleName}: ${error.message}"

    /**
     * spec: SS4.1 step 3, the re-arm gap. The watcher is normally armed only by the setup card
     * while it is visible; when the process dies with no key stored (reinstall, low memory) and
     * no card is on screen, nothing re-arms it, so a pairing dialog opened from Android's own
     * Settings is never discovered. Re-arming at the next process start is only correct when the
     * watcher was actually doing something when the process died ([wasArmed]) and pairing is
     * still outstanding ([hasStoredKey] false); a flag that was never set means no card armed it
     * this run, and a stored key means the flow already finished.
     */
    fun shouldRearmPairingWatcherAtProcessStart(wasArmed: Boolean, hasStoredKey: Boolean): Boolean =
        wasArmed && !hasStoredKey
}

/**
 * The one streamed command the broker runs: installing PhysiBoard's own update from a file the
 * app keeps private (app-shell.md SS32.3). Only a number goes into the line, never a path or a name.
 */
object PackageInstallLine {
    /** `pm install` waits for the whole APK, then verifies and optimises it before it answers. */
    const val READ_TIMEOUT_MS = 120_000

    fun command(sizeBytes: Long): String {
        require(sizeBytes > 0) { "an APK has bytes" }
        return "cmd package install -r -S $sizeBytes"
    }

    /** `pm` prints "Success" on a line of its own, or "Failure [REASON]". */
    fun succeeded(output: String): Boolean = output.lineSequence().any { it.trim() == "Success" }
}
