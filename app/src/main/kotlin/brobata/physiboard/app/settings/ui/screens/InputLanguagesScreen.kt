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
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.LanguagePrefs

/** dictionaries-languages.md SS11: "System default", then these ten in this order. */
private val APP_LANGUAGE_TAGS = listOf("", "en", "it", "de", "es", "fr", "pl", "ru", "uk", "vi", "hy")

private fun appLanguageLabel(tag: String): String = when (tag) {
    "" -> "System default"
    "en" -> "English"
    "it" -> "Italiano"
    "de" -> "Deutsch"
    "es" -> "Español"
    "fr" -> "Français"
    "pl" -> "Polski"
    "ru" -> "Русский"
    "uk" -> "Українська"
    "vi" -> "Tiếng Việt"
    "hy" -> "Հայերեն"
    else -> tag
}

/**
 * "Input Languages" (settings-catalog.md SS9.2, dictionaries-languages.md). "Installed
 * dictionaries" (SS6), "Manage input styles" (the input style list, SS8.2, add/edit/delete/
 * hide/show and its per-style "Suggestion dictionaries") and "Keyboard Layout" (layers-sym-alt.md
 * SS9.6, the standalone `keyboard_layout` picker) each navigate to their own screen.
 * `toast_on_layout_switch` sits in the same "Layout Switch Shortcuts" group in
 * dictionaries-languages.md SS13, but settings-catalog.md's own screen map (SS9.2, the map this
 * module mirrors) never lists it as a row, so it stays unbound too.
 */
@Composable
fun InputLanguagesScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val controller = LocalSettingsController.current
    val languages = controller.current.value.languages
    fun set(transform: (LanguagePrefs) -> LanguagePrefs) = controller.update { it.copy(languages = transform(it.languages)) }

    SettingsScreenScaffold(title = "Input Languages", onBack = onBack) {
        RowList {
            item {
                NavigateRow("Installed dictionaries", "Download, import or remove per-language dictionaries", icon = Icons.Outlined.Download) { onNavigate(Routes.INSTALLED_DICTIONARIES) }
            }
            item {
                NavigateRow("Manage input styles", "Add, edit or hide the languages and layouts you type in", icon = Icons.Outlined.Translate) { onNavigate(Routes.INPUT_STYLES) }
            }
            item {
                NavigateRow("Keyboard Layout", "Standard, QWERTZ, AZERTY and other bundled or imported layouts", icon = Icons.Outlined.Keyboard) { onNavigate(Routes.KEYBOARD_LAYOUT) }
            }
            item {
                SwitchRow("Automatic Layout Mapping", checked = languages.layoutAutoByLocale, onCheckedChange = { set { p -> p.copy(layoutAutoByLocale = it) } })
            }
            header("Layout Switch Shortcuts")
            item {
                SwitchRow("Alt+Shift Layout Switch", checked = languages.altShiftLayoutSwitch, onCheckedChange = { set { p -> p.copy(altShiftLayoutSwitch = it) } })
            }
            item {
                SwitchRow("Alt+Enter Layout Switch", checked = languages.altEnterLayoutSwitch, onCheckedChange = { set { p -> p.copy(altEnterLayoutSwitch = it) } })
            }
            item {
                SwitchRow("Ctrl+Space Layout Switch", checked = languages.ctrlSpaceLayoutSwitch, onCheckedChange = { set { p -> p.copy(ctrlSpaceLayoutSwitch = it) } })
            }
            item {
                SingleChoiceDropdownRow(
                    label = "App Language",
                    options = APP_LANGUAGE_TAGS,
                    optionLabel = ::appLanguageLabel,
                    selected = languages.appLanguageTag,
                    onSelect = { tag -> set { p -> p.copy(appLanguageTag = tag) } },
                )
            }
        }
    }
}
