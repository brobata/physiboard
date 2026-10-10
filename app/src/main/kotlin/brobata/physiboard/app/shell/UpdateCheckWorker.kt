package brobata.physiboard.app.shell

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.core.shell.GithubChecks
import brobata.physiboard.core.shell.UpdateCheckResult
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The background half of the update checker (app-shell.md SS13.7): a periodic WorkManager job,
 * network-constrained, every six hours (SS32.7), that runs the same pure decision
 * ([brobata.physiboard.core.shell.UpdatePolicy], via [GithubUpdateClient]) the interactive checks
 * use. A found release goes to [AutoUpdater] first; only when it does not take the release on
 * ("Off", a build that never installs, nothing to download) is the 3.2 notification posted
 * (SS13.8). Enqueued and cancelled only from [PhysiBoardApplication] via [UpdateCheckScheduler];
 * `:ime` never schedules or runs it.
 */
class UpdateCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // spec: SS13.1, "every trigger first checks 'GitHub checks allowed'". The scheduling call
        // already gates enqueue-vs-cancel on this; this is the same gate applied to the one run
        // that could already be queued at the moment a build stops being allowed to ask GitHub.
        val installer = runCatching {
            applicationContext.packageManager.getInstallSourceInfo(applicationContext.packageName).installingPackageName
        }.getOrNull()
        if (!GithubChecks.allowed(buildFlagOn = true, installerPackageName = installer)) return Result.success()

        val app = applicationContext as PhysiBoardApplication
        val dismissedReleases = app.settingsStore.current().shell.dismissedReleases.toSet()

        // spec: SS13.7, "blocks for up to 30s waiting for the result; no result within 30s asks
        // the scheduler to retry with its default backoff."
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            GithubUpdateClient.check(BuildConfig.VERSION_NAME, dismissedReleases, ignoreDismissedReleases = true)
        } ?: return Result.retry()

        // spec: SS13.7, "a result without an update completes silently."
        if (result is UpdateCheckResult.Update && !AutoUpdater.releaseFound(applicationContext, result.release)) {
            UpdateNotifications.announce(applicationContext, result.release)
        }
        return Result.success()
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
