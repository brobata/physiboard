package brobata.physiboard.app.shell

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import brobata.physiboard.core.shell.AutoUpdatePolicy
import java.util.concurrent.TimeUnit

/**
 * Arms or disarms the background update check (app-shell.md SS13.7, every six hours since SS32.7) from the one place that runs
 * regardless of whether the launcher activity or the keyboard service brought this process up:
 * [brobata.physiboard.app.PhysiBoardApplication]. `:ime` never calls this.
 */
object UpdateCheckScheduler {

    /** spec: SS13.7's job name, `pastiera_update_check` (the historical channel/job naming this line keeps). */
    const val UNIQUE_WORK_NAME = "pastiera_update_check"

    /**
     * spec: SS13.7, SS32.7. A periodic job with a six-hour period and a "connected network"
     * constraint when GitHub checks are allowed; "when checks are not allowed the job is cancelled
     * instead." [githubChecksAllowed] is
     * [brobata.physiboard.core.shell.GithubChecks.allowed]: a build that must not call GitHub
     * never schedules the job, here or anywhere else.
     */
    fun scheduleOrCancel(context: Context, githubChecksAllowed: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!githubChecksAllowed) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(AutoUpdatePolicy.CHECK_PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // UPDATE, not KEEP: a phone that already has 3.2's 24-hour job is moved to the six-hour period
        // (KEEP would leave it on 24 hours forever), and the job's schedule is still not restarted
        // by every launch (SS13.7), because UPDATE keeps the existing work's enqueue time.
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
