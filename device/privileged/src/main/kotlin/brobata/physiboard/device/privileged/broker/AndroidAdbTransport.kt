package brobata.physiboard.device.privileged.broker

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.Observer
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbInvalidPairingCodeException
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbPairingClient
import moe.shizuku.manager.adb.EmbeddedAdbInit
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [AdbTransport] over the vendored Shizuku client, unchanged (broker/NOTICE). The key lives in
 * the `embedded_adb` preferences under `adbkey`, encrypted exactly as 2.x stored it, under the
 * public-key name `physiboard`, so an upgrade keeps the pairing (spec SS4.2, SS23 "keep,
 * byte-for-byte"); both of those are the vendored glue's own constants.
 *
 * Everything here needs the phone: mDNS discovery answers only when Wireless debugging is on
 * and advertising, and the pairing client's native SPAKE2 library loads only on the device.
 *
 * spec: broker-privileged-toolbox.md SS2, SS4.2, SS4.4, SS5.1, SS6.
 */
class AndroidAdbTransport(private val context: Context) : AdbTransport {

    private val prefs: SharedPreferences get() = EmbeddedAdbInit.prefs(context)

    override fun hasStoredKey(): Boolean = prefs.contains(KEY_ENTRY)

    override fun isWirelessDebuggingOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1

    override fun discoverConnectPort(timeoutMs: Long): Int? {
        val latch = CountDownLatch(1)
        val found = AtomicInteger(-1)
        val mdns = AdbMdns(context, AdbMdns.TLS_CONNECT, Observer { port -> if (port > 0 && found.compareAndSet(-1, port)) latch.countDown() })
        mdns.start()
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            mdns.stop()
        }
        return found.get().takeIf { it > 0 }
    }

    override fun startPairingDiscovery(onPort: (Int) -> Unit): PairingDiscovery {
        val mdns = AdbMdns(context, AdbMdns.TLS_PAIRING, Observer { port -> if (port > 0) onPort(port) })
        mdns.start()
        return PairingDiscovery { mdns.stop() }
    }

    override fun runShell(port: Int, line: String): String {
        val key = loadStoredKey()
        AdbClient(LOOPBACK, port, key).use { client ->
            client.connect()
            val output = ByteArrayOutputStream()
            client.shellCommand(line) { bytes -> output.write(bytes) }
            return String(output.toByteArray(), Charsets.UTF_8)
        }
    }

    override fun execWithInput(port: Int, command: String, input: java.io.InputStream, size: Long, readTimeoutMs: Int, shouldContinue: () -> Boolean): String {
        val key = loadStoredKey()
        AdbClient(LOOPBACK, port, key).use { client ->
            client.connect()
            val output = ByteArrayOutputStream()
            client.execWithInput("exec:$command", input, size, readTimeoutMs, shouldContinue) { bytes -> output.write(bytes) }
            return String(output.toByteArray(), Charsets.UTF_8)
        }
    }

    override fun pair(port: Int, code: String): PairResult {
        val key = try {
            loadStoredKey()
        } catch (_: StoredKeyUnreadableException) {
            // spec SS4.4: during a pairing attempt the unreadable entry is removed (done) and a
            // fresh key minted, because the phone is about to be told the new public key anyway.
            try {
                newKey()
            } catch (error: Exception) {
                Log.e(TAG, "minting a fresh key failed", error)
                return PairResult.Failed(PairResult.KEY_STORE)
            }
        } catch (error: Exception) {
            Log.e(TAG, "key store unavailable", error)
            return PairResult.Failed(PairResult.KEY_STORE)
        }
        return try {
            val accepted = AdbPairingClient(LOOPBACK, port, code, key).use { it.start() }
            if (accepted) PairResult.Accepted else PairResult.Failed(NOT_ACCEPTED)
        } catch (_: ConnectException) {
            PairResult.Failed(PairResult.PORT_REFUSED)
        } catch (_: AdbInvalidPairingCodeException) {
            PairResult.Failed(PairResult.WRONG_CODE)
        } catch (_: AdbKeyException) {
            PairResult.Failed(PairResult.KEY_STORE)
        } catch (error: Exception) {
            Log.e(TAG, "pairing failed", error)
            PairResult.Failed(error.stackTraceToString())
        }
    }

    override fun forgetKey() = PreferenceAdbKeyStore(prefs).clear()

    /** Loads (or, when absent, mints) the key; an unreadable stored entry is removed and reported, never silently replaced (SS4.4). */
    private fun loadStoredKey(): AdbKey = try {
        newKey()
    } catch (error: AdbKeyException) {
        forgetKey()
        throw StoredKeyUnreadableException("Stored ADB key could not be read; pair again", error)
    }

    private fun newKey(): AdbKey = AdbKey(PreferenceAdbKeyStore(prefs), EmbeddedAdbInit.KEY_NAME)

    private companion object {
        const val TAG = "PrivilegedAdb"
        const val LOOPBACK = "127.0.0.1"
        const val KEY_ENTRY = "adbkey"
        const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

        /**
         * SPEC GAP: the pairing client can return false without throwing (a malformed exchange);
         * the spec lists only exception-shaped failures, so this gets a message of its own.
         */
        const val NOT_ACCEPTED = "The phone did not accept the pairing. Try again with a fresh code."
    }
}
