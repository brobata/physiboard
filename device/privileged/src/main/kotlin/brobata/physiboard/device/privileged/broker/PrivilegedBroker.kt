package brobata.physiboard.device.privileged.broker

import brobata.physiboard.device.privileged.DiagnosticsStore
import brobata.physiboard.device.privileged.VerdictRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The narrow face every privileged step runs its shell line through. [PrivilegedBroker] is the
 * only production implementation; the JVM tests substitute a scripted one so each step's
 * outcome recording can be pinned against exact shell text.
 *
 * spec: broker-privileged-toolbox.md SS6, SS7.
 */
interface ShellRunner {
    /** The cheap pre-flight, on the calling thread. spec: SS7. */
    fun blocker(): BrokerBlocker?

    /** One shell line, blocking, never throwing, never on the main thread. spec: SS6. */
    fun run(line: String): ShellResult

    /** The text recorded by the last failed [run]. spec: SS6 step 5. */
    val lastError: String?
}

/**
 * The app's own wireless-ADB client as one serialised service.
 *
 * Every discovery-plus-shell sequence in the process runs under one lock. This is not an
 * optimisation: two overlapping mDNS discoveries of the same service type both fail silently
 * (D3), so two privileged steps started together would each see "no service found" (spec SS6
 * step 1, SS21 "Two privileged steps start at once"). The port is rediscovered on every call
 * because it rotates on every toggle and reboot, and on a charge-only supply about once a second
 * (SS6, SS18); nothing about it is ever cached.
 *
 * There is exactly one verdict in the process ([verdict]), shared by every screen so two can
 * never disagree (SS5.2). It is fresh for [BrokerRules.VERDICT_FRESH_MS]; a request inside that
 * window returns the cached one, a request while a check is in flight waits for that check,
 * and [invalidate] forces the next request to check again.
 *
 * spec: broker-privileged-toolbox.md SS5, SS6.
 */
class PrivilegedBroker(
    private val transport: AdbTransport,
    private val diagnostics: DiagnosticsStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShellRunner {

    private val lock = ReentrantLock()

    private val verdictFlow = MutableStateFlow<BrokerVerdict?>(diagnostics.brokerVerdict()?.verdict)

    /** The one shared verdict; null until the first check lands, seeded from the persisted one so a screen never opens blank. spec: SS5.2. */
    val verdict: StateFlow<BrokerVerdict?> = verdictFlow

    private val checkingFlow = MutableStateFlow(false)

    /** True while a verification holds the lock, for the "Checking…" label. spec: SS5.3. */
    val checking: StateFlow<Boolean> = checkingFlow

    @Volatile
    private var cache = VerdictCache(
        verdict = diagnostics.brokerVerdict()?.verdict,
        recordedAtMs = diagnostics.brokerVerdict()?.atMs ?: 0L,
        // A persisted verdict seeds the display but is never trusted as fresh across a process start.
        invalidated = true,
    )

    @Volatile
    override var lastError: String? = null
        private set

    /** The output of the last successful [run]. spec: SS6 step 5. */
    @Volatile
    var lastResult: String? = null
        private set

    /** spec: SS4.3, "the `adbkey` entry exists". */
    fun isPaired(): Boolean = transport.hasStoredKey()

    /** spec: SS5.1 step 2, SS6 (the switch is read, never cached). */
    fun isWirelessDebuggingOn(): Boolean = transport.isWirelessDebuggingOn()

    override fun blocker(): BrokerBlocker? = BrokerRules.blocker(transport.hasStoredKey(), transport.isWirelessDebuggingOn())

    /** spec: SS6, the whole sequence, blocking under the lock. Never throws. */
    override fun run(line: String): ShellResult = lock.withLock { runLocked(line) }

    /**
     * Installs the APK at [apk] with shell privileges, streaming it to `cmd package install -S`
     * (app-shell.md SS32.3), under the broker lock like every other line. Installing PhysiBoard's
     * own package ends this process before the answer can arrive; a [ShellResult.Ok] is returned
     * only when the shell printed "Success". Never throws; never call on the main thread.
     */
    fun installApk(apk: java.io.File): ShellResult = lock.withLock {
        if (!transport.hasStoredKey()) return@withLock fail(BrokerRules.NOT_PAIRED_MESSAGE)
        val size = apk.length()
        if (size <= 0L) return@withLock fail("The update file is empty.")
        val port = transport.discoverConnectPort(BrokerRules.CONNECT_DISCOVERY_TIMEOUT_MS) ?: return@withLock fail(BrokerRules.NO_SERVICE_MESSAGE)
        try {
            val output = apk.inputStream().buffered().use { input ->
                transport.execWithInput(port, PackageInstallLine.command(size), input, size, PackageInstallLine.READ_TIMEOUT_MS)
            }
            if (PackageInstallLine.succeeded(output)) {
                lastResult = output
                lastError = null
                ShellResult.Ok(output)
            } else {
                fail(output.trim().ifEmpty { "The install gave no answer." })
            }
        } catch (error: Exception) {
            fail(BrokerRules.errorText(error))
        }
    }

    /** [run] for coroutine callers; the lock is taken on [ioDispatcher], never on the caller's thread. */
    suspend fun runLine(line: String): ShellResult = withContext(ioDispatcher) { run(line) }

    /** Runs [block] under the broker lock: the pairing client uses this so a pair never overlaps a discovery. spec: SS6 step 1. */
    fun <T> exclusive(block: () -> T): T = lock.withLock(block)

    /** spec: SS4.5 and SS5.2: forgetting invalidates the verdict and clears the last error and result. */
    fun forgetPairing() {
        transport.forgetKey()
        lastError = null
        lastResult = null
        invalidate()
    }

    /** spec: SS5.2 ("Forgetting a pairing, and the end of any pairing attempt, invalidate the cache"). */
    fun invalidate() {
        cache = cache.copy(invalidated = true)
    }

    /**
     * The verified verdict. Cached when fresh, joined when in flight, otherwise a real
     * connection attempt under the lock (up to about 8 s of discovery plus 5 s of connect plus
     * reads). spec: SS5.1, SS5.2.
     */
    suspend fun verify(force: Boolean = false): BrokerVerdict = withContext(ioDispatcher) { verifyBlocking(force) }

    /** [verify] for a plain thread; never call on the main thread. */
    fun verifyBlocking(force: Boolean = false): BrokerVerdict {
        if (!BrokerRules.needsFreshCheck(cache, clock(), force)) return cache.verdict!!
        val seen = checksLanded
        // A caller arriving while a check holds the lock blocks here until it is done, then
        // takes that check's verdict: "wait for that check rather than starting their own".
        return lock.withLock {
            if (checksLanded > seen) {
                cache.verdict!!
            } else {
                checkingFlow.value = true
                try {
                    record(checkLocked())
                } finally {
                    checkingFlow.value = false
                }
            }
        }
    }

    /** How many real checks have landed; lets a waiter tell "the check I queued behind finished" apart from "nothing happened". */
    @Volatile
    private var checksLanded = 0L

    /** For the tests: how many real connection attempts were made. */
    val checkCount: Long get() = checksLanded

    private fun record(verdict: BrokerVerdict): BrokerVerdict {
        val now = clock()
        checksLanded++
        cache = VerdictCache(verdict, now, invalidated = false)
        diagnostics.recordBrokerVerdict(VerdictRecord(verdict, now))
        verdictFlow.value = verdict
        return verdict
    }

    /** spec: SS5.1, the four ordered checks; T34 to T38. */
    private fun checkLocked(): BrokerVerdict {
        if (!transport.hasStoredKey()) return BrokerVerdict.NOT_PAIRED
        if (!transport.isWirelessDebuggingOn()) return BrokerVerdict.WIRELESS_DEBUGGING_OFF
        val port = transport.discoverConnectPort(BrokerRules.CONNECT_DISCOVERY_TIMEOUT_MS) ?: return BrokerVerdict.NO_SERVICE
        return try {
            lastResult = transport.runShell(port, BrokerRules.VERIFY_LINE)
            lastError = null
            BrokerVerdict.OK
        } catch (error: StoredKeyUnreadableException) {
            // The entry is gone (SS4.4): the app now reads as unpaired and the card offers to pair again.
            lastError = BrokerRules.errorText(error)
            BrokerVerdict.NOT_PAIRED
        } catch (error: Exception) {
            lastError = BrokerRules.errorText(error)
            BrokerVerdict.REJECTED
        }
    }

    private fun runLocked(line: String): ShellResult {
        if (!transport.hasStoredKey()) return fail(BrokerRules.NOT_PAIRED_MESSAGE)
        val port = transport.discoverConnectPort(BrokerRules.CONNECT_DISCOVERY_TIMEOUT_MS) ?: return fail(BrokerRules.NO_SERVICE_MESSAGE)
        return try {
            val output = transport.runShell(port, line)
            lastResult = output
            lastError = null
            ShellResult.Ok(output)
        } catch (error: Exception) {
            fail(BrokerRules.errorText(error))
        }
    }

    private fun fail(message: String): ShellResult.Failed {
        lastError = message
        return ShellResult.Failed(message)
    }
}
