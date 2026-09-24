package brobata.physiboard.app.settings.ui.screens

import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.TextFieldRow
import androidx.compose.runtime.Composable

private const val PUNCTUATION_ALPHABET = ".,;:!?/\")]}"

/**
 * The "Punctuation spacing" dialog (settings-catalog.md SS9.2, text-input.md SS6.3/6.6), rendered
 * as its own screen rather than a dialog so DPAD/Enter can reach both fields (rebuild-from-
 * scratch.md's hardware-navigation requirement). Both rows hold a subset of `.,;:!?\/")]}` in
 * that order (text-input.md); this screen accepts free text and lets the typed value stand,
 * consistent with the "text" row type having no built-in "subset of an alphabet" validator.
 */
@Composable
fun PunctuationSpacingScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val typing = controller.current.value.typing

    SettingsScreenScaffold(title = "Punctuation spacing", onBack = onBack) {
        RowList {
            item {
                TextFieldRow(
                    label = "Remove before",
                    description = "Characters from \"$PUNCTUATION_ALPHABET\" that eat the space before them",
                    value = typing.removeSpaceBefore,
                    onValueChange = { value -> controller.update { it.copy(typing = it.typing.copy(removeSpaceBefore = value)) } },
                )
            }
            item {
                TextFieldRow(
                    label = "Before next text",
                    description = "Characters from \"$PUNCTUATION_ALPHABET\" that get a space inserted before whatever comes next",
                    value = typing.spaceBeforeNextText,
                    onValueChange = { value -> controller.update { it.copy(typing = it.typing.copy(spaceBeforeNextText = value)) } },
                )
            }
        }
    }
}
