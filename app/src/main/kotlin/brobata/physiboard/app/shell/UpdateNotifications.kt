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
import brobata.physiboard.core.shell.ResolvedRelease
import brobata.physiboard.core.shell.VersionComparison

/**
 * The update channel's two notifications, sharing one id so one replaces the other (app-shell.md
 * SS13.8, SS32.4): "a new version is out" (3.2's, opens the release in a browser) and "ready, tap to
 * install" (opens [InstallUpdateActivity]). Posted only with the notification permission; without
 * it a warning is logged and nothing else happens, since a worker cannot ask.
 */
object UpdateNotifications {
    private const val CHANNEL_ID = "pastiera_update_channel"
    private const val NOTIFICATION_ID = 2

    /** spec: SS13.8. */
    fun announce(context: Context, release: ResolvedRelease) {
        // "the APK asset URL when there is one, else the release page, else the releases list";
        // ResolvedRelease.pageUrl already falls back to the releases list.
        val targetUrl = release.apkDownloadUrl ?: release.pageUrl
        val tapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, tapIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(context, "PhysiBoard - New update available", "A new version of PhysiBoard is available (${release.tag})", pendingIntent)
    }

    /** The release page for [tag], for an announcement made after the download (SS32.3). */
    fun releasePage(tag: String): String = "https://github.com/brobata/physiboard/releases/tag/$tag"

    /** spec: SS32.4. A checked APK waits for the user. */
    fun ready(context: Context, tag: String) {
        val version = VersionComparison.normalize(tag)
        val tapIntent = Intent(context, InstallUpdateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID + 1, tapIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(context, "PhysiBoard $version is ready", "Tap to install it. The keyboard restarts for a moment.", pendingIntent)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun post(context: Context, title: String, text: String, tap: PendingIntent) {
        if (!permissionGranted(context)) {
            Log.w(TAG, "\"$title\" not shown: the notification permission is not granted")
            return
        }
        val notifications = context.getSystemService(NotificationManager::class.java) ?: return
        // spec: SS16, "the update channel is created (idempotently) right before each update notification."
        notifications.createNotificationChannel(channel())
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(brobata.physiboard.design.R.drawable.pb_ic_mark)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }

    /** spec: SS13.8. Default importance, badge on, no light, vibration pattern 0/50 ms, no sound. */
    private fun channel(): NotificationChannel =
        NotificationChannel(CHANNEL_ID, "PhysiBoard Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Notifications about new PhysiBoard versions"
            setShowBadge(true)
            enableLights(false)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 50)
            setSound(null, null)
        }

    /** POST_NOTIFICATIONS only exists from Android 13; below that a notification permission is implicit. */
    private fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private const val TAG = "UpdateNotifications"
}
