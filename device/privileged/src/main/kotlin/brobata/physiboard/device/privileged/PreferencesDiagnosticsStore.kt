package brobata.physiboard.device.privileged

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import brobata.physiboard.device.privileged.broker.BrokerVerdict

/**
 * [DiagnosticsStore] in its own preferences file, written synchronously so an outcome survives
 * the IME process restarting a moment later. The row names are the spec's
 * (`privileged_<step>_ok` / `_reason` / `_at`, `privileged_backlight_device_value` / `_at`,
 * `privileged_broker_status` / `_at`), kept so the debug export reads the same as 2.x's.
 *
 * spec: broker-privileged-toolbox.md SS8, SS20. The spec keeps these in the main preferences;
 * 3.0's main store is a typed schema with no room for free-form diagnostics rows, so they live
 * beside it in `privileged_diagnostics.xml`.
 */
class PreferencesDiagnosticsStore(private val prefs: SharedPreferences) : DiagnosticsStore {

    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    override fun recordStep(step: PrivilegedStep, outcome: StepOutcome) {
        prefs.edit(commit = true) {
            putBoolean("privileged_${step.key}_ok", outcome.ok)
            putString("privileged_${step.key}_reason", outcome.reason)
            putLong("privileged_${step.key}_at", outcome.atMs)
        }
    }

    override fun step(step: PrivilegedStep): StepOutcome? {
        val at = prefs.getLong("privileged_${step.key}_at", 0L)
        if (at == 0L) return null
        return StepOutcome(
            ok = prefs.getBoolean("privileged_${step.key}_ok", false),
            reason = prefs.getString("privileged_${step.key}_reason", null) ?: "",
            atMs = at,
        )
    }

    override fun recordBacklightDeviceValue(record: DeviceValueRecord) {
        prefs.edit(commit = true) {
            if (record.value == null) remove(DEVICE_VALUE) else putString(DEVICE_VALUE, record.value)
            putLong(DEVICE_VALUE_AT, record.atMs)
        }
    }

    override fun backlightDeviceValue(): DeviceValueRecord? {
        val at = prefs.getLong(DEVICE_VALUE_AT, 0L)
        if (at == 0L) return null
        return DeviceValueRecord(prefs.getString(DEVICE_VALUE, null), at)
    }

    override fun recordBrokerVerdict(record: VerdictRecord) {
        prefs.edit(commit = true) {
            putString(BROKER_STATUS, record.verdict.name)
            putLong(BROKER_STATUS_AT, record.atMs)
        }
    }

    override fun brokerVerdict(): VerdictRecord? {
        val name = prefs.getString(BROKER_STATUS, null) ?: return null
        val verdict = BrokerVerdict.entries.firstOrNull { it.name == name } ?: return null
        return VerdictRecord(verdict, prefs.getLong(BROKER_STATUS_AT, 0L))
    }

    companion object {
        const val FILE_NAME = "privileged_diagnostics"
        private const val DEVICE_VALUE = "privileged_backlight_device_value"
        private const val DEVICE_VALUE_AT = "privileged_backlight_device_value_at"
        private const val BROKER_STATUS = "privileged_broker_status"
        private const val BROKER_STATUS_AT = "privileged_broker_status_at"
    }
}
