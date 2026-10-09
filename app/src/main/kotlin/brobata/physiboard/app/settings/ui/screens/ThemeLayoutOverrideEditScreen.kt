package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.ColorFieldRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.settings.ThemeLayoutOverride

/**
 * Add or edit one row of "Layout overrides" (status-bar.md SS9.2). [index] is -1 for a new
 * override, else its position in `layoutOverrides`. spec: "an entry with neither locale nor
 * layout is dropped" (settings-catalog.md SS2.6) is enforced on save, not as the user types.
 */
@Composable
fun ThemeLayoutOverrideEditScreen(index: Int, onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val overrides = controller.current.value.statusBar.layoutOverrides
    val existing = overrides.getOrNull(index)
    val baseTheme = existing?.theme ?: controller.current.value.statusBar.theme

    var locale by remember { mutableStateOf(existing?.locale.orEmpty()) }
    var layout by remember { mutableStateOf(existing?.layout.orEmpty()) }
    var theme by remember { mutableStateOf(baseTheme) }

    fun save() {
        if (locale.isBlank() && layout.isBlank()) {
            onBack()
            return
        }
        val updated = ThemeLayoutOverride(locale.trim().ifBlank { null }, layout.trim().ifBlank { null }, theme)
        controller.update { s ->
            val list = s.statusBar.layoutOverrides
            val newList = if (index in list.indices) list.toMutableList().apply { set(index, updated) } else list + updated
            s.copy(statusBar = s.statusBar.copy(layoutOverrides = newList))
        }
        onBack()
    }

    SettingsScreenScaffold(title = if (index >= 0) "Edit override" else "Add override", onBack = ::save) {
        RowList {
            header("Applies to")
            item {
                OutlinedTextField(
                    value = locale,
                    onValueChange = { locale = it },
                    label = { Text("Locale (blank = any)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = layout,
                    onValueChange = { layout = it },
                    label = { Text("Layout (blank = any)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // Same labels as Customize colors: what each colour paints on the Sym pages now.
            header("Sym pages and panels")
            item { ColorFieldRow("Background", theme.background) { v -> theme = theme.copy(background = v) } }
            item { ColorFieldRow("Keys", theme.suggestion) { v -> theme = theme.copy(suggestion = v) } }
            item { ColorFieldRow("Buttons", theme.statusBarButton) { v -> theme = theme.copy(statusBarButton = v) } }
            item { ColorFieldRow("Key outlines", theme.divider) { v -> theme = theme.copy(divider = v) } }
            item { ColorFieldRow("Text and icons", theme.textAndIcons) { v -> theme = theme.copy(textAndIcons = v) } }
            item { ColorFieldRow("Accent", theme.accent) { v -> theme = theme.copy(accent = v) } }
            header("Status strip (switched off)")
            item { ColorFieldRow("LED inactive", theme.ledInactive) { v -> theme = theme.copy(ledInactive = v) } }
            item { ColorFieldRow("LED active", theme.ledActive) { v -> theme = theme.copy(ledActive = v) } }
            item { ColorFieldRow("LED locked", theme.ledLocked) { v -> theme = theme.copy(ledLocked = v) } }
            item { ButtonRow(label = "Save", buttonText = "Save", onClick = ::save) }
            if (existing != null) {
                item {
                    ButtonRow(
                        label = "Delete this override",
                        buttonText = "Delete",
                        onClick = {
                            controller.update { s -> s.copy(statusBar = s.statusBar.copy(layoutOverrides = s.statusBar.layoutOverrides.filterIndexed { i, _ -> i != index })) }
                            onBack()
                        },
                    )
                }
            }
        }
    }
}
