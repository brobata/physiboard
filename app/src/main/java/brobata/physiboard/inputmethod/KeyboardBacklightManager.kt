package brobata.physiboard.inputmethod

import android.content.Context
import brobata.physiboard.SettingsManager
import java.util.concurrent.Executors

/**
 * Keeps the Unihertz keyboard backlight on beyond the stock 30s cap using a PERSISTENT
 * vendor setting instead of a fragile per-session LED hold.
 *
 * The vendor stores a keyboard-backlight timeout that accepts a sentinel `-1` = "never turn
 * off". It is written through the system binder service (`agui_functional_service`,
 * transaction 2 = SET(key, value)):
 *   - ALWAYS ON: keyboard_brightness_timeout = "-1"
 *   - REVERT:    keyboard_brightness_timeout = "30000" (the stock 30s default)
 *
 * The value is a STORED setting, so it takes effect instantly and SURVIVES REBOOTS — no
 * light sensor, no screen receivers, no per-keystroke re-latch, and no per-reboot re-arm.
 * We only need shell privilege to WRITE it, which we do exactly once through the app's OWN
 * embedded wireless-ADB broker ([EmbeddedAdbShell]) — no separate Shizuku app required.
 *
 * Every broker call runs off the IME's main thread and is fully wrapped, so a broker that
 * cannot connect is a silent no-op (the user can also change the value from the vendor menu).
 */
class KeyboardBacklightManager(private val context: Context) {

    /** Apply the persistent "always on" setting if the feature is enabled. */
    fun start() {
        // Runs every privileged step (backlight if enabled, screen-trackpad overlay grant),
        // so a device paired once stays fully set up across reboots and IME restarts.
        PrivilegedSetup.applyAll(context, reason = "ime_start")
    }

    /**
     * No teardown needed: the vendor setting persists on its own. Disabling the feature is an
     * explicit user action handled via [revertToDefault]; IME shutdown must NOT revert.
     */
    fun stop() {
        // Intentionally empty — the persistent setting outlives the IME process.
    }

    companion object {
        // Serializes the blocking embedded-adb shell calls off any caller's main thread.
        private val executor = Executors.newSingleThreadExecutor()

        private const val VENDOR_SERVICE = "agui_functional_service"
        // transaction 2 = SET(String key, String value) on agui_functional_service.
        private const val TXN_SET = "2"
        // transaction 1 = GET(String key). Confirmed on a Titan 2 Elite, 2026-08-31: returns a
        // String16 parcel holding the current value. This is what lets the UI report what the
        // device actually has rather than trusting our own "configured once" flag, which is a
        // one-way latch and so cannot notice the setting being lost later.
        private const val TXN_GET = "1"
        private const val KEY_TIMEOUT = "keyboard_brightness_timeout"
        // Sentinel: -1 = never turn off; 30000 = stock 30s default. Verified on-device
        // (Titan 2 Elite), 2026-08-21. Survives reboots.
        private const val VALUE_ALWAYS_ON = "-1"
        private const val VALUE_DEFAULT = "30000"

        /** Persist "keyboard backlight never turns off". Best-effort, off-thread, never throws. */
        fun applyAlwaysOn(context: Context) = runSet(context, VALUE_ALWAYS_ON, applied = true)

        /** Restore the stock 30s timeout. Best-effort, off-thread, never throws. */
        fun revertToDefault(context: Context) = runSet(context, VALUE_DEFAULT, applied = false)

        /**
         * Reads the timeout the device actually has, or null if it could not be read.
         * BLOCKING on a broker round-trip; call off the main thread.
         */
        fun readAppliedValue(context: Context): String? {
            val appContext = context.applicationContext
            if (PrivilegedDiagnostics.brokerBlocker(appContext) != null) return null
            val ok = runCatching {
                EmbeddedAdbShell.runShell(
                    appContext,
                    "service call $VENDOR_SERVICE $TXN_GET s16 \"$KEY_TIMEOUT\""
                )
            }.getOrDefault(false)
            if (!ok) return null
            return parseParcelString(EmbeddedAdbShell.lastResult)
        }

        /** True when the device is confirmed to hold the always-on sentinel. */
        fun isAlwaysOnOnDevice(context: Context): Boolean =
            readAppliedValue(context) == VALUE_ALWAYS_ON

        /**
         * Pulls the String16 out of a `service call` result parcel.
         *
         * The output looks like `Result: Parcel(00000000 00000002 0031002d ... '....-.1....')`:
         * word 0 is the exception code, word 1 the character count, and the rest pack two
         * little-endian UTF-16 units per word. Parsing the hex is exact; scraping the quoted
         * ASCII rendering beside it would lose any character it prints as a dot.
         */
        internal fun parseParcelString(raw: String?): String? {
            val parcel = raw?.substringAfter("Parcel(", "")?.takeIf { it.isNotEmpty() } ?: return null
            val words = Regex("[0-9a-fA-F]{8}")
                .findAll(parcel.substringBefore("'"))
                .map { it.value.toLong(16) }
                .toList()
            if (words.size < 2) return null
            val length = words[1].toInt()
            if (length <= 0 || length > 64) return null

            val out = StringBuilder(length)
            var index = 2
            while (out.length < length && index < words.size) {
                val word = words[index]
                out.append((word and 0xFFFF).toInt().toChar())
                if (out.length < length) {
                    out.append(((word shr 16) and 0xFFFF).toInt().toChar())
                }
                index++
            }
            return out.toString().takeIf { it.length == length }
        }

        /**
         * Writes the vendor timeout setting off-thread. On a successful write, records the
         * persisted-readiness flag ([SettingsManager.setSmartBacklightApplied]) — `true` for the
         * always-on value, `false` for the revert — so the UI can key readiness off "configured
         * once" rather than live Wireless-debugging state.
         */
        private fun runSet(context: Context, value: String, applied: Boolean) {
            val appContext = context.applicationContext
            // Cheap gate: without a working broker the write can't possibly land, so don't
            // queue an ~8s mDNS discovery. Checking Wireless debugging as well as the pairing
            // matters — Android turns it off across reboots, and isPaired() only asks whether a
            // key was ever stored, so a paired device routinely cannot connect. Recording the
            // reason is the difference between "backlight stopped and I can't get it back" and
            // a screen that says which switch to flip.
            PrivilegedDiagnostics.brokerBlocker(appContext)?.let { blocker ->
                PrivilegedDiagnostics.record(
                    appContext, PrivilegedDiagnostics.Step.BACKLIGHT, ok = false, reason = blocker
                )
                return
            }
            executor.execute {
                val outcome = runCatching {
                    EmbeddedAdbShell.runShell(
                        appContext,
                        "service call $VENDOR_SERVICE $TXN_SET s16 \"$KEY_TIMEOUT\" s16 \"$value\""
                    )
                }
                outcome.onSuccess { ok ->
                    if (ok) SettingsManager.setSmartBacklightApplied(appContext, applied)
                    // Already off the main thread and already holding the broker, so confirm
                    // what actually landed rather than assuming the write took.
                    if (ok) {
                        PrivilegedDiagnostics.recordObservedBacklightValue(
                            appContext,
                            runCatching { readAppliedValue(appContext) }.getOrNull()
                        )
                    }
                    PrivilegedDiagnostics.record(
                        appContext,
                        PrivilegedDiagnostics.Step.BACKLIGHT,
                        ok = ok,
                        reason = if (ok) {
                            PrivilegedDiagnostics.REASON_OK
                        } else {
                            EmbeddedAdbShell.lastError ?: PrivilegedDiagnostics.REASON_SHELL_FAILED
                        }
                    )
                }.onFailure { t ->
                    PrivilegedDiagnostics.record(
                        appContext,
                        PrivilegedDiagnostics.Step.BACKLIGHT,
                        ok = false,
                        reason = "${t.javaClass.simpleName}: ${t.message}"
                    )
                }
            }
        }
    }
}
