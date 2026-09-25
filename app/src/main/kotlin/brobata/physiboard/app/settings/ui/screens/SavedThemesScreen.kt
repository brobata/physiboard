package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.settings.NamedTheme
import brobata.physiboard.core.settings.StatusBarPrefs

/**
 * "Saved themes" (status-bar.md SS9.2, `keyboard_theme_saved_themes`): apply or delete a theme the
 * user saved from [StripThemeScreen]'s "Save theme" action. Names compare case-insensitively on
 * delete (settings-catalog.md SS2.6).
 */
@Composable
fun SavedThemesScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val statusBar = controller.current.value.statusBar
    fun set(transform: (StatusBarPrefs) -> StatusBarPrefs) = controller.update { it.copy(statusBar = transform(it.statusBar)) }

    var pendingDelete by remember { mutableStateOf<NamedTheme?>(null) }

    SettingsScreenScaffold(title = "Saved themes", onBack = onBack) {
        RowList {
            if (statusBar.savedThemes.isEmpty()) {
                item { Text("No saved themes yet. Use \"Save theme\" on Customize colors.", modifier = Modifier.padding(16.dp)) }
            }
            items(statusBar.savedThemes, key = { it.name.lowercase() }) { named ->
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(named.name, modifier = Modifier.weight(1f))
                    TextButton(onClick = { set { p -> p.copy(theme = named.theme) } }) { Text("Apply") }
                    IconButton(onClick = { pendingDelete = named }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
            }
        }
    }

    pendingDelete?.let { named ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete saved theme") },
            text = { Text("Delete \"${named.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    set { p -> p.copy(savedThemes = p.savedThemes.filterNot { it.name.equals(named.name, ignoreCase = true) }) }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}
