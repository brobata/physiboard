package it.palsoftware.pastiera

import android.content.SharedPreferences

/** Stores bounded, raw calibration samples so the robust median survives process restarts. */
class SharedPreferencesClicksPowerSocCalibrationStore(
    private val preferences: SharedPreferences
) : ClicksPowerSocCalibrationStore {
    override fun load(keyboardId: String): ClicksPowerSocCalibrationAggregate? {
        val encoded = preferences.getString(storageKey(keyboardId), null) ?: return null
        val samples = encoded.split(',').mapNotNull { token ->
            val parts = token.split(':')
            if (parts.size != 2) return@mapNotNull null
            val phoneGain = parts[0].toIntOrNull() ?: return@mapNotNull null
            val keyboardSpent = parts[1].toIntOrNull() ?: return@mapNotNull null
            ClicksPowerSocCalibrationSample(phoneGain, keyboardSpent).takeIf { it.isValid() }
        }
        return ClicksPowerSocCalibrationAggregate(samples).sanitized()
    }

    override fun save(keyboardId: String, aggregate: ClicksPowerSocCalibrationAggregate) {
        val encoded = aggregate.sanitized().samples.joinToString(",") {
            "${it.phoneGainPercent}:${it.keyboardSpentPercent}"
        }
        preferences.edit().putString(storageKey(keyboardId), encoded).apply()
    }

    private fun storageKey(keyboardId: String): String = "$PREFERENCE_KEY_PREFIX$keyboardId"

    private companion object {
        const val PREFERENCE_KEY_PREFIX = "clicks_power_soc_calibration_"
    }
}
