package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.app.settings.ui.TextFieldRow
import brobata.physiboard.core.settings.ExpansionPrefs
import brobata.physiboard.core.settings.SnippetPresentation

/**
 * "Text expansion" (settings-catalog.md SS8 fixes this to Extras, not Smart Features; SS9.2;
 * expansion-clipboard-pickers-launcher.md SS2.7). "Manage snippets" needs a list editor beyond
 * this app's six row types and is left unbound; so is [ExpansionPrefs.snippets] itself, since it
 * is only ever written through that list.
 */
@Composable
fun TextExpansionScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val expansion = controller.current.value.expansion
    fun set(transform: (ExpansionPrefs) -> ExpansionPrefs) = controller.update { it.copy(expansion = transform(it.expansion)) }

    SettingsScreenScaffold(title = "Text expansion", onBack = onBack) {
        RowList {
            item {
                SwitchRow(
                    "Enable snippets",
                    description = "Expand global text snippets after a shared prefix.",
                    checked = expansion.snippetsEnabled,
                    onCheckedChange = { set { p -> p.copy(snippetsEnabled = it) } },
                )
            }
            item {
                TextFieldRow(
                    label = "Snippet prefix",
                    description = "One printable symbol. A colon is reserved for emoji and symbol shortcodes.",
                    value = expansion.snippetPrefix,
                    onValueChange = { text ->
                        val last = text.lastOrNull()?.toString()
                        if (last != null && isValidSnippetPrefix(last)) set { p -> p.copy(snippetPrefix = last) }
                    },
                    validate = { if (it.isNotEmpty() && !isValidSnippetPrefix(it.last().toString())) "Choose one non-whitespace symbol other than a colon." else null },
                )
            }
            item {
                SingleChoiceChipsRow(
                    label = "Show matches in",
                    options = listOf(SnippetPresentation.OFF, SnippetPresentation.FLOATING_POPUP, SnippetPresentation.SUGGESTION_BAR),
                    optionLabel = ::presentationLabel,
                    selected = expansion.presentation,
                    onSelect = { value -> set { p -> p.copy(presentation = value) } },
                )
            }
            item {
                SwitchRow(
                    "Accept with Tab",
                    description = "Use Tab to accept the highlighted or unique exact match.",
                    checked = expansion.acceptWithTab,
                    onCheckedChange = { set { p -> p.copy(acceptWithTab = it) } },
                )
            }
            item {
                SwitchRow(
                    "Accept with Enter",
                    description = "Use Enter to accept the highlighted or unique exact match.",
                    checked = expansion.acceptWithEnter,
                    onCheckedChange = { set { p -> p.copy(acceptWithEnter = it) } },
                )
            }
            item {
                SwitchRow(
                    "Expand exact match with Space",
                    description = "Press Space to expand a unique exact shortcut and keep the trailing space. Prefix matches are ignored.",
                    checked = expansion.expandExactOnSpace,
                    onCheckedChange = { set { p -> p.copy(expandExactOnSpace = it) } },
                )
            }
            item {
                SwitchRow(
                    "Accept prefix match with Space",
                    description = "Press Space to accept the highlighted match when the typed shortcut is not an exact match.",
                    checked = expansion.acceptPrefixWithSpace,
                    onCheckedChange = { set { p -> p.copy(acceptPrefixWithSpace = it) } },
                )
            }
        }
    }
}

private fun isValidSnippetPrefix(char: String): Boolean = char.length == 1 && char[0] != ':' && !char[0].isWhitespace()

private fun presentationLabel(presentation: SnippetPresentation): String = when (presentation) {
    SnippetPresentation.OFF -> "Off"
    SnippetPresentation.FLOATING_POPUP -> "Floating popup"
    SnippetPresentation.SUGGESTION_BAR -> "Suggestion bar"
}
