package brobata.physiboard.app.shell

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.R
import brobata.physiboard.core.shell.GithubChecks
import brobata.physiboard.core.shell.ResolvedRelease
import brobata.physiboard.core.shell.UpdateCheckResult
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The daily background half of the update checker (app-shell.md SS13.7): a periodic WorkManager
 * job, network-constrained, that runs the same pure decision
 * ([brobata.physiboard.core.shell.UpdatePolicy], via [GithubUpdateClient]) the interactive checks
 * use, and posts [postUpdateNotification] (SS13.8) when it finds a release. Enqueued and cancelled
 * only from [PhysiBoardApplication] via [UpdateCheckScheduler]; `:ime` never schedules or runs it.
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
        if (result is UpdateCheckResult.Update) postUpdateNotification(applicationContext, result.release)
        return Result.success()
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}

/**
 * spec: SS13.8, SS16's update-channel row. Posted only when the notification permission is
 * granted; "a missing result... logs a warning and does nothing otherwise" (SS13.7) rather than
 * letting the post throw.
 */
private fun postUpdateNotification(context: Context, release: ResolvedRelease) {
    if (!notificationPermissionGranted(context)) {
        Log.w("UpdateCheckWorker", "update available (${release.tag}) but the notification permission is not granted")
        return
    }

    val notifications = context.getSystemService(NotificationManager::class.java)
    // spec: SS16, "the update channel is created (idempotently) right before each update notification."
    notifications.createNotificationChannel(updateChannel())

    // spec: SS13.8, "the APK asset URL when there is one, else the release page, else the releases list."
    // ResolvedRelease.pageUrl already falls back to the releases list when the release has no page URL.
    val targetUrl = release.apkDownloadUrl ?: release.pageUrl
    val tapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, tapIntent, PendingIntent.FLAG_IMMUTABLE)

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(brobata.physiboard.design.R.drawable.pb_ic_mark)
        .setContentTitle("PhysiBoard - New update available")
        .setContentText("A new version of PhysiBoard is available (${release.tag})")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    notifications.notify(NOTIFICATION_ID, notification)
}

/** spec: SS13.8. Default importance, badge on, no light, vibration pattern 0/50 ms, no sound. */
private fun updateChannel(): NotificationChannel =
    NotificationChannel(CHANNEL_ID, "PhysiBoard Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Notifications about new PhysiBoard versions"
        setShowBadge(true)
        enableLights(false)
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 50)
        setSound(null, null)
    }

/** POST_NOTIFICATIONS only exists from Android 13; below that a notification permission is implicit. */
private fun notificationPermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private const val CHANNEL_ID = "pastiera_update_channel"
private const val NOTIFICATION_ID = 2
