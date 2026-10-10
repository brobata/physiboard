package brobata.physiboard.app.settings.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SystemUpdate
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.shell.AutoUpdater
import brobata.physiboard.core.settings.UpdateMode
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
    val autoUpdateBuild = remember { AutoUpdater.buildMayInstall(context) }
    val updateMode = controller.current.value.updates.mode
    // Re-read on every return: the choice is made in Android's own settings.
    var canInstall by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canInstall = context.packageManager.canRequestPackageInstalls()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                                context.applicationContext,
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
                // app-shell.md SS32, settings-catalog.md SS2.18: how a new version gets onto the phone.
                if (autoUpdateBuild) {
                    item {
                        SingleChoiceChipsRow(
                            label = "Updates",
                            description = updateModeDescription(updateMode),
                            options = UpdateMode.entries,
                            optionLabel = ::updateModeLabel,
                            selected = updateMode,
                            onSelect = { mode -> controller.update { it.copy(updates = it.updates.copy(mode = mode)) } },
                        )
                    }
                    // Shown only while it matters: Android's "Install unknown apps" for PhysiBoard is
                    // off. It is offered here once, as a row, never as a prompt.
                    if (updateMode != UpdateMode.OFF && !canInstall) {
                        item {
                            NavigateRow(
                                "Allow PhysiBoard to install its updates",
                                "Android asks once whether PhysiBoard may install apps. It only ever installs its own updates.",
                                icon = Icons.Outlined.SystemUpdate,
                            ) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    UpdateFoundDialog(updateState)
}

/** settings-catalog.md SS2.18: the chips' labels. */
fun updateModeLabel(mode: UpdateMode): String = when (mode) {
    UpdateMode.INSTALL_AUTOMATICALLY -> "Install automatically"
    UpdateMode.DOWNLOAD_AND_ASK -> "Download and ask me"
    UpdateMode.OFF -> "Off"
}

/** settings-catalog.md SS2.18: one plain sentence for what the chosen mode does. */
fun updateModeDescription(mode: UpdateMode): String = when (mode) {
    UpdateMode.INSTALL_AUTOMATICALLY ->
        "New versions download on Wi-Fi and install while your screen is off, so they never interrupt your typing."
    UpdateMode.DOWNLOAD_AND_ASK -> "New versions download on Wi-Fi, then a notification asks you to install them."
    UpdateMode.OFF -> "PhysiBoard only tells you when a new version is out."
}
