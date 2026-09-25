package brobata.physiboard.app.shell

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Arms or disarms the daily update check (app-shell.md SS13.7) from the one place that runs
 * regardless of whether the launcher activity or the keyboard service brought this process up:
 * [brobata.physiboard.app.PhysiBoardApplication]. `:ime` never calls this.
 */
object UpdateCheckScheduler {

    /** spec: SS13.7's job name, `pastiera_update_check` (the historical channel/job naming this line keeps). */
    const val UNIQUE_WORK_NAME = "pastiera_update_check"

    /**
     * spec: SS13.7. "A periodic job... is enqueued with a 24-hour period, a 'connected network'
     * constraint, and keep-if-existing policy" when GitHub checks are allowed; "when checks are
     * not allowed the job is cancelled instead." [githubChecksAllowed] is
     * [brobata.physiboard.core.shell.GithubChecks.allowed]: a build that must not call GitHub
     * never schedules the job, here or anywhere else.
     */
    fun scheduleOrCancel(context: Context, githubChecksAllowed: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!githubChecksAllowed) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // ExistingPeriodicWorkPolicy.KEEP: "the period is not reset by every launch" (SS13.7).
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
