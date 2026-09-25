package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.DeviceSetupCard
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SettingsSearchField

/**
 * "T2E Tools" (settings-catalog.md SS9.2, broker-privileged-toolbox.md SS3). The device setup
 * card is the hub's one home for pairing (device-backlight-ring.md SS11 Keep/Drop); every other
 * screen's "Set up pairing" link routes back here rather than re-embedding it.
 */
@Composable
fun T2EToolsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    SettingsScreenScaffold(title = "T2E Tools", onBack = onBack) {
        SettingsSearchField(onSettingsRoot = false, onNavigate = onNavigate)
        RowList {
            item {
                Column {
                    Text(
                        "Titan-specific tools. These change the phone itself rather than the keyboard, so anything here that outlives an uninstall can be undone with Reset device settings to stock.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                    DeviceSetupCard()
                }
            }
            t2eToolsRows(onNavigate)
        }
    }
}

private fun LazyListScope.t2eToolsRows(onNavigate: (String) -> Unit) {
    item {
        NavigateRow(
            label = "Smart keyboard backlight",
            description = "Keep the keyboard lit in the dark, past the 30s limit",
            onClick = { onNavigate(Routes.SMART_BACKLIGHT) },
        )
    }
    item {
        NavigateRow(
            label = "Remove bloat",
            description = "Disable or uninstall the Titan's own factory and vendor apps",
            onClick = { onNavigate(Routes.REMOVE_BLOAT) },
        )
    }
    item {
        NavigateRow(
            label = "Screen density",
            description = "Fit more on screen, or make everything bigger",
            onClick = { onNavigate(Routes.SCREEN_DENSITY) },
        )
    }
    item {
        NavigateRow(
            label = "System tweaks",
            description = "Animation speed, notification history, one-handed mode",
            onClick = { onNavigate(Routes.SYSTEM_TWEAKS) },
        )
    }
    item {
        NavigateRow(
            label = "Notification ring",
            description = "A glow around the camera hole while the screen is off",
            onClick = { onNavigate(Routes.NOTIFICATION_RING) },
        )
    }
    item {
        NavigateRow(
            label = "Screen trackpad",
            description = "Hold a key and swipe anywhere on the screen to move the cursor",
            onClick = { onNavigate(Routes.SCREEN_TRACKPAD) },
        )
    }
    item {
        NavigateRow(
            label = "Key mapping",
            description = "What every key does, in the firmware and in PhysiBoard",
            onClick = { onNavigate(Routes.KEY_MAPPING) },
        )
    }
}
