package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.settings.CorrectionPrefs
import brobata.physiboard.core.settings.SubstitutionSet

/**
 * "Manage text replacements" > Text Replacements (settings-catalog.md SS9.2: the system language,
 * other languages, "Custom Substitutions"). Lists every language code with a custom set
 * ([CorrectionPrefs.customSubstitutions], the `auto_correct_custom_<code>` rows) plus the enabled
 * replacement languages, and lets a code be added; each row opens [CustomSubstitutionsEditScreen].
 */
@Composable
fun CustomSubstitutionsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val controller = LocalSettingsController.current
    val correction = controller.current.value.correction
    var adding by remember { mutableStateOf(false) }
    val codes = (correction.customSubstitutions.keys + correction.textReplacementLanguages).distinct().sorted()

    SettingsScreenScaffold(
        title = "Text Replacements",
        onBack = onBack,
        trailingAction = {
            IconButton(onClick = { adding = true }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Add, contentDescription = "Add language")
            }
        },
    ) {
        if (codes.isEmpty()) Text("No custom substitutions yet. Tap + to add a language, then its replacements.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        RowList {
            items(codes.size) { index ->
                val code = codes[index]
                val set = correction.customSubstitutions[code]
                val name = set?.displayName?.takeIf { it.isNotBlank() } ?: code
                NavigateRow(name, "${set?.rules?.size ?: 0} custom substitutions") { onOpen(code) }
            }
        }
    }

    if (adding) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add language") },
            text = { OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Language code") }, supportingText = { Text("For example en, de or it") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(
                    enabled = code.trim().isNotEmpty(),
                    onClick = {
                        val key = code.trim().lowercase()
                        controller.update { s ->
                            val sets = s.correction.customSubstitutions
                            s.copy(correction = s.correction.copy(customSubstitutions = if (key in sets) sets else sets + (key to SubstitutionSet())))
                        }
                        adding = false
                        onOpen(key)
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

/** "Custom Substitutions" for one language: every `wrong: right` rule, "Add Correction", tap to edit, delete icon. */
@Composable
fun CustomSubstitutionsEditScreen(code: String, onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val set = controller.current.value.correction.customSubstitutions[code] ?: SubstitutionSet()
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun save(rules: Map<String, String>) = controller.update { s ->
        s.copy(correction = s.correction.copy(customSubstitutions = s.correction.customSubstitutions + (code to set.copy(rules = rules))))
    }

    SettingsScreenScaffold(
        title = set.displayName.takeIf { it.isNotBlank() } ?: "Custom Substitutions ($code)",
        onBack = onBack,
        trailingAction = {
            IconButton(onClick = { adding = true }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Add, contentDescription = "Add Correction")
            }
        },
    ) {
        if (set.rules.isEmpty()) Text("No custom substitutions yet.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        RowList {
            val triggers = set.rules.keys.sorted()
            items(triggers.size) { index ->
                val trigger = triggers[index]
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).clickable { editing = trigger }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trigger, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(set.rules.getValue(trigger), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { save(set.rules - trigger) }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete $trigger")
                    }
                }
            }
        }
    }

    if (adding) {
        CorrectionDialog("Add Correction", "", "", onDismiss = { adding = false }) { wrong, right ->
            save(set.rules + (wrong to right))
            adding = false
        }
    }
    editing?.let { original ->
        CorrectionDialog("Edit Correction", original, set.rules[original].orEmpty(), onDismiss = { editing = null }) { wrong, right ->
            save((set.rules - original) + (wrong to right))
            editing = null
        }
    }
}

@Composable
private fun CorrectionDialog(title: String, initialWrong: String, initialRight: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var wrong by remember { mutableStateOf(initialWrong) }
    var right by remember { mutableStateOf(initialRight) }
    val trigger = wrong.trim().lowercase()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = wrong, onValueChange = { wrong = it }, label = { Text("Typed") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = right, onValueChange = { right = it }, label = { Text("Replacement") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(trigger, right) }, enabled = trigger.isNotEmpty() && trigger != "__name" && right.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
