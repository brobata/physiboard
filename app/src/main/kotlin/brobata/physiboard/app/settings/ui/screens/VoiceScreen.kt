package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SectionHeader
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.SpeechEngines
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.AssistantAction
import brobata.physiboard.core.settings.DictationPrefs

/**
 * "Voice" (settings-catalog.md SS9.2, dictation.md). "Orange key opens the assistant" is bound
 * only to the store's `side_key_assistant` row here. The vendor-slot write itself now exists as
 * `:device:privileged`'s `SideKeyAssistantRemap.bind`/`unbind` (broker-privileged-toolbox.md SS9,
 * SS15), but this screen does not call it: `bind` needs the fully-qualified class name of
 * PhysiBoard's own trampoline activity, and that activity (dictation.md SS11.3's transparent
 * relay to the assistant launch of SS11.2) does not exist in this build yet, so there is nothing
 * for the switch to point the vendor slot at. Wiring this switch is dictation.md's own feature to
 * finish, not this toolbox module's (see this module's report).
 */
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val dictation = controller.current.value.dictation
    val context = LocalContext.current
    val engineOptions = remember { SpeechEngines.options(context) }
    fun set(transform: (DictationPrefs) -> DictationPrefs) = controller.update { it.copy(dictation = transform(it.dictation)) }

    SettingsScreenScaffold(title = "Voice", onBack = onBack) {
        RowList {
            item { SectionHeader("Triggers") }
            item {
                SwitchRow("Long-press Fn for speech input", checked = dictation.fnLongPressSpeech, onCheckedChange = { set { p -> p.copy(fnLongPressSpeech = it) } })
            }
            item { SectionHeader("Transcription") }
            item {
                SingleChoiceDropdownRow(
                    label = "Speech engine",
                    options = engineOptions,
                    optionLabel = { it.label },
                    selected = engineOptions.firstOrNull { it.storedValue == dictation.engine } ?: engineOptions.first(),
                    onSelect = { option -> set { p -> p.copy(engine = option.storedValue) } },
                )
            }
            item {
                SwitchRow("Automatic punctuation", checked = dictation.autoPunctuation, onCheckedChange = { set { p -> p.copy(autoPunctuation = it) } })
            }
            item {
                SwitchRow("Block offensive words", checked = dictation.maskOffensive, onCheckedChange = { set { p -> p.copy(maskOffensive = it) } })
            }
            item {
                IntRangeRow(
                    label = "End-of-speech pause",
                    value = dictation.endSilenceMs,
                    range = IntClosedRange(0, 10000),
                    step = 500,
                    valueLabel = { if (it == 0) "System default" else "${it / 1000.0} s" },
                    onValueChange = { value -> set { p -> p.copy(endSilenceMs = value) } },
                )
            }
            item {
                SwitchRow("Let the engine time the pause", checked = dictation.continuousSession, onCheckedChange = { set { p -> p.copy(continuousSession = it) } })
            }
            item { SectionHeader("Voice assistant") }
            item {
                SwitchRow("Orange key opens the assistant", checked = dictation.sideKeyAssistant, onCheckedChange = { set { p -> p.copy(sideKeyAssistant = it) } })
            }
            item {
                SwitchRow("Hold Sym for the assistant", checked = dictation.symLongPressAssistant, onCheckedChange = { set { p -> p.copy(symLongPressAssistant = it) } })
            }
            item {
                SingleChoiceChipsRow(
                    label = "How the assistant opens",
                    options = AssistantAction.entries,
                    optionLabel = ::assistantActionLabel,
                    selected = dictation.assistantAction,
                    onSelect = { action -> set { p -> p.copy(assistantAction = action) } },
                )
            }
        }
    }
}

private fun assistantActionLabel(action: AssistantAction): String = when (action) {
    AssistantAction.AUTO -> "Auto"
    AssistantAction.VOICE_COMMAND -> "Voice command"
    AssistantAction.HANDS_FREE -> "Hands free"
    AssistantAction.ASSIST -> "Assist"
}
