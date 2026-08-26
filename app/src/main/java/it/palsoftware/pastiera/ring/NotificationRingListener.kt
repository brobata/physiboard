package it.palsoftware.pastiera.ring

import android.content.ComponentName
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import it.palsoftware.pastiera.SettingsManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Watches notifications so the ring can answer them.
 *
 * The system binds this service itself once notification access is granted; it runs whether
 * or not the keyboard is in use and needs nothing from the ADB broker at runtime. Every
 * decision about *whether* to ring is in [NotificationRingPolicy]; this class only gathers
 * the facts and checks the two things a policy object cannot: that the screen is off and
 * that the phone is not face-down or in a pocket.
 */
class NotificationRingListener : NotificationListenerService() {

    private val worker = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val enabled = SettingsManager.isNotificationRingEnabled(this)
        Log.d(TAG, "posted ${sbn.packageName} enabled=$enabled")
        if (!enabled) return
        val n = sbn.notification
        val candidate = NotificationRingPolicy.Candidate(
            packageName = sbn.packageName,
            flags = n.flags,
            isClearable = sbn.isClearable,
            priority = n.priority,
            color = n.color
        )
        val skip = NotificationRingPolicy.skipReason(candidate, packageName)
        if (skip != null) {
            Log.d(TAG, "skip ${sbn.packageName}: $skip")
            return
        }
        val source = RingSource(
            key = sbn.key,
            packageName = sbn.packageName,
            color = SettingsManager.getNotificationRingAppColors(this)[sbn.packageName]
                ?: NotificationRingPolicy.ringColor(n.color)
        )
        val showing = NotificationRingActivity.current
        if (showing != null) {
            showing.runOnUiThread { showing.addSource(source) }
            return
        }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isInteractive) return
        worker.execute {
            if (isCovered()) {
                Log.d(TAG, "skip ${sbn.packageName}: proximity covered")
                return@execute
            }
            NotificationRingLauncher.show(this, source)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val showing = NotificationRingActivity.current ?: return
        showing.runOnUiThread { showing.removeSource(sbn.key) }
    }

    /**
     * True when the proximity sensor reports something against the glass. Waits at most
     * [PROXIMITY_WAIT_MS] for a reading; no sensor, or no reading in time, counts as clear —
     * a missed ring is cheaper than a phone that never rings.
     */
    private fun isCovered(): Boolean {
        val sensors = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return false
        val latch = CountDownLatch(1)
        var covered = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                covered = event.values[0] < min(sensor.maximumRange, NEAR_CM)
                latch.countDown()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)) return false
        try {
            latch.await(PROXIMITY_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
        } finally {
            sensors.unregisterListener(listener)
        }
        return covered
    }

    companion object {
        private const val TAG = "NotificationRing"
        private const val PROXIMITY_WAIT_MS = 300L
        private const val NEAR_CM = 5f

        fun componentName(context: Context) =
            ComponentName(context, NotificationRingListener::class.java)

        /** Whether the user (or the broker) has granted notification access. */
        fun isAccessGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
