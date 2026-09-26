package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.MultiChoiceRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold

/** spec: text-input.md SS6.3/6.6, SS15: the fixed alphabet, in the order every stored subset must keep. */
private const val PUNCTUATION_ALPHABET = ".,;:!?/\")]}"

/** spec: text-input.md SS15, the row's own label for each candidate character. */
private fun candidateLabel(char: Char): String = "\"$char\""

/**
 * Keeps [current]'s membership rule for the fixed alphabet order (text-input.md SS15: "subset of
 * `.,;:!?\/")]}` in that order"), toggling [char] on or off. Building the result by filtering the
 * alphabet, rather than appending/removing in place, is what keeps the stored string in the
 * spec's canonical order regardless of which row the user checked first.
 */
private fun toggled(current: String, char: Char, on: Boolean): String {
    val members = current.toSet().let { if (on) it + char else it - char }
    return PUNCTUATION_ALPHABET.filter { it in members }
}

/**
 * The "Punctuation spacing" dialog (settings-catalog.md SS9.2, text-input.md SS6.3/6.6/SS15),
 * rendered as its own screen rather than a dialog so DPAD/Enter can reach every row (rebuild-
 * from-scratch.md's hardware-navigation requirement). One row per candidate character with two
 * checkboxes ("Remove before", "Before next text"), a Reset button that empties both lists, and
 * a help dialog explaining what "Remove before" actually promises.
 */
@Composable
fun PunctuationSpacingScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val typing = controller.current.value.typing
    var showHelp by remember { mutableStateOf(false) }

    SettingsScreenScaffold(
        title = "Punctuation spacing",
        onBack = onBack,
        trailingAction = {
            Row {
                IconButton(onClick = { showHelp = true }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = "How punctuation spacing works")
                }
                IconButton(
                    onClick = {
                        controller.update { it.copy(typing = it.typing.copy(removeSpaceBefore = "", spaceBeforeNextText = "")) }
                    },
                    modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset")
                }
            }
        },
    ) {
        RowList {
            item {
                MultiChoiceRow(
                    label = "Remove before",
                    description = "Removes the space right before the character when it is one PhysiBoard inserted",
                    options = PUNCTUATION_ALPHABET.toList(),
                    optionLabel = ::candidateLabel,
                    selected = typing.removeSpaceBefore.toSet(),
                    onToggle = { char, on ->
                        controller.update { it.copy(typing = it.typing.copy(removeSpaceBefore = toggled(typing.removeSpaceBefore, char, on))) }
                    },
                )
            }
            item {
                MultiChoiceRow(
                    label = "Before next text",
                    description = "Inserts a space before whatever is typed right after the character",
                    options = PUNCTUATION_ALPHABET.toList(),
                    optionLabel = ::candidateLabel,
                    selected = typing.spaceBeforeNextText.toSet(),
                    onToggle = { char, on ->
                        controller.update { it.copy(typing = it.typing.copy(spaceBeforeNextText = toggled(typing.spaceBeforeNextText, char, on))) }
                    },
                )
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("How punctuation spacing works") },
            text = {
                Text(
                    "\"Remove before\" only removes a space PhysiBoard inserted, not one typed " +
                        "manually, in a restricted field (URL, email, filter, password). In a " +
                        "normal text field every typed space after a word is marked, so it is " +
                        "removed there too.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Close") }
            },
        )
    }
}
