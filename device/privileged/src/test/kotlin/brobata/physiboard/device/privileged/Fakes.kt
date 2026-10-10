package brobata.physiboard.device.privileged

import brobata.physiboard.device.privileged.broker.AdbTransport
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import brobata.physiboard.device.privileged.broker.BrokerRules
import brobata.physiboard.device.privileged.broker.PairResult
import brobata.physiboard.device.privileged.broker.PairingDiscovery
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.broker.ShellRunner
import brobata.physiboard.device.privileged.broker.StoredKeyUnreadableException
import brobata.physiboard.device.privileged.ring.DelayedRunner
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/** A clock the tests move by hand. */
class FakeClock(@Volatile var nowMs: Long = 1_000L) : () -> Long {
    override fun invoke(): Long = nowMs
}

/** Runs everything on the caller's thread, so a test sees every effect before its next line. */
object DirectExecutor : Executor {
    override fun execute(command: Runnable) = command.run()
}

/**
 * A scripted phone: which key/switch facts it reports, which port discovery yields, and what
 * each shell line answers. Every discovery and shell call is counted, and overlapping
 * discoveries are detected, since that is the whole reason the broker has a lock (D3).
 */
class FakeAdbTransport(
    @Volatile var hasKey: Boolean = true,
    @Volatile var wirelessDebuggingOn: Boolean = true,
    @Volatile var port: Int? = 40_001,
) : AdbTransport {
    val shellLines = mutableListOf<String>()
    val responses = mutableMapOf<String, String>()

    /** A line in this set throws instead of answering (a refused key, a dropped socket). */
    val failing = mutableSetOf<String>()

    @Volatile
    var keyUnreadable = false

    @Volatile
    var pairResult: PairResult = PairResult.Accepted

    @Volatile
    var discoveryDelayMs: Long = 0

    val discoveries = AtomicInteger(0)
    val overlappingDiscoveries = AtomicInteger(0)
    private val inDiscovery = AtomicInteger(0)
    val pairCalls = mutableListOf<Pair<Int, String>>()
    var pairingListener: ((Int) -> Unit)? = null
    var pairingDiscoveryStops = 0

    override fun hasStoredKey(): Boolean = hasKey
    override fun isWirelessDebuggingOn(): Boolean = wirelessDebuggingOn

    override fun discoverConnectPort(timeoutMs: Long): Int? {
        discoveries.incrementAndGet()
        if (inDiscovery.incrementAndGet() > 1) overlappingDiscoveries.incrementAndGet()
        try {
            if (discoveryDelayMs > 0) Thread.sleep(discoveryDelayMs)
            return port
        } finally {
            inDiscovery.decrementAndGet()
        }
    }

    override fun startPairingDiscovery(onPort: (Int) -> Unit): PairingDiscovery {
        pairingListener = onPort
        return PairingDiscovery { pairingDiscoveryStops++ }
    }

    override fun runShell(port: Int, line: String): String {
        if (keyUnreadable) {
            hasKey = false
            throw StoredKeyUnreadableException("Stored ADB key could not be read; pair again", null)
        }
        shellLines += line
        if (line in failing) throw IllegalStateException("not A_CNXN")
        return responses[line] ?: ""
    }

    /** Every streamed command, with the bytes it was given. */
    val execs = mutableListOf<Pair<String, ByteArray>>()

    override fun execWithInput(port: Int, command: String, input: java.io.InputStream, size: Long, readTimeoutMs: Int): String {
        val bytes = ByteArray(size.toInt())
        var read = 0
        while (read < bytes.size) {
            val n = input.read(bytes, read, bytes.size - read)
            if (n < 0) break
            read += n
        }
        execs += command to bytes.copyOf(read)
        if (command in failing) throw IllegalStateException("not A_CNXN")
        return responses[command] ?: ""
    }

    override fun pair(port: Int, code: String): PairResult {
        pairCalls += port to code
        // A key is minted the moment a pairing is attempted (SS4.3).
        hasKey = true
        return pairResult
    }

    override fun forgetKey() {
        hasKey = false
    }
}

/** A [ShellRunner] answering from a script, recording every line it was asked to run. */
class FakeShell(@Volatile var blocker: BrokerBlocker? = null) : ShellRunner {
    val lines = mutableListOf<String>()
    val responses = mutableMapOf<String, ShellResult>()

    /** Called after each line, so a test can flip a permission "because the grant landed". */
    var onLine: (String) -> Unit = {}

    override fun blocker(): BrokerBlocker? = blocker

    override fun run(line: String): ShellResult {
        lines += line
        val result = responses[line] ?: ShellResult.Ok("")
        lastError = (result as? ShellResult.Failed)?.message
        onLine(line)
        return result
    }

    @Volatile
    override var lastError: String? = null

    fun failWith(line: String, message: String = "IllegalStateException: not A_CNXN") {
        responses[line] = ShellResult.Failed(message)
    }

    companion object {
        fun notPaired(): FakeShell = FakeShell(BrokerBlocker.NOT_PAIRED)
        fun debuggingOff(): FakeShell = FakeShell(BrokerBlocker.WIRELESS_DEBUGGING_OFF)
    }
}

/** A timer the test fires by hand. */
class FakeTimer : DelayedRunner {
    private var pending: (() -> Unit)? = null
    var scheduledDelayMs: Long? = null
    var cancels = 0

    override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
        scheduledDelayMs = delayMs
        pending = action
        return {
            cancels++
            if (pending === action) pending = null
        }
    }

    val isArmed: Boolean get() = pending != null

    fun fire() {
        val action = pending ?: return
        pending = null
        action()
    }
}

/** The broker document's exact message strings, so a test asserting them cannot drift from the rules. */
val NOT_PAIRED_MESSAGE: String = BrokerRules.NOT_PAIRED_MESSAGE
val NO_SERVICE_MESSAGE: String = BrokerRules.NO_SERVICE_MESSAGE
