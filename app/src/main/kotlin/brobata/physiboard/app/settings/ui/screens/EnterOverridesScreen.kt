package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.AppCatalog
import brobata.physiboard.app.settings.ui.InstalledApp
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.WideDialogProperties
import brobata.physiboard.app.settings.ui.toEnterOverride
import brobata.physiboard.app.settings.ui.wideDialog
import brobata.physiboard.core.settings.EnterOverrideRow
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.EnterOverrideResolver
import brobata.physiboard.core.text.EnterSendMethod
import brobata.physiboard.core.text.ExtraSendShortcut
import brobata.physiboard.core.text.FavouriteApp
import brobata.physiboard.core.text.FavouriteStatusCard
import brobata.physiboard.core.text.MessagingPreset

/** spec: per-app-behavior.md SS3.11's "Wanted behaviour" dropdown offer. */
private val OFFERED_BEHAVIORS = listOf(EnterBehavior.APP_DEFAULT, EnterBehavior.SEND_SHIFT_NEWLINE, EnterBehavior.NEWLINE_CTRL_SEND)

/**
 * "App overrides" (per-app-behavior.md SS3.11): favourites first, in favourite order, each with a
 * read-only status card behind a "Manual override" reveal ([FavouriteStatusCard]); any other
 * app's card shows the three choosers directly with a delete icon; a top-bar "+" opens the
 * two-step "Add app" dialog. Changing "Wanted behaviour" on any row flips the messaging preset to
 * Custom (SS3.11 point 4); send method and extra shortcut never do.
 */
@Composable
fun EnterOverridesScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val perApp = controller.current.value.perApp
    val context = LocalContext.current

    val installedFavourites = remember { FavouriteApp.ORDERED.filter { AppCatalog.isInstalled(context, it.packageName) } }
    val nonFavouriteRows = perApp.enterOverrides.filter { FavouriteApp.of(it.packageName) == null }
    val catalog = remember(nonFavouriteRows.map { it.packageName }) {
        AppCatalog.installedApps(context, alsoInclude = FavouriteApp.PACKAGE_NAMES + nonFavouriteRows.map { it.packageName }.toSet())
            .associateBy { it.packageName }
    }
    val sortedNonFavourites = nonFavouriteRows.sortedBy { (catalog[it.packageName]?.label ?: it.packageName).lowercase() }
    val overridesAsCore = remember(perApp.enterOverrides) { perApp.enterOverrides.map { it.toEnterOverride() } }

    fun updateRow(packageName: String, flipPresetToCustom: Boolean, transform: (EnterOverrideRow) -> EnterOverrideRow) {
        controller.update { settings ->
            val overrides = settings.perApp.enterOverrides
            val updated = if (overrides.any { it.packageName == packageName }) {
                overrides.map { if (it.packageName == packageName) transform(it) else it }
            } else {
                overrides + transform(EnterOverrideRow(packageName))
            }
            settings.copy(
                perApp = settings.perApp.copy(
                    enterOverrides = updated,
                    enterPreset = if (flipPresetToCustom) MessagingPreset.CUSTOM else settings.perApp.enterPreset,
                ),
            )
        }
    }

    fun removeRow(packageName: String) {
        controller.update { settings -> settings.copy(perApp = settings.perApp.copy(enterOverrides = settings.perApp.enterOverrides.filterNot { it.packageName == packageName })) }
    }

    var showAddDialog by remember { mutableStateOf(false) }

    SettingsScreenScaffold(
        title = "App overrides",
        onBack = onBack,
        trailingAction = {
            IconButton(onClick = { showAddDialog = true }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Add, contentDescription = "Add app")
            }
        },
    ) {
        RowList {
            items(installedFavourites, key = { it.packageName }) { fav ->
                FavouriteOverrideCard(
                    label = catalog[fav.packageName]?.label ?: fav.packageName,
                    fav = fav,
                    row = perApp.enterOverrides.firstOrNull { it.packageName == fav.packageName },
                    resolvedBehavior = EnterOverrideResolver.resolveBehavior(fav.packageName, overridesAsCore, perApp.enterPreset, perApp.enterBehaviorEnabled),
                    resolvedSendMethod = EnterOverrideResolver.resolveSendMethod(fav.packageName, overridesAsCore, perApp.enterBehaviorEnabled),
                    resolvedShortcut = EnterOverrideResolver.resolveExtraShortcut(fav.packageName, overridesAsCore, perApp.enterBehaviorEnabled),
                    onBehaviorChange = { value -> updateRow(fav.packageName, flipPresetToCustom = true) { it.copy(behavior = value) } },
                    onSendMethodChange = { value -> updateRow(fav.packageName, flipPresetToCustom = false) { it.copy(sendMethod = value) } },
                    onShortcutChange = { value -> updateRow(fav.packageName, flipPresetToCustom = false) { it.copy(extraSendShortcut = value) } },
                )
            }
            items(sortedNonFavourites, key = { it.packageName }) { row ->
                NonFavouriteOverrideCard(
                    label = catalog[row.packageName]?.label ?: row.packageName,
                    row = row,
                    onBehaviorChange = { value -> updateRow(row.packageName, flipPresetToCustom = true) { it.copy(behavior = value) } },
                    onSendMethodChange = { value -> updateRow(row.packageName, flipPresetToCustom = false) { it.copy(sendMethod = value) } },
                    onShortcutChange = { value -> updateRow(row.packageName, flipPresetToCustom = false) { it.copy(extraSendShortcut = value) } },
                    onRemove = { removeRow(row.packageName) },
                )
            }
            // spec SS3.11 point 5: the same dialog as the "+", for anyone who does not spot it.
            item(key = "add-app") {
                NavigateRow("Add app", "Any app on your phone - Messenger, Slack, a fork of one of these", icon = Icons.Outlined.Add) { showAddDialog = true }
            }
        }
    }

    if (showAddDialog) {
        val excluded = FavouriteApp.PACKAGE_NAMES + nonFavouriteRows.map { it.packageName }.toSet()
        val candidates = remember { AppCatalog.installedApps(context).filterNot { it.packageName in excluded } }
        AddAppDialog(
            candidates = candidates,
            onDismiss = { showAddDialog = false },
            onAdd = { packageName, behavior, sendMethod, shortcut ->
                updateRow(packageName, flipPresetToCustom = false) { EnterOverrideRow(packageName, behavior, sendMethod, shortcut) }
                showAddDialog = false
            },
        )
    }
}

/** spec SS3.11 point 4: "A favourite's card is not editable: it shows the app icon, label, package, and a status block, plus a 'Manual override' text button that reveals the three dropdowns". */
@Composable
private fun FavouriteOverrideCard(
    label: String,
    fav: FavouriteApp,
    row: EnterOverrideRow?,
    resolvedBehavior: EnterBehavior,
    resolvedSendMethod: EnterSendMethod,
    resolvedShortcut: ExtraSendShortcut,
    onBehaviorChange: (EnterBehavior) -> Unit,
    onSendMethodChange: (EnterSendMethod) -> Unit,
    onShortcutChange: (ExtraSendShortcut) -> Unit,
) {
    var manualOverrideRevealed by remember(fav.packageName) { mutableStateOf(false) }
    val status = FavouriteStatusCard.forRow(fav, resolvedBehavior)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(fav.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!manualOverrideRevealed) {
            Text(status.badge, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(status.line1, style = MaterialTheme.typography.bodyMedium)
            Text(status.line2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { manualOverrideRevealed = true }) { Text("Manual override") }
        } else {
            // spec SS3.11 point 4: the note goes with the revealed dropdowns, so it is not
            // repeated under every collapsed card.
            Text(
                "The curated strategy is usually the better default. Override only for app updates or special cases.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceChipsRow(
                label = "Wanted behaviour",
                options = OFFERED_BEHAVIORS,
                optionLabel = FavouriteStatusCard::behaviorLabel,
                selected = row?.behavior ?: resolvedBehavior,
                onSelect = onBehaviorChange,
            )
            SingleChoiceDropdownRow(
                label = "Send method",
                options = EnterSendMethod.entries,
                optionLabel = ::sendMethodLabel,
                selected = row?.sendMethod ?: resolvedSendMethod,
                onSelect = onSendMethodChange,
            )
            SingleChoiceChipsRow(
                label = "Extra shortcut",
                options = ExtraSendShortcut.entries,
                optionLabel = ::shortcutLabel,
                selected = row?.extraSendShortcut ?: resolvedShortcut,
                onSelect = onShortcutChange,
            )
        }
    }
}

/** spec SS3.11 point 4: "Any other app's card shows the three dropdowns directly and a red delete icon ('Remove app')." */
@Composable
private fun NonFavouriteOverrideCard(
    label: String,
    row: EnterOverrideRow,
    onBehaviorChange: (EnterBehavior) -> Unit,
    onSendMethodChange: (EnterSendMethod) -> Unit,
    onShortcutChange: (ExtraSendShortcut) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(row.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove app", tint = MaterialTheme.colorScheme.error)
            }
        }
        SingleChoiceChipsRow(
            label = "Wanted behaviour",
            options = OFFERED_BEHAVIORS,
            optionLabel = FavouriteStatusCard::behaviorLabel,
            selected = row.behavior,
            onSelect = onBehaviorChange,
        )
        SingleChoiceDropdownRow(
            label = "Send method",
            options = EnterSendMethod.entries,
            optionLabel = ::sendMethodLabel,
            selected = row.sendMethod,
            onSelect = onSendMethodChange,
        )
        SingleChoiceChipsRow(
            label = "Extra shortcut",
            options = ExtraSendShortcut.entries,
            optionLabel = ::shortcutLabel,
            selected = row.extraSendShortcut,
            onSelect = onShortcutChange,
        )
    }
}

/**
 * spec SS3.11's two-step "Add app" dialog: step 1 a searched, sorted list of every app not
 * already listed; step 2 the chosen app's label as the title, a note, and the three choosers
 * defaulting to `enter_send_shift_newline` / `auto` / `none`. "Back" returns to step 1 keeping the
 * query; "Cancel" (step 1) or dismissing closes without saving.
 */
@Composable
private fun AddAppDialog(
    candidates: List<InstalledApp>,
    onDismiss: () -> Unit,
    onAdd: (packageName: String, behavior: EnterBehavior, sendMethod: EnterSendMethod, shortcut: ExtraSendShortcut) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<InstalledApp?>(null) }
    var behavior by remember { mutableStateOf(EnterBehavior.SEND_SHIFT_NEWLINE) }
    var sendMethod by remember { mutableStateOf(EnterSendMethod.AUTO) }
    var shortcut by remember { mutableStateOf(ExtraSendShortcut.NONE) }

    val filtered = remember(candidates, query) {
        val sorted = candidates.sortedBy { it.label.lowercase() }
        if (query.isBlank()) sorted else sorted.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    val app = chosen
    if (app == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = WideDialogProperties,
            modifier = Modifier.wideDialog(),
            title = { Text("Add app") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(modifier = Modifier.heightIn(min = 240.dp, max = 420.dp).fillMaxWidth()) {
                        if (filtered.isEmpty()) {
                            Text("No apps match that search.", modifier = Modifier.padding(top = 16.dp))
                        } else {
                            LazyColumn {
                                items(filtered, key = { it.packageName }) { candidate ->
                                    Text(
                                        candidate.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget)
                                            .clickable { chosen = candidate }.padding(vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(app.label) },
            text = {
                Column {
                    Text(
                        "For additional apps this is a target configuration first. The curated messaging list uses tested strategies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceChipsRow(
                        label = "Wanted behaviour",
                        options = OFFERED_BEHAVIORS,
                        optionLabel = FavouriteStatusCard::behaviorLabel,
                        selected = behavior,
                        onSelect = { behavior = it },
                    )
                    SingleChoiceDropdownRow(
                        label = "Send method",
                        options = EnterSendMethod.entries,
                        optionLabel = ::sendMethodLabel,
                        selected = sendMethod,
                        onSelect = { sendMethod = it },
                    )
                    SingleChoiceChipsRow(
                        label = "Extra shortcut",
                        options = ExtraSendShortcut.entries,
                        optionLabel = ::shortcutLabel,
                        selected = shortcut,
                        onSelect = { shortcut = it },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onAdd(app.packageName, behavior, sendMethod, shortcut) }) { Text("Add app") } },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text("Back") } },
        )
    }
}

private fun sendMethodLabel(method: EnterSendMethod): String = when (method) {
    EnterSendMethod.AUTO -> "Auto"
    EnterSendMethod.EDITOR_ACTION -> "Editor action"
    EnterSendMethod.CTRL_ENTER -> "Ctrl+Enter"
    EnterSendMethod.PLAIN_ENTER -> "Plain Enter"
}

private fun shortcutLabel(shortcut: ExtraSendShortcut): String = when (shortcut) {
    ExtraSendShortcut.NONE -> "None"
    ExtraSendShortcut.SYM_ENTER -> "Sym + Enter"
}
