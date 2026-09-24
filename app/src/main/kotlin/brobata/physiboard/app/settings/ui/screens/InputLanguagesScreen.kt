package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SectionHeader
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
 * "Input Languages" (settings-catalog.md SS9.2, dictionaries-languages.md). The input style list
 * ("Installed dictionaries", add/edit/delete/hide/show, "Suggestion dictionaries" per style) is a
 * list editor beyond this app's six row types and is left unbound, which also leaves
 * [LanguagePrefs.keyboardLayout] (only ever set through that list's layout picker) with nothing
 * to bind to. `toast_on_layout_switch` sits in the same "Layout Switch Shortcuts" group in
 * dictionaries-languages.md SS13, but settings-catalog.md's own screen map (SS9.2, the map this
 * module mirrors) never lists it as a row, so it stays unbound too.
 */
@Composable
fun InputLanguagesScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val languages = controller.current.value.languages
    fun set(transform: (LanguagePrefs) -> LanguagePrefs) = controller.update { it.copy(languages = transform(it.languages)) }

    SettingsScreenScaffold(title = "Input Languages", onBack = onBack) {
        RowList {
            item {
                SwitchRow("Automatic Layout Mapping", checked = languages.layoutAutoByLocale, onCheckedChange = { set { p -> p.copy(layoutAutoByLocale = it) } })
            }
            item { SectionHeader("Layout Switch Shortcuts") }
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
