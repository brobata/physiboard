package it.palsoftware.pastiera.inputmethod

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.AppBroadcastActions
import it.palsoftware.pastiera.AutoCorrectionSubstitutionStore
import it.palsoftware.pastiera.LocalizedComponentActivity
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.core.suggestions.UserDictionaryStore

class AddSubstitutionActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableActivityAnimations()
        window.requestFeature(android.view.Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val replacement = intent.getStringExtra(EXTRA_REPLACEMENT)?.trim().orEmpty()
        val languageCode = intent.getStringExtra(EXTRA_LANGUAGE_CODE)?.trim().orEmpty()
        if (replacement.isBlank() || languageCode.isBlank()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AddSubstitutionSheet(
                        replacement = replacement,
                        onSave = { trigger, addToDictionary ->
                            val saved = AutoCorrectionSubstitutionStore.addCustomSubstitution(
                                this@AddSubstitutionActivity,
                                languageCode,
                                trigger,
                                replacement
                            )
                            if (saved && addToDictionary) {
                                addReplacementToUserDictionary(replacement)
                            }
                            Toast.makeText(
                                this@AddSubstitutionActivity,
                                if (saved) R.string.add_substitution_saved else R.string.add_substitution_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        disableActivityAnimations()
    }

    private fun disableActivityAnimations() {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun addReplacementToUserDictionary(word: String) {
        val store = UserDictionaryStore()
        store.loadUserEntries(this)
        val exists = store.getSnapshot().any { it.word.equals(word, ignoreCase = true) }
        if (!exists) {
            store.addWord(this, word)
            sendBroadcast(android.content.Intent(AppBroadcastActions.USER_DICTIONARY_UPDATED).apply {
                setPackage(packageName)
            })
        }
    }

    companion object {
        const val EXTRA_REPLACEMENT = "replacement"
        const val EXTRA_LANGUAGE_CODE = "language_code"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddSubstitutionSheet(
    replacement: String,
    onSave: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    var trigger by remember { mutableStateOf("") }
    var addToDictionary by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.add_substitution_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.add_substitution_replacement_label, replacement),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = trigger,
                onValueChange = { trigger = it },
                label = { Text(stringResource(R.string.add_substitution_trigger_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = addToDictionary,
                    onCheckedChange = { addToDictionary = it }
                )
                Text(
                    text = stringResource(R.string.add_substitution_also_dictionary),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.auto_correct_cancel))
                }
                Button(
                    onClick = { onSave(trigger, addToDictionary) },
                    enabled = trigger.isNotBlank()
                ) {
                    Text(stringResource(R.string.auto_correct_save))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
