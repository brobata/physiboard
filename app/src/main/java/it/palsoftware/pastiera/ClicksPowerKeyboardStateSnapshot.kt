package it.palsoftware.pastiera

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * The durable boundary for Clicks Power Keyboard data. Connection lifecycle fields deliberately
 * stay out of this snapshot: a saved value is never evidence that a keyboard is connected.
 */
data class ClicksPowerKeyboardStateSnapshot(
    val deviceName: String,
    val state: ClicksPowerKeyboardState,
    val savedAtMillis: Long
) {
    val identity: String
        get() = state.serialNumber?.trim()?.takeIf(String::isNotEmpty)?.let { "serial:$it" }
            ?: "name:${deviceName.trim().lowercase()}"
}

enum class ClicksBatteryReserveSource { Live, LastKnown }

data class ClicksBatteryReserveDisplay(
    val reservePercent: Int,
    val keyboardBatteryPercent: Int?,
    val source: ClicksBatteryReserveSource
)

internal fun ClicksPowerKeyboardState.batteryReserveDisplay(): ClicksBatteryReserveDisplay? =
    chargingReservePercent?.let {
        ClicksBatteryReserveDisplay(
            reservePercent = it,
            keyboardBatteryPercent = batteryPercent,
            source = if (stale || batteryPercentStale || chargingReservePercentStale) {
                ClicksBatteryReserveSource.LastKnown
            } else {
                ClicksBatteryReserveSource.Live
            }
        )
    }

internal object ClicksPowerKeyboardStateSnapshotCodec {
    private const val VERSION = 1

    fun encode(snapshot: ClicksPowerKeyboardStateSnapshot): String = JSONObject().apply {
        put("version", VERSION)
        put("deviceName", snapshot.deviceName)
        put("savedAtMillis", snapshot.savedAtMillis)
        put("state", stateToJson(snapshot.state))
    }.toString()

    fun decode(serialized: String): ClicksPowerKeyboardStateSnapshot? = runCatching {
        val root = JSONObject(serialized)
        if (root.optInt("version") != VERSION) return null
        val deviceName = root.optString("deviceName").takeIf(String::isNotBlank) ?: return null
        ClicksPowerKeyboardStateSnapshot(
            deviceName = deviceName,
            state = stateFromJson(root.getJSONObject("state")),
            savedAtMillis = root.optLong("savedAtMillis").coerceAtLeast(0L)
        )
    }.getOrNull()

    fun forStorage(state: ClicksPowerKeyboardState): ClicksPowerKeyboardState = state.copy(
        connected = false,
        ready = false,
        mtu = 23,
        stale = false,
        sessionValidated = false,
        batteryPercentStale = false,
        chargingReservePercentStale = false,
        wirelessChargingEnabledStale = false,
        error = null
    )

    fun forOfflineDisplay(state: ClicksPowerKeyboardState): ClicksPowerKeyboardState =
        forStorage(state).copy(
            stale = state.containsMeaningfulDeviceData(),
            batteryPercentStale = state.batteryPercent != null,
            chargingReservePercentStale = state.chargingReservePercent != null,
            wirelessChargingEnabledStale = state.wirelessChargingEnabled != null
        )

    /** Prepares an offline snapshot for a new GATT session without claiming its values are fresh. */
    fun forGattReconnect(state: ClicksPowerKeyboardState): ClicksPowerKeyboardState =
        forStorage(state).copy(
            stale = state.stale,
            batteryPercentStale = state.batteryPercentStale,
            chargingReservePercentStale = state.chargingReservePercentStale,
            wirelessChargingEnabledStale = state.wirelessChargingEnabledStale
        )

    fun hasMeaningfulDeviceData(state: ClicksPowerKeyboardState): Boolean = state.containsMeaningfulDeviceData()

    private fun ClicksPowerKeyboardState.containsMeaningfulDeviceData(): Boolean =
        model != null || serialNumber != null || firmwareVersion != null || batteryPercent != null ||
            backlightBrightness != null || chargingReservePercent != null ||
            backlightTimeoutSeconds != null || idleTimeoutSeconds != null || featureFlags != null ||
            specialKeyEnableFlags != null || numberKeyEnableFlags != null || wirelessChargingEnabled != null ||
            activeHostSlot != null || hostConfigurations.any { it != null } || hostNames.any { it != null } ||
            tabRemap != null || geminiRemap != null || altRemap != null || backspaceRemap != null ||
            numberRemaps.any { it != null }

    private fun stateToJson(state: ClicksPowerKeyboardState): JSONObject = JSONObject().apply {
        val persisted = forStorage(state)
        putNullable("model", persisted.model)
        putNullable("serialNumber", persisted.serialNumber)
        putNullable("firmwareVersion", persisted.firmwareVersion)
        putNullable("batteryPercent", persisted.batteryPercent)
        putNullable("backlightBrightness", persisted.backlightBrightness)
        putNullable("chargingReservePercent", persisted.chargingReservePercent)
        putNullable("backlightTimeoutSeconds", persisted.backlightTimeoutSeconds)
        putNullable("idleTimeoutSeconds", persisted.idleTimeoutSeconds)
        putNullable("featureFlags", persisted.featureFlags)
        putNullable("specialKeyEnableFlags", persisted.specialKeyEnableFlags)
        putNullable("numberKeyEnableFlags", persisted.numberKeyEnableFlags)
        putNullable("wirelessChargingEnabled", persisted.wirelessChargingEnabled)
        putNullable("activeHostSlot", persisted.activeHostSlot)
        put("hostConfigurations", persisted.hostConfigurations.toNullableIntJsonArray())
        put("hostNames", persisted.hostNames.toNullableStringJsonArray())
        putNullable("tabRemap", persisted.tabRemap?.toBase64())
        putNullable("geminiRemap", persisted.geminiRemap?.toBase64())
        putNullable("altRemap", persisted.altRemap?.toBase64())
        putNullable("backspaceRemap", persisted.backspaceRemap?.toBase64())
        put("numberRemaps", persisted.numberRemaps.map { it?.toBase64() }.toNullableStringJsonArray())
    }

    private fun stateFromJson(json: JSONObject): ClicksPowerKeyboardState = ClicksPowerKeyboardState(
        model = json.optionalString("model"),
        serialNumber = json.optionalString("serialNumber"),
        firmwareVersion = json.optionalString("firmwareVersion"),
        batteryPercent = json.optionalInt("batteryPercent"),
        backlightBrightness = json.optionalInt("backlightBrightness"),
        chargingReservePercent = json.optionalInt("chargingReservePercent"),
        backlightTimeoutSeconds = json.optionalInt("backlightTimeoutSeconds"),
        idleTimeoutSeconds = json.optionalInt("idleTimeoutSeconds"),
        featureFlags = json.optionalInt("featureFlags"),
        specialKeyEnableFlags = json.optionalInt("specialKeyEnableFlags"),
        numberKeyEnableFlags = json.optionalInt("numberKeyEnableFlags"),
        wirelessChargingEnabled = json.optionalBoolean("wirelessChargingEnabled"),
        activeHostSlot = json.optionalInt("activeHostSlot"),
        hostConfigurations = json.optionalIntList("hostConfigurations", 9),
        hostNames = json.optionalStringList("hostNames", 9),
        tabRemap = json.optionalBytes("tabRemap"),
        geminiRemap = json.optionalBytes("geminiRemap"),
        altRemap = json.optionalBytes("altRemap"),
        backspaceRemap = json.optionalBytes("backspaceRemap"),
        numberRemaps = json.optionalByteList("numberRemaps", 9)
    )

    private fun JSONObject.putNullable(name: String, value: Any?) {
        if (value != null) put(name, value)
    }

    private fun JSONObject.optionalString(name: String): String? =
        takeIf { has(name) && !isNull(name) }?.optString(name)?.takeIf(String::isNotEmpty)

    private fun JSONObject.optionalInt(name: String): Int? =
        takeIf { has(name) && !isNull(name) }?.optInt(name)

    private fun JSONObject.optionalBoolean(name: String): Boolean? =
        takeIf { has(name) && !isNull(name) }?.optBoolean(name)

    private fun JSONObject.optionalBytes(name: String): ByteArray? =
        optionalString(name)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    private fun JSONObject.optionalIntList(name: String, size: Int): List<Int?> {
        val values = optJSONArray(name) ?: return List(size) { null }
        return List(size) { index -> values.optionalInt(index) }
    }

    private fun JSONObject.optionalStringList(name: String, size: Int): List<String?> {
        val values = optJSONArray(name) ?: return List(size) { null }
        return List(size) { index -> values.optionalString(index) }
    }

    private fun JSONObject.optionalByteList(name: String, size: Int): List<ByteArray?> {
        val values = optJSONArray(name) ?: return List(size) { null }
        return List(size) { index -> values.optionalString(index)?.let {
            encoded -> runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
        } }
    }

    private fun JSONArray.optionalInt(index: Int): Int? =
        takeIf { index < length() && !isNull(index) }?.optInt(index)

    private fun JSONArray.optionalString(index: Int): String? =
        takeIf { index < length() && !isNull(index) }?.optString(index)?.takeIf(String::isNotEmpty)

    private fun List<Int?>.toNullableIntJsonArray(): JSONArray = JSONArray().also { result -> forEach(result::put) }
    private fun List<String?>.toNullableStringJsonArray(): JSONArray = JSONArray().also { result -> forEach(result::put) }
    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
}
