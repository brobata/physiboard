package brobata.physiboard.inputmethod

import android.content.Context
import android.provider.Settings
import android.util.Log
import brobata.physiboard.SettingsManager
import java.util.concurrent.Executors

/**
 * One-tap setup for the screen trackpad on devices that already have the embedded ADB broker
 * paired (the keyboard-backlight setup). Grants "Display over other apps" through the broker
 * so the user never has to visit system settings, and switches the trackpad on the first time
 * the permission is obtained this way.
 */
object ScreenTrackpadSetup {
    private const val TAG = "ScreenTrackpadSetup"
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Best-effort, off-thread, never throws. No-op when the permission is already granted or
     * the broker isn't paired. [onDone] is invoked on the worker thread with the final grant
     * state.
     */
    fun grantOverlayPermissionViaBroker(context: Context, onDone: ((granted: Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        if (Settings.canDrawOverlays(appContext)) {
            onDone?.invoke(true)
            return
        }
        if (!EmbeddedAdbShell.isPaired(appContext)) {
            onDone?.invoke(false)
            return
        }
        executor.execute {
            val granted = runCatching {
                EmbeddedAdbShell.runShell(
                    appContext,
                    "appops set ${appContext.packageName} SYSTEM_ALERT_WINDOW allow"
                )
                Settings.canDrawOverlays(appContext)
            }.getOrDefault(false)
            Log.i(TAG, "broker grant SYSTEM_ALERT_WINDOW → granted=$granted")
            if (granted && !SettingsManager.isScreenTrackpadEnabled(appContext)) {
                // First successful grant through the broker: turn the feature on so the
                // backlight setup is genuinely the only step the user has to take.
                SettingsManager.setScreenTrackpadEnabled(appContext, true)
            }
            onDone?.invoke(granted)
        }
    }
}
