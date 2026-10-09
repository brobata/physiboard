package brobata.physiboard.app.settings.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.InfoText
import brobata.physiboard.app.settings.ui.Summaries
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.LanguagePrefs

/**
 * "Languages & layouts" (formerly "Input Languages"; settings-catalog.md SS9.2,
 * dictionaries-languages.md): the layout the keys type in, the languages you switch between, the
 * dictionaries behind them, and the key combinations that switch. "Keyboard layout" (layers-sym-
 * alt.md SS9.6) carries "Follow the language" (`layout_auto_by_locale`) itself, next to the
 * layouts it overrides, so this screen shows it only as the row's value. The app's own language
 * moved to Look & feel (one row, `AppLanguageScreen`), where the dropdown that duplicated it here
 * is gone. `toast_on_layout_switch` stays unbound, as the catalogue's screen map has it.
 */
@Composable
fun InputLanguagesScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val controller = LocalSettingsController.current
    val settings = controller.current.value
    val languages = settings.languages
    fun set(transform: (LanguagePrefs) -> LanguagePrefs) = controller.update { it.copy(languages = transform(it.languages)) }

    SettingsScreenScaffold(title = "Languages & layouts", onBack = onBack) {
        RowList {
            header("")
            item {
                NavigateRow("Keyboard layout", "QWERTY, QWERTZ, AZERTY and your own", icon = Icons.Outlined.Keyboard, value = Summaries.languages(settings)) {
                    onNavigate(Routes.KEYBOARD_LAYOUT)
                }
            }
            item {
                NavigateRow("Languages you type in", "Add, edit or hide languages and their layouts", icon = Icons.Outlined.Translate) { onNavigate(Routes.INPUT_STYLES) }
            }
            item {
                NavigateRow("Dictionaries", "Download, import or remove a language's words", icon = Icons.Outlined.Download) { onNavigate(Routes.INSTALLED_DICTIONARIES) }
            }
            header("Switch language with")
            item {
                SwitchRow("Alt + Shift", checked = languages.altShiftLayoutSwitch, onCheckedChange = { set { p -> p.copy(altShiftLayoutSwitch = it) } })
            }
            item {
                SwitchRow("Alt + Enter", checked = languages.altEnterLayoutSwitch, onCheckedChange = { set { p -> p.copy(altEnterLayoutSwitch = it) } })
            }
            item {
                SwitchRow("Ctrl + Space", checked = languages.ctrlSpaceLayoutSwitch, onCheckedChange = { set { p -> p.copy(ctrlSpaceLayoutSwitch = it) } })
                InfoText("Off by default: the keyboard takes these combinations before the app sees them.")
            }
        }
    }
}
