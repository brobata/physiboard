package it.palsoftware.pastiera

import android.content.Context
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import java.util.LinkedHashMap
import java.util.Locale

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
        SettingsManager.saveCustomAutoCorrections(context, languageCode, updated)
        enableSubstitutionLanguage(context, languageCode)
        reloadAutoCorrector(context)
        return true
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

    private fun reloadAutoCorrector(context: Context) {
        try {
            AutoCorrector.loadCorrections(context.assets, context)
        } catch (_: Exception) {
            // The persisted substitution will still be picked up on the next full reload.
        }
    }
}
