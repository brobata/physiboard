package it.palsoftware.pastiera.physi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.EmbeddedAdbShell
import it.palsoftware.pastiera.inputmethod.NotificationHelper

/**
 * On boot, if the Smart Backlight feature is on but the embedded-ADB backlight path can't work yet
 * (wireless debugging does NOT persist across reboot), post a single guidance notification pointing
 * the user at the fix.
 *
 * IMPORTANT: this deliberately does NOT start any (foreground) service. On some OEM builds a
 * foreground-service-start from BOOT_COMPLETED is refused and the resulting exception, thrown inside
 * the service as it calls startForeground(), crashes the process ("PhysiBoard has stopped"). A
 * BroadcastReceiver may post a notification directly, so no service is needed. The prompt re-arm +
 * notification-clear that the old watcher service provided now lives in the IME's
 * [it.palsoftware.pastiera.inputmethod.KeyboardBacklightManager] (a ContentObserver on
 * `adb_wifi_enabled`), which is already alive while the keyboard is in use.
 *
 * The entire body is wrapped so boot can never crash.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val action = intent?.action ?: return
            if (action != Intent.ACTION_BOOT_COMPLETED &&
                action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
                action != "android.intent.action.QUICKBOOT_POWERON"
            ) {
                return
            }

            if (!SettingsManager.getSmartBacklightEnabled(context)) return
            if (!NotificationHelper.hasNotificationPermission(context)) return

            val paired = EmbeddedAdbShell.isPaired(context)
            val wirelessDebuggingOn = EmbeddedAdbShell.isWirelessDebuggingEnabled(context)

            when {
                // Already connectable — the IME will arm the light on its own, nothing to guide.
                paired && wirelessDebuggingOn -> return
                // Paired, but wireless debugging is off after the reboot: guide to re-enable it.
                paired -> NotificationHelper.postBacklightGuidance(context, paused = true)
                // Never paired: guide the user to pair once.
                else -> NotificationHelper.postBacklightGuidance(context, paused = false)
            }
        } catch (t: Throwable) {
            // Boot must never crash the app.
            android.util.Log.w("BootReceiver", "boot backlight guidance failed", t)
        }
    }
}
