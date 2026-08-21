package it.palsoftware.pastiera.inputmethod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import it.palsoftware.pastiera.SettingsManager
import java.util.concurrent.Executors

/**
 * Drives the Unihertz keyboard backlight beyond the stock 30s cap.
 *
 * The vendor's KeyboardLightController exposes a "test mode" over its system binder
 * service (`agui_functional_service`, transaction 7 = keyboardLightTest(String)):
 * arg "1" latches the LED on indefinitely, arg "0" releases it. That service is only
 * reachable at shell privilege, so we invoke it through the app's OWN embedded
 * wireless-ADB broker ([EmbeddedAdbShell]) — no separate Shizuku app required.
 *
 * Policy: hold the LED on whenever the screen is on AND ambient light is below the
 * user's lux threshold. When it is bright or the screen is off, we release the hold and
 * the keyboard falls back to the vendor's own behavior (lights on keypress, 30s timeout).
 *
 * Requires the `physi` flavor + a paired embedded broker (Wireless debugging on). Every
 * effect is a no-op when the broker cannot connect, so nothing here can crash the IME.
 */
class KeyboardBacklightManager(private val context: Context) {

    // Serializes the blocking embedded-adb shell calls off the IME's main thread.
    private val shellExecutor = Executors.newSingleThreadExecutor()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private var screenOn = true
    private var lastLux = Float.MAX_VALUE
    private var ledLatchedOn = false
    private var registered = false

    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lastLux = event.values.firstOrNull() ?: lastLux
            evaluate()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> { screenOn = true; startSensor(); evaluate() }
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; stopSensor(); setLed(false) }
            }
        }
    }

    // Watches `Settings.Global.adb_wifi_enabled`. When Wireless debugging comes on after a reboot,
    // the embedded broker can re-arm the light, so we retire the boot guidance notification. This
    // replaces the old foreground AutoReArmService — the IME is already a long-running service.
    private var wdObserverRegistered = false
    private val wirelessDebuggingObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (isWirelessDebuggingOn()) {
                runCatching { NotificationHelper.cancelBacklightGuidance(context) }
            }
        }
    }

    private fun isWirelessDebuggingOn(): Boolean =
        runCatching {
            Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1
        }.getOrDefault(false)

    private fun registerWirelessDebuggingObserver() {
        if (wdObserverRegistered) return
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(ADB_WIFI_ENABLED),
                false,
                wirelessDebuggingObserver
            )
            wdObserverRegistered = true
        }
    }

    private fun unregisterWirelessDebuggingObserver() {
        if (!wdObserverRegistered) return
        runCatching { context.contentResolver.unregisterContentObserver(wirelessDebuggingObserver) }
        wdObserverRegistered = false
    }

    fun start() {
        if (registered) return
        if (!SettingsManager.getSmartBacklightEnabled(context)) return
        registered = true
        context.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
        registerWirelessDebuggingObserver()
        startSensor()
        evaluate()
    }

    fun stop() {
        if (!registered) return
        registered = false
        stopSensor()
        unregisterWirelessDebuggingObserver()
        runCatching { context.unregisterReceiver(screenReceiver) }
        setLed(false)
    }

    /**
     * Called by the IME on each physical keypress. The vendor lights the LED on the press;
     * we re-arm the hold so it does not fade at the 30s cap while the room is dark.
     */
    fun onKeyActivity() {
        if (!registered || !SettingsManager.getSmartBacklightEnabled(context)) return
        if (shouldBeOn()) setLed(true)
    }

    private fun startSensor() {
        val sensor = lightSensor ?: return
        sensorManager?.registerListener(lightListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopSensor() {
        sensorManager?.unregisterListener(lightListener)
    }

    private fun shouldBeOn(): Boolean {
        if (!screenOn) return false
        val threshold = SettingsManager.getSmartBacklightLuxThreshold(context)
        // Hysteresis: while already on, tolerate up to 1.5x threshold before dropping.
        val effective = if (ledLatchedOn) threshold * 1.5f else threshold.toFloat()
        return lastLux <= effective
    }

    private fun evaluate() {
        if (!SettingsManager.getSmartBacklightEnabled(context)) { setLed(false); return }
        setLed(shouldBeOn())
    }

    private fun setLed(on: Boolean) {
        if (on == ledLatchedOn) return
        // Cheap gate: if the broker has never been paired it cannot possibly work, so
        // don't flip state or queue an 8s discovery — each keypress will re-check cheaply.
        if (!EmbeddedAdbShell.isPaired(context)) return
        // Update state optimistically so rapid re-entrancy on the main thread does not
        // queue duplicate (blocking) shell calls; the actual connect runs off-thread.
        ledLatchedOn = on
        shellExecutor.execute {
            if (on) {
                // Latch the LED on via the vendor's keyboardLightTest test mode.
                val ok = EmbeddedAdbShell.runShell(context, "service call $VENDOR_SERVICE $TXN_KEYBOARD_LIGHT_TEST s16 1")
                // A successful hold means the backlight is working again — retire any boot guidance.
                if (ok) runCatching { NotificationHelper.cancelBacklightGuidance(context) }
            } else {
                // Release: exit test mode, then the vendor's CLOSE broadcast powers it down.
                EmbeddedAdbShell.runShell(context, "service call $VENDOR_SERVICE $TXN_KEYBOARD_LIGHT_TEST s16 0")
                EmbeddedAdbShell.runShell(context, "am broadcast -a $CLOSE_LIGHT_ACTION")
            }
        }
    }

    companion object {
        private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
        private const val VENDOR_SERVICE = "agui_functional_service"
        // keyboardLightTest(String): "1" latches LED on, "0" releases. Verified on
        // Titan 2 Elite build V02.00.02, 2026-08-19.
        private const val TXN_KEYBOARD_LIGHT_TEST = "7"
        private const val CLOSE_LIGHT_ACTION = "agui.action.CLOSE_KEYBOARD_LIGHT"
    }
}
