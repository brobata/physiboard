package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SectionHeader
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.actions.commands.CommandSource
import brobata.physiboard.core.actions.commands.SourceVisibility

/**
 * "QuickLauncher entries" (expansion-clipboard-pickers-launcher.md SS7.8: "Choose which sources
 * appear in PhysiBoard search.", one switch per source), editing the `command_surface_sources`
 * document through [SourceVisibility] (SS8.6: only the quick launcher surface is filtered).
 */
@Composable
fun QuickLauncherEntriesScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val visibility = SourceVisibility.parse(controller.current.value.launcher.commandSurfaceSourcesJson)

    SettingsScreenScaffold(title = "QuickLauncher entries", onBack = onBack) {
        RowList {
            item { SectionHeader("Choose which sources appear in PhysiBoard search.") }
            items(CommandSource.entries.size) { index ->
                val source = CommandSource.entries[index]
                SwitchRow(
                    label = source.label,
                    checked = visibility.isEnabled(source),
                    onCheckedChange = { checked ->
                        controller.update { s ->
                            val next = SourceVisibility.parse(s.launcher.commandSurfaceSourcesJson).with(source, checked)
                            s.copy(launcher = s.launcher.copy(commandSurfaceSourcesJson = SourceVisibility.encode(next)))
                        }
                    },
                )
            }
        }
    }
}
