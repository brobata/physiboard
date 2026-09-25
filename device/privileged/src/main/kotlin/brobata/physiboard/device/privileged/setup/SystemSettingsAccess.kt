package brobata.physiboard.device.privileged.setup

import android.content.Context
import android.provider.Settings

/**
 * The one plain `Settings.System` read this module needs without a broker: the vendor key-config
 * table is a normal `Settings.System` row, readable by any app (broker-privileged-toolbox.md
 * SS15, "It needs no broker: `Settings.System` is readable without permission"). Writing still
 * goes through the broker ([ShellLines.systemPut]); this seam exists only to read a row back and
 * to build the Key mapping inventory's snapshot, and it never holds an Android reference in a
 * test.
 */
interface SystemSettingsAccess {
    fun getInt(key: String): Int?
    fun getString(key: String): String?
}

class AndroidSystemSettingsAccess(private val context: Context) : SystemSettingsAccess {
    override fun getInt(key: String): Int? = try {
        Settings.System.getInt(context.contentResolver, key)
    } catch (_: Settings.SettingNotFoundException) {
        null
    }

    override fun getString(key: String): String? = Settings.System.getString(context.contentResolver, key)
}

/** The tests' stand-in: every row lives in a plain map, values stored as strings the way `Settings.System` itself does. */
class FakeSystemSettingsAccess(private val values: MutableMap<String, String> = mutableMapOf()) : SystemSettingsAccess {
    fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    fun put(key: String, value: Int?) = put(key, value?.toString())

    override fun getInt(key: String): Int? = values[key]?.toIntOrNull()
    override fun getString(key: String): String? = values[key]
}
