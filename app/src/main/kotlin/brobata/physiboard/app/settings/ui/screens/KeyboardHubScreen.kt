package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.PerAppListKind
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SettingsSearchField

/** "Keyboard" (settings-catalog.md SS9.2): rows in the catalogue's order and labels. */
@Composable
fun KeyboardHubScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    SettingsScreenScaffold(title = "Keyboard", onBack = onBack) {
        SettingsSearchField(onSettingsRoot = false, onNavigate = onNavigate)
        RowList { keyboardHubRows(onNavigate) }
    }
}

private fun LazyListScope.keyboardHubRows(onNavigate: (String) -> Unit) {
    item { NavigateRow("Smart Features", "Auto-capitalization, spacing and text expansion") { onNavigate(Routes.SMART_FEATURES) } }
    item { NavigateRow("Auto-correction", "Corrections, suggestions and the personal dictionary") { onNavigate(Routes.AUTO_CORRECTION) } }
    item { NavigateRow("Voice", "Hold Fn to dictate, and the assistant triggers") { onNavigate(Routes.VOICE) } }
    item { NavigateRow("Status Bar Theme", "Colours, LEDs, and which buttons sit on the bar") { onNavigate(Routes.STATUS_BAR_THEME) } }
    item { NavigateRow("Sound & Haptics", "Typing sounds and vibration") { onNavigate(Routes.SOUND_HAPTICS) } }
    item { NavigateRow("Exact typing", "For terminals, SSH and code: what you type is what goes in") { onNavigate(Routes.appPicker(PerAppListKind.EXACT_TYPING)) } }
    item { NavigateRow("Text box under the bar", "For apps like Teams that leave the text box hidden under the bar") { onNavigate(Routes.appPicker(PerAppListKind.TEXT_BOX_UNDER_BAR)) } }
    item { NavigateRow("Enter key behaviour", "Configure app-specific Enter and newline handling") { onNavigate(Routes.ENTER_KEY_BEHAVIOUR) } }
}
