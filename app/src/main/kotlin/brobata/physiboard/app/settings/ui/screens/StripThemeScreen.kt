package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.ColorHex
import brobata.physiboard.app.settings.ui.ColorPickerDialog
import brobata.physiboard.app.settings.ui.ExpandableSection
import brobata.physiboard.app.settings.ui.InfoText
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.RowMinHeight
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.core.settings.NamedTheme
import brobata.physiboard.core.settings.StripTheme
import brobata.physiboard.core.settings.StripThemePresets
import kotlin.math.roundToInt

/**
 * "Customize colors" (settings-catalog.md SS9.2, status-bar.md SS9.1, SS9.4 "3.x Theme page").
 * The six colours the Sym pages and panels use come first, labelled by what they paint now
 * (`suggestion` is "Keys", `status_bar_button` is "Buttons", `divider` is "Key outlines"); the
 * stored keys keep their old names. The LED colours and the three geometry ratios only style the
 * hidden strip, so they sit collapsed under "Status strip". The ratios are Doubles in the schema,
 * edited as a 0-200 slider standing for 0.00-2.00. "Choose a preset" and the preview live on
 * [StatusBarThemeScreen]. "Save theme" saves the colours shown under a name into
 * `keyboard_theme_saved_themes`.
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
            item { ButtonRow(label = "Save theme", description = "Keep these colours under a name", buttonText = "Save as…", onClick = { showSaveDialog = true }) }
            // The labels say what each colour paints now: the Sym pages and the panels beside them
            // (emoji picker, clipboard, GIFs). The stored keys keep their old names.
            header("Sym pages and panels")
            item { ThemeColorRow("Background", "Behind the Sym pages, emoji picker and clipboard", theme().background) { v -> set { p -> p.copy(background = v) } } }
            item { ThemeColorRow("Keys", "Sym page keys, search fields, clips and GIF cards", theme().suggestion) { v -> set { p -> p.copy(suggestion = v) } } }
            item { ThemeColorRow("Buttons", "Pencil, globe, close and the panels' tool buttons", theme().statusBarButton) { v -> set { p -> p.copy(statusBarButton = v) } } }
            item { ThemeColorRow("Key outlines", "The edge around keys, cards and the selected tab", theme().divider) { v -> set { p -> p.copy(divider = v) } } }
            item { ThemeColorRow("Text and icons", "Characters, labels and icons", theme().textAndIcons) { v -> set { p -> p.copy(textAndIcons = v) } } }
            item { ThemeColorRow("Accent", "The selected emoji tab, pinned clips and Clear all", theme().accent) { v -> set { p -> p.copy(accent = v) } } }
            header("Status strip")
            item {
                InfoText("The strip above the keyboard is switched off, so these change nothing you can see.")
                ExpandableSection("Strip LEDs, corners and height") {
                    ThemeColorRow("LED inactive", null, theme().ledInactive) { v -> set { p -> p.copy(ledInactive = v) } }
                    ThemeColorRow("LED active", null, theme().ledActive) { v -> set { p -> p.copy(ledActive = v) } }
                    ThemeColorRow("LED locked", null, theme().ledLocked) { v -> set { p -> p.copy(ledLocked = v) } }
                    IntRangeRow(
                        label = "Strip key corners",
                        value = (theme().keyCornerRadiusRatio * 100).roundToInt(),
                        range = IntClosedRange(0, 100),
                        valueLabel = { "${it}%" },
                        onValueChange = { v -> set { p -> p.copy(keyCornerRadiusRatio = v / 100.0) } },
                    )
                    IntRangeRow(
                        label = "Strip button corners",
                        value = (theme().chromeCornerRadiusRatio * 100).roundToInt(),
                        range = IntClosedRange(0, 100),
                        valueLabel = { "${it}%" },
                        onValueChange = { v -> set { p -> p.copy(chromeCornerRadiusRatio = v / 100.0) } },
                    )
                    IntRangeRow(
                        label = "Strip text size",
                        value = (theme().suggestionsHeightScale * 100).roundToInt(),
                        range = IntClosedRange(50, 200),
                        valueLabel = { "${it}%" },
                        onValueChange = { v -> set { p -> p.copy(suggestionsHeightScale = v / 100.0) } },
                    )
                }
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

/**
 * One colour row (status-bar.md SS9.4 item 3): a swatch preview and the hex text,
 * opening the shared [ColorPickerDialog] (SS9.5) rather than editing the hex inline.
 */
@Composable
private fun ThemeColorRow(label: String, description: String?, value: Int, onChange: (Int) -> Unit) {
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

/** spec status-bar.md SS9.5: "the union of every preset's... colours, de-duplicated, for theme colors". 3.0's [StripTheme] keeps nine of the fields the 2.x presets had thirteen of (SS9.1's Keep/Drop); the union is taken over those nine. */
private object ThemeSwatches {
    val ALL: List<Int> by lazy {
        StripThemePresets.ALL.flatMap { preset ->
            with(preset.theme) { listOf(background, suggestion, statusBarButton, accent, textAndIcons, divider, ledInactive, ledActive, ledLocked) }
        }.distinct()
    }
}
