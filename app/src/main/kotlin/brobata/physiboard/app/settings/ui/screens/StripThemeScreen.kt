package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.ColorFieldRow
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.settings.StripTheme
import kotlin.math.roundToInt

/**
 * "Customize colors" (settings-catalog.md SS9.2, status-bar.md SS9.1). The three geometry ratios
 * are Doubles in the schema; this screen edits them as a 0-200 slider standing for 0.00-2.00 so
 * they fit the "integer with a range" row type rather than adding a seventh row kind for one
 * screen. "Choose a preset" has no data in [StripTheme] to bind (3.0 does not model 2.x's preset
 * list) and the "Keyboard UI Preview" is a render, not a setting; both are left out. So are the
 * 2.x colour rows "Normal keys", "Special keys", "Cursor swipe", "Key popup" and "Key popup
 * selected": [StripTheme] was reduced to only the fields the strip itself uses
 * (status-bar.md SS9.1), and those five never made the cut.
 */
@Composable
fun StripThemeScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    fun theme() = controller.current.value.statusBar.theme
    fun set(transform: (StripTheme) -> StripTheme) =
        controller.update { it.copy(statusBar = it.statusBar.copy(theme = transform(it.statusBar.theme))) }

    SettingsScreenScaffold(title = "Customize colors", onBack = onBack) {
        RowList {
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
}
