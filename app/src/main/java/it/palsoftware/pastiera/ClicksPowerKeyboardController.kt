package it.palsoftware.pastiera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import java.io.Closeable

data class ClicksPowerKeyboardControllerState(
    val deviceName: String? = null,
    val phoneBatteryPercent: Int? = null,
    val manualChargingUntil: Long = 0L,
    val keyboard: ClicksPowerKeyboardState = ClicksPowerKeyboardState()
)

/** Process-wide Clicks connection used by both the IME and the settings UI. */
object ClicksPowerKeyboardController {
    private lateinit var context: Context
    private var initialized = false
    private var client: ClicksPowerKeyboardGattClient? = null
    private var connectedDeviceName: String? = null
    private var pendingAutomaticChargingState: Boolean? = null
    private var manualOverrideExpiredOnReconnect = false
    private val handler = Handler(Looper.getMainLooper())
    private val chargingRefresh = object : Runnable {
        override fun run() {
            if (connectedDeviceName != null) {
                refreshManualOverrideState()
                client?.refreshChargingInputs()
                handler.postDelayed(this, CHARGING_REFRESH_INTERVAL_MS)
            }
        }
    }
    private var state = ClicksPowerKeyboardControllerState()
    private val listeners = linkedSetOf<(ClicksPowerKeyboardControllerState) -> Unit>()

    fun initialize(appContext: Context) {
        if (initialized) return
        initialized = true
        context = appContext.applicationContext
        val inputManager = context.getSystemService(InputManager::class.java)
        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = updateConnection()
            override fun onInputDeviceRemoved(deviceId: Int) = updateConnection()
            override fun onInputDeviceChanged(deviceId: Int) = updateConnection()
        }, null)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (level >= 0 && scale > 0) {
                    state = state.copy(phoneBatteryPercent = level * 100 / scale)
                    publishAndEvaluate()
                }
            }
        }, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key?.startsWith("clicks_charging_") == true) evaluateChargingAutomation()
                }
            )
        updateConnection()
    }

    fun onBluetoothPermissionChanged() = updateConnection(forceReconnect = true)

    fun observe(listener: (ClicksPowerKeyboardControllerState) -> Unit): Closeable {
        listeners += listener
        listener(state)
        return Closeable { listeners -= listener }
    }

    fun currentState(): ClicksPowerKeyboardControllerState = state
    fun activeClient(): ClicksPowerKeyboardGattClient? = client

    fun setManualChargingOverride(enabled: Boolean) {
        if (!initialized) return
        val until = if (enabled) {
            System.currentTimeMillis() + MANUAL_CHARGING_DURATION_MS
        } else {
            0L
        }
        SettingsManager.setClicksManualChargingUntil(context, until)
        state = state.copy(manualChargingUntil = until)
        publish()
        if (!enabled && !SettingsManager.isClicksChargingAutomationEnabled(context)) {
            pendingAutomaticChargingState = false
            client?.setWirelessCharging(false)
        } else {
            evaluateChargingAutomation()
        }
    }

    private fun updateConnection(forceReconnect: Boolean = false) {
        if (!initialized) return
        val deviceName = InputDevice.getDeviceIds().asSequence()
            .mapNotNull(InputDevice::getDevice)
            .firstOrNull(DeviceSpecific::isClicksPowerKeyboard)
            ?.name
        if (!forceReconnect && deviceName == connectedDeviceName) return
        client?.close()
        handler.removeCallbacks(chargingRefresh)
        client = null
        connectedDeviceName = deviceName
        pendingAutomaticChargingState = null
        val storedManualUntil = SettingsManager.getClicksManualChargingUntil(context)
        manualOverrideExpiredOnReconnect = deviceName != null &&
            storedManualUntil > 0L && storedManualUntil <= System.currentTimeMillis()
        if (manualOverrideExpiredOnReconnect) {
            SettingsManager.setClicksManualChargingUntil(context, 0L)
        }
        state = ClicksPowerKeyboardControllerState(
            deviceName = deviceName,
            phoneBatteryPercent = state.phoneBatteryPercent,
            manualChargingUntil = activeManualChargingUntil()
        )
        publish()
        if (deviceName != null && hasBluetoothPermission()) {
            client = ClicksPowerKeyboardGattClient(context, deviceName) { keyboardState ->
                state = state.copy(keyboard = keyboardState)
                if (pendingAutomaticChargingState == keyboardState.wirelessChargingEnabled) {
                    pendingAutomaticChargingState = null
                }
                publishAndEvaluate()
            }
            handler.postDelayed(chargingRefresh, CHARGING_REFRESH_INTERVAL_MS)
        }
    }

    private fun evaluateChargingAutomation() {
        if (!initialized || connectedDeviceName == null) return
        val keyboardState = state.keyboard
        if (!keyboardState.ready) return
        val keyboardBattery = keyboardState.batteryPercent ?: return
        val reserve = keyboardState.chargingReservePercent ?: return
        val charging = keyboardState.wirelessChargingEnabled ?: return
        if (manualOverrideExpiredOnReconnect) {
            manualOverrideExpiredOnReconnect = false
            if (!SettingsManager.isClicksChargingAutomationEnabled(context)) {
                if (charging && pendingAutomaticChargingState != false) {
                    pendingAutomaticChargingState = false
                    client?.setWirelessCharging(false)
                }
                return
            }
        }
        val manualOverrideActive = activeManualChargingUntil() > 0L
        if (manualOverrideActive) {
            val desired = keyboardBattery > reserve
            if (desired != charging && pendingAutomaticChargingState != desired) {
                pendingAutomaticChargingState = desired
                client?.setWirelessCharging(desired)
            }
            return
        }
        if (!SettingsManager.isClicksChargingAutomationEnabled(context)) return
        val phone = state.phoneBatteryPercent ?: return
        val desired = when {
            !charging && phone <= SettingsManager.getClicksChargingStartPercent(context) &&
                keyboardBattery > reserve -> true
            charging && (phone >= SettingsManager.getClicksChargingStopPercent(context) ||
                keyboardBattery <= reserve) -> false
            else -> charging
        }
        if (desired != charging && pendingAutomaticChargingState != desired) {
            pendingAutomaticChargingState = desired
            client?.setWirelessCharging(desired)
        }
    }

    private fun publishAndEvaluate() {
        publish()
        evaluateChargingAutomation()
    }

    private fun publish() = listeners.toList().forEach { it(state) }

    private fun activeManualChargingUntil(): Long {
        val until = SettingsManager.getClicksManualChargingUntil(context)
        return until.takeIf { it > System.currentTimeMillis() } ?: 0L
    }

    private fun refreshManualOverrideState() {
        val activeUntil = activeManualChargingUntil()
        if (state.manualChargingUntil != activeUntil) {
            val expired = state.manualChargingUntil > 0L && activeUntil == 0L
            if (activeUntil == 0L) SettingsManager.setClicksManualChargingUntil(context, 0L)
            state = state.copy(manualChargingUntil = activeUntil)
            publish()
            if (expired && !SettingsManager.isClicksChargingAutomationEnabled(context)) {
                pendingAutomaticChargingState = false
                client?.setWirelessCharging(false)
            } else {
                evaluateChargingAutomation()
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private const val CHARGING_REFRESH_INTERVAL_MS = 60_000L
    private const val MANUAL_CHARGING_DURATION_MS = 15 * 60_000L
}
