package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SectionHeader
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SpeechEnginePickerDialog
import brobata.physiboard.app.settings.ui.SpeechEngines
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.AssistantAction
import brobata.physiboard.core.settings.DictationPrefs
import brobata.physiboard.device.privileged.setup.RevertOutcome
import brobata.physiboard.ime.AssistantTriggerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Voice" (settings-catalog.md SS9.2, dictation.md SS12.1). "Orange key opens the assistant"
 * binds and unbinds the vendor's `func1_long_press_*` slot through `:device:privileged`'s
 * `SideKeyAssistantRemap` (dictation.md SS11.3, broker-privileged-toolbox.md SS9), pointing it at
 * [AssistantTriggerActivity]. spec SS11.3: "does not trust `side_key_assistant`: on opening it
 * reads the two slot keys and shows 'on' only when they point at PhysiBoard, correcting the
 * stored flag if they differ."
 */
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val dictation = controller.current.value.dictation
    val context = LocalContext.current
    val engineOptions = remember { SpeechEngines.options(context) }
    var showEnginePicker by remember { mutableStateOf(false) }
    fun set(transform: (DictationPrefs) -> DictationPrefs) = controller.update { it.copy(dictation = transform(it.dictation)) }

    val application = context.applicationContext as PhysiBoardApplication
    val sideKeyRemap = application.privileged.sideKeyAssistantRemap
    val scope = rememberCoroutineScope()
    var sideKeyBound by remember { mutableStateOf(sideKeyRemap.isBoundToThisApp()) }
    var sideKeyWorking by remember { mutableStateOf(false) }
    var sideKeyMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val actuallyBound = sideKeyRemap.isBoundToThisApp()
        sideKeyBound = actuallyBound
        if (actuallyBound != dictation.sideKeyAssistant) set { p -> p.copy(sideKeyAssistant = actuallyBound) }
    }

    SettingsScreenScaffold(title = "Voice", onBack = onBack) {
        RowList {
            item { SectionHeader("Triggers") }
            item {
                SwitchRow("Long-press Fn for speech input", checked = dictation.fnLongPressSpeech, onCheckedChange = { set { p -> p.copy(fnLongPressSpeech = it) } })
            }
            item { SectionHeader("Transcription") }
            item {
                // spec: dictation.md SS4.2, SS12.1: "the current engine's label", or "System
                // default" when the stored id no longer matches any row.
                val selectedLabel = engineOptions.firstOrNull { it.storedValue == dictation.engine }?.label ?: "System default"
                NavigateRow(label = "Speech engine", description = selectedLabel, onClick = { showEnginePicker = true })
            }
            item {
                SwitchRow("Automatic punctuation", checked = dictation.autoPunctuation, onCheckedChange = { set { p -> p.copy(autoPunctuation = it) } })
            }
            item {
                SwitchRow("Block offensive words", checked = dictation.maskOffensive, onCheckedChange = { set { p -> p.copy(maskOffensive = it) } })
            }
            item {
                // dictation.md SS12.1, SS6.4: 0 is "runs until you stop it" (the safety limits of
                // SS6.4 still apply); anything else is how much silence ends the session by itself.
                IntRangeRow(
                    label = "Stop after silence",
                    value = dictation.stopAfterSilenceMs,
                    range = IntClosedRange(0, 60000),
                    step = 5000,
                    valueLabel = { if (it == 0) "Never: Fn or any key stops it" else "${it / 1000} s of silence" },
                    onValueChange = { value -> set { p -> p.copy(stopAfterSilenceMs = value) } },
                )
            }
            item {
                SwitchRow(
                    "Typing stops dictation",
                    description = "Any key except a modifier ends the session, keeps the words on screen, then does its usual job. Hold Fn again stops it either way.",
                    checked = dictation.stopOnTyping,
                    onCheckedChange = { set { p -> p.copy(stopOnTyping = it) } },
                )
            }
            item {
                SwitchRow(
                    "Keep speech on the phone",
                    description = "Use the engine's on-device recognizer: faster, works with no signal, and it is the one that punctuates. Falls back online only when the language pack is missing. Private mode always keeps speech on the phone.",
                    checked = dictation.preferOffline,
                    onCheckedChange = { set { p -> p.copy(preferOffline = it) } },
                )
            }
            item {
                SwitchRow(
                    "Pause music while dictating",
                    description = "Takes the audio for the whole session, so a player pauses once when you start and resumes once when you stop.",
                    checked = dictation.pauseMedia,
                    onCheckedChange = { set { p -> p.copy(pauseMedia = it) } },
                )
            }
            item { SectionHeader("Voice assistant") }
            item {
                SwitchRow(
                    "Orange key opens the assistant",
                    checked = sideKeyBound,
                    enabled = !sideKeyWorking,
                    onCheckedChange = { turnOn ->
                        sideKeyWorking = true
                        scope.launch(Dispatchers.IO) {
                            val outcome = if (turnOn) {
                                sideKeyRemap.bind(AssistantTriggerActivity::class.java.name)
                            } else {
                                sideKeyRemap.unbind()
                            }
                            val nowBound = sideKeyRemap.isBoundToThisApp()
                            sideKeyBound = nowBound
                            set { p -> p.copy(sideKeyAssistant = nowBound) }
                            sideKeyMessage = sideKeyOutcomeMessage(outcome)
                            sideKeyWorking = false
                        }
                    },
                )
            }
            sideKeyMessage?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
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
    if (showEnginePicker) {
        SpeechEnginePickerDialog(
            options = engineOptions,
            selected = dictation.engine,
            onSelect = { value -> set { p -> p.copy(engine = value) }; showEnginePicker = false },
            onDismiss = { showEnginePicker = false },
        )
    }
}

private fun assistantActionLabel(action: AssistantAction): String = when (action) {
    AssistantAction.AUTO -> "Auto"
    AssistantAction.VOICE_COMMAND -> "Voice command"
    AssistantAction.HANDS_FREE -> "Hands free"
    AssistantAction.ASSIST -> "Assist"
}

/** spec: dictation.md SS11.3: the two failure toasts; null (no message) on success. */
private fun sideKeyOutcomeMessage(outcome: RevertOutcome): String? = when (outcome) {
    RevertOutcome.SUCCESS -> null
    RevertOutcome.FAILED -> "Could not change the side key setting."
    RevertOutcome.NEEDS_PERMISSION -> "PhysiBoard needs permission to change system settings, or a paired wireless-debugging session."
}
