package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardCapslock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.AboutExpander
import brobata.physiboard.app.settings.ui.InfoText
import brobata.physiboard.app.settings.ui.KeycapIcon
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.RowMinHeight
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.core.toolbox.KeyMappingInventory
import brobata.physiboard.core.toolbox.KeyMappingRow

/**
 * "Key mapping" (broker-privileged-toolbox.md SS15): a read-only inventory, read once, needing no
 * broker. Rows with an editor navigate; the rest are fixed by the phone or the hardware.
 */
@Composable
fun KeyMappingScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val controller = LocalSettingsController.current
    val rows = remember { application.privileged.keyMapping.read(controller.current.value) }

    SettingsScreenScaffold(title = "Key mapping", onBack = onBack) {
        RowList {
            item {
                InfoText("What each key does, both in the phone's own settings and in PhysiBoard. Keys with an arrow can be changed.")
                AboutExpander(
                    title = "About key mapping",
                    text = "Bindings live in two places that never talk to each other: rows the firmware reads before any app sees the key, and PhysiBoard's own handling. This is both, per key, including the ones bound to nothing. Keys without an arrow are fixed by the phone or the hardware.",
                )
            }
            header("Keys")
            rows.forEach { row -> item { KeyMappingRowView(row, onNavigate) } }
        }
    }
}

@Composable
private fun KeyMappingRowView(row: KeyMappingRow, onNavigate: (String) -> Unit) {
    val opensRoute = row.opensRoute
    val icon = iconFor(row.label)
    if (opensRoute != null) {
        NavigateRow(label = row.label, description = "${row.bindingText} · ${row.hardwareText}", icon = icon, onClick = { onNavigate(routeFor(opensRoute)) })
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = RowMinHeight).padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeycapIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.l)) {
                Text(row.label, style = MaterialTheme.typography.bodyLarge)
                Text(row.bindingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(row.hardwareText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A glyph per key; the labels are [KeyMappingInventory]'s own. */
private fun iconFor(label: String): ImageVector = when (label) {
    "Fn" -> Icons.Outlined.Functions
    "Hold Sym: assistant" -> Icons.Outlined.EmojiSymbols
    "Orange side key" -> Icons.Outlined.Circle
    "Space" -> Icons.Outlined.SpaceBar
    "Right Shift" -> Icons.Outlined.KeyboardCapslock
    "Home" -> Icons.Outlined.Circle
    "Recent apps" -> Icons.Outlined.ViewAgenda
    "Back" -> Icons.AutoMirrored.Outlined.ArrowBack
    "Volume up / down" -> Icons.AutoMirrored.Outlined.VolumeUp
    "Power" -> Icons.Outlined.PowerSettingsNew
    else -> Icons.Outlined.Keyboard
}

/** The route id [KeyMappingInventory] hands back is a literal matching [Routes]'s constants (that module cannot depend on this one). */
private fun routeFor(id: String): String = when (id) {
    KeyMappingInventory.ROUTE_FN_LAYER -> Routes.FN_LAYER
    KeyMappingInventory.ROUTE_VOICE -> Routes.VOICE
    KeyMappingInventory.ROUTE_SCREEN_TRACKPAD -> Routes.SCREEN_TRACKPAD
    else -> Routes.KEYS
}
