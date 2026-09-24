package brobata.physiboard.device.privileged.ring

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import brobata.physiboard.device.privileged.PrivilegedServices
import brobata.physiboard.device.titan.NotificationRingCandidate

/**
 * The notification listener that feeds the ring. Bound by the system once notification access
 * is granted (by the setup pass through the broker, or by hand); runs whether or not the
 * keyboard is the active IME and needs nothing from the broker at runtime.
 *
 * Both callbacks are guarded: this service shares the keyboard's process. The listener
 * component's name is this class's, which is what the setup pass's `allow_listener` line names
 * (never a literal from another package).
 *
 * spec: device-backlight-ring.md SS5.2, SS5.3.
 */
class NotificationRingListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        runCatching {
            val posted = sbn ?: return
            val services = PrivilegedServices.from(this) ?: return
            services.ring.onNotificationPosted(candidateOf(posted), posted.notification.color, posted.key, packageName)
        }.onFailure { Log.e(TAG, "onNotificationPosted crashed", it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        runCatching {
            val removed = sbn ?: return
            PrivilegedServices.from(this)?.ring?.onNotificationRemoved(removed.key)
        }.onFailure { Log.e(TAG, "onNotificationRemoved crashed", it) }
    }

    /** spec: SS5.3's facts. `priority` is the field the policy names; the platform's replacement (channel importance) is not what the spec keys on. */
    @Suppress("DEPRECATION")
    private fun candidateOf(sbn: StatusBarNotification): NotificationRingCandidate {
        val notification = sbn.notification
        val flags = notification.flags
        return NotificationRingCandidate(
            packageName = sbn.packageName,
            isOngoingOrForegroundService = flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isClearable = sbn.isClearable,
            priority = notification.priority,
        )
    }

    private companion object {
        const val TAG = "NotificationRing"
    }
}
