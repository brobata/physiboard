package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.util.Log
import it.palsoftware.pastiera.SettingsManager

/**
 * Single entry point for everything the app wants to do through the embedded ADB broker.
 *
 * The user pairs Wireless debugging ONCE (from the keyboard-backlight setup); from then on
 * every privileged change is applied in one go — at pairing success, at IME start, and
 * whenever the backlight screen sees "enabled + paired". All steps are idempotent, run
 * off-thread and never throw, so calling this repeatedly is harmless.
 */
object PrivilegedSetup {
    private const val TAG = "PrivilegedSetup"

    fun applyAll(context: Context, reason: String) {
        val appContext = context.applicationContext
        if (!EmbeddedAdbShell.isPaired(appContext)) return
        Log.i(TAG, "applyAll ($reason)")
        if (SettingsManager.getSmartBacklightEnabled(appContext)) {
            KeyboardBacklightManager.applyAlwaysOn(appContext)
        }
        ScreenTrackpadSetup.grantOverlayPermissionViaBroker(appContext)
    }
}
