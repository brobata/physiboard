package brobata.physiboard.inputmethod

import android.content.Context
import android.util.Log
import brobata.physiboard.SettingsManager
import brobata.physiboard.ring.NotificationRingSetup

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
        // Log.i is stripped from release builds, so a bail-out here left no trace at all.
        // Record it against every step the user can see instead.
        PrivilegedDiagnostics.brokerBlocker(appContext)?.let { blocker ->
            PrivilegedDiagnostics.Step.entries.forEach { step ->
                PrivilegedDiagnostics.record(appContext, step, ok = false, reason = blocker)
            }
            return
        }
        Log.i(TAG, "applyAll ($reason)")
        if (SettingsManager.getSmartBacklightEnabled(appContext)) {
            KeyboardBacklightManager.applyAlwaysOn(appContext)
        }
        ScreenTrackpadSetup.grantOverlayPermissionViaBroker(appContext)
        if (SettingsManager.isNotificationRingEnabled(appContext)) {
            NotificationRingSetup.grantViaBroker(appContext)
        }
    }
}
