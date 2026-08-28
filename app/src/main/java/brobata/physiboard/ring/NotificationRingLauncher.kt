package brobata.physiboard.ring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import brobata.physiboard.R

/**
 * Gets [NotificationRingActivity] onto a locked, dark screen.
 *
 * An app cannot simply start an activity from a background service: since Android 10 the
 * system refuses, and the overlay-permission exemption was narrowed in Android 15 to apps
 * that already have a *visible* overlay — which the lock screen hides. What the system will
 * do is launch an activity itself, on the app's behalf, for a full-screen notification: the
 * mechanism alarms and incoming calls use. So the ring is announced with a silent
 * full-screen notification that the activity cancels the moment it appears. The user never
 * sees it; the system sees exactly the kind of request it is built to honour.
 */
object NotificationRingLauncher {
    private const val TAG = "NotificationRing"
    private const val CHANNEL_ID = "physiboard_notification_ring"
    const val NOTIFICATION_ID = 41

    fun show(context: Context, source: RingSource) {
        val intent = NotificationRingActivity.intent(context, source)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!NotificationRingSetup.canUseFullScreenIntent(context) || !nm.areNotificationsEnabled()) {
            // Without the full-screen route the only option is to ask directly. The system may
            // decline; the ring screen offers the grant that makes this reliable.
            Log.w(TAG, "full-screen intent unavailable; trying a direct start")
            runCatching { context.startActivity(intent) }
                .onFailure { Log.e(TAG, "direct start failed", it) }
            return
        }
        ensureChannel(nm, context)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lightbulb_24)
            .setContentTitle(context.getString(R.string.ring_title))
            .setContentText(context.getString(R.string.ring_launcher_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // Silence comes from the channel. setSilent() would also mark the group-alert
            // behaviour as suppressive, and SystemUI refuses full-screen launches for those.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .setAutoCancel(true)
            .setTimeoutAfter(LAUNCHER_TIMEOUT_MS)
            .setFullScreenIntent(pending, true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.e(TAG, "notify failed", it) }
    }

    /** Called by the activity as soon as it is up: the announcement has done its job. */
    fun dismissAnnouncement(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(nm: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.ring_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.ring_channel_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    /** If the system never launched the activity, the announcement should not linger. */
    private const val LAUNCHER_TIMEOUT_MS = 15_000L
}
