package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.WatchBrokerVerdict
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SectionHeader
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.toolbox.BloatCatalog
import brobata.physiboard.core.toolbox.BloatEntry
import brobata.physiboard.core.toolbox.BloatPresetInfo
import brobata.physiboard.core.toolbox.BloatState
import brobata.physiboard.core.toolbox.BloatTier
import brobata.physiboard.device.privileged.broker.BrokerVerdict
import brobata.physiboard.device.privileged.toolbox.AndroidDeviceProfile
import brobata.physiboard.device.privileged.toolbox.BloatCensusOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Remove bloat" (broker-privileged-toolbox.md SS12). Gated on the Titan 2 Elite device profile
 * and the verified broker status before anything else is drawn (SS12.1).
 */
@Composable
fun RemoveBloatScreen(onBack: () -> Unit, onNavigateToolbox: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val privileged = application.privileged
    val scope = rememberCoroutineScope()
    val isTitanElite = remember { AndroidDeviceProfile.isTitan2Elite() }
    // spec broker-privileged-toolbox.md SS5.2: a screen showing a verdict polls for it.
    WatchBrokerVerdict(privileged)
    val verdict by privileged.broker.verdict.collectAsState()

    var states by remember { mutableStateOf<Map<String, BloatState>>(emptyMap()) }
    var unrecognized by remember { mutableStateOf<List<String>>(emptyList()) }
    var journalCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var confirmUninstall by remember { mutableStateOf<BloatEntry?>(null) }
    var busyPackage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            loading = true
            when (val outcome = privileged.bloatRemover.census()) {
                is BloatCensusOutcome.Loaded -> {
                    states = outcome.result.states
                    unrecognized = outcome.result.unrecognizedVendorPackages
                }
                else -> Unit
            }
            journalCount = privileged.toolboxStore.journal().size
            loading = false
        }
    }

    LaunchedEffect(isTitanElite, verdict) { if (isTitanElite && verdict == BrokerVerdict.OK) refresh() }

    SettingsScreenScaffold(title = "Remove bloat", onBack = onBack) {
        when {
            !isTitanElite -> RowList {
                item {
                    Text(
                        "This list was written for the Titan 2 Elite and stays switched off on other phones, because it removes packages by name.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            verdict != BrokerVerdict.OK -> RowList {
                item {
                    Text("Needs the same wireless-debugging pairing as the keyboard backlight.", modifier = Modifier.padding(16.dp))
                }
                item { ButtonRow(label = "Set up pairing", buttonText = "Open", onClick = onNavigateToolbox) }
            }
            loading -> RowList { item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) } }
            else -> RowList {
                if (journalCount > 0) {
                    item {
                        ButtonRow(
                            label = "$journalCount package(s) changed by PhysiBoard",
                            buttonText = "Restore all",
                            onClick = { scope.launch(Dispatchers.IO) { privileged.bloatRemover.restoreAll(); refresh() } },
                        )
                    }
                }
                item { SectionHeader("Presets") }
                BloatCatalog.presets.forEach { preset ->
                    val active = BloatCatalog.entriesForPreset(preset.tag).filter { states[it.packageName] == BloatState.ACTIVE }
                    if (active.isNotEmpty()) {
                        item {
                            PresetCard(preset, active.size, busy = busyPackage != null) {
                                scope.launch(Dispatchers.IO) {
                                    active.forEach { entry -> privileged.bloatRemover.disable(entry.packageName, BloatState.ACTIVE) }
                                    refresh()
                                }
                            }
                        }
                    }
                }
                BloatTier.entries.forEach { tier ->
                    val entries = BloatCatalog.entries.filter { it.tier == tier && states[it.packageName] != BloatState.ABSENT }
                    if (entries.isNotEmpty()) {
                        item { SectionHeader(tier.header) }
                        item { Text(tier.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) }
                        entries.forEach { entry ->
                            item {
                                BloatRow(
                                    entry = entry,
                                    state = states[entry.packageName] ?: BloatState.ABSENT,
                                    busy = busyPackage == entry.packageName,
                                    onDisable = {
                                        busyPackage = entry.packageName
                                        scope.launch(Dispatchers.IO) {
                                            privileged.bloatRemover.disable(entry.packageName, states[entry.packageName] ?: BloatState.ACTIVE)
                                            refresh(); busyPackage = null
                                        }
                                    },
                                    onUninstall = { confirmUninstall = entry },
                                    onRestore = {
                                        busyPackage = entry.packageName
                                        scope.launch(Dispatchers.IO) {
                                            privileged.bloatRemover.restore(entry.packageName)
                                            refresh(); busyPackage = null
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                if (unrecognized.isNotEmpty()) {
                    item { SectionHeader("${unrecognized.size} vendor apps not in this list") }
                    item {
                        Text(
                            "They came from a later firmware, or this is not a Titan 2 Elite. Shown but never touched.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                    unrecognized.forEach { pkg -> item { Text(pkg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) } }
                }
            }
        }
    }

    confirmUninstall?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            title = { Text("Uninstall ${entry.label}?") },
            text = {
                Text(
                    "Disabling is usually enough: it hides the app and stops it running, and is instantly reversible. Uninstalling removes it for this user — PhysiBoard can still restore it because the app stays in system storage, but a firmware update may bring it back, and if wireless debugging is ever unavailable you cannot undo it from here.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val pkg = entry.packageName
                    confirmUninstall = null
                    busyPackage = pkg
                    scope.launch(Dispatchers.IO) {
                        privileged.bloatRemover.uninstall(pkg, states[pkg] ?: BloatState.ACTIVE)
                        refresh(); busyPackage = null
                    }
                }) { Text("Uninstall") }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PresetCard(preset: BloatPresetInfo, count: Int, busy: Boolean, onDisableAll: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(preset.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                preset.badge?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
            Text(preset.description, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onDisableAll, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) { Text("Disable all $count") }
        }
    }
}

@Composable
private fun BloatRow(entry: BloatEntry, state: BloatState, busy: Boolean, onDisable: () -> Unit, onUninstall: () -> Unit, onRestore: () -> Unit) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(entry.label, style = MaterialTheme.typography.bodyLarge)
        Text(entry.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stateLabel(state), style = MaterialTheme.typography.labelSmall)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            when (state) {
                BloatState.ACTIVE -> {
                    TextButton(onClick = onDisable, enabled = !busy) { Text("Disable") }
                    TextButton(onClick = onUninstall, enabled = !busy) { Text("Uninstall") }
                }
                BloatState.DISABLED -> {
                    TextButton(onClick = onRestore, enabled = !busy) { Text("Restore") }
                    TextButton(onClick = onUninstall, enabled = !busy) { Text("Uninstall") }
                }
                BloatState.UNINSTALLED -> TextButton(onClick = onRestore, enabled = !busy) { Text("Restore") }
                BloatState.ABSENT -> Unit
            }
        }
    }
}

private fun stateLabel(state: BloatState): String = when (state) {
    BloatState.ACTIVE -> "Active"
    BloatState.DISABLED -> "Disabled"
    BloatState.UNINSTALLED -> "Uninstalled"
    BloatState.ABSENT -> "Absent"
}
