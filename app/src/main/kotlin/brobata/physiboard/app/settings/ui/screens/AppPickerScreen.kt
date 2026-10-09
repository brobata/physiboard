package brobata.physiboard.app.settings.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.settings.ui.AppCatalog
import brobata.physiboard.app.settings.ui.AppPickerBody
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.PerAppListKind
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold

/**
 * The screen behind the per-app lists (rebuild-from-scratch.md: "a searchable app picker over
 * installed packages with a switch per app"). Two lists have a screen: Terminal mode
 * ([PerAppListKind.EXACT_TYPING]) and the Enter overrides. The strip's app list and the dip list
 * only ever applied to the suggestion strip, which is gone, so they have none (their stored keys
 * stay, for backups).
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
    require(kind == PerAppListKind.EXACT_TYPING) { "unknown per-app list kind: $kind" }
    val controller = LocalSettingsController.current
    val selected = controller.current.value.perApp.exactTypingPackages
    val context = LocalContext.current
    val apps = remember(kind) { AppCatalog.installedApps(context, alsoInclude = selected) }

    // spec: per-app-behavior.md SS4.4 (the screen's own summary and explanation) and SS4.3 ("The
    // Terminal mode list screen tells the user about the expansion on web-app rows; no other
    // screen does"). The summary is one line so the app list keeps the screen (SS6.1).
    SettingsScreenScaffold(title = "Terminal mode", onBack = onBack) {
        AppPickerBody(
            apps = apps,
            selected = selected,
            summary = "No corrections or capitals in these apps; Ctrl, Esc, Tab and Alt symbols go straight through.",
            details = "In the apps you pick here, PhysiBoard sends every keystroke as-is, without correction or " +
                "capitalisation. Autocorrect, auto-capitalisation, double-space periods and text expansion " +
                "are all turned off in these apps. Ctrl stays Ctrl (Ctrl+C, Ctrl+A, Ctrl+Z reach the app " +
                "instead of copying or undoing), Esc, Tab and the arrows go straight through, and Alt and " +
                "Sym symbols arrive as real key presses, so terminals and SSH clients get exactly the keys " +
                "you pressed.",
            detailsTitle = "About terminal mode",
            noteFor = { app -> AppCatalog.webApkNote(context, app.packageName) },
            onToggle = { packageName, checked ->
                controller.update {
                    val current = it.perApp.exactTypingPackages
                    it.copy(perApp = it.perApp.copy(exactTypingPackages = if (checked) current + packageName else current - packageName))
                }
            },
        )
    }
}
