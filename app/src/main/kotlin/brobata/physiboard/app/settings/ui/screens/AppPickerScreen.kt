package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.settings.ui.AppCatalog
import brobata.physiboard.app.settings.ui.AppPickerBody
import brobata.physiboard.app.settings.ui.InstalledApp
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
 *
 * [PerAppListKind.ENTER_OVERRIDES] delegates to [EnterOverridesScreen] instead of the plain
 * on/off body below: per-app-behavior.md SS3.11 also needs a send-method and extra-shortcut
 * chooser per row, which the shared switch-only body was never built to carry.
 */
@Composable
fun AppPickerScreen(kind: String, onBack: () -> Unit) {
    if (kind == PerAppListKind.ENTER_OVERRIDES) {
        EnterOverridesScreen(onBack = onBack)
        return
    }
    val controller = LocalSettingsController.current
    val settings = controller.current.value
    val context = LocalContext.current

    val (title, selected) = when (kind) {
        PerAppListKind.EXACT_TYPING -> "Terminal mode" to settings.perApp.exactTypingPackages
        PerAppListKind.TEXT_BOX_UNDER_BAR -> "Text box under the bar" to settings.perApp.nudgePackages
        PerAppListKind.STATUS_BAR_APPS -> "Choose apps" to settings.statusBar.apps
        PerAppListKind.ENTER_OVERRIDES -> "App overrides" to settings.perApp.enterOverrides.map { it.packageName }.toSet()
        else -> error("unknown per-app list kind: $kind")
    }

    val apps = remember(kind) { AppCatalog.installedApps(context, alsoInclude = selected) }

    // spec: per-app-behavior.md SS4.4 (the screen's own summary and explanation) and SS4.3 ("The
    // Terminal mode list screen tells the user about the expansion on web-app rows; no other
    // screen does"). The summary is one line so the app list keeps the screen (SS6.1).
    val summary = if (kind == PerAppListKind.EXACT_TYPING) {
        "No corrections or capitals in these apps; Ctrl, Esc, Tab and Alt symbols go straight through."
    } else {
        null
    }
    val details = if (kind == PerAppListKind.EXACT_TYPING) {
        "In the apps you pick here, PhysiBoard sends every keystroke as-is, without correction or " +
            "capitalisation. Word suggestions, autocorrect, auto-capitalisation, double-space " +
            "periods and text expansion are all turned off in these apps. Ctrl stays Ctrl (Ctrl+C, " +
            "Ctrl+A, Ctrl+Z reach the app instead of copying or undoing), Esc, Tab and the arrows " +
            "go straight through, and Alt and Sym symbols arrive as real key presses, so terminals " +
            "and SSH clients get exactly the keys you pressed."
    } else {
        null
    }
    val noteFor: ((InstalledApp) -> String?)? = if (kind == PerAppListKind.EXACT_TYPING) {
        { app -> AppCatalog.webApkNote(context, app.packageName) }
    } else {
        null
    }

    SettingsScreenScaffold(title = title, onBack = onBack) {
        AppPickerBody(
            apps = apps,
            selected = selected,
            summary = summary,
            details = details,
            detailsTitle = "About terminal mode",
            noteFor = noteFor,
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
