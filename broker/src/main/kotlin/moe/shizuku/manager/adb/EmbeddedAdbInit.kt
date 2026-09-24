package moe.shizuku.manager.adb

import android.content.Context
import android.content.SharedPreferences
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Small self-contained glue for the vendored Shizuku ADB pairing/connect classes.
 *
 * Replaces Shizuku's `ShizukuSettings` key store with a plain SharedPreferences file and
 * installs the hidden-API exemption required to reflectively reach
 * `com.android.org.conscrypt.Conscrypt.exportKeyingMaterial` on API 28+.
 */
object EmbeddedAdbInit {

    /** SharedPreferences file that backs [PreferenceAdbKeyStore]. */
    const val PREFS_NAME = "embedded_adb"

    /** ADB public-key comment / name, must match between pairing and connect. */
    const val KEY_NAME = "physiboard"

    @Volatile
    private var exempted = false

    /** Idempotently lift the hidden-API restriction for all packages (safe on API 28+). */
    @JvmStatic
    fun ensure() {
        if (exempted) return
        synchronized(this) {
            if (exempted) return
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
            } catch (_: Throwable) {
                // Best effort: on some ROMs / API levels this is a no-op or already open.
            }
            exempted = true
        }
    }

    @JvmStatic
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
