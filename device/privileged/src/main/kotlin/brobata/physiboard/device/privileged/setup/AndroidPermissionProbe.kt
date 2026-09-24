package brobata.physiboard.device.privileged.setup

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * [PermissionProbe] against the real system, re-read on every call and never cached, because
 * every one of these can change behind the app (a grant through the broker, a revoke in system
 * settings) and the setup pass decides each step's outcome by re-checking.
 *
 * spec: broker-privileged-toolbox.md SS7; device-backlight-ring.md SS5.9 ("How the screen reads it").
 */
class AndroidPermissionProbe(private val context: Context, private val ringListener: ComponentName) : PermissionProbe {

    private val notifications: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    override fun isNotificationListenerGranted(): Boolean = notifications.isNotificationListenerAccessGranted(ringListener)

    override fun canUseFullScreenIntent(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) notifications.canUseFullScreenIntent() else true

    override fun areNotificationsEnabled(): Boolean = notifications.areNotificationsEnabled()

    override fun hasWriteSecureSettings(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
}
