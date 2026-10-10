package brobata.physiboard.app.settings.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.ExpandableSection
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.keys.AltBackspaceAction
import brobata.physiboard.core.settings.TypingPrefs
import brobata.physiboard.core.text.DashStyle
import brobata.physiboard.core.text.SmartQuoteStyle

/**
 * "Typing" (formerly "Smart Features"; settings-catalog.md SS9.2, text-input.md): what PhysiBoard
 * does to the text as you type it, apart from correcting words (that is Autocorrect & words).
 * Capitals, punctuation, what Backspace deletes and text expansion, in that order: the things
 * that change every sentence first. The typographic extras (quotes, dashes, the comma space, the
 * per-character spacing lists) open behind "More punctuation", collapsed.
 *
 * "Show keyboard automatically" (`auto_show_keyboard`), the "Currency Symbol" chips
 * (`physical_keyboard_currency_symbol`) and "Quotes inside words" (`mid_word_quote_to_apostrophe`)
 * are dropped for 3.0 (there is no soft keyboard on the Titan).
 */
@Composable
fun TypingScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val controller = LocalSettingsController.current
    val settings = controller.current.value
    val typing = settings.typing
    val expansion = settings.expansion
    fun set(transform: (TypingPrefs) -> TypingPrefs) = controller.update { it.copy(typing = transform(it.typing)) }

    SettingsScreenScaffold(title = "Typing", onBack = onBack) {
        RowList {
            header("Capitals")
            item {
                SwitchRow("At the start of a text box", checked = typing.capitalizeAtTextStart, onCheckedChange = { set { p -> p.copy(capitalizeAtTextStart = it) } })
            }
            item {
                SwitchRow("After a full stop, ? or !", checked = typing.capitalizeAfterSentenceEnd, onCheckedChange = { set { p -> p.copy(capitalizeAfterSentenceEnd = it) } })
            }
            if (typing.capitalizeAtTextStart) {
                // text-input.md SS6: shown only while the first row is on.
                item {
                    SwitchRow(
                        "In web address and email boxes too",
                        description = "Never in password boxes or Terminal mode apps.",
                        checked = typing.capitalizeRestrictedFields,
                        onCheckedChange = { set { p -> p.copy(capitalizeRestrictedFields = it) } },
                    )
                }
            }
            header("Punctuation")
            item {
                SwitchRow("Double Space types a full stop", checked = typing.doubleSpaceToPeriod, onCheckedChange = { set { p -> p.copy(doubleSpaceToPeriod = it) } })
            }
            item {
                ExpandableSection("More punctuation") {
                    SwitchRow("Space after a comma", checked = typing.commaSpace, onCheckedChange = { set { p -> p.copy(commaSpace = it) } })
                    SwitchRow(
                        "Spaced hyphen becomes a dash",
                        description = "\"a - b\" becomes \"a – b\".",
                        checked = typing.spacedHyphenToDash,
                        onCheckedChange = { set { p -> p.copy(spacedHyphenToDash = it) } },
                    )
                    if (typing.spacedHyphenToDash) {
                        SingleChoiceChipsRow(
                            label = "Dash",
                            options = listOf(DashStyle.EN_DASH, DashStyle.EM_DASH),
                            optionLabel = { style -> if (style == DashStyle.EN_DASH) "En dash (–)" else "Em dash (—)" },
                            selected = typing.dashStyle,
                            onSelect = { style -> set { p -> p.copy(dashStyle = style) } },
                        )
                    }
                    SwitchRow(
                        "Curly quotation marks",
                        description = "A straight \" becomes an opening or closing mark.",
                        checked = typing.smartQuotes,
                        onCheckedChange = { set { p -> p.copy(smartQuotes = it) } },
                    )
                    if (typing.smartQuotes) {
                        SingleChoiceDropdownRow(
                            label = "Quotation marks",
                            options = SmartQuoteStyle.entries,
                            optionLabel = ::smartQuoteStyleLabel,
                            selected = typing.smartQuoteStyle,
                            onSelect = { style -> set { p -> p.copy(smartQuoteStyle = style) } },
                        )
                    }
                    NavigateRow(
                        "Spaces around punctuation",
                        "Remove the space before a mark, or add one after it",
                        icon = Icons.Outlined.SpaceBar,
                    ) { onNavigate(Routes.PUNCTUATION_SPACING) }
                }
            }
            header("Backspace")
            item {
                SwitchRow(
                    "Shift + Backspace deletes forward",
                    checked = typing.shiftBackspaceDeletesForward,
                    onCheckedChange = { set { p -> p.copy(shiftBackspaceDeletesForward = it) } },
                )
            }
            item {
                SingleChoiceDropdownRow(
                    label = "Alt + Backspace",
                    description = "With Alt held, tapped or locked. Deleting to the start of the line keeps the line break; " +
                        "press again to join the line to the one above.",
                    options = AltBackspaceAction.entries,
                    optionLabel = ::altBackspaceLabel,
                    selected = typing.altBackspace,
                    onSelect = { action -> set { p -> p.copy(altBackspace = action) } },
                )
            }
            item {
                SwitchRow(
                    "At the start of a line, delete forward",
                    description = "Backspace with nothing before the cursor deletes the next character instead.",
                    checked = typing.backspaceAtStartDeletesForward,
                    onCheckedChange = { set { p -> p.copy(backspaceAtStartDeletesForward = it) } },
                )
            }
            header("Alt key")
            item {
                SwitchRow(
                    "Space or Enter releases Alt",
                    description = "After Alt is pressed once or locked, the next Space or Enter turns it off.",
                    checked = typing.clearAltOnSpace,
                    onCheckedChange = { set { p -> p.copy(clearAltOnSpace = it) } },
                )
            }
            header("Shortcuts")
            item {
                NavigateRow(
                    "Text expansion",
                    "Type a short trigger, get the text you saved",
                    icon = Icons.Outlined.Bolt,
                    value = if (expansion.snippetsEnabled) "${expansion.snippets.size} saved" else "Off",
                ) { onNavigate(Routes.TEXT_EXPANSION) }
            }
        }
    }
}

private fun altBackspaceLabel(action: AltBackspaceAction): String = when (action) {
    AltBackspaceAction.DELETE_CHARACTER -> "Delete one character"
    AltBackspaceAction.DELETE_TO_LINE_START -> "Delete to the start of the line"
    AltBackspaceAction.DELETE_FORWARD -> "Delete forward"
}

private fun smartQuoteStyleLabel(style: SmartQuoteStyle): String = when (style) {
    SmartQuoteStyle.GERMAN_GUILLEMETS -> "»...«"
    SmartQuoteStyle.FRENCH_GUILLEMETS -> "«...»"
    SmartQuoteStyle.FRENCH_GUILLEMETS_NARROW_SPACED -> "« ... »"
    SmartQuoteStyle.GERMAN_LOW_HIGH -> "„...“"
    SmartQuoteStyle.ENGLISH_CURLY -> "“...”"
}
