package brobata.physiboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import brobata.physiboard.ui.SettingsTopBar
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
    // These rows are changed in Android's own settings, so they are re-read every time this
    // screen comes back to the front. Computed once, they would report the state the user just
    // left rather than the one they just chose.
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val (imeEnabled, imeActive) = remember(refreshTick) { resolvePhysiBoardImeStatus(context) }
    val inputLanguage = remember(refreshTick) { resolveCurrentInputLanguage(context) }
    val backlightOn = remember(refreshTick) { SettingsManager.getSmartBacklightEnabled(context) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.status_screen_title),
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Both rows are actionable when they are the thing that is wrong: reporting "No"
            // without a way to fix it is the whole complaint.
            StatusRow(
                title = stringResource(R.string.status_enabled_title),
                description = stringResource(R.string.status_enabled_description),
                value = yesNo(imeEnabled),
                positive = imeEnabled,
                onClick = if (imeEnabled) null else {
                    { openInputMethodSettings(context) }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatusRow(
                title = stringResource(R.string.status_active_ime_title),
                description = stringResource(R.string.status_active_ime_description),
                value = yesNo(imeActive),
                positive = imeActive,
                // The picker is the only way to switch keyboard; it needs PhysiBoard enabled
                // first, so before that this row sends you to the enable screen instead.
                onClick = if (imeActive) null else {
                    {
                        if (imeEnabled) showInputMethodPicker(context)
                        else openInputMethodSettings(context)
                    }
                }
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
    positive: Boolean?,
    onClick: (() -> Unit)? = null
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
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
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Android's "Manage on-screen keyboards" list, where PhysiBoard is switched on. */
private fun openInputMethodSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** The system keyboard picker, the only way to make a keyboard the active one. */
private fun showInputMethodPicker(context: Context) {
    runCatching {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showInputMethodPicker()
    }
}

/**
 * Whether PhysiBoard is enabled as an input method, and whether it is the selected one.
 *
 * Internal rather than private so the home screen can summarise the same two checks on its Status
 * tile — the point of that tile is answering "is anything wrong" without opening it.
 */
internal fun resolvePhysiBoardImeStatus(context: Context): Pair<Boolean, Boolean> {
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
