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
         * Writes the vendor timeout setting off-thread. On a successful write, records the
         * persisted-readiness flag ([SettingsManager.setSmartBacklightApplied]) — `true` for the
         * always-on value, `false` for the revert — so the UI can key readiness off "configured
         * once" rather than live Wireless-debugging state.
         */
        private fun runSet(context: Context, value: String, applied: Boolean) {
            // Cheap gate: without a paired broker the write can't possibly land, so don't
            // queue an ~8s mDNS discovery. The setting is applied once pairing completes.
            if (!EmbeddedAdbShell.isPaired(context)) return
            val appContext = context.applicationContext
            executor.execute {
                runCatching {
                    val ok = EmbeddedAdbShell.runShell(
                        appContext,
                        "service call $VENDOR_SERVICE $TXN_SET s16 \"$KEY_TIMEOUT\" s16 \"$value\""
                    )
                    if (ok) SettingsManager.setSmartBacklightApplied(appContext, applied)
                }
            }
        }
    }
}
