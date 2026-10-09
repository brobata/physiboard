package brobata.physiboard.app.settings.ui.screens

import android.content.Context
import android.provider.Settings as AndroidSettings
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalHaptics
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.actions.feedback.HapticEvent
import brobata.physiboard.core.actions.feedback.HapticIntensity
import brobata.physiboard.core.actions.feedback.TypingSoundMode
import brobata.physiboard.core.settings.HapticStrength
import brobata.physiboard.core.speech.CueStrength
import brobata.physiboard.core.speech.DictationCues

/**
 * "Sound & Haptics" (settings-catalog.md SS9.2, SS9.4). "Vibration" carries the haptic language's
 * two switches and the key tick's strength (keys-and-modifiers.md SS13.5). "Typing Sounds" carries
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
    val context = LocalContext.current
    val player = LocalHaptics.current
    // keys-and-modifiers.md SS13.5: Android's own touch feedback switch silences all of it.
    val systemHapticsOff = remember {
        runCatching { AndroidSettings.System.getInt(context.contentResolver, AndroidSettings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0 }.getOrDefault(false)
    }
    val systemNote = if (systemHapticsOff) "Android's touch feedback is off, so these stay still until it is on again." else null

    SettingsScreenScaffold(title = "Sound & haptics", onBack = onBack) {
        RowList {
            header("Vibration")
            item {
                SwitchRow(
                    "Vibrate on every key",
                    description = "A light tick as each key goes down. The keys already click under your finger, so this starts off.",
                    note = systemNote,
                    checked = feedback.keyHaptics,
                    onCheckedChange = { checked -> controller.update { it.copy(feedback = it.feedback.copy(keyHaptics = checked)) } },
                )
            }
            if (feedback.keyHaptics) {
                item {
                    SingleChoiceChipsRow(
                        label = "Key vibration strength",
                        options = HapticIntensity.entries,
                        optionLabel = ::intensityLabel,
                        selected = feedback.keyHapticStrength,
                        onSelect = { strength ->
                            controller.update { it.copy(feedback = it.feedback.copy(keyHapticStrength = strength)) }
                            // Each chip plays the tick it chooses.
                            player?.preview(HapticEvent.KEY, strength)
                        },
                    )
                }
            }
            item {
                SwitchRow(
                    "Feedback vibrations",
                    description = "Shift and caps lock, opening and turning Sym pages, picking an accent, a correction and its undo, a long press, a key the keyboard refuses, and this app's own switches and sliders. Each has its own feel.",
                    note = systemNote,
                    checked = feedback.eventHaptics,
                    onCheckedChange = { checked -> controller.update { it.copy(feedback = it.feedback.copy(eventHaptics = checked)) } },
                )
            }
            header("Typing")
            item {
                SingleChoiceDropdownRow(
                    label = "Key sounds",
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
                        label = "Vibration length",
                        value = feedback.tapHapticDurationMs.toInt(),
                        range = IntClosedRange(5, 80),
                        valueLabel = { "$it ms" },
                        onValueChange = { value -> controller.update { it.copy(feedback = it.feedback.copy(tapHapticDurationMs = value.toLong())) } },
                    )
                }
            }
            header("Dictation")
            item {
                SwitchRow(
                    "Vibrate when dictation starts and stops",
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
                        onSelect = { strength ->
                            controller.update { it.copy(dictation = it.dictation.copy(hapticStrength = strength)) }
                            // spec: dictation.md SS12.2: "tapping a chip saves and plays that level's start cue."
                            playHapticStrengthPreview(context, strength)
                        },
                    )
                }
            }
        }
    }
}

private fun intensityLabel(intensity: HapticIntensity): String = when (intensity) {
    HapticIntensity.LIGHT -> "Light"
    HapticIntensity.STANDARD -> "Standard"
    HapticIntensity.STRONG -> "Strong"
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

private fun cueStrengthOf(strength: HapticStrength): CueStrength = when (strength) {
    HapticStrength.LIGHT -> CueStrength.LIGHT
    HapticStrength.STANDARD -> CueStrength.STANDARD
    HapticStrength.STRONG -> CueStrength.STRONG
}

/** spec: dictation.md SS12.2, SS8.1: the same start-cue pattern a real dictation session plays. */
private fun playHapticStrengthPreview(context: Context, strength: HapticStrength) {
    val pattern = DictationCues.startCue(cueStrengthOf(strength))
    val vibrator = runCatching { (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator }.getOrNull()
    val effect = VibrationEffect.createWaveform(pattern.timingsMs.toLongArray(), pattern.amplitudes.toIntArray(), -1)
    runCatching { vibrator?.vibrate(effect) }
}
