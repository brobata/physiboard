package brobata.physiboard.app.settings.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.BackupArchive
import brobata.physiboard.app.settings.ui.InfoText
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.rememberHaptic
import brobata.physiboard.core.actions.feedback.HapticEvent
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.shell.toast
import brobata.physiboard.core.shell.BackupMeta
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Backup & restore" (settings-catalog.md SS9.2, SS9.5; app-shell.md SS16): the maintenance rows
 * that used to sit on the old Settings screen. Back up and restore first; the two resets last, in
 * a card of their own and in the error colour, each behind a confirmation. A backup carries every
 * stored key, including the ones no screen offers any more (the suggestion strip's), so an old
 * backup restores in full.
 */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val controller = LocalSettingsController.current
    val haptic = rememberHaptic()
    var showResetConfirm by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    var showResetDeviceConfirm by remember { mutableStateOf(false) }
    var resettingDevice by remember { mutableStateOf(false) }
    var resetDeviceMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val settings = controller.current.value
        val meta = BackupMeta(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(java.util.Date()))
        scope.launch {
            val result = runCatching {
                val stream = context.contentResolver.openOutputStream(uri) ?: error("Unable to open target destination")
                stream.use { BackupArchive.write(context, settings, meta, it) }
            }
            result.onSuccess { toast(context, "Backup completed") }
                .onFailure { toast(context, "Backup failed: ${it.message}") }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            if (stream == null) {
                restoreMessage = "Restore failed: Unable to open the selected file"
                return@launch
            }
            when (val result = stream.use { BackupArchive.restore(context, controller.current.value, it) }) {
                is BackupArchive.RestoreResult.Failed -> restoreMessage = "Restore failed: ${result.reason}"
                is BackupArchive.RestoreResult.Applied -> {
                    controller.update { result.outcome.settings }
                    val skipped = result.outcome.skippedCount + result.sideFileFailures + result.unreadablePrefsFiles
                    restoreMessage = when {
                        skipped == 0 -> "Restore completed"
                        skipped == 1 -> "Restored, but one item could not be applied"
                        else -> "Restored, but $skipped items could not be applied"
                    }
                }
            }
        }
    }

    SettingsScreenScaffold(title = "Backup & restore", onBack = onBack) {
        RowList {
            plainItem { InfoText("One file with every PhysiBoard setting, your Fn layer, your accent lists and your imported layouts. Restore it on this phone or another to put them back.") }
            header("")
            item {
                NavigateRow(label = "Back up now", description = "Save all settings to a file", icon = Icons.Outlined.Save) {
                    val name = "physiboard-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(java.util.Date())}.zip"
                    backupLauncher.launch(name)
                }
            }
            item {
                NavigateRow(label = "Restore from a file", description = "Load a PhysiBoard backup", icon = Icons.Outlined.Restore) {
                    restoreLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
                }
            }
            header("Start over")
            item {
                NavigateRow(
                    label = if (resettingDevice) "Resetting…" else "Reset device settings to stock",
                    description = "Undo the Fn key and backlight changes. Do this before you uninstall.",
                    icon = Icons.Outlined.PhonelinkSetup,
                    destructive = true,
                    showChevron = false,
                    enabled = !resettingDevice,
                ) { showResetDeviceConfirm = true }
            }
            item {
                NavigateRow(
                    label = "Reset all settings",
                    description = "Every PhysiBoard setting back to how it shipped",
                    icon = Icons.Outlined.RestartAlt,
                    destructive = true,
                    showChevron = false,
                ) { showResetConfirm = true }
            }
        }
    }


    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset to defaults?") },
            text = { Text("This restores every PhysiBoard setting to its factory baseline. It does not touch anything outside the app.") },
            confirmButton = {
                // app-shell.md SS22.4: one of the two resets that keep their confirmation, felt as one.
                TextButton(onClick = {
                    haptic(HapticEvent.CONFIRM_DESTRUCTIVE)
                    controller.resetToDefaults()
                    showResetConfirm = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
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

    // spec: broker-privileged-toolbox.md SS10 ("Reset device settings to stock").
    if (showResetDeviceConfirm) {
        AlertDialog(
            onDismissRequest = { showResetDeviceConfirm = false },
            title = { Text("Reset device settings to stock?") },
            text = {
                Text(
                    "This restores the Fn key mapping and keyboard backlight to your device's stock settings. Your PhysiBoard preferences are kept. You can re-apply these features anytime.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic(HapticEvent.CONFIRM_DESTRUCTIVE)
                    showResetDeviceConfirm = false
                    resettingDevice = true
                    scope.launch(Dispatchers.IO) {
                        val report = application.privileged.reset.run()
                        resetDeviceMessage = report.message
                        resettingDevice = false
                    }
                }) { Text("Reset to stock", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetDeviceConfirm = false }) { Text("Cancel") } },
        )
    }

    resetDeviceMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { resetDeviceMessage = null },
            title = { Text("Reset device settings to stock") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { resetDeviceMessage = null }) { Text("OK") } },
        )
    }
}
