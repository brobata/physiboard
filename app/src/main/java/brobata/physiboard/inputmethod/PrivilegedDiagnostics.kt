package brobata.physiboard.inputmethod

import android.content.Context
import androidx.core.content.edit
import brobata.physiboard.SettingsManager

/**
 * Remembers why each privileged step last succeeded or failed.
 *
 * Every step behind the ADB broker used to fail in silence: [PrivilegedSetup] and
 * [KeyboardBacklightManager] both returned early when the device was not paired, `runCatching`
 * swallowed anything thrown, and the only progress logging was `Log.i`/`Log.d` — both stripped
 * from release builds. A user whose keyboard backlight stopped working therefore had a toggle
 * that still read "on", no error anywhere, and no way to find out that the real problem was
 * Android turning Wireless debugging off across a reboot.
 *
 * [EmbeddedAdbShell.lastError] exists but is an in-memory field on a process that restarts
 * constantly, and the backlight path never read it. These outcomes are persisted instead, so
 * they survive an IME restart and can be shown in settings and included in a debug export.
 */
object PrivilegedDiagnostics {

    enum class Step(val key: String) {
        BACKLIGHT("backlight"),
        OVERLAY_GRANT("overlay_grant"),
        NOTIFICATION_RING("notification_ring")
    }

    const val REASON_OK = "ok"
    const val REASON_NOT_PAIRED = "not_paired"
    const val REASON_WIRELESS_DEBUGGING_OFF = "wireless_debugging_off"
    const val REASON_SHELL_FAILED = "shell_failed"

    data class Outcome(
        val step: Step,
        val ok: Boolean,
        val reason: String,
        val atMs: Long
    )

    fun record(context: Context, step: Step, ok: Boolean, reason: String) {
        SettingsManager.getPreferences(context.applicationContext).edit {
            putBoolean(okKey(step), ok)
            putString(reasonKey(step), reason)
            putLong(atKey(step), System.currentTimeMillis())
        }
    }

    /** The last recorded outcome for [step], or null if the step has never run. */
    fun last(context: Context, step: Step): Outcome? {
        val prefs = SettingsManager.getPreferences(context.applicationContext)
        val at = prefs.getLong(atKey(step), 0L)
        if (at == 0L) return null
        return Outcome(
            step = step,
            ok = prefs.getBoolean(okKey(step), false),
            reason = prefs.getString(reasonKey(step), REASON_OK) ?: REASON_OK,
            atMs = at
        )
    }

    /**
     * The keyboard-backlight timeout last read back off the device, and when.
     *
     * The "configured once" flag is a one-way latch, so it cannot notice the setting being lost
     * later. This is the observed truth instead — recorded off-thread after a write, because
     * reading it costs a broker round-trip and must never run on the UI thread.
     */
    fun recordObservedBacklightValue(context: Context, value: String?) {
        SettingsManager.getPreferences(context.applicationContext).edit {
            putString(KEY_OBSERVED_BACKLIGHT, value)
            putLong(KEY_OBSERVED_BACKLIGHT_AT, System.currentTimeMillis())
        }
    }

    fun observedBacklightValue(context: Context): Pair<String?, Long>? {
        val prefs = SettingsManager.getPreferences(context.applicationContext)
        val at = prefs.getLong(KEY_OBSERVED_BACKLIGHT_AT, 0L)
        if (at == 0L) return null
        return prefs.getString(KEY_OBSERVED_BACKLIGHT, null) to at
    }

    /**
     * Remember the last [EmbeddedAdbShell.verify] result, so a screen can show the truth
     * immediately and refresh it in the background rather than blocking on an 8s check.
     */
    fun recordBrokerStatus(context: Context, status: EmbeddedAdbShell.BrokerStatus) {
        SettingsManager.getPreferences(context.applicationContext).edit {
            putString(KEY_BROKER_STATUS, status.name)
            putLong(KEY_BROKER_STATUS_AT, System.currentTimeMillis())
        }
    }

    /** The last verified broker status and when it was taken, or null if never checked. */
    fun lastBrokerStatus(context: Context): Pair<EmbeddedAdbShell.BrokerStatus, Long>? {
        val prefs = SettingsManager.getPreferences(context.applicationContext)
        val at = prefs.getLong(KEY_BROKER_STATUS_AT, 0L)
        if (at == 0L) return null
        val name = prefs.getString(KEY_BROKER_STATUS, null) ?: return null
        val status = runCatching { EmbeddedAdbShell.BrokerStatus.valueOf(name) }.getOrNull()
            ?: return null
        return status to at
    }

    private const val KEY_BROKER_STATUS = "privileged_broker_status"
    private const val KEY_BROKER_STATUS_AT = "privileged_broker_status_at"

    private const val KEY_OBSERVED_BACKLIGHT = "privileged_backlight_device_value"
    private const val KEY_OBSERVED_BACKLIGHT_AT = "privileged_backlight_device_value_at"

    fun snapshot(context: Context): List<Outcome> =
        Step.entries.mapNotNull { last(context, it) }

    /**
     * Why the broker cannot run right now, or null when it should work.
     *
     * Checking Wireless debugging matters as much as checking the pairing: [EmbeddedAdbShell.isPaired]
     * only asks whether a key was ever stored, and Android disables Wireless debugging across
     * reboots, so a paired device is routinely unable to connect.
     */
    fun brokerBlocker(context: Context): String? = when {
        !EmbeddedAdbShell.isPaired(context) -> REASON_NOT_PAIRED
        !EmbeddedAdbShell.isWirelessDebuggingEnabled(context) -> REASON_WIRELESS_DEBUGGING_OFF
        else -> null
    }

    private fun okKey(step: Step) = "privileged_${step.key}_ok"
    private fun reasonKey(step: Step) = "privileged_${step.key}_reason"
    private fun atKey(step: Step) = "privileged_${step.key}_at"
}
