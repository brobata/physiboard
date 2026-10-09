package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.FnLayerMappingStore
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.InfoText
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.CtrlMapping
import brobata.physiboard.core.keys.CtrlMappingCodec
import brobata.physiboard.core.keys.CtrlMappingTable
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.settings.KeyPrefs
import brobata.physiboard.device.privileged.setup.RevertOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Fn Layer" (settings-catalog.md SS9.2, keys-and-modifiers.md, trackpad-caret-nav.md SS5.8-5.9).
 * "Set Fn key to Ctrl" (keys-and-modifiers.md SS3.6) is wired to `:device:privileged`'s
 * [brobata.physiboard.device.privileged.setup.FnCtrlRemap]. The 26-key grid editor reads and
 * writes `ctrl_key_mappings.json` through [FnLayerMappingStore] (`:core:keys`'s
 * [CtrlMappingCodec] is the shared parser with the keyboard).
 *
 * SPEC GAP: SS5.8's key dialog lays out keycode and action choices as a labelled icon grid and
 * shows a layout-aware "would type Z" hint per key; this screen offers the same three
 * vocabularies (keycode name, action id, command id) as plain choice rows instead, since the icon
 * set and the active-layout lookup are presentation detail beyond what a first cut needs.
 */
@Composable
fun FnLayerScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val keys = controller.current.value.keys
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val remap = application.privileged.fnCtrlRemap
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(remap.isEnabled()) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val mappingStore = remember { FnLayerMappingStore(context) }
    var mappings by remember { mutableStateOf(CtrlMappingTable()) }
    var editingLetter by remember { mutableStateOf<Char?>(null) }

    LaunchedEffect(Unit) { enabled = remap.isEnabled() }
    LaunchedEffect(Unit) {
        val loaded = mappingStore.load(keys.navModeDefaultMappingsVersion)
        mappings = loaded
        // spec: keys-and-modifiers.md SS12.1: an install whose file predates a later default
        // (already backfilled into [loaded] by the store) gets that default written back, once,
        // so a plain reopen of this screen is also a chance to catch up, not only the keyboard's
        // own startup path (see [brobata.physiboard.ime.KeyboardSession]'s matching migration).
        if (keys.navModeDefaultMappingsVersion < brobata.physiboard.core.keys.CTRL_MAPPING_DEFAULTS_VERSION) {
            mappingStore.save(loaded)
            controller.update {
                it.copy(keys = it.keys.copy(navModeDefaultMappingsVersion = brobata.physiboard.core.keys.CTRL_MAPPING_DEFAULTS_VERSION))
            }
        }
    }

    SettingsScreenScaffold(title = "Fn layer", onBack = onBack) {
        RowList {
            plainItem {
                InfoText("Fn with a letter moves the cursor, selects and edits, like the arrow keys and Ctrl shortcuts on a computer. Tap a letter below to change what it does.")
            }
            header("Fn key")
            item {
                ButtonRow(
                    label = "Set Fn key to Ctrl",
                    description = message ?: if (enabled) "Fn is set to Ctrl." else "The phone can turn the Fn key into Ctrl. A restart may be needed.",
                    buttonText = if (working) "Working…" else if (enabled) "Undo" else "Set",
                    enabled = !working,
                    onClick = {
                        working = true
                        scope.launch(Dispatchers.IO) {
                            val outcome = if (enabled) remap.resetToDefault() else remap.apply()
                            enabled = remap.isEnabled()
                            message = outcomeMessage(outcome)
                            working = false
                        }
                    },
                )
            }
            header("Fn layer")
            item {
                SwitchRow(
                    label = "Fn layer",
                    checked = keys.navModeEnabled,
                    onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(navModeEnabled = checked)) } },
                )
            }
            item {
                SwitchRow(
                    label = "Holding Ctrl works like Fn",
                    description = "A held Ctrl uses the keys below instead of sending Ctrl shortcuts to the app.",
                    checked = keys.navModeCtrlHoldEnabled,
                    onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(navModeCtrlHoldEnabled = checked)) } },
                )
            }
            item {
                SwitchRow(
                    label = "Ctrl shortcuts follow the layout",
                    description = "On QWERTZ, Ctrl with the Y key sends Ctrl+Z.",
                    checked = keys.layoutAwareCtrlShortcuts,
                    onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(layoutAwareCtrlShortcuts = checked)) } },
                )
            }
            if (keys.navModeEnabled) {
                header("Keys")
                item {
                    InfoText("Changing a key here also changes Ctrl with that key in text boxes.")
                }
                item {
                    FnLayerKeyGrid(mappings = mappings, onKeyTapped = { letter -> editingLetter = letter })
                }
                item {
                    ButtonRow(
                        label = "Revert Fn Layer mappings",
                        buttonText = "Revert",
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                mappings = mappingStore.revertToDefault()
                                // spec: trackpad-caret-nav.md SS5.8, SS5.9: "Revert to Default"
                                // stamps `nav_mode_mappings_updated`, so the running keyboard
                                // reloads the map it now shares with the shipped asset again.
                                controller.update { it.copy(keys = it.keys.copy(navModeMappingsUpdatedAtMs = System.currentTimeMillis())) }
                            }
                        },
                    )
                }
            }
            item {
                // This used to reset every key setting (long press, accent lists, the bounce
                // filter) to its default from the Fn layer's own screen, with no warning; it now
                // resets only the switches on this screen.
                ButtonRow(
                    label = "Reset these switches",
                    buttonText = "Reset",
                    onClick = {
                        val defaults = KeyPrefs()
                        controller.update {
                            it.copy(
                                keys = it.keys.copy(
                                    navModeEnabled = defaults.navModeEnabled,
                                    navModeCtrlHoldEnabled = defaults.navModeCtrlHoldEnabled,
                                    layoutAwareCtrlShortcuts = defaults.layoutAwareCtrlShortcuts,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    editingLetter?.let { letter ->
        FnKeyEditDialog(
            letter = letter,
            current = mappings.mappingFor(KeyId.Letter(letter)),
            onDismiss = { editingLetter = null },
            onSave = { mapping ->
                val updated = mappings.copy(entries = mappings.entries + (KeyId.Letter(letter) to mapping))
                mappings = updated
                scope.launch(Dispatchers.IO) {
                    mappingStore.save(updated)
                    // spec: trackpad-caret-nav.md SS5.8, SS5.9: "Save... stamps
                    // `nav_mode_mappings_updated`", the signal `:ime` reloads the map on.
                    controller.update { it.copy(keys = it.keys.copy(navModeMappingsUpdatedAtMs = System.currentTimeMillis())) }
                }
                editingLetter = null
            },
        )
    }
}

private val FN_LAYER_ROWS: List<String> = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

@Composable
private fun FnLayerKeyGrid(mappings: CtrlMappingTable, onKeyTapped: (Char) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        FN_LAYER_ROWS.forEach { row ->
            // Every key takes a tenth of the width (the top row's count), so the keys fill the
            // screen like the real keyboard and stay above the 48 dp touch floor.
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { letter ->
                    val mapped = mappings.mappingFor(KeyId.Letter(letter)) != CtrlMapping.None
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(1.dp)
                            .defaultMinSize(minHeight = MinTouchTarget)
                            .then(Modifier.clickableCard { onKeyTapped(letter) }),
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text(letter.toString(), style = MaterialTheme.typography.labelMedium)
                            Text(if (mapped) "•" else "", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                val missing = FN_LAYER_ROWS.first().length - row.length
                if (missing > 0) Spacer(modifier = Modifier.weight(missing.toFloat()))
            }
        }
    }
}

private fun Modifier.clickableCard(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun FnKeyEditDialog(letter: Char, current: CtrlMapping, onDismiss: () -> Unit, onSave: (CtrlMapping) -> Unit) {
    var type by remember { mutableStateOf(mappingTypeOf(current)) }
    var keycodeName by remember { mutableStateOf((current as? CtrlMapping.Keycode)?.let { (it.key as? KeyId.Control)?.key?.name } ?: ControlKey.DPAD_UP.name) }
    var actionId by remember { mutableStateOf((current as? CtrlMapping.NamedAction)?.actionId ?: CtrlMappingCodec.ACTION_IDS.first()) }
    var commandId by remember { mutableStateOf((current as? CtrlMapping.Command)?.commandId.orEmpty()) }

    val keycodeNames = listOf("DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER", "TAB", "MOVE_HOME", "MOVE_END", "PAGE_UP", "PAGE_DOWN", "ESCAPE", "FORWARD_DEL")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure $letter") },
        text = {
            Column {
                SingleChoiceChipsRow(
                    label = "Type",
                    options = listOf("Keycode", "Action", "Native Ctrl", "Command", "None"),
                    optionLabel = { it },
                    selected = type,
                    onSelect = { type = it },
                )
                when (type) {
                    "Keycode" -> SingleChoiceChipsRow(label = "Keycode", options = keycodeNames, optionLabel = { it }, selected = keycodeName, onSelect = { keycodeName = it })
                    "Action" -> SingleChoiceChipsRow(label = "Action", options = CtrlMappingCodec.ACTION_IDS, optionLabel = { it }, selected = actionId, onSelect = { actionId = it })
                    "Command" -> OutlinedTextField(value = commandId, onValueChange = { commandId = it }, label = { Text("Command id") })
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mapping = when (type) {
                    "Keycode" -> keycodeNameToControlKey(keycodeName)?.let { CtrlMapping.Keycode(KeyId.Control(it)) } ?: CtrlMapping.None
                    "Action" -> CtrlMapping.NamedAction(actionId)
                    "Native Ctrl" -> CtrlMapping.NativeCtrl
                    "Command" -> if (commandId.isBlank()) CtrlMapping.None else CtrlMapping.Command(commandId.trim())
                    else -> CtrlMapping.None
                }
                onSave(mapping)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun mappingTypeOf(mapping: CtrlMapping): String = when (mapping) {
    is CtrlMapping.Keycode -> "Keycode"
    is CtrlMapping.NamedAction -> "Action"
    CtrlMapping.NativeCtrl -> "Native Ctrl"
    is CtrlMapping.Command -> "Command"
    CtrlMapping.None -> "None"
}

private fun keycodeNameToControlKey(name: String): ControlKey? = when (name) {
    "DPAD_UP" -> ControlKey.DPAD_UP
    "DPAD_DOWN" -> ControlKey.DPAD_DOWN
    "DPAD_LEFT" -> ControlKey.DPAD_LEFT
    "DPAD_RIGHT" -> ControlKey.DPAD_RIGHT
    "DPAD_CENTER" -> ControlKey.DPAD_CENTER
    "TAB" -> ControlKey.TAB
    "MOVE_HOME" -> ControlKey.MOVE_HOME
    "MOVE_END" -> ControlKey.MOVE_END
    "PAGE_UP" -> ControlKey.PAGE_UP
    "PAGE_DOWN" -> ControlKey.PAGE_DOWN
    "ESCAPE" -> ControlKey.ESCAPE
    "FORWARD_DEL" -> ControlKey.FORWARD_DELETE
    else -> null
}

private fun outcomeMessage(outcome: RevertOutcome): String? = when (outcome) {
    RevertOutcome.SUCCESS -> null
    RevertOutcome.FAILED -> "Could not change the Fn key setting."
    RevertOutcome.NEEDS_PERMISSION -> "Pair wireless debugging first, from Titan tools."
}
