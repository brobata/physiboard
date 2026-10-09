package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.app.settings.ui.TextFieldRow
import brobata.physiboard.core.actions.snippets.SnippetRules
import brobata.physiboard.core.settings.ExpansionPrefs
import brobata.physiboard.core.settings.SnippetPresentation

/**
 * "Text expansion" (settings-catalog.md SS8 fixes this to Extras, not Smart Features; SS9.2;
 * expansion-clipboard-pickers-launcher.md SS2.7), rows in the catalogue's order with "Manage
 * snippets" opening [Routes.MANAGE_SNIPPETS], the list editor [ExpansionPrefs.snippets] is written
 * through. "Enable snippets" ships off (SS2.8's own default and the project's default-ON rule).
 */
@Composable
fun TextExpansionScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
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
                    // The suggestion bar is gone (c61c240), so it is no longer offered. A setting
                    // restored from an older backup keeps its chip, so it shows and can be changed.
                    options = listOfNotNull(
                        SnippetPresentation.OFF,
                        SnippetPresentation.FLOATING_POPUP,
                        SnippetPresentation.SUGGESTION_BAR.takeIf { expansion.presentation == it },
                    ),
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
                NavigateRow("Manage snippets", "Add global shortcuts and multiline replacement text.") { onNavigate(Routes.MANAGE_SNIPPETS) }
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

/**
 * spec: expansion-clipboard-pickers-launcher.md SS2.7. Delegates to the engine's own
 * [SnippetRules.isValidPrefix] instead of restating a weaker copy of the rule here: a screen that
 * only checked "not whitespace, not a colon" let a letter or digit save with no error, while
 * `SnippetRules.isValidPrefix` (SS2.1: "not whitespace, not a letter or digit, and not a colon")
 * silently falls back to `!` for that exact prefix at runtime, so the row showed a value that did
 * nothing.
 */
private fun isValidSnippetPrefix(char: String): Boolean = SnippetRules.isValidPrefix(char)

private fun presentationLabel(presentation: SnippetPresentation): String = when (presentation) {
    SnippetPresentation.OFF -> "Off"
    SnippetPresentation.FLOATING_POPUP -> "Floating popup"
    SnippetPresentation.SUGGESTION_BAR -> "Suggestion bar (removed)"
}
