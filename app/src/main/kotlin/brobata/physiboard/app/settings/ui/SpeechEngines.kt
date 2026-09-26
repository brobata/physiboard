package brobata.physiboard.app.settings.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import brobata.physiboard.core.speech.SpeechEngineCatalog

/**
 * One row of the "Speech engine" picker (settings-catalog.md SS9.2, dictation.md SS4.1).
 * [storedValue] is what `DictationPrefs.engine` holds; [isCurrentSystemDefault] drives the
 * accent-coloured "Currently the system default" tag.
 */
data class SpeechEngineOption(
    val storedValue: String,
    val label: String,
    val detail: String,
    val isCurrentSystemDefault: Boolean = false,
)

/** dictation.md SS4.1: the empty string (system default), `ondevice`, or an installed recognizer's flattened component name. */
object SpeechEngines {
    const val SYSTEM_DEFAULT = SpeechEngineCatalog.SYSTEM_DEFAULT
    const val ON_DEVICE = SpeechEngineCatalog.ON_DEVICE

    /**
     * spec SS4.1: system default first, then the on-device engine (only on Android 12+ and only
     * when the platform actually reports one), then every installed `RecognitionService` in the
     * order the package manager returns them (not re-sorted).
     */
    fun options(context: Context): List<SpeechEngineOption> {
        val systemDefaultPackage = systemDefaultPackage(context)
        val systemDefaultLabel = systemDefaultPackage?.let { SpeechEngineCatalog.friendlyName(it, appLabel(context, it)) }
        val result = mutableListOf(
            SpeechEngineOption(
                storedValue = SYSTEM_DEFAULT,
                label = "System default",
                detail = SpeechEngineCatalog.systemDefaultDetail(systemDefaultLabel),
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)) {
            result += SpeechEngineOption(ON_DEVICE, "Android offline engine", SpeechEngineCatalog.ON_DEVICE_DETAIL)
        }
        context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .forEach { resolveInfo ->
                val service = resolveInfo.serviceInfo ?: return@forEach
                val component = ComponentName(service.packageName, service.name).flattenToShortString()
                result += SpeechEngineOption(
                    storedValue = component,
                    label = SpeechEngineCatalog.friendlyName(service.packageName, resolveInfo.loadLabel(context.packageManager)?.toString()),
                    detail = SpeechEngineCatalog.detailFor(service.packageName),
                    isCurrentSystemDefault = service.packageName == systemDefaultPackage,
                )
            }
        return result
    }

    /** spec SS4.1 row 1: the package named by `Settings.Secure` `voice_recognition_service`, or null when empty or unreadable. */
    private fun systemDefaultPackage(context: Context): String? {
        val raw = runCatching { Settings.Secure.getString(context.contentResolver, "voice_recognition_service") }.getOrNull()
        if (raw.isNullOrBlank()) return null
        return ComponentName.unflattenFromString(raw)?.packageName ?: raw
    }

    private fun appLabel(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}

/**
 * spec SS4.1: "The picker dialog opens with the [intro] text ... Choosing a row saves it and
 * closes the dialog; the only button is Cancel." Each row shows the label, the system-default tag
 * when it applies, and the per-engine detail text.
 */
@Composable
fun SpeechEnginePickerDialog(
    options: List<SpeechEngineOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speech engine") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(SpeechEngineCatalog.INTRO, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.storedValue) }
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = option.storedValue == selected, onClick = { onSelect(option.storedValue) })
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Row {
                                Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                if (option.isCurrentSystemDefault) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        SpeechEngineCatalog.SYSTEM_DEFAULT_TAG,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            Text(option.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
