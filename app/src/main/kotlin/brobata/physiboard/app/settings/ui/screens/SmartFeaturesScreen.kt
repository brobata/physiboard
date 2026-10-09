package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.ExpandableSection
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.TypingPrefs
import brobata.physiboard.core.text.DashStyle
import brobata.physiboard.core.text.SmartQuoteStyle

/**
 * "Smart Features" (settings-catalog.md SS9.2, text-input.md). "Show keyboard automatically"
 * (`auto_show_keyboard`) and the "Currency Symbol" chips (`physical_keyboard_currency_symbol`)
 * are dropped rows: their fields are dropped from [TypingPrefs] for 3.0 (there is no soft
 * keyboard on the Titan). "Quotes inside words" (`mid_word_quote_to_apostrophe`) is dropped the
 * same way. See this module's report for the full list.
 */
@Composable
fun SmartFeaturesScreen(onBack: () -> Unit, onNavigateFnLayer: () -> Unit, onNavigatePunctuationSpacing: () -> Unit) {
    val controller = LocalSettingsController.current
    val typing = controller.current.value.typing
    fun set(transform: (TypingPrefs) -> TypingPrefs) = controller.update { it.copy(typing = transform(it.typing)) }

    SettingsScreenScaffold(title = "Smart Features", onBack = onBack) {
        RowList {
            header("Capitalization")
            item {
                SwitchRow("Capitalize at text start", checked = typing.capitalizeAtTextStart, onCheckedChange = { set { p -> p.copy(capitalizeAtTextStart = it) } })
            }
            item {
                SwitchRow("Capitalize after sentence end", checked = typing.capitalizeAfterSentenceEnd, onCheckedChange = { set { p -> p.copy(capitalizeAfterSentenceEnd = it) } })
            }
            header("Spacing & punctuation")
            item {
                SwitchRow("Double Space inserts period", checked = typing.doubleSpaceToPeriod, onCheckedChange = { set { p -> p.copy(doubleSpaceToPeriod = it) } })
            }
            header("Keyboard behavior")
            item {
                SwitchRow("Release Alt with Space", checked = typing.clearAltOnSpace, onCheckedChange = { set { p -> p.copy(clearAltOnSpace = it) } })
            }
            header("Delete")
            item {
                Text(
                    "Backspace normally deletes the character before the cursor. These make it delete forward instead while the modifier is held.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                SwitchRow("Shift + Backspace", checked = typing.shiftBackspaceDeletesForward, onCheckedChange = { set { p -> p.copy(shiftBackspaceDeletesForward = it) } })
            }
            item {
                SwitchRow("Alt + Backspace", checked = typing.altBackspaceDeletesForward, onCheckedChange = { set { p -> p.copy(altBackspaceDeletesForward = it) } })
            }
            item {
                NavigateRow("Open Fn Layer settings", onClick = onNavigateFnLayer, icon = Icons.Outlined.Functions)
            }
            item {
                ExpandableSection("Advanced") {
                    SwitchRow(
                        "Shift in all text fields",
                        description = "Auto-capitalize in URL, email and filter fields too (never in password fields or Terminal mode apps)",
                        checked = typing.capitalizeRestrictedFields,
                        onCheckedChange = { set { p -> p.copy(capitalizeRestrictedFields = it) } },
                    )
                    NavigateRow("Punctuation spacing", onClick = onNavigatePunctuationSpacing, icon = Icons.Outlined.SpaceBar)
                    SwitchRow("Space after comma", checked = typing.commaSpace, onCheckedChange = { set { p -> p.copy(commaSpace = it) } })
                    SwitchRow("Hyphen to dash", checked = typing.spacedHyphenToDash, onCheckedChange = { set { p -> p.copy(spacedHyphenToDash = it) } })
                    if (typing.spacedHyphenToDash) {
                        SingleChoiceChipsRow(
                            label = "Dash style",
                            options = listOf(DashStyle.EN_DASH, DashStyle.EM_DASH),
                            optionLabel = { style -> if (style == DashStyle.EN_DASH) "En dash (–)" else "Em dash (—)" },
                            selected = typing.dashStyle,
                            onSelect = { style -> set { p -> p.copy(dashStyle = style) } },
                        )
                    }
                    SwitchRow(
                        "Quotation mark style",
                        description = "Turn a straight \" into a typographic pair on the next delimiter",
                        checked = typing.smartQuotes,
                        onCheckedChange = { set { p -> p.copy(smartQuotes = it) } },
                    )
                    if (typing.smartQuotes) {
                        SingleChoiceDropdownRow(
                            label = "Style",
                            options = SmartQuoteStyle.entries,
                            optionLabel = ::smartQuoteStyleLabel,
                            selected = typing.smartQuoteStyle,
                            onSelect = { style -> set { p -> p.copy(smartQuoteStyle = style) } },
                        )
                    }
                    SwitchRow("Backspace at line start", checked = typing.backspaceAtStartDeletesForward, onCheckedChange = { set { p -> p.copy(backspaceAtStartDeletesForward = it) } })
                }
            }
        }
    }
}

private fun smartQuoteStyleLabel(style: SmartQuoteStyle): String = when (style) {
    SmartQuoteStyle.GERMAN_GUILLEMETS -> "»...«"
    SmartQuoteStyle.FRENCH_GUILLEMETS -> "«...»"
    SmartQuoteStyle.FRENCH_GUILLEMETS_NARROW_SPACED -> "« ... »"
    SmartQuoteStyle.GERMAN_LOW_HIGH -> "„...“"
    SmartQuoteStyle.ENGLISH_CURLY -> "“...”"
}
