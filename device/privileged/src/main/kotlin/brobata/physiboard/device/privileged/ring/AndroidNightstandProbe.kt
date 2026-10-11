package brobata.physiboard.device.privileged.ring

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/**
 * The charging clock's phone facts, read on demand and never polled. "Plugged in" comes from the
 * battery's sticky broadcast (a read, no receiver is left registered), so a full battery still on
 * the cable counts, which Android's "charging" status would not.
 *
 * spec: device-backlight-ring.md SS5.11.2.
 */
class AndroidNightstandProbe(private val context: Context) : NightstandProbe {

    override fun isPluggedIn(): Boolean = isPluggedIn(context)

    override fun isKeyguardLocked(): Boolean = isKeyguardLocked(context)

    override fun isInteractive(): Boolean = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    companion object {
        fun isPluggedIn(context: Context): Boolean {
            // A null receiver only reads the sticky value; ACTION_BATTERY_CHANGED is a system
            // broadcast, so no export flag is needed for it.
            val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
            return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        }

        /** Unknown reads as unlocked: the charging clock then never holds the screen (the safe side). */
        fun isKeyguardLocked(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false
    }
}
