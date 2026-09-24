package brobata.physiboard.app.settings.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SettingsSearchField
import androidx.compose.foundation.lazy.LazyListScope

/**
 * The "Settings" screen (settings-catalog.md SS9.2), stripped to what this milestone owns: the
 * search box, and the two rows rebuild-from-scratch.md calls out for day one. Status, Backup,
 * Restore, Diagnostics, "Reset device settings to stock", About and Updates are the app-shell
 * milestone's rows and are deliberately not here (see the settings module's report).
 */
@Composable
fun SettingsRootScreen(onNavigate: (String) -> Unit) {
    val controller = LocalSettingsController.current
    var showResetConfirm by remember { mutableStateOf(false) }

    SettingsScreenScaffold(title = "Settings", onBack = null) {
        SettingsSearchField(onSettingsRoot = true, onNavigate = onNavigate)
        RowList {
            rootRows(onNavigate = onNavigate, onResetClick = { showResetConfirm = true })
        }
    }

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
}

private fun LazyListScope.rootRows(onNavigate: (String) -> Unit, onResetClick: () -> Unit) {
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
        NavigateRow(
            label = "Reset to defaults",
            description = "Restore every PhysiBoard setting to its factory baseline",
            onClick = onResetClick,
        )
    }
}
