package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.EmptyState
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.settings.ThemeLayoutOverride

/**
 * "Layout overrides" (status-bar.md SS9.2, settings-catalog.md SS2.6): the list half of
 * `keyboard_theme_layout_overrides_hardware`. Tapping a row opens
 * [ThemeLayoutOverrideEditScreen] with its index; [onNavigate] is given the index-suffixed route
 * rather than this screen owning the edit UI, matching how [CustomSubstitutionsScreen] hands off
 * to its own per-language edit screen.
 */
@Composable
fun ThemeLayoutOverridesScreen(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val controller = LocalSettingsController.current
    val overrides = controller.current.value.statusBar.layoutOverrides

    SettingsScreenScaffold(
        title = "Layout overrides",
        onBack = onBack,
        trailingAction = {
            IconButton(onClick = { onOpen(-1) }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Add, contentDescription = "Add override")
            }
        },
    ) {
        RowList {
            if (overrides.isEmpty()) {
                plainItem { EmptyState(Icons.Outlined.Translate, "No overrides yet. Add one for a locale or layout that should use a different theme.") }
            }
            items(overrides.withIndex().toList(), key = { it.index }) { (index, override) ->
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(overrideLabel(override), modifier = Modifier.weight(1f))
                    IconButton(onClick = { onOpen(index) }) { Text("Edit") }
                    IconButton(onClick = {
                        controller.update { s -> s.copy(statusBar = s.statusBar.copy(layoutOverrides = s.statusBar.layoutOverrides.filterIndexed { i, _ -> i != index })) }
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete override") }
                }
            }
        }
    }
}

private fun overrideLabel(override: ThemeLayoutOverride): String = listOfNotNull(
    override.locale?.let { "locale $it" },
    override.layout?.let { "layout $it" },
).joinToString(", ").ifBlank { "(unset)" }
