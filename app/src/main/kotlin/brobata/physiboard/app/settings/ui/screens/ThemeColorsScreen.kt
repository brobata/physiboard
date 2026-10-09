package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.ColorHex
import brobata.physiboard.app.settings.ui.ColorPickerDialog
import brobata.physiboard.app.settings.ui.KeyboardUiPreview
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.RowMinHeight
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.core.settings.NamedTheme
import brobata.physiboard.core.settings.StripTheme
import brobata.physiboard.core.settings.SymPagePrefs
import brobata.physiboard.core.settings.StripThemePresets

/**
 * "Colours" (settings-catalog.md SS9.2, status-bar.md SS9.1, SS9.4 in its 3.1 form): the six
 * colours the Sym pages and panels use, labelled by what they paint (`suggestion` is "Keys",
 * `status_bar_button` is "Buttons", `divider` is "Key outlines"); the stored keys keep their old
 * names. The LED colours and the three geometry ratios only ever styled the suggestion strip,
 * which is gone (c61c240): no screen offers them, and they stay in the stored theme so a backup
 * restores them. "Save as" keeps the colours shown under a name in `keyboard_theme_saved_themes`.
 */
@Composable
fun ThemeColorsScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    fun theme() = controller.current.value.statusBar.theme
    fun set(transform: (StripTheme) -> StripTheme) =
        controller.update { it.copy(statusBar = it.statusBar.copy(theme = transform(it.statusBar.theme))) }

    var showSaveDialog by remember { mutableStateOf(false) }

    SettingsScreenScaffold(
        title = "Colours",
        onBack = onBack,
        trailingAction = { TextButton(onClick = { showSaveDialog = true }) { Text("Save as…") } },
    ) {
        RowList {
            plainItem { KeyboardUiPreview(theme = theme(), characters = previewCharacters(controller.current.value.symPages)) }
            header("Sym pages and panels")
            item { ThemeColorRow("Background", "Behind the Sym pages, emoji picker and clipboard", theme().background) { v -> set { p -> p.copy(background = v) } } }
            item { ThemeColorRow("Keys", "Sym page keys, search fields, clips and GIF cards", theme().suggestion) { v -> set { p -> p.copy(suggestion = v) } } }
            item { ThemeColorRow("Buttons", "Pencil, globe, close and the panels' tool buttons", theme().statusBarButton) { v -> set { p -> p.copy(statusBarButton = v) } } }
            item { ThemeColorRow("Key outlines", "The edge around keys, cards and the selected tab", theme().divider) { v -> set { p -> p.copy(divider = v) } } }
            item { ThemeColorRow("Text and icons", "Characters, labels and icons", theme().textAndIcons) { v -> set { p -> p.copy(textAndIcons = v) } } }
            item { ThemeColorRow("Accent", "The selected emoji tab, pinned clips and Clear all", theme().accent) { v -> set { p -> p.copy(accent = v) } } }
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

/**
 * One colour row (status-bar.md SS9.4 item 3): a swatch preview and the hex text,
 * opening the shared [ColorPickerDialog] (SS9.5) rather than editing the hex inline.
 */
@Composable
internal fun ThemeColorRow(label: String, description: String?, value: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = RowMinHeight)
            .clickable(role = Role.Button) { open = true }
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(value))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.l)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(ColorHex.toHex(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        ColorPickerDialog(
            title = label,
            initial = value,
            swatches = ThemeSwatches.ALL,
            onDismiss = { open = false },
            onConfirm = { color -> onChange(color); open = false },
        )
    }
}

/** spec status-bar.md SS9.5: "the union of every preset's... colours, de-duplicated, for theme colors", taken over the six colours a screen still offers. */
private object ThemeSwatches {
    val ALL: List<Int> by lazy {
        StripThemePresets.ALL.flatMap { preset ->
            with(preset.theme) { listOf(background, suggestion, statusBarButton, accent, textAndIcons, divider) }
        }.distinct()
    }
}

/** What the preview's keys show: the Symbols page as the user has it. */
private fun previewCharacters(symPages: SymPagePrefs): Map<Char, String> =
    effectiveCharacters(isEmoji = false, customEmoji = symPages.customEmojiPage, customSymbols = symPages.customSymbolsPage)
