package brobata.physiboard.app.settings.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SettingsSearchField
import brobata.physiboard.app.shell.AutoUpdateCheckOnCreate
import brobata.physiboard.app.shell.UpdateFoundDialog
import brobata.physiboard.app.shell.rememberUpdateCheckState
import brobata.physiboard.app.shell.runUpdateCheck
import brobata.physiboard.app.shell.toast
import brobata.physiboard.core.shell.BackupCodec
import brobata.physiboard.core.shell.BackupMeta
import brobata.physiboard.core.shell.BackupRestore
import brobata.physiboard.core.shell.GithubChecks
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The "Settings" screen (settings-catalog.md SS9.2): the search box, the maintenance rows this
 * milestone owns (Backup, Restore, Diagnostics, Updates, About, Reset to defaults) plus the three
 * hub rows another milestone built (T2E Tools, Keyboard, Extras). This screen is reached from the
 * home screen's "Settings" tile (app-shell.md SS6.4), not drawn at process start any more (see
 * `HomeScreen`).
 */
@Composable
fun SettingsRootScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current
    var showResetConfirm by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val updateState = rememberUpdateCheckState()
    val installer = remember { runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull() }
    val githubChecksAllowed = remember { GithubChecks.allowed(buildFlagOn = true, installerPackageName = installer) }
    if (githubChecksAllowed) {
        // spec: SS9, "the settings screen itself also runs one automatic update check ... dialog only, no card."
        AutoUpdateCheckOnCreate(updateState, BuildConfig.VERSION_NAME, controller.current.value.shell.dismissedReleases)
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val settings = controller.current.value
        val meta = BackupMeta(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(java.util.Date()))
        val text = BackupCodec.encode(settings, meta)
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
            .onSuccess { toast(context, "Backup completed") }
            .onFailure { toast(context, "Backup failed: ${it.message}") }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val backup = text?.let { BackupCodec.decode(it) }
        if (backup == null) {
            restoreMessage = "Restore failed: Not a PhysiBoard backup: backup_meta.json is missing or unreadable"
        } else {
            scope.launch {
                val outcome = BackupRestore.restore(controller.current.value, backup)
                controller.update { outcome.settings }
                restoreMessage = when {
                    outcome.skippedCount == 0 -> "Restore completed"
                    outcome.skippedCount == 1 -> "Restored, but one item could not be applied"
                    else -> "Restored, but ${outcome.skippedCount} items could not be applied"
                }
            }
        }
    }

    SettingsScreenScaffold(title = "Settings", onBack = null) {
        SettingsSearchField(onSettingsRoot = true, onNavigate = onNavigate)
        RowList {
            rootRows(
                onNavigate = onNavigate,
                onResetClick = { showResetConfirm = true },
                onAboutClick = { onNavigate(Routes.ABOUT) },
                onDiagnosticsClick = { onNavigate(Routes.DIAGNOSTICS) },
                githubChecksAllowed = githubChecksAllowed,
                updatesChecking = updateState.checking,
                onUpdatesClick = {
                    scope.launch {
                        runUpdateCheck(
                            updateState,
                            BuildConfig.VERSION_NAME,
                            controller.current.value.shell.dismissedReleases.toSet(),
                            ignoreDismissedReleases = false,
                            onNoNetwork = { toast(context, "Unable to reach GitHub.") },
                            onUpToDate = { toast(context, "App is up to date.") },
                        )
                    }
                },
                onBackupClick = {
                    val name = "physiboard-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(java.util.Date())}.json"
                    backupLauncher.launch(name)
                },
                onRestoreClick = { restoreLauncher.launch(arrayOf("application/json", "application/zip", "*/*")) },
            )
        }
    }

    UpdateFoundDialog(updateState)

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset to defaults?") },
            text = { Text("This restores every PhysiBoard setting to its factory baseline. It does not touch anything outside the app.") },
            confirmButton = {
                TextButton(onClick = {
                    controller.resetToDefaults()
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } },
        )
    }

    restoreMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { restoreMessage = null },
            title = { Text("Restore") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { restoreMessage = null }) { Text("OK") } },
        )
    }
}

private fun LazyListScope.rootRows(
    onNavigate: (String) -> Unit,
    onResetClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    githubChecksAllowed: Boolean,
    updatesChecking: Boolean,
    onUpdatesClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
) {
    item {
        NavigateRow(
            label = "Test field",
            description = "A place to type, to try the keyboard while settings screens are built",
            onClick = { onNavigate(Routes.TEST_FIELD) },
        )
    }
    item {
        NavigateRow(label = "T2E Tools", description = "Titan-specific tools", onClick = { onNavigate(Routes.T2E_TOOLS) })
    }
    item {
        NavigateRow(label = "Keyboard", description = "Everything about how the keyboard behaves when you type", onClick = { onNavigate(Routes.KEYBOARD) })
    }
    item {
        NavigateRow(label = "Extras", description = "The quick launcher, languages and text expansion", onClick = { onNavigate(Routes.EXTRAS) })
    }
    item {
        NavigateRow(label = "Status", description = "Check PhysiBoard is set up correctly", onClick = { onNavigate(Routes.STATUS) })
    }
    item {
        NavigateRow(
            label = "Diagnostics",
            description = "Physical key-event logger and debug export",
            onClick = onDiagnosticsClick,
        )
    }
    if (githubChecksAllowed) {
        item {
            NavigateRow(
                label = if (updatesChecking) "Checking for updates…" else "Updates",
                description = "Check the latest release on GitHub.",
                onClick = onUpdatesClick,
            )
        }
    }
    item {
        NavigateRow(label = "Backup now", description = "Export all settings to a file", onClick = onBackupClick)
    }
    item {
        NavigateRow(label = "Restore from file", description = "Import a PhysiBoard backup", onClick = onRestoreClick)
    }
    item {
        NavigateRow(label = "About", description = "Version, licence, and credits", onClick = onAboutClick)
    }
    item {
        NavigateRow(
            label = "Reset to defaults",
            description = "Restore every PhysiBoard setting to its factory baseline",
            onClick = onResetClick,
        )
    }
}
