package brobata.physiboard.device.privileged.ring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import brobata.physiboard.device.titan.PocketCheck
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * [PocketProbe] over the proximity sensor: one reading, at most 300 ms of waiting, unregistered
 * as soon as it arrives or the wait ends. No sensor, a registration failure and no reading in
 * time all count as clear (the decision is [PocketCheck]'s: "a missed ring is cheaper than a
 * phone that never rings"). Blocks the calling worker, never the main thread.
 *
 * spec: device-backlight-ring.md SS5.5, D25.
 */
class ProximityPocketCheck(private val context: Context) : PocketProbe {

    override fun isCovered(): Boolean {
        val manager = context.getSystemService(SensorManager::class.java) ?: return false
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return false
        val latch = CountDownLatch(1)
        val reading = AtomicReference<Float?>(null)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                reading.set(event.values.firstOrNull())
                latch.countDown()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = try {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        } catch (error: Exception) {
            Log.e(TAG, "proximity registration failed", error)
            false
        }
        if (!registered) return false
        try {
            latch.await(PocketCheck.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            manager.unregisterListener(listener)
        }
        return PocketCheck.isCovered(reading.get(), sensor.maximumRange)
    }

    private companion object {
        const val TAG = "PocketCheck"
    }
}
