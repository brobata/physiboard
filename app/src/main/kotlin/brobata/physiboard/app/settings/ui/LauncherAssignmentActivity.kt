package brobata.physiboard.app.settings.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.core.actions.commands.Command
import brobata.physiboard.core.actions.commands.CommandSource
import brobata.physiboard.core.actions.commands.LaunchSpec
import brobata.physiboard.core.actions.launcher.AssignableKeys
import brobata.physiboard.core.actions.launcher.AssignmentSheet
import brobata.physiboard.core.actions.launcher.LauncherShortcuts
import brobata.physiboard.core.actions.launcher.ShortcutEntry
import brobata.physiboard.ime.actions.AndroidCommandCatalog
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * The assignment sheet (expansion-clipboard-pickers-launcher.md SS6.4): a transparent,
 * animation-free activity holding a modal sheet with the key's chip, a search toggle, source
 * chips, a "Remove" chip when the key is assigned, and the command grid. Answers the IME's
 * `brobata.physiboard.action.ASSIGN_LAUNCHER_KEY` intent with the spec's `key_code` and
 * `skip_launch` extras; result 1 "assigned", 2 "removed". The content and its ordering are
 * [AssignmentSheet]'s; the store write is this activity's, since only `:app` holds the store.
 *
 * SS6.2/SS6.4's "when the sheet was opened by a key press the command also runs immediately" is
 * honoured for apps and intents, which this process can start directly, and for an internal
 * PhysiBoard action or a nav-mode command (the quick launcher, Ctrl+letter and the rest) by
 * asking the keyboard to run it, via [launchNow]'s [AssignmentSheet.ACTION_RUN_COMMAND_NOW]
 * broadcast, since only the running session has a quick launcher or an input connection to run
 * one against.
 */
class LauncherAssignmentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        overridePendingTransition(0, 0)
        val keycode = intent.getIntExtra(AssignmentSheet.EXTRA_KEY_CODE, -1)
        val skipLaunch = intent.getBooleanExtra(AssignmentSheet.EXTRA_SKIP_LAUNCH, false)
        if (keycode < 0) {
            finish()
            return
        }
        val application = application as PhysiBoardApplication
        val store = application.settingsStore
        val catalog = AndroidCommandCatalog(this)

        setContent {
            PhysiBoardTheme {
                // The store is a DataStore: reading or writing it blocks. This sheet opens from a
                // hardware key press and must not stall the main thread while it does (2026-09-25
                // review); the assignment is read into state and every write runs in the scope.
                val scope = rememberCoroutineScope()
                // Read the way the keyboard reads it (ImeSettings): with the Space default applied, so a
                // fresh install's Space shows the quick launcher it really runs, and can be removed.
                val existing by produceState<ShortcutEntry?>(initialValue = null, keycode) {
                    val json = store.current().launcher.assignedKeysJson
                    value = LauncherShortcuts.parse(json).applyDefault(defaultAlreadyAssigned = json.isNotBlank()).shortcuts[keycode]
                }
                AssignmentSheetContent(
                    keyLabel = AssignableKeys.label(keycode),
                    keycode = keycode,
                    commands = AssignmentSheet.candidates(catalog.build()),
                    catalog = catalog,
                    current = existing,
                    onClose = { finishWith(Activity.RESULT_CANCELED) },
                    onRemove = {
                        scope.launch {
                            store.update { s -> s.copy(launcher = s.launcher.copy(assignedKeysJson = LauncherShortcuts.encode(LauncherShortcuts.parse(s.launcher.assignedKeysJson).remove(keycode)))) }
                            finishWith(AssignmentSheet.RESULT_REMOVED)
                        }
                    },
                    onChoose = { command ->
                        scope.launch {
                            store.update { s ->
                                val current = LauncherShortcuts.parse(s.launcher.assignedKeysJson).applyDefault(defaultAlreadyAssigned = s.launcher.assignedKeysJson.isNotBlank()).shortcuts
                                s.copy(launcher = s.launcher.copy(assignedKeysJson = LauncherShortcuts.encode(current.assign(keycode, ShortcutEntry.of(command)))))
                            }
                            if (!skipLaunch) launchNow(command)
                            finishWith(AssignmentSheet.RESULT_ASSIGNED)
                        }
                    },
                )
            }
        }
    }

    private fun finishWith(result: Int) {
        setResult(result)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun launchNow(command: Command) {
        // spec SS6.2/SS6.4: an `InternalAction`/`NavAction` command needs the running keyboard's
        // own session (its quick launcher, its input connection), which this process does not
        // have; it asks the keyboard to run it instead, the same way it asked the keyboard to
        // open this sheet in the first place.
        if (command.launch is LaunchSpec.InternalAction || command.launch is LaunchSpec.NavAction) {
            sendBroadcast(
                Intent(AssignmentSheet.ACTION_RUN_COMMAND_NOW).apply {
                    setPackage(packageName)
                    putExtra(AssignmentSheet.EXTRA_COMMAND_ID, command.id)
                },
            )
            return
        }
        val intent = when (val launch = command.launch) {
            is LaunchSpec.AppPackage -> packageManager.getLaunchIntentForPackage(launch.packageName)
            is LaunchSpec.IntentUri -> Intent(launch.action).apply {
                launch.data?.let { data = android.net.Uri.parse(it) }
                launch.packageName?.let { setPackage(it) }
                launch.categories.forEach { addCategory(it) }
            }
            else -> null
        }
        if (intent == null) {
            Toast.makeText(this, "Command not available", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }.onFailure { Toast.makeText(this, "Could not open app", Toast.LENGTH_SHORT).show() }
    }
}

@Composable
private fun AssignmentSheetContent(
    keyLabel: String,
    keycode: Int,
    commands: List<Command>,
    catalog: AndroidCommandCatalog,
    current: ShortcutEntry?,
    onClose: () -> Unit,
    onRemove: () -> Unit,
    onChoose: (Command) -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<CommandSource?>(null) }
    val key = AssignableKeys.keyOf(keycode)
    val ordered = remember(query, source, commands) { AssignmentSheet.order(commands, key, query, source) }
    val sources = remember(commands) { AssignmentSheet.sourcesPresent(commands) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f).navigationBarsPadding().clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Box(modifier = Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline).align(Alignment.CenterHorizontally))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(AssignmentSheet.TITLE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    AssistChip(onClick = {}, label = { Text(keyLabel) })
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                // spec SS6.4: what the key does now, so holding an assigned key (SS6.2 D) shows what
                // a choice replaces and what "Remove" takes away.
                if (current != null) {
                    Text(AssignmentSheet.currentLabel(current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { searching = !searching }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    FilterChip(colors = terminalChipColors(), selected = source == null, onClick = { source = null }, label = { Text(AssignmentSheet.ALL_CHIP) })
                    sources.forEach { s -> FilterChip(colors = terminalChipColors(), selected = source == s, onClick = { source = s }, label = { Text(s.label) }) }
                    if (current != null) FilterChip(colors = terminalChipColors(), selected = false, onClick = onRemove, label = { Text(AssignmentSheet.REMOVE_CHIP, color = MaterialTheme.colorScheme.error) })
                }
                if (searching) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text(AssignmentSheet.SEARCH_HINT) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (ordered.isEmpty()) {
                    Text(if (query.isBlank()) AssignmentSheet.EMPTY else AssignmentSheet.emptyForQuery(query), modifier = Modifier.padding(16.dp))
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        var lastSource: CommandSource? = null
                        for (command in ordered) {
                            if (source == null && query.isBlank() && command.source != lastSource) {
                                lastSource = command.source
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(command.source.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            item { CommandCell(command, catalog) { onChoose(command) } }
                        }
                    }
                }
            }
        }
    }
}

/** spec SS6.4: cells of aspect ratio 0.85 with the command's icon above its single-line label. */
@Composable
private fun CommandCell(command: Command, catalog: AndroidCommandCatalog, onClick: () -> Unit) {
    val icon = command.iconPackage?.let { pkg -> remember(pkg) { catalog.appIcon(pkg)?.toBitmap(96, 96)?.asImageBitmap() } }
    Column(
        modifier = Modifier.aspectRatio(0.85f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp)) else Text(CommandGlyphs.glyph(command.icon), style = MaterialTheme.typography.headlineSmall)
        Text(command.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}
