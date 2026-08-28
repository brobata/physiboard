package brobata.physiboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import brobata.physiboard.backup.BackupManager
import brobata.physiboard.backup.RestoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup, restore, diagnostics and reset-to-stock, rendered straight onto the settings list.
 *
 * These four lived behind an "Advanced" screen until 2.0. With the rest of that screen's contents
 * moved elsewhere, hiding them one level down bought nothing, so they are emitted inline here and
 * the Advanced screen is gone. Kept in their own file rather than inlined into the settings screen
 * because they carry their own launchers, dialog and snackbar traffic.
 *
 * @param snackbarHostState where backup/restore/reset results are reported; the host page owns it.
 */
@Composable
fun MaintenanceSettingsRows(
    snackbarHostState: SnackbarHostState,
    onOpenDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetToStockDialog by remember { mutableStateOf(false) }
    var resetToStockInProgress by remember { mutableStateOf(false) }

    fun defaultBackupName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        return "physiboard-backup-${formatter.format(Date())}.zip"
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = BackupManager.createBackup(context, uri)
                val message = when (result) {
                    is brobata.physiboard.backup.BackupResult.Success ->
                        context.getString(R.string.backup_completed)
                    is brobata.physiboard.backup.BackupResult.Failure ->
                        context.getString(R.string.backup_failed, result.reason)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = RestoreManager.restore(context, uri)
                val message = when (result) {
                    is brobata.physiboard.backup.RestoreResult.Success ->
                        context.getString(R.string.restore_completed)
                    is brobata.physiboard.backup.RestoreResult.Failure ->
                        context.getString(R.string.restore_failed, result.reason)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

        // Backup
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable {
                    backupLauncher.launch(defaultBackupName())
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.backup_now),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.backup_now_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    
        // Restore
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable {
                    restoreLauncher.launch(arrayOf("application/zip"))
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.restore_from_file),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.restore_from_file_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    
        // Diagnostics (physical key-event logger + debug export)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { onOpenDiagnostics() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.diagnostics_row_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Reset device settings to stock — undoes EVERY system-wide change
        // PhysiBoard can make (Fn->Ctrl, keyboard backlight). Survives uninstall,
        // so the user must tap this BEFORE uninstalling.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !resetToStockInProgress) {
                    showResetToStockDialog = true
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SettingsBackupRestore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reset_to_stock_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2
                    )
                    Text(
                        text = stringResource(R.string.reset_to_stock_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (resetToStockInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showResetToStockDialog) {
            AlertDialog(
                onDismissRequest = { showResetToStockDialog = false },
                title = { Text(stringResource(R.string.reset_to_stock_confirm_title)) },
                text = { Text(stringResource(R.string.reset_to_stock_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showResetToStockDialog = false
                        resetToStockInProgress = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                SystemChangeManager.resetToStock(context)
                            }
                            resetToStockInProgress = false
                            val message = when {
                                result.allSucceeded ->
                                    context.getString(R.string.reset_to_stock_result_success)
                                result.needsPermission ->
                                    context.getString(R.string.reset_to_stock_result_needs_permission)
                                else ->
                                    context.getString(R.string.reset_to_stock_result_partial)
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                    }) {
                        Text(stringResource(R.string.reset_to_stock_confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetToStockDialog = false }) {
                        Text(stringResource(R.string.reset_to_stock_confirm_cancel))
                    }
                }
            )
        }
}
