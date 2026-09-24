package brobata.physiboard.device.privileged.backlight

import android.content.Context
import android.provider.Settings
import android.util.Log
import brobata.physiboard.device.titan.KeyboardBacklight

/**
 * [MasterSwitchAccess] against `Settings.Global`. A `SecurityException` (the grant is missing)
 * is logged and reported as a failed write, never thrown: the ring listener and the tile both
 * run on paths where an exception would take the process down. spec: device-backlight-ring.md
 * SS2.2 step 3c ("A SecurityException is logged and swallowed"), SS5.8.
 */
class GlobalMasterSwitchAccess(private val context: Context) : MasterSwitchAccess {

    override fun read(): Int? =
        Settings.Global.getString(context.contentResolver, KeyboardBacklight.MASTER_SWITCH_KEY)?.trim()?.toIntOrNull()

    override fun write(value: Int): Boolean = try {
        Settings.Global.putInt(context.contentResolver, KeyboardBacklight.MASTER_SWITCH_KEY, value)
    } catch (error: SecurityException) {
        Log.e(TAG, "cannot write the keyboard light switch: WRITE_SECURE_SETTINGS missing", error)
        false
    }

    private companion object {
        const val TAG = "KeyboardLightSwitch"
    }
}
