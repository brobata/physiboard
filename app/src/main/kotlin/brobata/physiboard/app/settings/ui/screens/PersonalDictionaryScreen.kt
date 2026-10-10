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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    // Every edit is a change applied to the file as it is now, not this screen's copy written
    // over it: the list was read when the screen opened, and the keyboard may have saved a word
    // since. The list shows the change at once, then what the file holds after the write. The
    // writes run one at a time in the order they were made and finish even when the screen is
    // closed straight after (or, for Undo, when another snackbar replaces this one). The file's
    // contents replace the list only after the last queued write, so an edit still waiting to be
    // written never blinks back out of the list.
    val writes = remember { Mutex() }
    val queuedWrites = remember { intArrayOf(0) }

    suspend fun write(block: suspend () -> UserWordStore?) = withContext(NonCancellable) {
        queuedWrites[0]++
        writes.withLock {
            val written = try { block() } finally { queuedWrites[0]-- }
            if (written != null && queuedWrites[0] == 0) store = written
        }
    }

    fun persist(change: (UserWordStore) -> UserWordStore) {
        store = change(store)
        scope.launch(NonCancellable) { write { fileStore.updatePersonal(change) } }
    }

    fun persistDefaults(change: (List<WordFrequency>) -> List<WordFrequency>) {
        store = UserWordStore.of(change(store.defaultWords()), store.personalWords())
        scope.launch(NonCancellable) { write { fileStore.updateDefaults(change)?.let { UserWordStore.of(it, store.personalWords()) } } }
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
                    // count and last use, or its place among the default words). Like every edit
                    // here, the restore is applied to the file as it is now, so a word added in
                    // the meantime, here or by the keyboard, is kept.
                    IconButton(onClick = {
                        val word = row.word
                        if (row.isPersonal) {
                            val removed = store.personalWords().firstOrNull { it.word == word }
                            persist { it.withPersonalWordRemoved(word) }
                            if (removed != null) {
                                undo?.offer("dictionary-$word", "Deleted “$word”") {
                                    write { fileStore.updatePersonal { DictionaryUndo.restorePersonal(it, removed) } }
                                }
                            }
                        } else {
                            val defaults = store.defaultWords()
                            val index = defaults.indexOfFirst { it.word == word }
                            persistDefaults { words -> words.filterNot { it.word == word } }
                            if (index >= 0) {
                                val removed = defaults[index]
                                undo?.offer("dictionary-$word", "Deleted “$word”") {
                                    write {
                                        fileStore.updateDefaults { DictionaryUndo.restoreDefault(it, removed, index) }
                                            ?.let { words -> UserWordStore.of(words, store.personalWords()) }
                                    }
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
                val now = System.currentTimeMillis()
                persist { it.withPersonalWordAdded(newWord, now) }
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
                val oldWord = row.word
                if (row.isPersonal) {
                    persist { it.withPersonalWordRenamed(oldWord, newWord) }
                } else {
                    persistDefaults { words -> words.map { if (it.word == oldWord) it.copy(word = newWord) else it } }
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
