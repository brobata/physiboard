package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.actions.feedback.TypingSoundMode
import brobata.physiboard.core.settings.HapticStrength

/**
 * "Sound & Haptics" (settings-catalog.md SS9.2, SS9.4). "Typing Sounds" carries
 * `typing_sound_mode` (expansion-clipboard-pickers-launcher.md SS9.1, SS9.3): the dropdown offers
 * Off, Keyboard click and Typewriter; the `custom` pack and the output-mode row stay hidden, as
 * 2.x itself hides them ("hidden to declutter", SS9.1), since there is no pack-import flow in
 * 3.0. The two dictation rows point here, not at Voice, correcting the drift settings-catalog.md
 * SS8 calls out.
 */
@Composable
fun SoundHapticsScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val settings = controller.current.value
    val feedback = settings.feedback
    val dictation = settings.dictation

    SettingsScreenScaffold(title = "Sound & Haptics", onBack = onBack) {
        RowList {
            item {
                SingleChoiceDropdownRow(
                    label = "Typing Sounds",
                    options = listOf(TypingSoundMode.OFF, TypingSoundMode.CLICK, TypingSoundMode.TYPEWRITER),
                    optionLabel = ::typingSoundModeLabel,
                    selected = feedback.typingSoundMode,
                    onSelect = { mode -> controller.update { it.copy(feedback = it.feedback.copy(typingSoundMode = mode)) } },
                )
            }
            item {
                SwitchRow(
                    "Tap vibration",
                    checked = feedback.tapHapticUseSystem,
                    onCheckedChange = { checked -> controller.update { it.copy(feedback = it.feedback.copy(tapHapticUseSystem = checked)) } },
                )
            }
            if (!feedback.tapHapticUseSystem) {
                item {
                    IntRangeRow(
                        label = "Custom vibration",
                        value = feedback.tapHapticDurationMs.toInt(),
                        range = IntClosedRange(5, 80),
                        valueLabel = { "$it ms" },
                        onValueChange = { value -> controller.update { it.copy(feedback = it.feedback.copy(tapHapticDurationMs = value.toLong())) } },
                    )
                }
            }
            item {
                SwitchRow(
                    "Vibrate on dictation start/stop",
                    checked = dictation.haptics,
                    onCheckedChange = { checked -> controller.update { it.copy(dictation = it.dictation.copy(haptics = checked)) } },
                )
            }
            if (dictation.haptics) {
                item {
                    SingleChoiceChipsRow(
                        label = "Vibration strength",
                        options = listOf(HapticStrength.LIGHT, HapticStrength.STANDARD, HapticStrength.STRONG),
                        optionLabel = ::hapticStrengthLabel,
                        selected = dictation.hapticStrength,
                        onSelect = { strength -> controller.update { it.copy(dictation = it.dictation.copy(hapticStrength = strength)) } },
                    )
                }
            }
        }
    }
}

/** SS9.3: "Off, Keyboard click, Typewriter (the custom entry is hidden)". */
private fun typingSoundModeLabel(mode: TypingSoundMode): String = when (mode) {
    TypingSoundMode.OFF -> "Off"
    TypingSoundMode.CLICK -> "Keyboard click"
    TypingSoundMode.TYPEWRITER -> "Typewriter"
}

private fun hapticStrengthLabel(strength: HapticStrength): String = when (strength) {
    HapticStrength.LIGHT -> "Light"
    HapticStrength.STANDARD -> "Standard"
    HapticStrength.STRONG -> "Strong"
}
