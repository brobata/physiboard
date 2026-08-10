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
    val lastKnownDeviceName: String? = null,
    val phoneBatteryPercent: Int? = null,
    val manualChargingUntil: Long = 0L,
    val socCalibration: ClicksPowerSocCalibrationStatus? = null,
    val keyboard: ClicksPowerKeyboardState = ClicksPowerKeyboardState()
)

/** Process-wide Clicks connection used by both the IME and the settings UI. */
object ClicksPowerKeyboardController {
    private lateinit var context: Context
    private var initialized = false
    private var client: ClicksPowerKeyboardGattClient? = null
    private var connectedDeviceName: String? = null
    private var pendingAutomaticChargingState: Boolean? = null
    private val buttonRemapWritesInProgress = mutableSetOf<ClicksButtonBindingTarget>()
    private val buttonRemapCallbacks = mutableMapOf<ClicksButtonBindingTarget, (Boolean) -> Unit>()
    private var manualOverrideExpiredOnReconnect = false
    private lateinit var socCalibrationStore: ClicksPowerSocCalibrationStore
    private var socCalibrationTracker: ClicksPowerSocCalibrationTracker? = null
    private var socCalibrationKeyboardId: String? = null
    private var phonePluggedType = ClicksPhonePluggedType.UNKNOWN
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
        socCalibrationStore = SharedPreferencesClicksPowerSocCalibrationStore(
            context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        )
        SettingsManager.getMostRecentClicksPowerKeyboardSnapshot(context)?.let { snapshot ->
            state = state.copy(
                lastKnownDeviceName = snapshot.deviceName,
                socCalibration = loadSocCalibrationStatus(snapshot.state),
                keyboard = ClicksPowerKeyboardStateSnapshotCodec.forOfflineDisplay(snapshot.state)
            )
        }
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
                phonePluggedType = intent?.let { batteryIntent ->
                    if (batteryIntent.hasExtra(BatteryManager.EXTRA_PLUGGED)) {
                        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                        when {
                            plugged == 0 -> ClicksPhonePluggedType.NONE
                            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> ClicksPhonePluggedType.AC
                            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> ClicksPhonePluggedType.USB
                            plugged and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> ClicksPhonePluggedType.DOCK
                            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 ->
                                ClicksPhonePluggedType.WIRELESS
                            else -> ClicksPhonePluggedType.UNKNOWN
                        }
                    } else {
                        ClicksPhonePluggedType.UNKNOWN
                    }
                } ?: ClicksPhonePluggedType.UNKNOWN
                if (level >= 0 && scale > 0) {
                    state = state.copy(phoneBatteryPercent = level * 100 / scale)
                    observeSocCalibration()
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

    internal fun requestButtonBinding(
        target: ClicksButtonBindingTarget,
        binding: ClicksDesiredButtonBinding,
        onComplete: (Boolean) -> Unit = {}
    ): ClicksButtonBindingRequestStatus {
        check(initialized)
        SettingsManager.setClicksDesiredButtonBinding(context, target, binding)
        if (ClicksButtonBindingSyncPolicy.isConfirmed(state.keyboard, target, binding.firmwareOutput)) {
            return ClicksButtonBindingRequestStatus.CONFIRMED
        }
        if (!ClicksButtonBindingSyncPolicy.canWrite(state.keyboard) || client == null) {
            return ClicksButtonBindingRequestStatus.PENDING_CONNECTION
        }
        // A newer selection supersedes the UI callback of an older in-flight write. The older
        // firmware write may still finish, but only the latest request owns the visible result.
        buttonRemapCallbacks[target] = onComplete
        startDesiredButtonRemapWrite(target, binding)
        return ClicksButtonBindingRequestStatus.APPLYING
    }

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
        val previousClient = client
        client = null
        previousClient?.close()
        finishAllButtonRemapCallbacks(success = false)
        handler.removeCallbacks(chargingRefresh)
        socCalibrationTracker?.disconnect()
        socCalibrationTracker = null
        socCalibrationKeyboardId = null
        connectedDeviceName = deviceName
        pendingAutomaticChargingState = null
        val storedManualUntil = SettingsManager.getClicksManualChargingUntil(context)
        manualOverrideExpiredOnReconnect = deviceName != null &&
            storedManualUntil > 0L && storedManualUntil <= System.currentTimeMillis()
        if (manualOverrideExpiredOnReconnect) {
            SettingsManager.setClicksManualChargingUntil(context, 0L)
        }
        val rememberedSnapshot = if (deviceName == null) {
            state.lastKnownDeviceName?.let {
                SettingsManager.getClicksPowerKeyboardSnapshot(context, it)
            } ?: SettingsManager.getMostRecentClicksPowerKeyboardSnapshot(context)
        } else {
            SettingsManager.getClicksPowerKeyboardSnapshot(context, deviceName)
        }
        state = ClicksPowerKeyboardControllerState(
            deviceName = deviceName,
            lastKnownDeviceName = rememberedSnapshot?.deviceName ?: if (deviceName == null) {
                state.lastKnownDeviceName
            } else {
                deviceName
            },
            phoneBatteryPercent = state.phoneBatteryPercent,
            manualChargingUntil = activeManualChargingUntil(),
            socCalibration = rememberedSnapshot?.state?.let(::loadSocCalibrationStatus),
            keyboard = rememberedSnapshot?.let { ClicksPowerKeyboardStateSnapshotCodec.forOfflineDisplay(it.state) }
                ?: ClicksPowerKeyboardStateSnapshotCodec.forOfflineDisplay(state.keyboard)
        )
        publish()
        if (deviceName != null && hasBluetoothPermission()) {
            client = ClicksPowerKeyboardGattClient(
                context = context,
                deviceName = deviceName,
                initialState = state.keyboard
            ) { keyboardState ->
                state = state.copy(keyboard = keyboardState)
                if (keyboardState.sessionValidated && !keyboardState.stale && keyboardState.serialNumber != null) {
                    SettingsManager.saveClicksPowerKeyboardSnapshot(context, deviceName, keyboardState)
                }
                observeSocCalibration()
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
        if (!keyboardState.ready || !keyboardState.sessionValidated) return
        if (keyboardState.stale ||
            keyboardState.batteryPercentStale ||
            keyboardState.chargingReservePercentStale ||
            keyboardState.wirelessChargingEnabledStale
        ) return
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
        reconcileDesiredButtonBindings()
    }

    private fun reconcileDesiredButtonBindings() {
        if (!ClicksButtonBindingSyncPolicy.canWrite(state.keyboard) || client == null) return
        ClicksButtonBindingTarget.entries.forEach { target ->
            val desired = SettingsManager.getClicksDesiredButtonBinding(context, target) ?: return@forEach
            if (!ClicksButtonBindingSyncPolicy.isConfirmed(state.keyboard, target, desired.firmwareOutput)) {
                startDesiredButtonRemapWrite(target, desired)
            }
        }
    }

    private fun startDesiredButtonRemapWrite(
        target: ClicksButtonBindingTarget,
        desired: ClicksDesiredButtonBinding
    ) {
        if (!buttonRemapWritesInProgress.add(target)) return
        val activeClient = client
        if (activeClient == null) {
            buttonRemapWritesInProgress.remove(target)
            finishButtonRemapCallback(target, success = false)
            return
        }
        activeClient.setSpecialKeyRemap(target.firmwareCommand, desired.firmwareOutput) {
            buttonRemapWritesInProgress.remove(target)
            val latestDesired = SettingsManager.getClicksDesiredButtonBinding(context, target)
            val latestDesiredConfirmed = latestDesired != null &&
                ClicksButtonBindingSyncPolicy.isConfirmed(
                    state.keyboard,
                    target,
                    latestDesired.firmwareOutput
                )
            when (
                ClicksButtonBindingCompletionPolicy.resolve(
                    attemptedOutput = desired.firmwareOutput,
                    latestDesiredOutput = latestDesired?.firmwareOutput,
                    latestDesiredConfirmed = latestDesiredConfirmed
                )
            ) {
                ClicksButtonBindingCompletion.SUCCESS ->
                    finishButtonRemapCallback(target, success = true)
                ClicksButtonBindingCompletion.FAILURE ->
                    finishButtonRemapCallback(target, success = false)
                ClicksButtonBindingCompletion.KEEP_PENDING -> Unit
            }
            reconcileDesiredButtonBindings()
        }
    }

    private fun finishButtonRemapCallback(target: ClicksButtonBindingTarget, success: Boolean) {
        buttonRemapCallbacks.remove(target)?.invoke(success)
    }

    private fun finishAllButtonRemapCallbacks(success: Boolean) {
        val callbacks = buttonRemapCallbacks.values.toList()
        buttonRemapCallbacks.clear()
        callbacks.forEach { it(success) }
    }

    /**
     * Creates a tracker only once the GATT serial number is known. The input-device name is not a
     * stable enough identity to persist a calibration for a specific keyboard.
     */
    private fun observeSocCalibration() {
        val keyboard = state.keyboard
        if (!keyboard.hasFreshSocCalibrationInputs()) return
        val keyboardId = keyboard.serialNumber?.trim()?.takeIf(String::isNotEmpty) ?: return
        if (keyboardId != socCalibrationKeyboardId) {
            socCalibrationTracker?.disconnect()
            socCalibrationKeyboardId = keyboardId
            socCalibrationTracker = ClicksPowerSocCalibrationTracker(keyboardId, socCalibrationStore)
        }
        val phoneBattery = state.phoneBatteryPercent ?: return
        val keyboardBattery = keyboard.batteryPercent ?: return
        val wirelessCharging = keyboard.wirelessChargingEnabled ?: return
        val tracker = socCalibrationTracker ?: return
        tracker.observe(
            ClicksPowerSocSnapshot(
                timestampMillis = System.currentTimeMillis(),
                phoneBatteryPercent = phoneBattery,
                keyboardBatteryPercent = keyboardBattery,
                wirelessChargingEnabled = wirelessCharging,
                phonePluggedType = phonePluggedType
            )
        )
        state = state.copy(socCalibration = tracker.status())
    }

    private fun loadSocCalibrationStatus(
        keyboardState: ClicksPowerKeyboardState
    ): ClicksPowerSocCalibrationStatus? {
        val keyboardId = keyboardState.serialNumber?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return (socCalibrationStore.load(keyboardId) ?: ClicksPowerSocCalibrationAggregate()).status()
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
