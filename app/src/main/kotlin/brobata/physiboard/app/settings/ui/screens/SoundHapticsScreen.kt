package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.HapticStrength

/**
 * "Sound & Haptics" (settings-catalog.md SS9.2, SS9.4). "Typing Sounds" (the chooser, custom
 * packs and output mode) is dropped from [brobata.physiboard.core.settings.FeedbackPrefs] for
 * 3.0 (settings-catalog.md SS13: "undecided on typing sounds and packs"; the pack import path is
 * unreachable in 2.x). The two dictation rows point here, not at Voice, correcting the drift
 * settings-catalog.md SS8 calls out.
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

private fun hapticStrengthLabel(strength: HapticStrength): String = when (strength) {
    HapticStrength.LIGHT -> "Light"
    HapticStrength.STANDARD -> "Standard"
    HapticStrength.STRONG -> "Strong"
}
