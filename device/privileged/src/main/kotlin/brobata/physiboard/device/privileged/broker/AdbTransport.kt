package brobata.physiboard.device.privileged.broker

/** What one shell line came back with. spec: broker-privileged-toolbox.md SS6 ("The call never throws"). */
sealed class ShellResult {
    data class Ok(val output: String) : ShellResult()
    data class Failed(val message: String) : ShellResult()

    val isOk: Boolean get() = this is Ok
    val outputOrNull: String? get() = (this as? Ok)?.output
}

/** How a pairing attempt ended. The four messages are the spec's own (SS4.1 step 5). */
sealed class PairResult {
    object Accepted : PairResult()
    data class Failed(val message: String) : PairResult()

    companion object {
        const val PORT_REFUSED = "Cannot connect to the pairing port."
        const val WRONG_CODE = "The pairing code is wrong."
        const val KEY_STORE = "Failed to access the ADB key store."
    }
}

/** A running discovery of the pairing service; [stop] ends it. spec: SS2 (discover a service). */
fun interface PairingDiscovery {
    fun stop()
}

/**
 * The stored private key cannot be decrypted (Keystore reset, corrupt entry). The transport has
 * already removed the entry when it throws this, so the app reads as unpaired afterwards.
 * spec: SS4.4.
 */
class StoredKeyUnreadableException(message: String, cause: Throwable?) : Exception(message, cause)

/**
 * The vendored ADB client as this module asks for it: the four operations of spec SS2 plus the
 * two cheap facts the blocker check needs. The Android implementation wraps `:broker`; the JVM
 * tests substitute a fake shell so the broker's own state and locking are pinned without a
 * socket.
 *
 * spec: broker-privileged-toolbox.md SS2 (operations and failure modes), SS4.2 (key storage),
 * SS4.4 (unreadable key), SS7 (`adb_wifi_enabled`).
 */
interface AdbTransport {
    /** The `adbkey` entry exists in the `embedded_adb` preferences. spec: SS4.3, the whole of "paired". */
    fun hasStoredKey(): Boolean

    /** `adb_wifi_enabled` in `global` is 1. spec: SS5.1 step 2. */
    fun isWirelessDebuggingOn(): Boolean

    /** The `_adb-tls-connect._tcp` port, or null when nothing was delivered within [timeoutMs]. spec: SS2 row 1. */
    fun discoverConnectPort(timeoutMs: Long): Int?

    /** Starts discovering `_adb-tls-pairing._tcp`; [onPort] fires once per resolved service. spec: SS4.1 step 4. */
    fun startPairingDiscovery(onPort: (Int) -> Unit): PairingDiscovery

    /**
     * Loads the key, connects to 127.0.0.1:[port], runs [line] and returns everything the shell
     * wrote. Throws [StoredKeyUnreadableException] (entry already removed) or any transport
     * error. spec: SS2 row 3, SS6 step 4.
     */
    fun runShell(port: Int, line: String): String

    /**
     * Pairs on 127.0.0.1:[port] with [code], minting a key when none is stored and re-minting
     * one that cannot be read (SS4.4, "the unreadable entry is removed and a fresh key is
     * minted"). Never throws: every failure is a [PairResult.Failed] with the spec's text.
     */
    fun pair(port: Int, code: String): PairResult

    /** Removes the `adbkey` entry. spec: SS4.5. */
    fun forgetKey()
}
