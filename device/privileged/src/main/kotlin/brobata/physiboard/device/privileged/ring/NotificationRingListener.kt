package brobata.physiboard.device.privileged.ring

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import brobata.physiboard.device.privileged.PrivilegedServices
import brobata.physiboard.device.titan.NotificationRingCandidate

/** What the connected listener lends the rest of the process: the shade, read only, never any text. spec: device-backlight-ring.md SS5.11. */
interface ShadeReader {
    /** The notifications still in the shade, as the ring's policy and colour read them. */
    fun active(): List<ActiveRingNotification>

    /** The small (status bar) icon of the notification with [notificationKey], or null when it is gone. */
    fun smallIcon(notificationKey: String): Icon?
}

/**
 * The notification listener that feeds the ring. Bound by the system once notification access
 * is granted (by the setup pass through the broker, or by hand); runs whether or not the
 * keyboard is the active IME and needs nothing from the broker at runtime.
 *
 * While connected it also hears the screen, unlock and charger broadcasts for "Stay on while
 * charging" (SS5.11.2): those can only be received by a receiver registered at run time, and this
 * service is the one component the system keeps alive for as long as the ring can work at all.
 *
 * Every callback is guarded: this service shares the keyboard's process. The listener
 * component's name is this class's, which is what the setup pass's `allow_listener` line names
 * (never a literal from another package).
 *
 * spec: device-backlight-ring.md SS5.2, SS5.3, SS5.11.2.
 */
class NotificationRingListener : NotificationListenerService() {

    private var deviceReceiver: BroadcastReceiver? = null

    private val shade = object : ShadeReader {
        override fun active(): List<ActiveRingNotification> =
            runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList()).map { sbn ->
                ActiveRingNotification(candidateOf(sbn), sbn.notification.color, sbn.key, sbn.postTime)
            }

        override fun smallIcon(notificationKey: String): Icon? =
            runCatching { getActiveNotifications(arrayOf(notificationKey))?.firstOrNull()?.notification?.smallIcon }.getOrNull()
    }

    override fun onListenerConnected() {
        runCatching {
            val services = PrivilegedServices.from(this) ?: return
            services.shade = shade
            registerDeviceReceiver(services)
        }.onFailure { Log.e(TAG, "onListenerConnected crashed", it) }
    }

    override fun onListenerDisconnected() {
        runCatching { detach() }.onFailure { Log.e(TAG, "onListenerDisconnected crashed", it) }
    }

    override fun onDestroy() {
        runCatching { detach() }.onFailure { Log.e(TAG, "onDestroy crashed", it) }
        super.onDestroy()
    }

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

    /** spec: SS5.11.2. Five protected system broadcasts; only the system can send them, so exporting the receiver opens nothing. */
    private fun registerDeviceReceiver(services: PrivilegedServices) {
        if (deviceReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                runCatching {
                    val nightstand = services.nightstand
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> nightstand.onScreenOff()
                        Intent.ACTION_SCREEN_ON -> nightstand.onScreenOn()
                        Intent.ACTION_USER_PRESENT -> nightstand.onUnlocked()
                        Intent.ACTION_POWER_CONNECTED -> nightstand.onPowerConnected()
                        Intent.ACTION_POWER_DISCONNECTED -> nightstand.onPowerDisconnected()
                    }
                }.onFailure { Log.e(TAG, "device receiver crashed", it) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        deviceReceiver = receiver
    }

    private fun detach() {
        deviceReceiver?.let { receiver -> runCatching { unregisterReceiver(receiver) } }
        deviceReceiver = null
        val services = PrivilegedServices.from(this) ?: return
        if (services.shade === shade) services.shade = null
    }

    private companion object {
        const val TAG = "NotificationRing"
    }
}

/** spec: SS5.3's facts. `priority` is the field the policy names; the platform's replacement (channel importance) is not what the spec keys on. */
@Suppress("DEPRECATION")
internal fun candidateOf(sbn: StatusBarNotification): NotificationRingCandidate {
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
