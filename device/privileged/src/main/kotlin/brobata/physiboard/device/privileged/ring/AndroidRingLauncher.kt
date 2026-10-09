package brobata.physiboard.device.privileged.ring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import brobata.physiboard.device.privileged.R
import brobata.physiboard.device.privileged.setup.PermissionProbe
import brobata.physiboard.device.titan.RingSource

/**
 * Puts the ring on a dark, locked screen by the one route Android 15+ leaves an app: a
 * full-screen notification, the mechanism alarms and calls use (D26). The announcement is
 * silent, never seen (the activity cancels it the moment it exists), and expires on its own
 * after 15 s if the system never launches the activity.
 *
 * Whether the system honours the launch on the Titan's lock screen needs the phone.
 *
 * spec: device-backlight-ring.md SS5.6.
 */
class AndroidRingLauncher(private val context: Context, private val permissions: PermissionProbe) : RingLauncher {

    private val notifications: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    override fun launch(sources: List<RingSource>) {
        val latest = sources.lastOrNull() ?: return
        val intent = NotificationRingActivity.intent(context, latest.notificationKey, latest.packageName, latest.colorArgb, demo = false)
        if (!permissions.canUseFullScreenIntent() || !permissions.areNotificationsEnabled()) {
            // spec SS5.6 point 1: a direct start is attempted and its failure logged; the system may refuse.
            startDirect(intent)
            return
        }
        ensureChannel()
        val pending = PendingIntent.getActivity(context, REQUEST_RING, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val announcement = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(brobata.physiboard.design.R.drawable.pb_ic_ring)
            .setContentTitle("Notification ring")
            .setContentText("Turning the screen on for a notification")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // "silent" on the notification itself would mark it alert-suppressed and SystemUI
            // refuses full-screen launches for those; the channel is what keeps it quiet.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .setAutoCancel(true)
            .setTimeoutAfter(ANNOUNCEMENT_TIMEOUT_MS)
            .setFullScreenIntent(pending, true)
            .build()
        notifications.notify(ANNOUNCEMENT_ID, announcement)
    }

    override fun launchDemo() {
        startDirect(NotificationRingActivity.intent(context, NotificationRingActivity.DEMO_KEY, context.packageName, DEMO_COLOR_ARGB, demo = true))
    }

    /** spec: SS5.7 step 1 ("cancels the announcement"). */
    fun cancelAnnouncement() {
        runCatching { notifications.cancel(ANNOUNCEMENT_ID) }
    }

    private fun startDirect(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (error: Exception) {
            Log.e(TAG, "direct ring start refused", error)
        }
    }

    /** spec: SS5.6 point 2: high importance, no sound, no vibration, no lights, no badge. */
    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Notification ring", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Used only to turn the screen on for the ring. Silent, and dismissed on its own."
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        notifications.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "RingLauncher"
        const val CHANNEL_ID = "physiboard_notification_ring"
        const val ANNOUNCEMENT_ID = 41
        const val ANNOUNCEMENT_TIMEOUT_MS = 15_000L
        private const val REQUEST_RING = 41

        /** spec: SS5.7.6 ("the built-in green"). */
        const val DEMO_COLOR_ARGB = 0xFF34C759.toInt()
    }
}
