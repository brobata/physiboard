package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.settings.LanguagePrefs

/** One `custom_input_styles` entry, parsed. spec: dictionaries-languages.md SS8.2's `locale:layout[:extra]` format. */
private data class InputStyleEntry(val locale: String, val layout: String, val extra: String?) {
    fun encoded(): String = if (extra.isNullOrBlank()) "$locale:$layout" else "$locale:$layout:$extra"
    val key: String get() = "$locale:$layout"
}

private fun parseEntry(raw: String): InputStyleEntry? {
    val parts = raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    return InputStyleEntry(parts[0], parts[1], parts.getOrNull(2))
}

private val LOCALE_PATTERN = Regex("^[a-zA-Z]{2,3}([_-][a-zA-Z]{2,3})?$")

/**
 * "Manage input styles" (dictionaries-languages.md SS8.2): add, edit and delete of
 * `custom_input_styles` rows, plus each row's suggestion-dictionary languages
 * (`input_style_suggestion_locales`, SS8.4). The screen edits only the user's own rows: SS8.2's
 * "System" rows (one per device locale, hide/show rather than add/delete) need the live device
 * locale list and Android subtype APIs `:app` does not otherwise touch from this settings layer,
 * so [LanguagePrefs.hiddenSystemInputStyles] stays in the schema unbound here.
 *
 * SPEC GAP: SS8.2's add/edit dialog offers a language dropdown sourced from "every language code
 * that has a dictionary in any tier" and a layout picker over the bundled and custom layout
 * files; neither list is reachable from this screen without duplicating `:core:dict`'s installed-
 * dictionary scan and a layout catalogue this build does not yet expose to `:app`. Locale and
 * layout are free-text fields here instead, validated against SS8.2's own locale regex; suggestion
 * languages are a comma-separated list of codes rather than SS8.2's per-language switch list.
 */
@Composable
fun InputStylesScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val languages = controller.current.value.languages
    fun set(transform: (LanguagePrefs) -> LanguagePrefs) = controller.update { it.copy(languages = transform(it.languages)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingRaw by remember { mutableStateOf<String?>(null) }

    val entries = remember(languages.inputStyles) { languages.inputStyles.mapNotNull { raw -> parseEntry(raw)?.let { raw to it } } }

    SettingsScreenScaffold(
        title = "Input styles",
        onBack = onBack,
        trailingAction = {
            IconButton(onClick = { showAddDialog = true }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Add, contentDescription = "Add Input Style")
            }
        },
    ) {
        RowList {
            items(entries, key = { it.first }) { (raw, entry) ->
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${entry.locale} - ${entry.layout}", modifier = Modifier.weight(1f))
                    IconButton(onClick = { editingRaw = raw }) { Text("Edit") }
                    IconButton(onClick = {
                        set { p ->
                            p.copy(
                                inputStyles = p.inputStyles - raw,
                                suggestionLocales = p.suggestionLocales - entry.key,
                            )
                        }
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete Input Style") }
                }
            }
        }
    }

    if (showAddDialog) {
        InputStyleEditDialog(
            title = "Add Input Style",
            initial = null,
            existingKeys = entries.map { it.second.key }.toSet(),
            initialSuggestions = "",
            onDismiss = { showAddDialog = false },
            onSave = { entry, suggestions ->
                set { p ->
                    p.copy(
                        inputStyles = p.inputStyles + entry.encoded(),
                        suggestionLocales = if (suggestions.isEmpty()) p.suggestionLocales else p.suggestionLocales + (entry.key to suggestions),
                    )
                }
                showAddDialog = false
            },
        )
    }

    editingRaw?.let { raw ->
        val entry = parseEntry(raw)
        if (entry != null) {
            InputStyleEditDialog(
                title = "Edit Input Style",
                initial = entry,
                existingKeys = entries.map { it.second.key }.toSet() - entry.key,
                initialSuggestions = languages.suggestionLocales[entry.key]?.joinToString(", ").orEmpty(),
                onDismiss = { editingRaw = null },
                onSave = { updated, suggestions ->
                    set { p ->
                        val withoutOld = p.suggestionLocales - entry.key
                        p.copy(
                            inputStyles = p.inputStyles.map { if (it == raw) updated.encoded() else it },
                            suggestionLocales = if (suggestions.isEmpty()) withoutOld else withoutOld + (updated.key to suggestions),
                        )
                    }
                    editingRaw = null
                },
            )
        }
    }
}

@Composable
private fun InputStyleEditDialog(
    title: String,
    initial: InputStyleEntry?,
    existingKeys: Set<String>,
    initialSuggestions: String,
    onDismiss: () -> Unit,
    onSave: (InputStyleEntry, List<String>) -> Unit,
) {
    var locale by remember { mutableStateOf(initial?.locale.orEmpty()) }
    var layout by remember { mutableStateOf(initial?.layout ?: "qwerty") }
    var suggestions by remember { mutableStateOf(initialSuggestions) }

    val localeValid = LOCALE_PATTERN.matches(locale.trim())
    val duplicate = localeValid && layout.isNotBlank() && "${locale.trim()}:${layout.trim()}" in existingKeys
    val error = when {
        locale.isBlank() -> null
        !localeValid -> "Invalid locale code format. Use format: xx_XX or xx"
        duplicate -> "This language and layout combination already exists"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(value = locale, onValueChange = { locale = it }, label = { Text("Locale (xx_XX or xx)") }, singleLine = true, isError = error != null, supportingText = { if (error != null) Text(error) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = layout, onValueChange = { layout = it }, label = { Text("Layout") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = suggestions, onValueChange = { suggestions = it }, label = { Text("Suggestion languages (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val suggestionList = suggestions.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(InputStyleEntry(locale.trim(), layout.trim(), initial?.extra), suggestionList)
                },
                enabled = locale.isNotBlank() && layout.isNotBlank() && localeValid && !duplicate,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
