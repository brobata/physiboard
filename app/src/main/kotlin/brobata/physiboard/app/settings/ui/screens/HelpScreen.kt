package brobata.physiboard.app.settings.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.shell.UpdateFoundDialog
import brobata.physiboard.app.shell.rememberUpdateCheckState
import brobata.physiboard.app.shell.runUpdateCheck
import brobata.physiboard.app.shell.toast
import brobata.physiboard.core.shell.GithubChecks
import kotlinx.coroutines.launch

/**
 * "Help" (docs/plans/settings-reorganization.md): the places to go when something is not right,
 * gathered from the old Settings screen and About. The status check, a field to try the keyboard
 * in, the key-event logger, the tutorial again, and (for a GitHub install only, app-shell.md SS9)
 * a manual update check.
 */
@Composable
fun HelpScreen(onBack: () -> Unit, onNavigate: (String) -> Unit, onShowTutorial: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current
    val scope = rememberCoroutineScope()
    val updateState = rememberUpdateCheckState()
    val installer = remember { runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull() }
    val githubChecksAllowed = remember { GithubChecks.allowed(buildFlagOn = true, installerPackageName = installer) }

    SettingsScreenScaffold(title = "Help", onBack = onBack) {
        RowList {
            header("")
            item {
                NavigateRow("Status check", "Is PhysiBoard on, chosen and able to reach the phone's settings?", icon = Icons.Outlined.CheckCircle) {
                    onNavigate(Routes.STATUS)
                }
            }
            item { NavigateRow("Test field", "A place to type, to try the keyboard", icon = Icons.Outlined.EditNote) { onNavigate(Routes.TEST_FIELD) } }
            item {
                NavigateRow("Diagnostics", "Log the keys you press and export a report", icon = Icons.Outlined.BugReport) {
                    onNavigate(Routes.DIAGNOSTICS)
                }
            }
            header("")
            item { NavigateRow("Show the tutorial", "The first-run pages again: turn on, choose, extras", icon = Icons.Outlined.School, onClick = onShowTutorial) }
            if (githubChecksAllowed) {
                item {
                    NavigateRow(
                        label = if (updateState.checking) "Checking for updates…" else "Check for updates",
                        description = "Looks for a newer release on GitHub",
                        icon = Icons.Outlined.SystemUpdate,
                        value = BuildConfig.VERSION_NAME,
                        enabled = !updateState.checking,
                    ) {
                        scope.launch {
                            runUpdateCheck(
                                updateState,
                                BuildConfig.VERSION_NAME,
                                controller.current.value.shell.dismissedReleases.toSet(),
                                ignoreDismissedReleases = false,
                                onNoNetwork = { toast(context, "Unable to reach GitHub.") },
                                onUpToDate = { toast(context, "App is up to date.") },
                                onBlocked = { reason -> toast(context, reason) },
                            )
                        }
                    }
                }
            }
        }
    }

    UpdateFoundDialog(updateState)
}
