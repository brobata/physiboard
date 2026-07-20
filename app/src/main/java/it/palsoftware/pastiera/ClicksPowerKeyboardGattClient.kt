package it.palsoftware.pastiera

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.util.UUID

data class ClicksPowerKeyboardState(
    val connected: Boolean = false,
    val ready: Boolean = false,
    val mtu: Int = 23,
    val model: String? = null,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val batteryPercent: Int? = null,
    val backlightBrightness: Int? = null,
    val chargingReservePercent: Int? = null,
    val backlightTimeoutSeconds: Int? = null,
    val idleTimeoutSeconds: Int? = null,
    val featureFlags: Int? = null,
    val specialKeyEnableFlags: Int? = null,
    val numberKeyEnableFlags: Int? = null,
    val wirelessChargingEnabled: Boolean? = null,
    val activeHostSlot: Int? = null,
    val hostConfigurations: List<Int?> = List(9) { null },
    val hostNames: List<String?> = List(9) { null },
    val tabRemap: ByteArray? = null,
    val geminiRemap: ByteArray? = null,
    val altRemap: ByteArray? = null,
    val backspaceRemap: ByteArray? = null,
    val numberRemaps: List<ByteArray?> = List(9) { null },
    val error: String? = null
) {
    fun hasFeature(flag: Int): Boolean? = featureFlags?.let { it and flag != 0 }
    fun supportsHostNameWrites(): Boolean = mtu >= 41
}

@SuppressLint("MissingPermission")
class ClicksPowerKeyboardGattClient(
    context: Context,
    deviceName: String,
    private val onStateChanged: (ClicksPowerKeyboardState) -> Unit
) : Closeable {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val operations = ArrayDeque<Operation>()
    private var currentOperation: Operation? = null
    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var servicesDiscoveryStarted = false
    private var closed = false
    private var state = ClicksPowerKeyboardState()
    private val operationTimeout = Runnable { completeCurrent(null) }
    private val mtuFallback = Runnable { gatt?.let(::discoverServices) }
    private val connectionTimeout = Runnable {
        fail(appContext.getString(R.string.clicks_gatt_error_connection_timeout))
    }

    private sealed class Operation(val onResult: (ByteArray?) -> Unit) {
        class StandardRead(val characteristic: BluetoothGattCharacteristic, onResult: (ByteArray?) -> Unit) :
            Operation(onResult)
        class Control(
            val frameProvider: () -> ByteArray?,
            val expectedGroup: Int,
            onResult: (ByteArray?) -> Unit
        ) : Operation(onResult) {
            constructor(frame: ByteArray, expectedGroup: Int, onResult: (ByteArray?) -> Unit) :
                this({ frame }, expectedGroup, onResult)
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (closed) return@post
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    updateState(state.copy(connected = true, error = null))
                    servicesDiscoveryStarted = false
                    if (gatt.requestMtu(REQUESTED_MTU)) {
                        handler.postDelayed(mtuFallback, MTU_CALLBACK_TIMEOUT_MS)
                    } else {
                        discoverServices(gatt)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    fail(appContext.getString(R.string.clicks_gatt_error_disconnected, status))
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            handler.post {
                if (closed) return@post
                handler.removeCallbacks(mtuFallback)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    updateState(state.copy(mtu = mtu))
                }
                discoverServices(gatt)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.post {
                if (closed) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail(appContext.getString(R.string.clicks_gatt_error_services))
                    return@post
                }
                val service = gatt.getService(CONTROL_SERVICE_UUID)
                val notify = service?.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)
                commandCharacteristic = service?.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
                val descriptor = notify?.getDescriptor(CCCD_UUID)
                if (notify == null || commandCharacteristic == null || descriptor == null ||
                    !gatt.setCharacteristicNotification(notify, true)
                ) {
                    fail(appContext.getString(R.string.clicks_gatt_error_control_service))
                    return@post
                }
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                        BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                if (!started) fail(appContext.getString(R.string.clicks_gatt_error_notifications))
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.post {
                if (closed || descriptor.uuid != CCCD_UUID) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail(appContext.getString(R.string.clicks_gatt_error_notifications))
                } else {
                    handler.removeCallbacks(connectionTimeout)
                    updateState(state.copy(ready = true, error = null))
                    enqueueInitialReads(gatt)
                }
            }
        }

        @Deprecated("Used on Android 12 and older")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            handleCharacteristicRead(characteristic, characteristic.value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) = handleCharacteristicRead(characteristic, value, status)

        @Deprecated("Used on Android 12 and older")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleNotification(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) = handleNotification(characteristic, value)
    }

    init {
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        val device = adapter?.bondedDevices?.firstOrNull { it.name.equals(deviceName, ignoreCase = true) }
            ?: adapter?.bondedDevices?.firstOrNull { it.name?.startsWith(DEVICE_NAME_PREFIX, ignoreCase = true) == true }
        if (device == null) {
            fail(appContext.getString(R.string.clicks_gatt_error_not_bonded))
        } else {
            gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                fail(appContext.getString(R.string.clicks_gatt_error_connect_start))
            } else {
                handler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MS)
            }
        }
    }

    fun refresh() {
        val activeGatt = gatt ?: return
        if (!state.ready) return
        operations.clear()
        enqueueInitialReads(activeGatt)
    }

    fun refreshChargingInputs() {
        val activeGatt = gatt ?: return
        if (!state.ready) return
        val battery = activeGatt.getService(BATTERY_SERVICE_UUID)
        enqueueStandardRead(battery?.getCharacteristic(BATTERY_LEVEL_UUID)) {
            updateState(state.copy(batteryPercent = it?.firstOrNull()?.toInt()?.and(0xff)))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_CHARGING_RESERVE, 1) {
            updateState(state.copy(chargingReservePercent = it.firstOrNull()?.toInt()?.and(0xff)))
        }
        enqueue(Operation.Control(
            ClicksPowerKeyboardProtocol.readWirelessCharging(),
            ClicksPowerKeyboardProtocol.GROUP_READ_WIRELESS
        ) { payload ->
            payload?.takeIf { it.size >= 4 }?.let {
                updateState(state.copy(wirelessChargingEnabled = it[3].toInt() and 0x04 == 0x04))
            }
        })
    }

    fun setBacklightEnabled(enabled: Boolean) = updateFeatureFlag(
        ClicksPowerKeyboardProtocol.FLAG_BACKLIGHT,
        enabled
    )

    fun setSymLock(enabled: Boolean) = updateFeatureFlag(
        ClicksPowerKeyboardProtocol.FLAG_SYM_LOCK,
        enabled
    )

    fun setCapsLock(enabled: Boolean) = updateFeatureFlag(
        ClicksPowerKeyboardProtocol.FLAG_CAPS_LOCK,
        enabled
    )

    fun setSoftReturn(enabled: Boolean) = updateFeatureFlag(
        ClicksPowerKeyboardProtocol.FLAG_SOFT_RETURN,
        enabled
    )

    fun setCursorMode(enabled: Boolean) = updateFeatureFlag(
        ClicksPowerKeyboardProtocol.FLAG_CURSOR_MODE,
        enabled
    )

    fun setBacklightBrightness(percent: Int) {
        val normalized = percent.coerceIn(0, 100)
        val raw = ((normalized / 100.0) * 255).toInt().coerceIn(0, 255)
        enqueueWrite(
            ClicksPowerKeyboardProtocol.writeConfig(
                ClicksPowerKeyboardProtocol.COMMAND_BACKLIGHT_BRIGHTNESS,
                raw
            ),
            ClicksPowerKeyboardProtocol.GROUP_WRITE_CONFIG
        ) { updateState(state.copy(backlightBrightness = normalized)) }
    }

    fun setChargingReserve(percent: Int) {
        val normalized = percent.coerceIn(0, 100)
        enqueueWrite(
            ClicksPowerKeyboardProtocol.writeConfig(
                ClicksPowerKeyboardProtocol.COMMAND_CHARGING_RESERVE,
                normalized
            ),
            ClicksPowerKeyboardProtocol.GROUP_WRITE_CONFIG
        ) { updateState(state.copy(chargingReservePercent = normalized)) }
    }

    fun setBacklightTimeout(seconds: Int) {
        val value = ClicksPowerKeyboardProtocol.littleEndian16((seconds * 1000).coerceIn(0, 0xffff))
        enqueueWrite(
            ClicksPowerKeyboardProtocol.writeConfigBytes(
                ClicksPowerKeyboardProtocol.COMMAND_BACKLIGHT_TIMEOUT,
                value
            ),
            ClicksPowerKeyboardProtocol.GROUP_WRITE_CONFIG
        ) { updateState(state.copy(backlightTimeoutSeconds = seconds)) }
    }

    fun setIdleTimeout(seconds: Int) {
        val value = ClicksPowerKeyboardProtocol.littleEndian16(seconds.coerceIn(0, 0xffff))
        enqueueWrite(
            ClicksPowerKeyboardProtocol.writeConfigBytes(
                ClicksPowerKeyboardProtocol.COMMAND_IDLE_TIMEOUT,
                value
            ),
            ClicksPowerKeyboardProtocol.GROUP_WRITE_CONFIG
        ) { updateState(state.copy(idleTimeoutSeconds = seconds)) }
    }

    fun setWirelessCharging(enabled: Boolean) {
        enqueueWrite(
            ClicksPowerKeyboardProtocol.writeWirelessCharging(enabled),
            ClicksPowerKeyboardProtocol.GROUP_WRITE_WIRELESS
        ) { updateState(state.copy(wirelessChargingEnabled = enabled)) }
    }

    fun setHostName(slotIndex: Int, name: String) {
        if (state.mtu < MIN_HOST_NAME_MTU) return
        val normalizedName = ClicksPowerKeyboardProtocol.normalizeHostName(name)
        enqueueWrite(
            ClicksPowerKeyboardProtocol.writeHostName(slotIndex, normalizedName),
            ClicksPowerKeyboardProtocol.GROUP_WRITE_HOST_NAME
        ) {
            val updated = state.hostNames.toMutableList().also { it[slotIndex] = normalizedName }
            updateState(state.copy(hostNames = updated))
        }
    }

    fun setSpecialKeyRemap(command: Int, bytes: ByteArray) {
        val target = requireNotNull(ClicksPowerKeyboardProtocol.remapTarget(command))
        require(bytes.size == 2)
        val disable = bytes.all { it == 0.toByte() }
        if (disable) {
            writeRemapEnableFlags(target, enabled = false, bytes = null)
        } else {
            enqueueWrite(
                ClicksPowerKeyboardProtocol.writeConfigBytes(command, bytes),
                ClicksPowerKeyboardProtocol.GROUP_WRITE_CONFIG
            ) {
                writeRemapEnableFlags(target, enabled = true, bytes = bytes, prioritize = true)
            }
        }
    }

    private fun writeRemapEnableFlags(
        target: ClicksPowerKeyboardProtocol.RemapTarget,
        enabled: Boolean,
        bytes: ByteArray?,
        prioritize: Boolean = false
    ) {
        var writtenFlags: Int? = null
        enqueue(
            Operation.Control(
                frameProvider = {
                    currentRemapEnableFlags(target)?.let { currentFlags ->
                        val updatedFlags = if (enabled) {
                            currentFlags or target.enableFlag
                        } else {
                            currentFlags and target.enableFlag.inv()
                        }
                        writtenFlags = updatedFlags
                        ClicksPowerKeyboardProtocol.writeConfig(target.enableCommand, updatedFlags)
                    }
                },
                expectedGroup = ClicksPowerKeyboardProtocol.GROUP_WRITE_CONFIG
            ) { payload ->
                if (payload != null) {
                    writtenFlags?.let { flags ->
                        updateState(state.withRemap(target, flags, bytes?.copyOf()))
                    }
                }
            },
            prioritize = prioritize
        )
    }

    private fun currentRemapEnableFlags(target: ClicksPowerKeyboardProtocol.RemapTarget): Int? =
        when (target.enableCommand) {
            ClicksPowerKeyboardProtocol.COMMAND_SPECIAL_KEY_ENABLE_FLAGS -> state.specialKeyEnableFlags
            else -> state.numberKeyEnableFlags
        }

    private fun ClicksPowerKeyboardState.withRemap(
        target: ClicksPowerKeyboardProtocol.RemapTarget,
        flags: Int,
        bytes: ByteArray?
    ): ClicksPowerKeyboardState {
        val withFlags = when (target.enableCommand) {
            ClicksPowerKeyboardProtocol.COMMAND_SPECIAL_KEY_ENABLE_FLAGS -> copy(specialKeyEnableFlags = flags)
            else -> copy(numberKeyEnableFlags = flags)
        }
        return when (target.command) {
            ClicksPowerKeyboardProtocol.COMMAND_TAB_REMAP -> withFlags.copy(tabRemap = bytes)
            ClicksPowerKeyboardProtocol.COMMAND_GEMINI_REMAP -> withFlags.copy(geminiRemap = bytes)
            ClicksPowerKeyboardProtocol.COMMAND_ALT_REMAP -> withFlags.copy(altRemap = bytes)
            ClicksPowerKeyboardProtocol.COMMAND_BACKSPACE_REMAP -> withFlags.copy(backspaceRemap = bytes)
            else -> {
                val index = ClicksPowerKeyboardProtocol.NUMBER_REMAP_COMMANDS.indexOf(target.command)
                if (index < 0) withFlags else withFlags.copy(
                    numberRemaps = withFlags.numberRemaps.toMutableList().also { it[index] = bytes }
                )
            }
        }
    }

    private fun updateFeatureFlag(flag: Int, enabled: Boolean) {
        var writtenFlags: Int? = null
        enqueue(Operation.Control(
            frameProvider = {
                state.featureFlags?.let { currentFlags ->
                    val updatedFlags = if (enabled) currentFlags or flag else currentFlags and flag.inv()
                    writtenFlags = updatedFlags
                    ClicksPowerKeyboardProtocol.writeConfig(
                        ClicksPowerKeyboardProtocol.COMMAND_FEATURE_FLAGS,
                        updatedFlags
                    )
                }
            },
            expectedGroup = ClicksPowerKeyboardProtocol.GROUP_WRITE_CONFIG
        ) { payload ->
            if (payload != null) {
                writtenFlags?.let { updateState(state.copy(featureFlags = it)) }
            }
        })
    }

    private fun enqueueInitialReads(gatt: BluetoothGatt) {
        val deviceInfo = gatt.getService(DEVICE_INFORMATION_SERVICE_UUID)
        val battery = gatt.getService(BATTERY_SERVICE_UUID)
        enqueueStandardRead(deviceInfo?.getCharacteristic(MODEL_NUMBER_UUID)) {
            updateState(state.copy(model = it.toGattString()))
        }
        enqueueStandardRead(deviceInfo?.getCharacteristic(SERIAL_NUMBER_UUID)) {
            updateState(state.copy(serialNumber = it.toGattString()))
        }
        enqueueStandardRead(deviceInfo?.getCharacteristic(FIRMWARE_REVISION_UUID)) {
            updateState(state.copy(firmwareVersion = it.toGattString()))
        }
        enqueueStandardRead(battery?.getCharacteristic(BATTERY_LEVEL_UUID)) {
            updateState(state.copy(batteryPercent = it?.firstOrNull()?.toInt()?.and(0xff)))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_BACKLIGHT_BRIGHTNESS, 1) {
            val raw = it.firstOrNull()?.toInt()?.and(0xff)
            updateState(state.copy(backlightBrightness = raw?.let { value -> (value * 100 + 127) / 255 }))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_CHARGING_RESERVE, 1) {
            updateState(state.copy(chargingReservePercent = it.firstOrNull()?.toInt()?.and(0xff)))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_BACKLIGHT_TIMEOUT, 2) {
            updateState(state.copy(backlightTimeoutSeconds =
                ClicksPowerKeyboardProtocol.decodeLittleEndian16(it)?.div(1000)))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_IDLE_TIMEOUT, 2) {
            updateState(state.copy(idleTimeoutSeconds = ClicksPowerKeyboardProtocol.decodeLittleEndian16(it)))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_FEATURE_FLAGS, 1) {
            updateState(state.copy(featureFlags = it.firstOrNull()?.toInt()?.and(0xff)))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_ACTIVE_HOST, 1) {
            updateState(state.copy(activeHostSlot = it.firstOrNull()?.toInt()?.and(0xff)))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_HOSTS_1_TO_4, 1) {
            updateHostConfigurations(startIndex = 0, count = 4, packed = it.firstOrNull())
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_HOSTS_5_TO_8, 1) {
            updateHostConfigurations(startIndex = 4, count = 4, packed = it.firstOrNull())
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_HOST_9, 1) {
            updateHostConfigurations(startIndex = 8, count = 1, packed = it.firstOrNull())
        }
        listOf(0x0000, 0x0060, 0x00C0).forEach { offset ->
            enqueue(Operation.Control(
                ClicksPowerKeyboardProtocol.readHostNames(offset, 0x60),
                ClicksPowerKeyboardProtocol.GROUP_READ_HOST_NAMES
            ) { payload ->
                if (payload != null && payload.size == 0x60) {
                    val updated = state.hostNames.toMutableList()
                    repeat(3) { blockIndex ->
                        val slotIndex = offset / 32 + blockIndex
                        val block = payload.copyOfRange(blockIndex * 32, (blockIndex + 1) * 32)
                        updated[slotIndex] = ClicksPowerKeyboardProtocol.decodeHostName(block)
                    }
                    updateState(state.copy(hostNames = updated))
                }
            })
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_SPECIAL_KEY_ENABLE_FLAGS, 1) {
            updateState(state.copy(specialKeyEnableFlags = it.firstOrNull()?.toInt()?.and(0xff)))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_NUMBER_KEY_ENABLE_FLAGS, 1) {
            updateState(state.copy(numberKeyEnableFlags = it.firstOrNull()?.toInt()?.and(0xff)))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_TAB_REMAP, 2) {
            val enabled = state.specialKeyEnableFlags?.and(ClicksPowerKeyboardProtocol.FLAG_TAB_REMAP_ENABLED) ==
                ClicksPowerKeyboardProtocol.FLAG_TAB_REMAP_ENABLED
            updateState(state.copy(tabRemap = it.takeIf { value -> enabled && value.size == 2 }?.copyOf()))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_GEMINI_REMAP, 2) {
            val enabled = state.specialKeyEnableFlags?.and(ClicksPowerKeyboardProtocol.FLAG_GEMINI_REMAP_ENABLED) ==
                ClicksPowerKeyboardProtocol.FLAG_GEMINI_REMAP_ENABLED
            updateState(state.copy(geminiRemap = it.takeIf { value -> enabled && value.size == 2 }?.copyOf()))
        }
        enqueueConfigRead(ClicksPowerKeyboardProtocol.COMMAND_ALT_REMAP, 2) {
            val enabled = state.specialKeyEnableFlags?.and(ClicksPowerKeyboardProtocol.FLAG_ALT_REMAP_ENABLED) ==
                ClicksPowerKeyboardProtocol.FLAG_ALT_REMAP_ENABLED
            updateState(state.copy(altRemap = it.takeIf { value -> enabled && value.size == 2 }?.copyOf()))
        }
        enqueueRemapRead(ClicksPowerKeyboardProtocol.COMMAND_BACKSPACE_REMAP)
        ClicksPowerKeyboardProtocol.NUMBER_REMAP_COMMANDS.forEach(::enqueueRemapRead)
        enqueue(Operation.Control(
            ClicksPowerKeyboardProtocol.readWirelessCharging(),
            ClicksPowerKeyboardProtocol.GROUP_READ_WIRELESS
        ) { payload ->
            payload?.takeIf { it.size >= 4 }?.let {
                updateState(state.copy(wirelessChargingEnabled = it[3].toInt() and 0x04 == 0x04))
            }
        })
    }

    private fun discoverServices(gatt: BluetoothGatt) {
        if (servicesDiscoveryStarted) return
        servicesDiscoveryStarted = true
        if (!gatt.discoverServices()) fail(appContext.getString(R.string.clicks_gatt_error_services))
    }

    private fun enqueueRemapRead(command: Int) {
        val target = ClicksPowerKeyboardProtocol.remapTarget(command) ?: return
        enqueueConfigRead(command, 2) { payload ->
            val flags = when (target.enableCommand) {
                ClicksPowerKeyboardProtocol.COMMAND_SPECIAL_KEY_ENABLE_FLAGS -> state.specialKeyEnableFlags
                else -> state.numberKeyEnableFlags
            } ?: return@enqueueConfigRead
            val enabled = flags.and(target.enableFlag) == target.enableFlag
            updateState(state.withRemap(target, flags, payload.takeIf { enabled && it.size == 2 }?.copyOf()))
        }
    }

    private fun updateHostConfigurations(startIndex: Int, count: Int, packed: Byte?) {
        val value = packed?.toInt()?.and(0xff) ?: return
        val updated = state.hostConfigurations.toMutableList()
        repeat(count) { offset -> updated[startIndex + offset] = value ushr (offset * 2) and 0x03 }
        updateState(state.copy(hostConfigurations = updated))
    }

    private fun enqueueConfigRead(command: Int, length: Int, result: (ByteArray) -> Unit) {
        enqueue(Operation.Control(
            ClicksPowerKeyboardProtocol.readConfig(command, length),
            ClicksPowerKeyboardProtocol.GROUP_READ_CONFIG
        ) { payload -> payload?.let(result) })
    }

    private fun enqueueStandardRead(
        characteristic: BluetoothGattCharacteristic?,
        result: (ByteArray?) -> Unit
    ) {
        if (characteristic == null) {
            result(null)
        } else {
            enqueue(Operation.StandardRead(characteristic, result))
        }
    }

    private fun enqueueWrite(frame: ByteArray, expectedGroup: Int, onSuccess: () -> Unit) {
        enqueue(Operation.Control(frame, expectedGroup) { payload -> if (payload != null) onSuccess() })
    }

    private fun enqueue(operation: Operation, prioritize: Boolean = false) {
        if (prioritize) operations.addFirst(operation) else operations.addLast(operation)
        runNextOperation()
    }

    private fun runNextOperation() {
        if (closed || currentOperation != null) return
        val operation = operations.removeFirstOrNull() ?: return
        currentOperation = operation
        val activeGatt = gatt
        val started = when (operation) {
            is Operation.StandardRead -> activeGatt?.readCharacteristic(operation.characteristic) == true
            is Operation.Control -> {
                val characteristic = commandCharacteristic
                val frame = operation.frameProvider()
                if (activeGatt == null || characteristic == null || frame == null) false else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activeGatt.writeCharacteristic(
                        characteristic,
                        frame,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    ) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    characteristic.value = frame
                    @Suppress("DEPRECATION")
                    activeGatt.writeCharacteristic(characteristic)
                }
            }
        }
        if (!started) {
            completeCurrent(null)
        } else {
            handler.postDelayed(operationTimeout, OPERATION_TIMEOUT_MS)
        }
    }

    private fun handleCharacteristicRead(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        handler.post {
            val operation = currentOperation as? Operation.StandardRead ?: return@post
            if (operation.characteristic.uuid == characteristic.uuid) {
                completeCurrent(value.takeIf { status == BluetoothGatt.GATT_SUCCESS })
            }
        }
    }

    private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        handler.post {
            if (characteristic.uuid != NOTIFY_CHARACTERISTIC_UUID) return@post
            val operation = currentOperation as? Operation.Control ?: return@post
            val response = ClicksPowerKeyboardProtocol.parseResponse(value) ?: return@post
            if (response.group == operation.expectedGroup) {
                completeCurrent(response.payload.takeIf { response.status == 0 })
            }
        }
    }

    private fun completeCurrent(result: ByteArray?) {
        handler.removeCallbacks(operationTimeout)
        val operation = currentOperation ?: return
        currentOperation = null
        operation.onResult(result)
        runNextOperation()
    }

    private fun updateState(newState: ClicksPowerKeyboardState) {
        state = newState
        onStateChanged(newState)
    }

    private fun fail(message: String) {
        if (closed) return
        operations.clear()
        currentOperation = null
        handler.removeCallbacks(operationTimeout)
        handler.removeCallbacks(mtuFallback)
        handler.removeCallbacks(connectionTimeout)
        updateState(state.copy(connected = false, ready = false, error = message))
    }

    override fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        operations.clear()
        currentOperation = null
        gatt?.close()
        gatt = null
    }

    private fun ByteArray?.toGattString(): String? = this
        ?.toString(Charsets.UTF_8)
        ?.trim('\u0000', ' ', '\r', '\n', '\t')
        ?.takeIf(String::isNotBlank)


    companion object {
        private const val DEVICE_NAME_PREFIX = "Power Keyboard"
        private const val OPERATION_TIMEOUT_MS = 3_000L
        private const val MTU_CALLBACK_TIMEOUT_MS = 2_000L
        private const val CONNECTION_TIMEOUT_MS = 12_000L
        private const val REQUESTED_MTU = 517
        private const val MIN_HOST_NAME_MTU = 41

        val CONTROL_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val DEVICE_INFORMATION_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_UUID: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val SERIAL_NUMBER_UUID: UUID = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REVISION_UUID: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }
}
