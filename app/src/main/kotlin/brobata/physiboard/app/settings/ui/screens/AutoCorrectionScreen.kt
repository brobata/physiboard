package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.CorrectionPrefs

/**
 * "Auto-correction" (settings-catalog.md SS9.2, autocorrect-suggestions.md). "Manage text
 * replacements" (per-language substitution lists) and "Personal dictionary" (backed by
 * `:core:dict`'s `UserWordStore`, not a row in this typed schema) both navigate to their own list
 * editors. "Edit Type Ranking" (`use_edit_type_ranking`) is dropped from [CorrectionPrefs] for 3.0.
 */
@Composable
fun AutoCorrectionScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val controller = LocalSettingsController.current
    val correction = controller.current.value.correction
    fun set(transform: (CorrectionPrefs) -> CorrectionPrefs) = controller.update { it.copy(correction = transform(it.correction)) }

    SettingsScreenScaffold(title = "Auto-correction", onBack = onBack) {
        RowList {
            item {
                SwitchRow("Text replacements", checked = correction.textReplacementsEnabled, onCheckedChange = { set { p -> p.copy(textReplacementsEnabled = it) } })
            }
            item {
                NavigateRow("Manage text replacements", "Custom substitutions per language") { onNavigate(Routes.CUSTOM_SUBSTITUTIONS) }
            }
            item {
                NavigateRow("Personal dictionary", "Words you've added, plus the built-in favourites") { onNavigate(Routes.PERSONAL_DICTIONARY) }
            }
            item {
                SwitchRow("Automatic correction", checked = correction.autoReplaceOnSpaceEnter, onCheckedChange = { set { p -> p.copy(autoReplaceOnSpaceEnter = it) } })
            }
            item {
                IntRangeRow(
                    label = "Maximum correction distance",
                    value = correction.maxAutoReplaceDistance,
                    range = IntClosedRange(0, 3),
                    valueLabel = { if (it == 0) "Off" else it.toString() },
                    onValueChange = { value -> set { p -> p.copy(maxAutoReplaceDistance = value) } },
                )
            }
            item {
                SwitchRow("Suggestions while typing", checked = correction.suggestionsEnabled, onCheckedChange = { set { p -> p.copy(suggestionsEnabled = it) } })
            }
            item {
                SwitchRow("Accent & spelling marks", checked = correction.accentMatching, onCheckedChange = { set { p -> p.copy(accentMatching = it) } })
            }
            item {
                SwitchRow("Keyboard Proximity Ranking", checked = correction.useKeyboardProximity, onCheckedChange = { set { p -> p.copy(useKeyboardProximity = it) } })
            }
        }
    }
}
