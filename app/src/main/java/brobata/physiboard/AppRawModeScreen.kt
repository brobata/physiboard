package brobata.physiboard

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import brobata.physiboard.inputmethod.WebApkHost

internal data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    /** Host browser when this entry is an installed web app (WebAPK); null otherwise. */
    val hostBrowser: String? = null
)

internal fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val packageManager = context.packageManager
    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return packageManager.queryIntentActivities(launchIntent, 0)
        .map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            LaunchableApp(
                packageName = packageName,
                label = resolveInfo.loadLabel(packageManager)?.toString() ?: packageName,
                icon = resolveInfo.loadIcon(packageManager),
                hostBrowser = WebApkHost.hostBrowser(context, packageName)
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/**
 * Per-app raw mode: apps where all smart typing features (suggestions,
 * auto-correction, auto-capitalization, double-space-to-period) are disabled.
 */
@Composable
fun AppRawModeScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    AppToggleListScreen(
        modifier = modifier,
        title = stringResource(R.string.app_raw_mode_title),
        description = stringResource(R.string.app_raw_mode_description),
        readEnabledPackages = { SettingsManager.getRawModePackages(context) },
        setEnabled = { packageName, enabled -> SettingsManager.setRawModeApp(context, packageName, enabled) },
        webApkNote = { host -> stringResource(R.string.app_raw_mode_webapk_note, hostAppLabel(context, host)) },
        onBack = onBack
    )
}

/**
 * One switch per installed app, with the enabled ones sorted to the top. Shared by every
 * per-app list in settings so they all look and behave the same.
 *
 * @param webApkNote a line to show under an installed web app naming its host browser, or null
 * when the setting does not carry over to the browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppToggleListScreen(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    readEnabledPackages: () -> Set<String>,
    setEnabled: (packageName: String, enabled: Boolean) -> Unit,
    webApkNote: (@Composable (hostBrowser: String) -> String)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var enabledPackages by remember { mutableStateOf(readEnabledPackages()) }
    val apps by produceState<List<LaunchableApp>?>(initialValue = null) {
        value = loadLaunchableApps(context)
    }

    BackHandler { onBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val loadedApps = apps
        if (loadedApps == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(
                loadedApps.sortedByDescending { it.packageName in enabledPackages },
                key = { it.packageName }
            ) { app ->
                val enabled = app.packageName in enabledPackages
                val toggle: (Boolean) -> Unit = { checked ->
                    setEnabled(app.packageName, checked)
                    enabledPackages = readEnabledPackages()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { toggle(!enabled) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    app.icon?.let { drawable ->
                        Image(
                            bitmap = remember(app.packageName) {
                                drawable.toBitmap(96, 96).asImageBitmap()
                            },
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    } ?: Spacer(modifier = Modifier.size(36.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (webApkNote != null) {
                            app.hostBrowser?.let { host ->
                                Text(
                                    text = webApkNote(host),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = toggle
                    )
                }
            }
        }
    }
}

internal fun hostAppLabel(context: Context, packageName: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
