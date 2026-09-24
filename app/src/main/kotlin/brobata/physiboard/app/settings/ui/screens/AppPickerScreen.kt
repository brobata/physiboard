package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.settings.ui.AppCatalog
import brobata.physiboard.app.settings.ui.AppPickerBody
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.PerAppListKind
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.settings.PerAppPrefs
import brobata.physiboard.core.settings.EnterOverrideRow
import brobata.physiboard.core.text.EnterBehavior

/**
 * The one screen behind every per-app list (rebuild-from-scratch.md: "a searchable app picker
 * over installed packages with a switch per app, used by exact typing, the status-bar app list,
 * the dip list and the Enter overrides"). [kind] picks which [PerAppPrefs] (or, for the status
 * bar, [brobata.physiboard.core.settings.StatusBarPrefs]) set the switches read and write.
 */
@Composable
fun AppPickerScreen(kind: String, onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val settings = controller.current.value
    val context = LocalContext.current

    val (title, selected) = when (kind) {
        PerAppListKind.EXACT_TYPING -> "Exact typing" to settings.perApp.exactTypingPackages
        PerAppListKind.TEXT_BOX_UNDER_BAR -> "Text box under the bar" to settings.perApp.nudgePackages
        PerAppListKind.STATUS_BAR_APPS -> "Choose apps" to settings.statusBar.apps
        PerAppListKind.ENTER_OVERRIDES -> "App overrides" to settings.perApp.enterOverrides.map { it.packageName }.toSet()
        else -> error("unknown per-app list kind: $kind")
    }

    val apps = remember(kind) { AppCatalog.installedApps(context, alsoInclude = selected) }

    SettingsScreenScaffold(title = title, onBack = onBack) {
        AppPickerBody(
            apps = apps,
            selected = selected,
            onToggle = { packageName, checked -> toggle(controller, kind, packageName, checked) },
        )
    }
}

private fun toggle(controller: brobata.physiboard.app.settings.ui.SettingsController, kind: String, packageName: String, checked: Boolean) {
    when (kind) {
        PerAppListKind.EXACT_TYPING -> controller.update {
            it.copy(perApp = it.perApp.copy(exactTypingPackages = it.perApp.exactTypingPackages.toggled(packageName, checked)))
        }
        PerAppListKind.TEXT_BOX_UNDER_BAR -> controller.update {
            it.copy(perApp = it.perApp.copy(nudgePackages = it.perApp.nudgePackages.toggled(packageName, checked)))
        }
        PerAppListKind.STATUS_BAR_APPS -> controller.update {
            it.copy(statusBar = it.statusBar.copy(apps = it.statusBar.apps.toggled(packageName, checked)))
        }
        PerAppListKind.ENTER_OVERRIDES -> controller.update {
            val overrides = it.perApp.enterOverrides
            val newOverrides = if (checked) {
                if (overrides.any { row -> row.packageName == packageName }) overrides
                else overrides + EnterOverrideRow(packageName, EnterBehavior.SEND_SHIFT_NEWLINE)
            } else {
                overrides.filterNot { row -> row.packageName == packageName }
            }
            it.copy(perApp = it.perApp.copy(enterOverrides = newOverrides))
        }
    }
}

private fun Set<String>.toggled(value: String, add: Boolean): Set<String> = if (add) this + value else this - value
