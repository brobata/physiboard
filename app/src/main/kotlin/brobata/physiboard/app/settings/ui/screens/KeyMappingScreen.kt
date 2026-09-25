package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
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
                Text(
                    "Bindings live in two places that never talk to each other: rows the firmware reads before any app sees the key, and PhysiBoard's own handling. This is both, per key — including the ones bound to nothing.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            rows.forEach { row -> item { KeyMappingRowView(row, onNavigate) } }
            item {
                Text(
                    "Keys with an arrow can be changed. The rest are fixed by the phone or the hardware.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun KeyMappingRowView(row: KeyMappingRow, onNavigate: (String) -> Unit) {
    val opensRoute = row.opensRoute
    if (opensRoute != null) {
        NavigateRow(label = row.label, description = "${row.bindingText} · ${row.hardwareText}", onClick = { onNavigate(routeFor(opensRoute)) })
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge)
            Text(row.bindingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(row.hardwareText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The route id [KeyMappingInventory] hands back is a literal matching [Routes]'s constants (that module cannot depend on this one). */
private fun routeFor(id: String): String = when (id) {
    KeyMappingInventory.ROUTE_FN_LAYER -> Routes.FN_LAYER
    KeyMappingInventory.ROUTE_VOICE -> Routes.VOICE
    KeyMappingInventory.ROUTE_SCREEN_TRACKPAD -> Routes.SCREEN_TRACKPAD
    else -> Routes.SETTINGS
}
