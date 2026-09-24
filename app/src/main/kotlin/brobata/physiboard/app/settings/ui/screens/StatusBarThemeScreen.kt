package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.ColorFieldRow
import brobata.physiboard.app.settings.ui.DividerLabel
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MultiChoiceRow
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.PerAppListKind
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.BarButton
import brobata.physiboard.core.settings.StatusBarPrefs
import brobata.physiboard.core.settings.StatusBarVisibility

/**
 * "Status Bar Theme" (settings-catalog.md SS9.2, status-bar.md). "Choose a preset" and the
 * "Keyboard UI Preview" live under [StripThemeScreen]'s doc comment (no data / not a setting).
 * The saved-theme list (add/duplicate/export/import/delete) and the per-layout override editor
 * are left out: both need a list editor beyond this app's six row types, and the per-layout
 * override map is dropped from the 3.0 schema outright (settings-catalog.md SS13). Left and
 * right button slots bind as a set (which buttons are on, not their order): [StatusBarPrefs]
 * keeps `leftButtons`/`rightButtons` ordered, but the "multi choice" row type this app defines
 * has no notion of order, so re-ordering a slot is not available from this screen.
 */
@Composable
fun StatusBarThemeScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val controller = LocalSettingsController.current
    val statusBar = controller.current.value.statusBar
    fun set(transform: (StatusBarPrefs) -> StatusBarPrefs) = controller.update { it.copy(statusBar = transform(it.statusBar)) }

    SettingsScreenScaffold(title = "Status Bar Theme", onBack = onBack) {
        RowList {
            item {
                SwitchRow("Show LEDs", checked = statusBar.theme.showLeds, onCheckedChange = { checked ->
                    set { p -> p.copy(theme = p.theme.copy(showLeds = checked)) }
                })
            }
            item { NavigateRow("Customize colors", onClick = { onNavigate(Routes.CUSTOMIZE_COLORS) }) }
            item { DividerLabel("Buttons") }
            item {
                MultiChoiceRow(
                    label = "Left buttons",
                    options = BarButton.entries,
                    optionLabel = ::barButtonLabel,
                    selected = statusBar.leftButtons.toSet(),
                    onToggle = { button, checked ->
                        set { p -> p.copy(leftButtons = if (checked) p.leftButtons + button else p.leftButtons - button) }
                    },
                )
            }
            item {
                MultiChoiceRow(
                    label = "Right buttons",
                    options = BarButton.entries,
                    optionLabel = ::barButtonLabel,
                    selected = statusBar.rightButtons.toSet(),
                    onToggle = { button, checked ->
                        set { p -> p.copy(rightButtons = if (checked) p.rightButtons + button else p.rightButtons - button) }
                    },
                )
            }
            item { DividerLabel("Show status bar") }
            item {
                SingleChoiceChipsRow(
                    label = "Visibility",
                    options = listOf(StatusBarVisibility.ALWAYS, StatusBarVisibility.NEVER, StatusBarVisibility.APPS),
                    optionLabel = ::visibilityLabel,
                    selected = statusBar.visibility,
                    onSelect = { v -> set { p -> p.copy(visibility = v) } },
                )
            }
            if (statusBar.visibility == StatusBarVisibility.APPS) {
                item { NavigateRow("Choose apps", onClick = { onNavigate(Routes.appPicker(PerAppListKind.STATUS_BAR_APPS)) }) }
            }
            item {
                SingleChoiceChipsRow(
                    label = "Bar height",
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
                ButtonRow(label = "Reset", buttonText = "Reset", onClick = { controller.update { it.copy(statusBar = StatusBarPrefs()) } })
            }
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

private fun visibilityLabel(visibility: StatusBarVisibility): String = when (visibility) {
    StatusBarVisibility.ALWAYS -> "Always"
    StatusBarVisibility.NEVER -> "Never"
    StatusBarVisibility.APPS -> "Only in these apps"
}
