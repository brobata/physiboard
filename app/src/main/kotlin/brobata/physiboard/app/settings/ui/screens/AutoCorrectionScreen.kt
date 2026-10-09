package brobata.physiboard.app.settings.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import brobata.physiboard.app.settings.ui.ExpandableSection
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SpellCheckerSettings
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.CorrectionPrefs

/**
 * "Autocorrect & words" (formerly "Auto-correction"; settings-catalog.md SS9.2,
 * autocorrect-suggestions.md): the corrections PhysiBoard makes to whole words, then the word
 * lists it corrects with (the personal dictionary, the per-language text replacements, Android's
 * spell checker), then the two tuning knobs, collapsed. "Text replacements" now carries its own
 * switch on its own screen; this row shows whether it is on. "Suggestions while typing"
 * (`suggestions_enabled`) has no row any more: it only ever filled the suggestion strip, which
 * is gone (c61c240); the key stays for backups. "Edit Type Ranking" (`use_edit_type_ranking`)
 * is dropped from [CorrectionPrefs] for 3.0.
 */
@Composable
fun AutoCorrectionScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val controller = LocalSettingsController.current
    val correction = controller.current.value.correction
    fun set(transform: (CorrectionPrefs) -> CorrectionPrefs) = controller.update { it.copy(correction = transform(it.correction)) }

    // autocorrect-suggestions.md SS18: re-read on every return, since the choice is made in Android's own settings.
    val context = LocalContext.current
    var spellChecker by remember { mutableStateOf(SpellCheckerSettings.state(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) spellChecker = SpellCheckerSettings.state(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreenScaffold(title = "Autocorrect & words", onBack = onBack) {
        RowList {
            header("Autocorrect")
            item {
                SwitchRow(
                    "Fix typos",
                    description = "A misspelled word is corrected when you press Space or Enter. Backspace right after puts back what you typed.",
                    checked = correction.autoReplaceOnSpaceEnter,
                    onCheckedChange = { set { p -> p.copy(autoReplaceOnSpaceEnter = it) } },
                )
            }
            item {
                SwitchRow(
                    "Fix mixed-up words",
                    description = "its/it's, your/you're, their/there, then/than, by reading the words on both sides.",
                    checked = correction.fixWordMixups,
                    onCheckedChange = { set { p -> p.copy(fixWordMixups = it) } },
                )
            }
            item {
                SwitchRow(
                    "Add missing apostrophes and accents",
                    description = "So dont becomes don't.",
                    checked = correction.accentMatching,
                    onCheckedChange = { set { p -> p.copy(accentMatching = it) } },
                )
            }
            header("Words")
            item {
                NavigateRow("Personal dictionary", "Words you've added, plus the built-in favourites", icon = Icons.AutoMirrored.Outlined.MenuBook) { onNavigate(Routes.PERSONAL_DICTIONARY) }
            }
            item {
                NavigateRow(
                    "Text replacements",
                    "Your own rules, per language",
                    icon = Icons.Outlined.FindReplace,
                    value = if (correction.textReplacementsEnabled) "On" else "Off",
                ) { onNavigate(Routes.CUSTOM_SUBSTITUTIONS) }
            }
            item {
                NavigateRow("System spell checker", SpellCheckerSettings.description(spellChecker), icon = Icons.Outlined.Spellcheck) { SpellCheckerSettings.open(context) }
            }
            header("")
            item {
                ExpandableSection("Fine-tuning") {
                    IntRangeRow(
                        label = "How far a correction may reach",
                        description = "The most letters a correction may change. Off corrects nothing but missing apostrophes and accents.",
                        value = correction.maxAutoReplaceDistance,
                        range = IntClosedRange(0, 3),
                        valueLabel = { if (it == 0) "Off" else if (it == 1) "1 letter" else "$it letters" },
                        onValueChange = { value -> set { p -> p.copy(maxAutoReplaceDistance = value) } },
                    )
                    SwitchRow(
                        "Weigh nearby keys",
                        description = "Prefer corrections that are a slip to a neighbouring key. English always does; this is for the other languages.",
                        checked = correction.useKeyboardProximity,
                        onCheckedChange = { set { p -> p.copy(useKeyboardProximity = it) } },
                    )
                }
            }
        }
    }
}
