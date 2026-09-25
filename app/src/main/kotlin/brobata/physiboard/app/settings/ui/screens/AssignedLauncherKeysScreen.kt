package brobata.physiboard.app.settings.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.core.actions.launcher.AssignableKeys
import brobata.physiboard.core.actions.launcher.AssignmentSheet
import brobata.physiboard.core.actions.launcher.LauncherShortcuts
import brobata.physiboard.core.actions.launcher.ShortcutEntry
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.ime.actions.AndroidCommandCatalog
import androidx.compose.foundation.Image
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold

/**
 * "Assigned launcher keys" (expansion-clipboard-pickers-launcher.md SS6.5): a fixed QWERTY grid
 * of square keys in three rows, the second and third centred; an assigned key shows the app's
 * icon, a magnifier for the quick launcher, or the command's glyph over a short label; tap opens
 * the assignment sheet for that key with launching skipped. Drag-to-swap is dropped (SS13).
 * Opening the screen prunes entries whose app is gone and applies the Space default on a store
 * never written (SS6.1).
 */
@Composable
fun AssignedLauncherKeysScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val context = LocalContext.current
    val launcher = controller.current.value.launcher
    val catalog = remember { AndroidCommandCatalog(context) }
    val shortcuts = remember(launcher.assignedKeysJson) {
        LauncherShortcuts.parse(launcher.assignedKeysJson).applyDefault(defaultAlreadyAssigned = launcher.assignedKeysJson.isNotBlank()).shortcuts
    }

    LaunchedEffect(Unit) {
        controller.update { s ->
            val parsed = LauncherShortcuts.parse(s.launcher.assignedKeysJson).applyDefault(defaultAlreadyAssigned = s.launcher.assignedKeysJson.isNotBlank()).shortcuts
            val pruned = parsed.pruneUninstalled { pkg -> catalog.isInstalled(pkg) }
            s.copy(launcher = s.launcher.copy(assignedKeysJson = LauncherShortcuts.encode(pruned)))
        }
    }

    val sheet = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun open(keycode: Int) {
        sheet.launch(
            Intent(AssignmentSheet.ACTION_ASSIGN_KEY).apply {
                setPackage(context.packageName)
                putExtra(AssignmentSheet.EXTRA_KEY_CODE, keycode)
                putExtra(AssignmentSheet.EXTRA_SKIP_LAUNCH, true)
            },
        )
    }

    SettingsScreenScaffold(title = "Assigned launcher keys", onBack = onBack) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Tap a key to assign or replace a command. Assigned keys are shared by both trigger modes." +
                    (shortcuts.quickLauncherKeycode?.let { " Quick launcher is currently assigned to ${AssignableKeys.label(it)}." } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            AssignableKeys.GRID_ROWS.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    row.forEach { key ->
                        val keycode = AssignableKeys.keycodeOf(key)!!
                        KeyCell(key, shortcuts[keycode], catalog, modifier = Modifier.weight(1f)) { open(keycode) }
                    }
                    // Centring the shorter rows: an empty cell's width for each key short of ten.
                    repeat(10 - row.size) { Box(modifier = Modifier.weight(0.5f)) }
                }
            }
        }
    }
}

@Composable
private fun KeyCell(key: KeyId, entry: ShortcutEntry?, catalog: AndroidCommandCatalog, modifier: Modifier, onClick: () -> Unit) {
    val assigned = entry != null
    val color = if (assigned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val label = AssignableKeys.label(AssignableKeys.keycodeOf(key)!!)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = entry?.takeIf { !it.isQuickLauncher }?.displayPackage?.let { pkg -> remember(pkg) { catalog.appIcon(pkg)?.toBitmap(96, 96)?.asImageBitmap() } }
        when {
            entry == null -> Text(label, style = MaterialTheme.typography.titleMedium)
            entry.isQuickLauncher -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", style = MaterialTheme.typography.titleMedium)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
            icon != null -> Image(bitmap = icon, contentDescription = entry.title, modifier = Modifier.size(36.dp))
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(entry.title.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
            }
        }
    }
}
