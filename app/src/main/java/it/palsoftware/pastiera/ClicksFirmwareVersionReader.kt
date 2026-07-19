package it.palsoftware.pastiera

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.util.UUID

object ClicksFirmwareVersionReader {
    private val deviceInformationServiceUuid: UUID =
        UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    private val firmwareRevisionUuid: UUID =
        UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    fun read(
        context: Context,
        deviceName: String,
        onResult: (String?) -> Unit
    ): Closeable {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val device = adapter?.bondedDevices?.firstOrNull { it.name == deviceName }
        if (device == null) {
            onResult(null)
            return Closeable { }
        }

        val session = FirmwareReadSession(onResult)
        session.gatt = device.connectGatt(
            context.applicationContext,
            false,
            session.callback,
            android.bluetooth.BluetoothDevice.TRANSPORT_LE
        )
        session.startTimeout()
        return session
    }

    fun isSupported(version: String): Boolean {
        val parts = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.')
            .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }
        if (parts.size < 2) return false
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major > 1 || major == 1 && (minor > 0 || minor == 0 && patch >= 9)
    }

    private class FirmwareReadSession(
        private val onResult: (String?) -> Unit
    ) : Closeable {
        private val handler = Handler(Looper.getMainLooper())
        private var completed = false
        var gatt: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!gatt.discoverServices()) finish(null)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    finish(null)
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(null)
                    return
                }
                val characteristic = gatt
                    .getService(deviceInformationServiceUuid)
                    ?.getCharacteristic(firmwareRevisionUuid)
                if (characteristic == null || !gatt.readCharacteristic(characteristic)) {
                    finish(null)
                }
            }

            @Deprecated("Used on Android 12 and older")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (characteristic.uuid == firmwareRevisionUuid) {
                    finish(characteristic.value.takeIf { status == BluetoothGatt.GATT_SUCCESS }.toFirmwareString())
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (characteristic.uuid == firmwareRevisionUuid) {
                    finish(value.takeIf { status == BluetoothGatt.GATT_SUCCESS }.toFirmwareString())
                }
            }
        }

        fun startTimeout() {
            handler.postDelayed({ finish(null) }, READ_TIMEOUT_MS)
        }

        @SuppressLint("MissingPermission")
        private fun finish(version: String?) {
            if (completed) return
            completed = true
            handler.removeCallbacksAndMessages(null)
            gatt?.close()
            gatt = null
            handler.post { onResult(version) }
        }

        override fun close() {
            if (completed) return
            completed = true
            handler.removeCallbacksAndMessages(null)
            gatt?.close()
            gatt = null
        }
    }

    private fun ByteArray?.toFirmwareString(): String? = this
        ?.toString(Charsets.UTF_8)
        ?.trim('\u0000', ' ', '\r', '\n', '\t')
        ?.takeIf { it.isNotBlank() }

    private const val READ_TIMEOUT_MS = 10_000L
}
