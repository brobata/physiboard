package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SectionHeader
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.LauncherBehavior
import brobata.physiboard.core.settings.LauncherPrefs

/**
 * "PhysiBoard-QuickLauncher" (settings-catalog.md SS9.2). "Assigned launcher keys" and
 * "QuickLauncher entries"/"Customize entries" edit [LauncherPrefs.assignedKeysJson] and
 * [LauncherPrefs.commandCustomizationsJson]: nested per-key and per-source editors beyond this
 * app's six row types, left unbound. "Animation duration" is dropped from the schema for 3.0
 * (settings-catalog.md SS13, "constants until a screen exists"). The "Appearance" sub-screen is
 * unreachable in 2.x and its Keep/Drop is undecided, so it is not built here.
 */
@Composable
fun QuickLauncherScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val launcher = controller.current.value.launcher
    fun set(transform: (LauncherPrefs) -> LauncherPrefs) = controller.update { it.copy(launcher = transform(it.launcher)) }

    SettingsScreenScaffold(title = "PhysiBoard-QuickLauncher", onBack = onBack) {
        RowList {
            item {
                SwitchRow("Homescreen shortcuts", checked = launcher.homeScreenShortcutsEnabled, onCheckedChange = { set { p -> p.copy(homeScreenShortcutsEnabled = it) } })
            }
            item {
                SwitchRow("SYM key shortcuts", checked = launcher.symShortcutsEnabled, onCheckedChange = { set { p -> p.copy(symShortcutsEnabled = it) } })
            }
            item { SectionHeader("Behaviour") }
            item {
                SingleChoiceChipsRow(
                    label = "QuickLauncher behaviour",
                    options = listOf(LauncherBehavior.PHYSIBOARD, LauncherBehavior.NIAGARA),
                    optionLabel = { if (it == LauncherBehavior.PHYSIBOARD) "PhysiBoard" else "Niagara" },
                    selected = launcher.behavior,
                    onSelect = { value -> set { p -> p.copy(behavior = value) } },
                )
            }
            item {
                SwitchRow("Open unique match automatically", checked = launcher.openUniqueMatch, onCheckedChange = { set { p -> p.copy(openUniqueMatch = it) } })
            }
            item {
                SwitchRow("Show only top search results", checked = launcher.limitResults, onCheckedChange = { set { p -> p.copy(limitResults = it) } })
            }
            item {
                SwitchRow("Use active keyboard layout", checked = launcher.respectKeyboardLayout, onCheckedChange = { set { p -> p.copy(respectKeyboardLayout = it) } })
            }
            item {
                SwitchRow("Typo-tolerant search", checked = launcher.typoTolerantRanking, onCheckedChange = { set { p -> p.copy(typoTolerantRanking = it) } })
            }
        }
    }
}
