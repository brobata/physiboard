package brobata.physiboard.inputmethod

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.Observer
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.EmbeddedAdbInit
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Runs a single shell command over the app's OWN embedded wireless-ADB broker
 * (the vendored classes under `moe.shizuku.manager.adb`), with NO dependency on the
 * separate Shizuku app.
 *
 * Sequence (lifted verbatim from EmbeddedAdbTestActivity / AutoReArmService, proven
 * on-device):
 *   1. mDNS-discover the local `_adb-tls-connect._tcp` port on 127.0.0.1
 *   2. AdbClient("127.0.0.1", port, storedKey).connect()
 *   3. shellCommand(command)
 *
 * [runShell] is BLOCKING (network IO, up to ~8s of mDNS discovery) — callers MUST
 * invoke it OFF the main thread. It never throws: every failure path is swallowed,
 * returns false, and is surfaced via [lastError] for troubleshooting UIs.
 */
object EmbeddedAdbShell {

    private const val TAG = "EmbeddedAdbShell"
    private const val MDNS_POLL_SECONDS = 8L
    // Pref key that PreferenceAdbKeyStore writes the private key under once paired.
    private const val STORED_KEY_PREF = "adbkey"

    /** Last connect/shell error, for a troubleshooting surface. null after a clean run. */
    @Volatile
    var lastError: String? = null
        private set

    /** Raw output of the last successful shell command, for a troubleshooting surface. */
    @Volatile
    var lastResult: String? = null
        private set

    /** True once the broker has a stored ADB key (i.e. the device has been paired). */
    fun isPaired(context: Context): Boolean =
        EmbeddedAdbInit.prefs(context).contains(STORED_KEY_PREF)

    /** Cheap best-effort check that Wireless debugging is on (the `adb_wifi_enabled` global). */
    fun isWirelessDebuggingEnabled(context: Context): Boolean =
        runCatching {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
        }.getOrDefault(false)

    /**
     * Discover the local `_adb-tls-connect._tcp` port. Returns null if nothing is
     * advertised within [MDNS_POLL_SECONDS]. BLOCKING; call off the main thread.
     */
    fun discoverPort(context: Context): Int? {
        EmbeddedAdbInit.ensure()
        val portQueue = ArrayBlockingQueue<Int>(1)
        val observer = Observer<Int> { p -> if (p > 0) portQueue.offer(p) }
        val mdns = AdbMdns(context, AdbMdns.TLS_CONNECT, observer)
        mdns.start()
        return try {
            portQueue.poll(MDNS_POLL_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            null
        } finally {
            runCatching { mdns.stop() }
        }
    }

    /**
     * Discover -> connect -> run [command] over the embedded broker. Returns true on a
     * clean run. BLOCKING; call off the main thread. Fails GRACEFULLY (returns false,
     * sets [lastError]) when the device is not paired, wireless debugging is off, or
     * nothing is discovered — the caller (backlight) simply does not arm.
     */
    fun runShell(context: Context, command: String): Boolean = synchronized(brokerLock) {
        runShellLocked(context, command)
    }

    /**
     * Serializes discovery + shell across callers: overlapping NsdManager discoveries for the
     * same service type fail silently, so two privileged steps started together (backlight
     * and overlay grant at IME start) would both see "no service found".
     */
    private val brokerLock = Any()

    private fun runShellLocked(context: Context, command: String): Boolean {
        EmbeddedAdbInit.ensure()
        if (!isPaired(context)) {
            lastError = "Not paired yet — set up wireless debugging first."
            return false
        }
        val port = discoverPort(context)
        if (port == null || port <= 0) {
            lastError = "No adb-tls-connect service found. Is wireless debugging on?"
            Log.w(TAG, "embedded adb shell: $lastError")
            return false
        }
        return try {
            val key = AdbKey(PreferenceAdbKeyStore(EmbeddedAdbInit.prefs(context)), EmbeddedAdbInit.KEY_NAME)
            AdbClient("127.0.0.1", port, key).use { client ->
                client.connect()
                val sb = StringBuilder()
                client.shellCommand(command) { bytes -> sb.append(String(bytes)) }
                lastResult = sb.toString()
            }
            lastError = null
            true
        } catch (t: Throwable) {
            Log.w(TAG, "embedded adb shell failed", t)
            lastError = "${t.javaClass.simpleName}: ${t.message}"
            false
        }
    }
}
