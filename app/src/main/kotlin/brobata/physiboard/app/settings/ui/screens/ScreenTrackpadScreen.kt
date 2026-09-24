package brobata.physiboard.app.settings.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.pointer.trackpad.ActivationMode
import brobata.physiboard.core.pointer.trackpad.TriggerKey

/**
 * "Screen trackpad" (settings-catalog.md SS9.2, trackpad-caret-nav.md SS2.2). The enable switch
 * defaults off (rebuild-from-scratch.md, "the trackpad enable switch default off, the row's
 * description says why"): it intercepts Space ahead of the typing pipeline, so a misfiring hold
 * swallows a keystroke for good until a screen like this one can switch it off again.
 */
@Composable
fun ScreenTrackpadScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val trackpad = controller.current.value.trackpad
    val context = LocalContext.current

    SettingsScreenScaffold(title = "Screen trackpad", onBack = onBack) {
        RowList {
            item {
                SwitchRow(
                    label = "Enable screen trackpad",
                    description = "Off by default: it takes over Space (or whichever key you pick below) before the keyboard sees it, so a misfiring hold can swallow a keystroke.",
                    checked = trackpad.enabled,
                    onCheckedChange = { checked -> controller.update { it.copy(trackpad = it.trackpad.copy(enabled = checked)) } },
                )
            }
            item {
                ButtonRow(
                    label = "Display over other apps",
                    description = "Required for the trackpad overlay to draw while you type",
                    buttonText = "Grant",
                    onClick = {
                        val intent = Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    },
                )
            }
            item {
                SingleChoiceChipsRow(
                    label = "Trigger key",
                    options = listOf(TriggerKey.SPACE, TriggerKey.SHIFT_LEFT, TriggerKey.SHIFT_RIGHT, TriggerKey.SHIFT_EITHER, TriggerKey.SYM),
                    optionLabel = ::triggerKeyLabel,
                    selected = trackpad.triggerKey,
                    onSelect = { key -> controller.update { it.copy(trackpad = it.trackpad.copy(triggerKey = key)) } },
                )
            }
            item {
                SingleChoiceChipsRow(
                    label = "Activate by",
                    options = listOf(ActivationMode.HOLD, ActivationMode.DOUBLE_TAP, ActivationMode.SINGLE_TAP),
                    optionLabel = ::activationModeLabel,
                    selected = trackpad.activation,
                    onSelect = { mode -> controller.update { it.copy(trackpad = it.trackpad.copy(activation = mode)) } },
                )
            }
            item {
                IntRangeRow(
                    label = "Sensitivity",
                    value = trackpad.stepPx,
                    range = IntClosedRange(8, 64),
                    onValueChange = { value -> controller.update { it.copy(trackpad = it.trackpad.copy(stepPx = value)) } },
                )
            }
            item {
                SwitchRow(
                    label = "Show on-screen hint",
                    checked = trackpad.showHint,
                    onCheckedChange = { checked -> controller.update { it.copy(trackpad = it.trackpad.copy(showHint = checked)) } },
                )
            }
        }
    }
}

private fun triggerKeyLabel(key: TriggerKey): String = when (key) {
    TriggerKey.SPACE -> "Space"
    TriggerKey.SHIFT_LEFT -> "Left Shift"
    TriggerKey.SHIFT_RIGHT -> "Right Shift"
    TriggerKey.SHIFT_EITHER -> "Either Shift"
    TriggerKey.SYM -> "Sym"
}

private fun activationModeLabel(mode: ActivationMode): String = when (mode) {
    ActivationMode.HOLD -> "Hold"
    ActivationMode.DOUBLE_TAP -> "Double tap"
    ActivationMode.SINGLE_TAP -> "Single tap"
}
