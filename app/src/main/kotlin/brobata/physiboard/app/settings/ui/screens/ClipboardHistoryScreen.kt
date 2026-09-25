package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow

/**
 * "Clipboard history": the two rows 2.x never built (expansion-clipboard-pickers-launcher.md
 * SS3.3, SS3.6, and SS13 "Retention and enable settings rows: keep (build them)"). The enable flag
 * is read once by the keyboard service (SS3.1), so its description says so; the retention slider
 * carries the 2.x strings ("0 = never delete clips", "%d minutes").
 */
@Composable
fun ClipboardHistoryScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val expansion = controller.current.value.expansion

    SettingsScreenScaffold(title = "Clipboard history", onBack = onBack) {
        RowList {
            item {
                SwitchRow(
                    "Clipboard history",
                    description = "Keep copied text for the clipboard panel. Takes effect after the keyboard restarts.",
                    checked = expansion.clipboardHistoryEnabled,
                    onCheckedChange = { checked -> controller.update { it.copy(expansion = it.expansion.copy(clipboardHistoryEnabled = checked)) } },
                )
            }
            item {
                IntRangeRow(
                    label = "Clipboard Retention Time",
                    description = "0 = never delete clips. Pinned clips never expire.",
                    value = expansion.clipboardRetentionMinutes.coerceIn(0, 120).toInt(),
                    range = IntClosedRange(0, 120),
                    valueLabel = { if (it == 0) "Never" else "$it minutes" },
                    onValueChange = { value -> controller.update { it.copy(expansion = it.expansion.copy(clipboardRetentionMinutes = value.toLong())) } },
                )
            }
        }
    }
}
