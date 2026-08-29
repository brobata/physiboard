package brobata.physiboard

import android.content.Context
import android.util.Log
import brobata.physiboard.inputmethod.AutoCorrector
import java.util.LinkedHashMap
import java.util.Locale

private const val TAG = "AutoCorrectionSubstitutionStore"

object AutoCorrectionSubstitutionStore {
    fun addCustomSubstitution(
        context: Context,
        languageCode: String,
        trigger: String,
        replacement: String
    ): Boolean {
        val normalizedTrigger = trigger.trim().lowercase(Locale.ROOT)
        val normalizedReplacement = replacement.trim()
        if (normalizedTrigger.isBlank() || normalizedReplacement.isBlank()) return false
        if (normalizedTrigger == "__name") return false

        val existing = SettingsManager.getCustomAutoCorrections(context, languageCode)
        val updated = LinkedHashMap<String, String>()
        updated[normalizedTrigger] = normalizedReplacement
        existing.forEach { (key, value) ->
            if (key != normalizedTrigger) {
                updated[key] = value
            }
        }
        val saved = SettingsManager.saveCustomAutoCorrections(context, languageCode, updated)
        enableSubstitutionLanguage(context, languageCode)
        val reloaded = reloadAutoCorrector(context)
        return saved && reloaded
    }

    private fun enableSubstitutionLanguage(context: Context, languageCode: String) {
        val normalizedLanguage = languageCode.trim().lowercase(Locale.ROOT)
        if (normalizedLanguage.isBlank()) return

        val enabled = SettingsManager.getAutoCorrectEnabledLanguages(context)
        if (enabled.contains(normalizedLanguage)) return

        SettingsManager.setAutoCorrectEnabledLanguages(
            context,
            enabled + normalizedLanguage
        )
    }

    private fun reloadAutoCorrector(context: Context): Boolean {
        return try {
            AutoCorrector.loadCorrections(context.assets, context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Substitution saved but the corrector did not reload", e)
            false
        }
    }
}
