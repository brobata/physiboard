package brobata.physiboard

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Simple "everything's fine" surface summarizing PhysiBoard's setup state.
 */
@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val (imeEnabled, imeActive) = remember(context) { resolvePhysiBoardImeStatus(context) }
    val inputLanguage = remember(context) { resolveCurrentInputLanguage(context) }
    val backlightOn = SettingsManager.getSmartBacklightEnabled(context)

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.status_screen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            StatusRow(
                title = stringResource(R.string.status_enabled_title),
                description = stringResource(R.string.status_enabled_description),
                value = yesNo(imeEnabled),
                positive = imeEnabled
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatusRow(
                title = stringResource(R.string.status_active_ime_title),
                description = stringResource(R.string.status_active_ime_description),
                value = yesNo(imeActive),
                positive = imeActive
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatusRow(
                title = stringResource(R.string.status_input_language_title),
                description = null,
                value = inputLanguage,
                positive = null
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatusRow(
                title = stringResource(R.string.status_backlight_title),
                description = null,
                value = if (backlightOn) {
                    stringResource(R.string.status_value_on)
                } else {
                    stringResource(R.string.status_value_off)
                },
                positive = null
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatusRow(
                title = stringResource(R.string.status_version_title),
                description = null,
                value = BuildConfig.VERSION_NAME,
                positive = null
            )
        }
    }
}

@Composable
private fun yesNo(value: Boolean): String =
    if (value) stringResource(R.string.status_value_yes) else stringResource(R.string.status_value_no)

@Composable
private fun StatusRow(
    title: String,
    description: String?,
    value: String,
    positive: Boolean?
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (positive) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

private fun resolvePhysiBoardImeStatus(context: Context): Pair<Boolean, Boolean> {
    return try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { info ->
            info.packageName == ImeIdentity.packageName || ImeIdentity.matchesImeId(info.id)
        }
        var active = false
        if (enabled) {
            active = try {
                val current = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
                )
                ImeIdentity.matchesImeId(current)
            } catch (_: Exception) {
                false
            }
        }
        enabled to active
    } catch (_: Exception) {
        false to false
    }
}

private fun resolveCurrentInputLanguage(context: Context): String {
    return try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val subtype = imm.currentInputMethodSubtype
        val tag = subtype?.languageTag?.takeIf { it.isNotBlank() }
        val locale = if (tag != null) {
            Locale.forLanguageTag(tag)
        } else {
            context.resources.configuration.locales[0]
        }
        locale.getDisplayName(locale).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
    } catch (_: Exception) {
        context.getString(R.string.status_value_unknown)
    }
}
