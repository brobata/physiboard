package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.PerAppListKind
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.text.MessagingPreset

/**
 * "Enter key behaviour" (settings-catalog.md SS9.2, per-app-behavior.md). "App overrides" opens
 * [brobata.physiboard.app.settings.ui.screens.EnterOverridesScreen], where a checked app also
 * gets the "Wanted behaviour", "Send method" and "Extra shortcut" choosers per-app-behavior.md
 * SS3.11 describes. The 2.x preset text names a fifth choice, "Newline only", that has no
 * [MessagingPreset] value in the 3.0 schema (only `NEWLINE`, an
 * [brobata.physiboard.core.text.EnterBehavior], carries that meaning, and only as a per-app
 * override); the chooser below offers the four values the enum actually has.
 */
@Composable
fun EnterKeyBehaviourScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val controller = LocalSettingsController.current
    val perApp = controller.current.value.perApp

    SettingsScreenScaffold(title = "Enter key behaviour", onBack = onBack) {
        RowList {
            item {
                SwitchRow(
                    "App-specific Enter behaviour",
                    checked = perApp.enterBehaviorEnabled,
                    onCheckedChange = { checked -> controller.update { it.copy(perApp = it.perApp.copy(enterBehaviorEnabled = checked)) } },
                )
            }
            item {
                SingleChoiceChipsRow(
                    label = "Messaging preset",
                    options = listOf(MessagingPreset.APP_DEFAULT, MessagingPreset.SEND_SHIFT_NEWLINE, MessagingPreset.NEWLINE_CTRL_SEND, MessagingPreset.CUSTOM),
                    optionLabel = ::presetLabel,
                    selected = perApp.enterPreset,
                    onSelect = { preset -> controller.update { it.copy(perApp = it.perApp.copy(enterPreset = preset)) } },
                )
            }
            item {
                NavigateRow(
                    "App overrides",
                    description = "Per-app behaviour, send method and extra shortcut, no matter the preset above",
                    onClick = { onNavigate(Routes.appPicker(PerAppListKind.ENTER_OVERRIDES)) },
                )
            }
        }
    }
}

private fun presetLabel(preset: MessagingPreset): String = when (preset) {
    MessagingPreset.APP_DEFAULT -> "App default"
    MessagingPreset.SEND_SHIFT_NEWLINE -> "Send with Enter, Shift+Enter newline"
    MessagingPreset.NEWLINE_CTRL_SEND -> "Newline with Enter, Ctrl+Enter sends"
    MessagingPreset.CUSTOM -> "Custom"
}
