package brobata.physiboard.device.privileged.setup

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import brobata.physiboard.core.toolbox.AccessibilityServiceList

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

    override fun isAccessibilityServiceEnabled(): Boolean = accessibilityServiceEnabled(context)

    override fun isAccessibilityServiceListed(): Boolean =
        AccessibilityServiceList.isListed(runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull(), context.packageName)

    companion object {
        /** Both secure rows are readable by any app; the list is the truth, the master switch must agree. */
        fun accessibilityServiceEnabled(context: Context): Boolean {
            val resolver = context.contentResolver
            val masterOn = runCatching { Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 }.getOrDefault(false)
            val list = runCatching { Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull()
            return masterOn && AccessibilityServiceList.isListed(list, context.packageName)
        }
    }
}
