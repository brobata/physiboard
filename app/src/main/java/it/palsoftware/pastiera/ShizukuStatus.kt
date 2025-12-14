package it.palsoftware.pastiera

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    /** Shizuku is running and the app has permission */
    Connected,
    /** Shizuku is running but the app lacks permission */
    NotAuthorized,
    /** Shizuku is not available */
    NotConnected
}

fun resolveShizukuStatus(): ShizukuStatus {
    return try {
        if (!Shizuku.pingBinder()) {
            ShizukuStatus.NotConnected
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuStatus.Connected
        } else {
            ShizukuStatus.NotAuthorized
        }
    } catch (e: Exception) {
        ShizukuStatus.NotConnected
    }
}
