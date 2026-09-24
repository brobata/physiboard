package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SettingsSearchField

/**
 * "T2E Tools" (settings-catalog.md SS9.2). Another agent owns the broker pairing, the backlight
 * and the ring under `:ime` and `:device:privileged`, so "Smart keyboard backlight" and
 * "Notification ring" are placeholders here (rebuild-from-scratch.md). "Remove bloat", "Screen
 * density", "System tweaks" and "Key mapping" have no field in `:core:settings`' typed schema and
 * belong to that same privileged toolbox, so this screen leaves them out rather than binding a
 * row to nothing; the device setup card is privileged UI for the same reason.
 */
@Composable
fun T2EToolsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    SettingsScreenScaffold(title = "T2E Tools", onBack = onBack) {
        SettingsSearchField(onSettingsRoot = false, onNavigate = onNavigate)
        RowList { t2eToolsRows(onNavigate) }
    }
}

private fun LazyListScope.t2eToolsRows(onNavigate: (String) -> Unit) {
    item {
        NavigateRow(
            label = "Smart keyboard backlight",
            description = "Keep the keyboard lit in the dark, past the 30s limit",
            onClick = { onNavigate(Routes.placeholder("Smart keyboard backlight")) },
        )
    }
    item {
        NavigateRow(
            label = "Notification ring",
            description = "A glow around the camera hole while the screen is off",
            onClick = { onNavigate(Routes.placeholder("Notification ring")) },
        )
    }
    item {
        NavigateRow(
            label = "Screen trackpad",
            description = "Hold a key and swipe anywhere on the screen to move the cursor",
            onClick = { onNavigate(Routes.SCREEN_TRACKPAD) },
        )
    }
}
