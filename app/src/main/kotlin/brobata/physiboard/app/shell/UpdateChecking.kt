package brobata.physiboard.app.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.core.settings.UpdateMode
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.core.shell.NetworkDecision
import brobata.physiboard.core.shell.NetworkPurpose
import brobata.physiboard.core.shell.ResolvedRelease
import brobata.physiboard.core.shell.UpdateCheckResult

/**
 * The one state holder every update-check trigger shares (app-shell.md SS13.1): home and settings
 * creation run a silent check that ignores dismissed releases; the "Updates" row runs a visible
 * one that does not. A found release always shows [UpdateFoundDialog]; only the caller decides
 * whether to also keep an "Update available" card on the page (home only, SS6.3).
 */
class UpdateCheckState {
    var checking by mutableStateOf(false)
        internal set

    /** spec: SS6.3: "a found update both shows the dialog at once and leaves the card on the page." Outlives the dialog being dismissed outside/Back (SS13.6). */
    var foundRelease by mutableStateOf<ResolvedRelease?>(null)
        internal set

    /** The tag of a release already downloaded and checked (app-shell.md SS32.4), which turns "Download APK" into "Install now". */
    var readyTag by mutableStateOf<String?>(null)
        internal set

    /** Whether the updater took the found release on (it is downloading or downloaded); only then does the dialog say what happens next. */
    var updaterTookIt by mutableStateOf(false)
        internal set

    /** Whether [UpdateFoundDialog] is currently drawn; tapping the home "Update available" card reopens it (SS6.3) without re-running the check. */
    var dialogVisible by mutableStateOf(false)
        internal set

    fun reopenDialog() {
        if (foundRelease != null) dialogVisible = true
    }
}

@Composable
fun rememberUpdateCheckState(): UpdateCheckState = remember { UpdateCheckState() }

/**
 * Runs one check (app-shell.md SS13.1-SS13.9). [ignoreDismissedReleases] is true for the silent
 * triggers and false for the manual "Updates" row. [onNoNetwork] and [onUpToDate] are only ever
 * invoked by the manual row (SS9: "no version returned" vs. "up to date" are a distinction only
 * the Updates row draws); silent triggers pass no-ops. [onBlocked] likewise only matters to the
 * manual row: private mode refused the check (SS31.2).
 */
suspend fun runUpdateCheck(
    context: Context,
    state: UpdateCheckState,
    installedVersionName: String,
    dismissedReleases: Set<String>,
    ignoreDismissedReleases: Boolean,
    onNoNetwork: () -> Unit = {},
    onUpToDate: () -> Unit = {},
    onBlocked: (reason: String) -> Unit = {},
) {
    // app-shell.md SS31.2: in private mode nothing is sent. The manual row says why; the silent
    // triggers pass a no-op and simply find nothing, the same as no network.
    val decision = GatedHttp.decide(NetworkPurpose.UPDATE_CHECK)
    if (decision is NetworkDecision.Blocked) {
        onBlocked(decision.reason)
        return
    }
    state.checking = true
    try {
        when (val result = GithubUpdateClient.check(installedVersionName, dismissedReleases, ignoreDismissedReleases)) {
            is UpdateCheckResult.Update -> {
                // app-shell.md SS32: a found release also starts the background download (or is
                // already downloaded), whichever trigger found it.
                state.updaterTookIt = runCatching { AutoUpdater.releaseFound(context, result.release) }.getOrDefault(false)
                state.readyTag = runCatching { AutoUpdater.readyTag(context) }.getOrNull()
                state.foundRelease = result.release
                state.dialogVisible = true
            }
            UpdateCheckResult.NoUpdate -> {
                // spec: SS13.9. Only the manual row tells "no network" apart from "up to date"; a
                // network failure and "nothing eligible" both land here from the pure decision, so
                // this Android-side split (no body vs. a body with a verdict) is glue's job. A
                // best-effort re-fetch would double the network cost for a distinction 2.x itself
                // only shows on the one row that asks for it, so the manual caller re-runs the raw
                // fetch below when it wants the split.
                if (!ignoreDismissedReleases) {
                    val body = GithubUpdateClient.fetchReleasesBody()
                    if (body == null) onNoNetwork() else onUpToDate()
                }
            }
        }
    } finally {
        state.checking = false
    }
}

/** spec: SS13.6, the update dialog. */
@Composable
fun UpdateFoundDialog(state: UpdateCheckState) {
    if (!state.dialogVisible) return
    val release = state.foundRelease ?: return
    val context = LocalContext.current
    val controller = LocalSettingsController.current
    val mode = controller.current.value.updates.mode
    val mayInstall = remember { AutoUpdater.buildMayInstall(context) }
    val ready = state.readyTag == release.tag
    AlertDialog(
        onDismissRequest = { state.dialogVisible = false }, // SS13.6: dismissing outside/Back records nothing; the card (if any) stays
        title = { Text("New update available") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Version ${release.tag} is available on GitHub.")
                // app-shell.md SS32.4: what the updater does with it, in one plain sentence.
                updateDialogNote(mode, mayInstall && state.updaterTookIt, ready)?.let { note ->
                    Text(note, modifier = androidx.compose.ui.Modifier.padding(top = 8.dp))
                }
                // spec: SS13.6, "Download APK" only when an asset was found. AlertDialog only
                // offers two side-button slots, so this third action is drawn in the body instead
                // of fought into confirm/dismiss. SS32.4: once the APK is downloaded and checked,
                // the same place offers to install it now.
                val apkUrl = release.apkDownloadUrl
                if (ready && mayInstall && mode != UpdateMode.OFF) {
                    TextButton(onClick = {
                        context.startActivity(Intent(context, InstallUpdateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        state.dialogVisible = false
                    }) { Text("Install now") }
                } else if (apkUrl != null) {
                    TextButton(onClick = { openInBrowser(context, apkUrl) }) { Text("Download APK") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openInBrowser(context, release.pageUrl.ifBlank { "https://github.com/brobata/physiboard/releases" })
                state.dialogVisible = false
            }) { Text("Open GitHub") }
        },
        dismissButton = {
            TextButton(onClick = {
                controller.update { it.copy(shell = it.shell.copy(dismissedReleases = brobata.physiboard.core.shell.UpdatePolicy.withDismissed(it.shell.dismissedReleases, release.tag))) }
                state.dialogVisible = false
                state.foundRelease = null // "Later" is the one action that also stops the card: the release is now dismissed
            }) { Text("Later") }
        },
    )
}

/** The dialog's line about what happens next (app-shell.md SS32.4); none when updates are Off or this build never installs. */
fun updateDialogNote(mode: UpdateMode, mayInstall: Boolean, ready: Boolean): String? = when {
    !mayInstall || mode == UpdateMode.OFF -> null
    mode == UpdateMode.INSTALL_AUTOMATICALLY && ready -> "It is downloaded and checked, and installs by itself the next time your screen is off."
    mode == UpdateMode.INSTALL_AUTOMATICALLY -> "PhysiBoard downloads it on Wi-Fi and installs it by itself the next time your screen is off."
    ready -> "It is downloaded and checked, ready to install."
    else -> "PhysiBoard downloads it on Wi-Fi and lets you know when it is ready to install."
}

fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** The "Updates" settings row's own toasts (app-shell.md SS9). */
fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

/**
 * Runs the silent check once per screen creation (app-shell.md SS6.5, SS9's "the settings screen
 * itself also runs one automatic update check"). Callers gate this on [brobata.physiboard.core.shell.GithubChecks.allowed].
 */
@Composable
fun AutoUpdateCheckOnCreate(state: UpdateCheckState, installedVersionName: String, dismissedReleases: List<String>) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        runUpdateCheck(context, state, installedVersionName, dismissedReleases.toSet(), ignoreDismissedReleases = true)
    }
}
