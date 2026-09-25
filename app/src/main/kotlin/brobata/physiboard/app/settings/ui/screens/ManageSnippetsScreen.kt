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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.actions.snippets.SnippetRules

/**
 * "Manage snippets" (expansion-clipboard-pickers-launcher.md SS2.7): each shortcut in medium
 * weight with its replacement below (newlines as ` ↵ `, at most two lines) and a delete icon; a
 * row tap edits, the plus adds, "No snippets yet." when empty. The editor dialog's validity rules
 * are [SnippetRules]'; the store's map is written whole, lowercased and trimmed on save (SS2.1),
 * and renaming a shortcut removes the old key.
 */
@Composable
fun ManageSnippetsScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val snippets = controller.current.value.expansion.snippets
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun save(map: Map<String, String>) = controller.update { it.copy(expansion = it.expansion.copy(snippets = SnippetRules.sanitize(map))) }

    SettingsScreenScaffold(
        title = "Manage snippets",
        onBack = onBack,
        trailingAction = {
            IconButton(onClick = { adding = true }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Add, contentDescription = "Add snippet")
            }
        },
    ) {
        if (snippets.isEmpty()) {
            Text("No snippets yet.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RowList {
            items(snippets.keys.sorted().size) { index ->
                val shortcut = snippets.keys.sorted()[index]
                val replacement = snippets.getValue(shortcut)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = MinTouchTarget)
                        .clickable { editing = shortcut }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(shortcut, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            replacement.replace("\n", " ↵ "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { save(snippets - shortcut) }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete $shortcut")
                    }
                }
            }
        }
    }

    if (adding) {
        SnippetEditorDialog(title = "Add snippet", initialShortcut = "", initialReplacement = "", onDismiss = { adding = false }) { shortcut, replacement ->
            save(snippets + (shortcut to replacement))
            adding = false
        }
    }
    editing?.let { original ->
        SnippetEditorDialog(title = "Edit snippet", initialShortcut = original, initialReplacement = snippets[original].orEmpty(), onDismiss = { editing = null }) { shortcut, replacement ->
            save((snippets - original) + (shortcut to replacement))
            editing = null
        }
    }
}

/** spec SS2.7: a single-line "Shortcut" field with its help and error, a 4 to 10 line "Replacement text" field, Save only when both are valid, Cancel discards. */
@Composable
private fun SnippetEditorDialog(title: String, initialShortcut: String, initialReplacement: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var shortcut by remember { mutableStateOf(initialShortcut) }
    var replacement by remember { mutableStateOf(initialReplacement) }
    val normalized = SnippetRules.normalizeShortcut(shortcut)
    val shortcutValid = SnippetRules.isValidShortcut(normalized)
    val canSave = shortcutValid && replacement.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    label = { Text("Shortcut") },
                    supportingText = { Text("Use 1–40 letters, numbers, or underscores.") },
                    isError = shortcut.isNotEmpty() && !shortcutValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("Replacement text") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(normalized, replacement) }, enabled = canSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
