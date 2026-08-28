package brobata.physiboard.ring

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import brobata.physiboard.inputmethod.EmbeddedAdbShell
import java.util.concurrent.Executors

/**
 * The two grants the ring needs, and the one pairing that supplies both.
 *
 * Notification access and the full-screen permission are each a trip into system settings
 * for the user. The paired broker can grant both in one shell line, which is why this is
 * wired into [brobata.physiboard.inputmethod.PrivilegedSetup]: turn the ring on, and a
 * phone paired once is set up without another tap. The settings intents remain as the route
 * for a phone that was never paired.
 */
object NotificationRingSetup {
    private const val TAG = "NotificationRing"
    private val executor = Executors.newSingleThreadExecutor()

    fun isListenerGranted(context: Context): Boolean =
        NotificationRingListener.isAccessGranted(context)

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    fun canPostNotifications(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }

    /** Everything is in place for a ring to actually appear. */
    fun isReady(context: Context): Boolean =
        isListenerGranted(context) && canUseFullScreenIntent(context) && canPostNotifications(context)

    /**
     * Grant whatever is missing through the broker. Best-effort, off-thread, never throws;
     * no-op when nothing is missing or the broker is not paired. [onDone] runs on the worker
     * thread with the final readiness.
     */
    fun grantViaBroker(context: Context, onDone: ((ready: Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        if (isReady(appContext)) {
            onDone?.invoke(true)
            return
        }
        if (!EmbeddedAdbShell.isPaired(appContext)) {
            onDone?.invoke(false)
            return
        }
        executor.execute {
            val component = NotificationRingListener.componentName(appContext).flattenToString()
            val pkg = appContext.packageName
            val commands = listOf(
                "cmd notification allow_listener $component",
                "appops set $pkg USE_FULL_SCREEN_INTENT allow",
                "appops set $pkg POST_NOTIFICATION allow"
            )
            val ok = runCatching { EmbeddedAdbShell.runShell(appContext, commands.joinToString("; ")) }
                .getOrDefault(false)
            val ready = isReady(appContext)
            Log.i(TAG, "broker grant ok=$ok ready=$ready")
            onDone?.invoke(ready)
        }
    }

    /**
     * Take the grants back. Used by Reset device settings to stock: notification access is
     * the one thing here that outlives an uninstall in a way the user could not see.
     * BLOCKING — call off the main thread. Returns true when nothing remains granted.
     */
    fun revokeViaBroker(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!isListenerGranted(appContext) && !canUseFullScreenIntent(appContext)) return true
        if (!EmbeddedAdbShell.isPaired(appContext)) return false
        val component = NotificationRingListener.componentName(appContext).flattenToString()
        val pkg = appContext.packageName
        val commands = listOf(
            "cmd notification disallow_listener $component",
            "appops set $pkg USE_FULL_SCREEN_INTENT default"
        )
        runCatching { EmbeddedAdbShell.runShell(appContext, commands.joinToString("; ")) }
        return !isListenerGranted(appContext)
    }

    fun listenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun fullScreenSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            appNotificationSettingsIntent(context)
        }

    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
