package brobata.physiboard.app.settings.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.UserWordFileStore
import brobata.physiboard.core.actions.picker.AddSubstitutionSheet
import brobata.physiboard.core.dict.isValidNewDictionaryWord
import brobata.physiboard.core.settings.SubstitutionSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Add substitution" (autocorrect-suggestions.md SS8.5): a transparent, animation-free sheet
 * (matching [LauncherAssignmentActivity]'s own pattern) opened by long-pressing the strip's
 * add-word candidate. Stores `shortcut (trimmed, lowercased) -> word` at the front of the custom
 * substitution set for [AddSubstitutionSheet.EXTRA_LANGUAGE_CODE], adds that language to the
 * enabled list if it was not already there, and optionally adds the word to the personal
 * dictionary. Tapping outside, or Cancel, dismisses without saving.
 */
class AddSubstitutionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        val word = intent.getStringExtra(AddSubstitutionSheet.EXTRA_WORD).orEmpty()
        if (word.isBlank()) {
            finish()
            return
        }
        val languageCode = intent.getStringExtra(AddSubstitutionSheet.EXTRA_LANGUAGE_CODE)?.trim().let { if (it.isNullOrBlank()) "it" else it }

        setContent {
            val app = application as PhysiBoardApplication
            val scope = rememberCoroutineScope()
            var shortcut by remember { mutableStateOf("") }
            var addToDictionary by remember { mutableStateOf(true) }
            val focusRequester = remember { FocusRequester() }

            // spec SS8.5: "a 'Shortcut' field (focused after 120 ms)".
            LaunchedEffect(Unit) {
                delay(120)
                runCatching { focusRequester.requestFocus() }
            }

            val trigger = shortcut.trim().lowercase()
            val validTrigger = trigger.isNotEmpty() && trigger != "__name"

            // spec: app-shell.md SS22.1. This activity has no other content (a transparent,
            // animation-free sheet holding just this dialog), but the dialog itself must still
            // read the app's own colour scheme and typography rather than Compose's defaults.
            PhysiBoardTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Add substitution") },
                    text = {
                        Column {
                            Text("Replacement: $word")
                            OutlinedTextField(
                                value = shortcut,
                                onValueChange = { shortcut = it },
                                label = { Text("Shortcut") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focusRequester),
                            )
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = addToDictionary, onCheckedChange = { addToDictionary = it })
                                Text("Also add replacement to dictionary")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = validTrigger,
                            onClick = {
                                scope.launch {
                                    val saved = runCatching {
                                        app.settingsStore.update { settings ->
                                            val existing = settings.correction.customSubstitutions[languageCode] ?: SubstitutionSet()
                                            val rules = LinkedHashMap<String, String>()
                                            rules[trigger] = word
                                            rules.putAll(existing.rules.filterKeys { it != trigger })
                                            val languages = if (languageCode in settings.correction.textReplacementLanguages) {
                                                settings.correction.textReplacementLanguages
                                            } else {
                                                settings.correction.textReplacementLanguages + languageCode
                                            }
                                            settings.copy(
                                                correction = settings.correction.copy(
                                                    customSubstitutions = settings.correction.customSubstitutions + (languageCode to existing.copy(rules = rules)),
                                                    textReplacementLanguages = languages,
                                                ),
                                            )
                                        }
                                    }.isSuccess
                                    if (saved && addToDictionary && isValidNewDictionaryWord(word)) {
                                        runCatching {
                                            val fileStore = UserWordFileStore(this@AddSubstitutionActivity)
                                            fileStore.savePersonal(fileStore.load().withPersonalWordAdded(word, System.currentTimeMillis()))
                                        }
                                    }
                                    Toast.makeText(
                                        this@AddSubstitutionActivity,
                                        if (saved) "Substitution saved" else "Could not save substitution",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    finish()
                                }
                            },
                        ) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Cancel") } },
                )
            }
        }
    }
}
