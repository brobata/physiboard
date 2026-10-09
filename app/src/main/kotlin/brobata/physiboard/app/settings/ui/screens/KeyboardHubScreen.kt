package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.PerAppListKind
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
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
    item { NavigateRow("Smart Features", "Auto-capitalization, spacing and text expansion", icon = Icons.Outlined.AutoAwesome) { onNavigate(Routes.SMART_FEATURES) } }
    item { NavigateRow("Auto-correction", "Corrections, suggestions and the personal dictionary", icon = Icons.Outlined.Spellcheck) { onNavigate(Routes.AUTO_CORRECTION) } }
    item { NavigateRow("Long press", "What holding a letter types (Alt symbol, capital, accent, Sym character) and how long to hold", icon = Icons.Outlined.Timer) { onNavigate(Routes.LONG_PRESS) } }
    item { NavigateRow("Voice", "Hold Fn to dictate, and the assistant triggers", icon = Icons.Outlined.Mic) { onNavigate(Routes.VOICE) } }
    item { NavigateRow("Theme", "Colours, LEDs, and the buttons on the Sym pages", icon = Icons.Outlined.Palette) { onNavigate(Routes.STATUS_BAR_THEME) } }
    item { NavigateRow("Customize SYM Keyboard", "Arrange, enable and edit the Sym key's Emoji and Symbols pages", icon = Icons.Outlined.EmojiSymbols) { onNavigate(Routes.CUSTOMIZE_SYM_KEYBOARD) } }
    item { NavigateRow("Sound & Haptics", "Typing sounds and vibration", icon = Icons.Outlined.Vibration) { onNavigate(Routes.SOUND_HAPTICS) } }
    item { NavigateRow("Terminal mode", "For terminals, SSH and code. Nothing corrected or capitalised; Ctrl, Esc and Alt symbols go straight to the app.", icon = Icons.Outlined.Terminal) { onNavigate(Routes.appPicker(PerAppListKind.EXACT_TYPING)) } }
    item { NavigateRow("Enter key behaviour", "Configure app-specific Enter and newline handling", icon = Icons.AutoMirrored.Outlined.KeyboardReturn) { onNavigate(Routes.ENTER_KEY_BEHAVIOUR) } }
}
