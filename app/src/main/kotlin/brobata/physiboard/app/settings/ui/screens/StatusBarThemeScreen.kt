package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.ColorFieldRow
import brobata.physiboard.app.settings.ui.DividerLabel
import brobata.physiboard.app.settings.ui.KeyboardUiPreview
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.ReorderableMultiChoiceRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.BarButton
import brobata.physiboard.core.settings.StatusBarPrefs
import brobata.physiboard.core.settings.StripTheme
import brobata.physiboard.core.settings.StripThemePresets
import brobata.physiboard.core.strip.ButtonSlots
import brobata.physiboard.core.strip.StripSide

/**
 * "Theme", formerly "Status Bar Theme" (settings-catalog.md SS9.2, status-bar.md SS9.4). The "Keyboard UI Preview"
 * lives under [StripThemeScreen]'s doc comment (a render, not a setting). Left and right button
 * slots use [ReorderableMultiChoiceRow] rather than the plain multi-choice row: [StatusBarPrefs]
 * keeps `leftButtons`/`rightButtons` ordered (status-bar.md SS6.3, "the strip... renders every
 * entry in the list"), so a screen that could only turn buttons on and off would silently lose
 * the order the strip actually draws them in.
 */
@Composable
fun StatusBarThemeScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val controller = LocalSettingsController.current
    val statusBar = controller.current.value.statusBar
    fun set(transform: (StatusBarPrefs) -> StatusBarPrefs) = controller.update { it.copy(statusBar = transform(it.statusBar)) }
    var confirmReset by remember { mutableStateOf(false) }

    SettingsScreenScaffold(title = "Theme", onBack = onBack) {
        RowList {
            item { DividerLabel("Choose a preset") }
            item {
                ThemePresetRow(
                    savedThemeNames = statusBar.savedThemes.map { it.name },
                    activeTheme = statusBar.theme,
                    onApplyPreset = { theme -> set { p -> p.copy(theme = theme) } },
                    onApplySaved = { name ->
                        statusBar.savedThemes.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { named ->
                            set { p -> p.copy(theme = named.theme) }
                        }
                    },
                )
            }
            item { DividerLabel("Keyboard UI Preview") }
            item { KeyboardUiPreview(statusBar.theme) }
            item {
                SwitchRow("Show LEDs", checked = statusBar.theme.showLeds, onCheckedChange = { checked ->
                    set { p -> p.copy(theme = p.theme.copy(showLeds = checked)) }
                })
            }
            item { NavigateRow("Customize colors", onClick = { onNavigate(Routes.CUSTOMIZE_COLORS) }) }
            item { NavigateRow("Saved themes", "${statusBar.savedThemes.size} saved") { onNavigate(Routes.SAVED_THEMES) } }
            item { NavigateRow("Layout overrides", "A different theme per language or layout") { onNavigate(Routes.THEME_LAYOUT_OVERRIDES) } }
            item { DividerLabel("Buttons") }
            item {
                ReorderableMultiChoiceRow(
                    label = "Left buttons",
                    options = BarButton.entries,
                    optionLabel = ::barButtonLabel,
                    selected = statusBar.leftButtons,
                    onChange = { updated -> set { p -> p.copy(leftButtons = updated) } },
                )
            }
            item {
                ReorderableMultiChoiceRow(
                    label = "Right buttons",
                    options = BarButton.entries,
                    optionLabel = ::barButtonLabel,
                    selected = statusBar.rightButtons,
                    onChange = { updated -> set { p -> p.copy(rightButtons = updated) } },
                )
            }
            item {
                SingleChoiceChipsRow(
                    label = "Bar height",
                    description = "36 dp is below Android's minimum touch target size; pick it only if you type on the physical keys and just read the bar.",
                    options = listOf(36, 48, 56, 64),
                    optionLabel = { "$it dp" },
                    selected = statusBar.heightDp,
                    onSelect = { height -> set { p -> p.copy(heightDp = height) } },
                )
            }
            item { DividerLabel("Modifiers") }
            item {
                SwitchRow(
                    "Show modifiers at the cursor",
                    description = "Shift, Alt, Ctrl and Sym appear next to the text cursor whenever they are on. Only works in apps that report where the cursor is.",
                    checked = statusBar.caretModifierBadge,
                    onCheckedChange = { checked -> set { p -> p.copy(caretModifierBadge = checked) } },
                )
            }
            item {
                ColorFieldRow("One press colour", statusBar.caretBadgeArmedColor) { v -> set { p -> p.copy(caretBadgeArmedColor = v) } }
            }
            item {
                ColorFieldRow("Locked colour", statusBar.caretBadgeLockedColor) { v -> set { p -> p.copy(caretBadgeLockedColor = v) } }
            }
            item {
                ButtonRow(label = "Reset", buttonText = "Reset", onClick = { confirmReset = true })
            }
        }
    }

    // spec status-bar.md SS9.4 item 4, SS6.3, SS17: Reset "restores the slot defaults only"
    // (`ButtonSlots.reset()`, hamburger left, emoji and microphone right); everything else on this
    // page (visibility, apps, height, theme, colours, layout overrides) is left untouched, and the
    // destructive action asks for confirmation first (the app's own convention: "Reset to Default"
    // and the density "keep/revert" both confirm).
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset buttons?") },
            text = { Text("Puts the left and right button slots back to Menu, and Emoji plus Microphone. Nothing else on this page changes.") },
            confirmButton = {
                TextButton(onClick = {
                    val defaults = ButtonSlots.reset()
                    set { p ->
                        p.copy(
                            leftButtons = defaults.ids(StripSide.LEFT).mapNotNull(BarButton::fromId),
                            rightButtons = defaults.ids(StripSide.RIGHT).mapNotNull(BarButton::fromId),
                        )
                    }
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

/**
 * status-bar.md SS9.4 item 1: "a horizontal... row of cards, one per preset, then one per
 * user-saved theme... the active one is marked 'Active'." Drafts are not modelled in 3.0 (there is
 * no [brobata.physiboard.core.settings.StatusBarPrefs] field for one), so this row stops at
 * presets and saved themes.
 */
@Composable
private fun ThemePresetRow(
    savedThemeNames: List<String>,
    activeTheme: StripTheme,
    onApplyPreset: (StripTheme) -> Unit,
    onApplySaved: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        items(StripThemePresets.ALL, key = { "preset:${it.name}" }) { preset ->
            PresetCard(name = preset.name, active = preset.theme == activeTheme, onClick = { onApplyPreset(preset.theme) })
        }
        items(savedThemeNames, key = { "saved:$it" }) { name ->
            PresetCard(name = name, active = false, onClick = { onApplySaved(name) })
        }
    }
}

@Composable
private fun PresetCard(name: String, active: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp).width(104.dp).height(104.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(name, maxLines = 2)
            if (active) Text("Active")
        }
    }
}

private fun barButtonLabel(button: BarButton): String = when (button) {
    BarButton.CLIPBOARD -> "Clipboard"
    BarButton.EMOJI -> "Emoji"
    BarButton.MICROPHONE -> "Microphone"
    BarButton.LANGUAGE -> "Language"
    BarButton.HAMBURGER -> "Hamburger"
    BarButton.SETTINGS -> "Settings"
    BarButton.SYMBOLS -> "Symbols"
    BarButton.UNDO -> "Undo"
    BarButton.REDO -> "Redo"
}

