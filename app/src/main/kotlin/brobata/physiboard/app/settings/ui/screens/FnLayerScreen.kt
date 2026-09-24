package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.KeyPrefs

/**
 * "Fn Layer" (settings-catalog.md SS9.2, keys-and-modifiers.md). The "Set Fn key to Ctrl" card
 * and the 26-key per-key editor are left out: the card writes a system key mapping (privileged,
 * owned by `:device:privileged`) and the per-key editor has no field in [KeyPrefs] to bind to
 * (see this module's report). `longPressMode`/`longPressThresholdMs` are kept in the schema but
 * the catalogue never gives them a reachable screen either, so they stay unbound here too.
 */
@Composable
fun FnLayerScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val keys = controller.current.value.keys

    SettingsScreenScaffold(title = "Fn Layer", onBack = onBack) {
        RowList {
            item {
                Text(
                    "Fn Layer turns the physical Fn key into arrow-key and Ctrl-shortcut navigation. See keys-and-modifiers.md.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                SwitchRow(
                    label = "Enable Fn Layer",
                    checked = keys.navModeEnabled,
                    onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(navModeEnabled = checked)) } },
                )
            }
            item {
                SwitchRow(
                    label = "Ctrl-hold navigation",
                    checked = keys.navModeCtrlHoldEnabled,
                    onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(navModeCtrlHoldEnabled = checked)) } },
                )
            }
            item {
                SwitchRow(
                    label = "Layout-aware app Ctrl shortcuts",
                    checked = keys.layoutAwareCtrlShortcuts,
                    onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(layoutAwareCtrlShortcuts = checked)) } },
                )
            }
            item {
                ButtonRow(
                    label = "Revert to Default",
                    buttonText = "Revert",
                    onClick = { controller.update { it.copy(keys = KeyPrefs()) } },
                )
            }
        }
    }
}
