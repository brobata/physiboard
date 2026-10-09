package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.DictionaryWordRow
import brobata.physiboard.app.settings.UserWordFileStore
import brobata.physiboard.app.settings.mergedRows
import brobata.physiboard.app.settings.ui.DictionaryUndo
import brobata.physiboard.app.settings.ui.LocalUndo
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SearchPill
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.dict.isValidNewDictionaryWord
import kotlinx.coroutines.launch

/**
 * "Personal dictionary" (autocorrect-suggestions.md SS6.3, dictionaries-languages.md SS7): list,
 * add, edit and delete of the user's own words plus the default word set, both tiers of
 * `:core:dict`'s [UserWordStore]. The screen owns its own file-backed state (through
 * [UserWordFileStore]) rather than routing through [brobata.physiboard.app.settings.ui.SettingsController],
 * because this is user content in its own file, the same one `:ime` will read (see
 * `UserWordFileStore`'s KDoc).
 */
@Composable
fun PersonalDictionaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fileStore = remember { UserWordFileStore(context) }
    val scope = rememberCoroutineScope()

    var store by remember { mutableStateOf(UserWordStore.empty()) }
    var query by rememberSaveable { mutableStateOf("") }
    val undo = LocalUndo.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DictionaryWordRow?>(null) }

    LaunchedEffect(Unit) { store = fileStore.load() }

    fun persist(newStore: UserWordStore) {
        store = newStore
        scope.launch { fileStore.savePersonal(newStore) }
    }

    fun persistDefaults(words: List<WordFrequency>) {
        store = UserWordStore.of(words, store.personalWords())
        scope.launch { fileStore.saveDefaults(words) }
    }

    val rows = remember(store, query) {
        val merged = store.mergedRows()
        if (query.isBlank()) merged else merged.filter { it.word.contains(query, ignoreCase = true) }
    }

    SettingsScreenScaffold(
        title = "Personal dictionary",
        onBack = onBack,
        trailingAction = {
            IconButton(onClick = { showAddDialog = true }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Add, contentDescription = "Add word")
            }
        },
    ) {
        RowList {
            plainItem {
                SearchPill(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search words",
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            }
            items(rows, key = { "${it.isPersonal}:${it.word}" }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.word, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editing = row }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    // app-shell.md SS22.4: deleted at once; Undo puts the word back exactly (its
                    // count and last use, or its place among the default words). The restore reads
                    // the file again, so a word added in the meantime is kept.
                    IconButton(onClick = {
                        if (row.isPersonal) {
                            val removed = store.personalWords().firstOrNull { it.word == row.word }
                            persist(store.withPersonalWordRemoved(row.word))
                            if (removed != null) {
                                undo?.offer("dictionary-${row.word}", "Deleted “${row.word}”") {
                                    val restored = DictionaryUndo.restorePersonal(fileStore.load(), removed)
                                    if (fileStore.savePersonal(restored)) store = restored
                                }
                            }
                        } else {
                            val defaults = store.defaultWords()
                            val index = defaults.indexOfFirst { it.word == row.word }
                            persistDefaults(defaults.filterNot { it.word == row.word })
                            if (index >= 0) {
                                val removed = defaults[index]
                                undo?.offer("dictionary-${row.word}", "Deleted “${row.word}”") {
                                    val current = fileStore.load()
                                    val words = DictionaryUndo.restoreDefault(current.defaultWords(), removed, index)
                                    if (fileStore.saveDefaults(words)) store = UserWordStore.of(words, current.personalWords())
                                }
                            }
                        }
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${row.word}") }
                }
            }
        }
    }

    if (showAddDialog) {
        WordEditDialog(
            title = "Add word",
            initialText = "",
            onDismiss = { showAddDialog = false },
            onSave = { newWord ->
                persist(store.withPersonalWordAdded(newWord, System.currentTimeMillis()))
                showAddDialog = false
            },
        )
    }

    editing?.let { row ->
        WordEditDialog(
            title = "Edit word",
            initialText = row.word,
            onDismiss = { editing = null },
            onSave = { newWord ->
                if (row.isPersonal) {
                    persist(store.withPersonalWordRenamed(row.word, newWord))
                } else {
                    persistDefaults(store.defaultWords().map { if (it.word == row.word) it.copy(word = newWord) else it })
                }
                editing = null
            },
        )
    }
}

@Composable
private fun WordEditDialog(title: String, initialText: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = isValidNewDictionaryWord(text)) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
