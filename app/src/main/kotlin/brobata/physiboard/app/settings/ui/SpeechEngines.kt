package brobata.physiboard.app.settings.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognitionService

/** One row of the "Speech engine" picker (settings-catalog.md SS9.2, dictation.md SS2.6). [storedValue] is what `DictationPrefs.engine` holds. */
data class SpeechEngineOption(val storedValue: String, val label: String)

/** dictation.md SS2.6: the empty string (system default), `ondevice`, or an installed recognizer's flattened component name. */
object SpeechEngines {
    const val SYSTEM_DEFAULT = ""
    const val ON_DEVICE = "ondevice"

    fun options(context: Context): List<SpeechEngineOption> {
        val installed = context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .mapNotNull { resolveInfo ->
                val service = resolveInfo.serviceInfo ?: return@mapNotNull null
                val component = android.content.ComponentName(service.packageName, service.name).flattenToShortString()
                SpeechEngineOption(component, resolveInfo.loadLabel(context.packageManager).toString())
            }
            .distinctBy { it.storedValue }
            .sortedBy { it.label.lowercase() }
        return listOf(SpeechEngineOption(SYSTEM_DEFAULT, "System default"), SpeechEngineOption(ON_DEVICE, "On-device")) + installed
    }
}
