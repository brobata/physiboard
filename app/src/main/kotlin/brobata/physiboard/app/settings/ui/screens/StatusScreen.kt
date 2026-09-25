package brobata.physiboard.app.settings.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.shell.ImeComponent
import brobata.physiboard.app.shell.ImeProbeAndroid
import java.util.Locale

/**
 * The Status screen (app-shell.md SS8.1): five read-only rows answering "why isn't PhysiBoard
 * typing", re-read every time the screen returns to the foreground since Android's own settings
 * can change any of them out from under it.
 */
@Composable
fun StatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current
    var probe by remember { mutableStateOf(ImeProbeAndroid.evaluate(context, ImeComponent.SERVICE_CLASS_NAME)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) probe = ImeProbeAndroid.evaluate(context, ImeComponent.SERVICE_CLASS_NAME)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val language = remember(probe) { currentInputLanguageDisplayName(context) }
    val smartBacklight = controller.current.value.device.smartBacklightEnabled

    SettingsScreenScaffold(title = "Status", onBack = onBack) {
        RowList {
            item {
                StatusRow(
                    label = "PhysiBoard enabled",
                    description = "Enabled as an input method",
                    value = if (probe.enabled) "Yes" else "No",
                    isGood = probe.enabled,
                    onClick = if (!probe.enabled) {
                        { context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    } else null,
                )
            }
            item { HorizontalDivider() }
            item {
                StatusRow(
                    label = "Active keyboard",
                    description = "Currently the active input method",
                    value = if (probe.selected) "Yes" else "No",
                    isGood = probe.selected,
                    onClick = if (!probe.selected) {
                        {
                            if (probe.enabled) {
                                (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
                            } else {
                                context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    } else null,
                )
            }
            item { HorizontalDivider() }
            item { StatusRow(label = "Input language", description = null, value = language, isGood = null, onClick = null) }
            item { HorizontalDivider() }
            item { StatusRow(label = "Smart backlight", description = null, value = if (smartBacklight) "On" else "Off", isGood = null, onClick = null) }
            item { HorizontalDivider() }
            item { StatusRow(label = "App version", description = null, value = BuildConfig.VERSION_NAME, isGood = null, onClick = null) }
        }
    }
}

private fun currentInputLanguageDisplayName(context: android.content.Context): String = try {
    val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val tag = imm?.currentInputMethodSubtype?.languageTag
    val locale = if (!tag.isNullOrBlank()) Locale.forLanguageTag(tag) else Locale.getDefault()
    val name = locale.getDisplayName(locale)
    if (name.isBlank()) "Unknown" else name.replaceFirstChar { it.titlecase(locale) }
} catch (_: Exception) {
    "Unknown"
}

@Composable
private fun StatusRow(label: String, description: String?, value: String, isGood: Boolean?, onClick: (() -> Unit)?) {
    val color = when (isGood) {
        true -> brobata.physiboard.app.settings.ui.PhysiBoardColors.SignalAmber
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurface
    }
    var rowModifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget)
    if (onClick != null) rowModifier = rowModifier.clickable(onClick = onClick)
    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, color = color, style = MaterialTheme.typography.bodyLarge)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
    }
}
