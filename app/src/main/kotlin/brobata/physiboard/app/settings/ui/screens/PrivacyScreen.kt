package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SectionHeader
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow

/**
 * "Privacy" (settings-catalog.md SS9.2): private mode (app-shell.md SS31) and clean links
 * (expansion-clipboard-pickers-launcher.md SS3.7). Reached from the Settings screen's own
 * "Privacy" row and from search.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val privacy = controller.current.value.privacy

    SettingsScreenScaffold(title = "Privacy", onBack = onBack) {
        RowList {
            item {
                SwitchRow(
                    "Private mode",
                    description = "While this is on, PhysiBoard remembers nothing you type: no new words, no word predictions learned, " +
                        "no clipboard history, no recent emoji. Autocorrect still uses what it already knows. PhysiBoard also makes no " +
                        "network requests: no update checks and no dictionary downloads. Dictation is done by your phone's speech " +
                        "service, which may still go online; it is not part of PhysiBoard. To switch it from the keyboard, give \"Private mode\" a key " +
                        "under Assigned launcher keys (Sym + that key), or put the command physiboard.toggle_private_mode on the Fn layer.",
                    note = if (privacy.privateMode) "On. The caret badge shows PRIVATE while you type." else null,
                    checked = privacy.privateMode,
                    onCheckedChange = { checked -> controller.update { it.copy(privacy = it.privacy.copy(privateMode = checked)) } },
                )
            }
            item { SectionHeader("Automatically private") }
            item {
                SwitchRow(
                    "Fields that ask for privacy",
                    description = "Apps can ask the keyboard not to learn from a text box, as incognito browser tabs do. PhysiBoard always " +
                        "honours that: nothing typed there is remembered, whether or not private mode is on.",
                    checked = true,
                    onCheckedChange = {},
                    enabled = false,
                )
            }
            item { SectionHeader("Links") }
            item {
                SwitchRow(
                    "Clean links",
                    description = "Remove tracking from links you copy and paste from the clipboard panel, such as utm_source, fbclid " +
                        "or a YouTube share code, and open up Google and Facebook redirect links to the real address. The rest of " +
                        "the link is kept exactly. Pasting with Ctrl+V is done by the app, so it pastes the link as it was copied.",
                    checked = privacy.cleanLinks,
                    onCheckedChange = { checked -> controller.update { it.copy(privacy = it.privacy.copy(cleanLinks = checked)) } },
                )
            }
        }
    }
}
