package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.core.actions.launcher.LauncherShortcuts
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SectionHeader
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.LauncherBehavior
import brobata.physiboard.core.settings.LauncherPrefs

/**
 * "PhysiBoard-QuickLauncher" (settings-catalog.md SS9.2; expansion-clipboard-pickers-launcher.md
 * SS6.5, SS7.8): the two trigger switches, the Behaviour rows, "Assigned launcher keys"
 * ([Routes.ASSIGNED_LAUNCHER_KEYS]), and the two Appearance rows 3.0 keeps, "QuickLauncher
 * entries" and "Customize entries". "Animation duration" and the cosmetic Appearance rows are
 * dropped (SS13). The blocked-default hint shows when Space held something before the quick
 * launcher's default could take it (SS6.1).
 */
@Composable
fun QuickLauncherScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val controller = LocalSettingsController.current
    val launcher = controller.current.value.launcher
    fun set(transform: (LauncherPrefs) -> LauncherPrefs) = controller.update { it.copy(launcher = transform(it.launcher)) }
    val shortcuts = LauncherShortcuts.parse(launcher.assignedKeysJson).applyDefault(defaultAlreadyAssigned = launcher.assignedKeysJson.isNotBlank())

    SettingsScreenScaffold(title = "PhysiBoard-QuickLauncher", onBack = onBack) {
        RowList {
            item {
                Text(
                    "These settings share one launcher-key assignment list. Choose where PhysiBoard should listen for those assigned keys.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (shortcuts.blockedBySpace) {
                item { Text(LauncherShortcuts.BLOCKED_DEFAULT_HINT, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
            item {
                SwitchRow(
                    "Homescreen shortcuts",
                    description = "Experimental. Listen for your assigned keys on the Android home screen. The keys themselves are set below, in Assigned launcher keys.",
                    checked = launcher.homeScreenShortcutsEnabled,
                    onCheckedChange = { set { p -> p.copy(homeScreenShortcutsEnabled = it) } },
                )
            }
            item {
                SwitchRow(
                    "SYM key shortcuts",
                    description = "Hold SYM with one of your assigned keys to launch an app or action. The keys are set below, in Assigned launcher keys.",
                    checked = launcher.symShortcutsEnabled,
                    onCheckedChange = { set { p -> p.copy(symShortcutsEnabled = it) } },
                )
            }
            item {
                NavigateRow(
                    "Assigned launcher keys",
                    "Tap a key to assign or replace a command. Assigned keys are shared by both trigger modes." +
                        (shortcuts.shortcuts.quickLauncherKeycode?.let { " Quick launcher is currently assigned to ${brobata.physiboard.core.actions.launcher.AssignableKeys.label(it)}." } ?: ""),
                ) { onNavigate(Routes.ASSIGNED_LAUNCHER_KEYS) }
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
            item { SectionHeader("Appearance") }
            item { NavigateRow("QuickLauncher entries", "Choose which sources appear in PhysiBoard search.") { onNavigate(Routes.QUICK_LAUNCHER_ENTRIES) } }
            item { NavigateRow("Customize entries", "Favorites, hidden entries, and search aliases") { onNavigate(Routes.CUSTOMIZE_ENTRIES) } }
        }
    }
}
