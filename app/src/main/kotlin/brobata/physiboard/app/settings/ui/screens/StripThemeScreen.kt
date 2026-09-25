package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.ColorFieldRow
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.settings.NamedTheme
import brobata.physiboard.core.settings.StripTheme
import kotlin.math.roundToInt

/**
 * "Customize colors" (settings-catalog.md SS9.2, status-bar.md SS9.1). The three geometry ratios
 * are Doubles in the schema; this screen edits them as a 0-200 slider standing for 0.00-2.00 so
 * they fit the "integer with a range" row type rather than adding a seventh row kind for one
 * screen. "Choose a preset" lives on [StatusBarThemeScreen] (status-bar.md SS9.4 item 1); the
 * "Keyboard UI Preview" is a render, not a setting, and is left out. So are the 2.x colour rows
 * "Normal keys", "Special keys", "Cursor swipe", "Key popup" and "Key popup selected":
 * [StripTheme] was reduced to only the fields the strip itself uses (status-bar.md SS9.1), and
 * those five never made the cut. "Save theme" (status-bar.md SS9.4 item 3, "Create a custom
 * theme" / "Save and use theme", simplified to one step since 3.0 has no draft state) saves the
 * colors currently shown under a name into `keyboard_theme_saved_themes`.
 */
@Composable
fun StripThemeScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    fun theme() = controller.current.value.statusBar.theme
    fun set(transform: (StripTheme) -> StripTheme) =
        controller.update { it.copy(statusBar = it.statusBar.copy(theme = transform(it.statusBar.theme))) }

    var showSaveDialog by remember { mutableStateOf(false) }

    SettingsScreenScaffold(title = "Customize colors", onBack = onBack) {
        RowList {
            item { ButtonRow(label = "Save theme", buttonText = "Save as...", onClick = { showSaveDialog = true }) }
            item { ColorFieldRow("Background", theme().background) { v -> set { p -> p.copy(background = v) } } }
            item { ColorFieldRow("Dividers", theme().divider) { v -> set { p -> p.copy(divider = v) } } }
            item { ColorFieldRow("Text and icons", theme().textAndIcons) { v -> set { p -> p.copy(textAndIcons = v) } } }
            item { ColorFieldRow("Accent", theme().accent) { v -> set { p -> p.copy(accent = v) } } }
            item { ColorFieldRow("Suggestions", theme().suggestion) { v -> set { p -> p.copy(suggestion = v) } } }
            item { ColorFieldRow("Status bar buttons", theme().statusBarButton) { v -> set { p -> p.copy(statusBarButton = v) } } }
            item { ColorFieldRow("LED inactive", theme().ledInactive) { v -> set { p -> p.copy(ledInactive = v) } } }
            item { ColorFieldRow("LED active", theme().ledActive) { v -> set { p -> p.copy(ledActive = v) } } }
            item { ColorFieldRow("LED locked", theme().ledLocked) { v -> set { p -> p.copy(ledLocked = v) } } }
            item {
                IntRangeRow(
                    label = "Key corner radius",
                    value = (theme().keyCornerRadiusRatio * 100).roundToInt(),
                    range = IntClosedRange(0, 100),
                    valueLabel = { "${it}%" },
                    onValueChange = { v -> set { p -> p.copy(keyCornerRadiusRatio = v / 100.0) } },
                )
            }
            item {
                IntRangeRow(
                    label = "Chrome corner radius",
                    value = (theme().chromeCornerRadiusRatio * 100).roundToInt(),
                    range = IntClosedRange(0, 100),
                    valueLabel = { "${it}%" },
                    onValueChange = { v -> set { p -> p.copy(chromeCornerRadiusRatio = v / 100.0) } },
                )
            }
            item {
                IntRangeRow(
                    label = "Suggestions height",
                    value = (theme().suggestionsHeightScale * 100).roundToInt(),
                    range = IntClosedRange(50, 200),
                    valueLabel = { "${it}%" },
                    onValueChange = { v -> set { p -> p.copy(suggestionsHeightScale = v / 100.0) } },
                )
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        val existingNames = controller.current.value.statusBar.savedThemes.map { it.name.lowercase() }
        val error = if (name.isNotBlank() && name.trim().lowercase() in existingNames) "That name is already used" else null
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save theme") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Theme name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { if (error != null) Text(error) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val savedName = name.trim().ifBlank { "Custom" }
                        controller.update { it.copy(statusBar = it.statusBar.copy(savedThemes = it.statusBar.savedThemes + NamedTheme(savedName, it.statusBar.theme))) }
                        showSaveDialog = false
                    },
                    enabled = error == null,
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
        )
    }
}
