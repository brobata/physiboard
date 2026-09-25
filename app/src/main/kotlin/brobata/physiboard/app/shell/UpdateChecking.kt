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
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.settings.ui.LocalSettingsController
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
 * the Updates row draws); silent triggers pass no-ops.
 */
suspend fun runUpdateCheck(
    state: UpdateCheckState,
    installedVersionName: String,
    dismissedReleases: Set<String>,
    ignoreDismissedReleases: Boolean,
    onNoNetwork: () -> Unit = {},
    onUpToDate: () -> Unit = {},
) {
    state.checking = true
    try {
        when (val result = GithubUpdateClient.check(installedVersionName, dismissedReleases, ignoreDismissedReleases)) {
            is UpdateCheckResult.Update -> {
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
    AlertDialog(
        onDismissRequest = { state.dialogVisible = false }, // SS13.6: dismissing outside/Back records nothing; the card (if any) stays
        title = { Text("New update available") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Version ${release.tag} is available on GitHub.")
                // spec: SS13.6, "Download APK" only when an asset was found. AlertDialog only
                // offers two side-button slots, so this third action is drawn in the body instead
                // of fought into confirm/dismiss.
                val apkUrl = release.apkDownloadUrl
                if (apkUrl != null) {
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
    LaunchedEffect(Unit) {
        runUpdateCheck(state, installedVersionName, dismissedReleases.toSet(), ignoreDismissedReleases = true)
    }
}
