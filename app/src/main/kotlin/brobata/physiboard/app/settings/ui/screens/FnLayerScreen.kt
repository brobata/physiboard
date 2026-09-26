package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import brobata.physiboard.app.settings.ui.LocalSettingsController
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
    LaunchedEffect(Unit) { mappings = mappingStore.load() }

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
                Card(modifier = Modifier.padding(16.dp)) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
                        Text("Set Fn key to Ctrl", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (enabled) "Fn is set to Ctrl ✓" else "The vendor layer can synthesize Ctrl out of the Fn key. A reboot may be needed.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if (!enabled) {
                            TextButton(
                                onClick = {
                                    working = true
                                    scope.launch(Dispatchers.IO) {
                                        val outcome = remap.apply()
                                        enabled = remap.isEnabled()
                                        message = outcomeMessage(outcome)
                                        working = false
                                    }
                                },
                                enabled = !working,
                            ) { Text(if (working) "Working…" else "Set Fn → Ctrl") }
                        } else {
                            TextButton(
                                onClick = {
                                    working = true
                                    scope.launch(Dispatchers.IO) {
                                        val outcome = remap.resetToDefault()
                                        enabled = remap.isEnabled()
                                        message = outcomeMessage(outcome)
                                        working = false
                                    }
                                },
                                enabled = !working,
                            ) { Text(if (working) "Working…" else "Reset Fn key to default") }
                        }
                    }
                }
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
            if (keys.navModeEnabled) {
                item {
                    Text(
                        "Note: Modifying Fn Layer keys also affects Ctrl+key combinations in text fields.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
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
                ButtonRow(
                    label = "Revert to Default",
                    buttonText = "Revert",
                    onClick = { controller.update { it.copy(keys = KeyPrefs()) } },
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
            Row {
                row.forEach { letter ->
                    val mapped = mappings.mappingFor(KeyId.Letter(letter)) != CtrlMapping.None
                    Card(
                        modifier = Modifier
                            .padding(1.dp)
                            .width(32.dp)
                            .defaultMinSize(minHeight = 40.dp)
                            .then(Modifier.clickableCard { onKeyTapped(letter) }),
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text(letter.toString(), style = MaterialTheme.typography.labelMedium)
                            Text(if (mapped) "•" else "", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
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
    RevertOutcome.NEEDS_PERMISSION -> "Pair wireless debugging first, from T2E Tools."
}
