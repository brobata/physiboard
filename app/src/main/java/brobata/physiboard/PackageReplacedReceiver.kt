package brobata.physiboard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import brobata.physiboard.inputmethod.PhysicalKeyboardInputMethodService

/**
 * Tells people to pick PhysiBoard again after an update that unselected it.
 *
 * 2.0 moved the app's namespace from `it.palsoftware.pastiera` to `brobata.physiboard`. The IME
 * service is declared with a relative name, so its *component* name moved with it:
 *
 *     1.2.4  brobata.physiboard/it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService
 *     2.0    brobata.physiboard/brobata.physiboard.inputmethod.PhysicalKeyboardInputMethodService
 *
 * Android identifies an input method by component name, so on upgrade the stored selection points
 * at something that no longer exists and the system quietly falls back to another keyboard. On a
 * phone bought for its physical keyboard that reads as "the update broke my keyboard".
 *
 * Declaring the old component as well would keep the selection, but it would also put a second
 * "PhysiBoard" in the keyboard picker forever. This is the cheaper trade: let the selection drop,
 * then say so immediately and offer one tap back to setup.
 *
 * Fires only when we are genuinely not the current IME, so an upgrade that kept the selection —
 * every upgrade after 2.0 — stays silent.
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (isCurrentInputMethod(context)) return

        try {
            createChannel(context)
            context.getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification(context))
        } catch (e: Exception) {
            // A denied POST_NOTIFICATIONS grant lands here. Nothing else to try: the app cannot ask
            // for the permission from a receiver, and the in-app setup screen still explains it.
            Log.e(TAG, "Could not post the re-selection notice", e)
        }
    }

    /** True when the selected keyboard is this build's IME. */
    private fun isCurrentInputMethod(context: Context): Boolean {
        val selected = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        // Compare on the component, not the package: the stale 1.x component shares our package
        // name and would otherwise read as "still selected".
        return selected.substringBefore('/') == context.packageName &&
            selected.substringAfter('/', "") ==
            PhysicalKeyboardInputMethodService::class.java.name
    }

    private fun buildNotification(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_reselect_title))
            .setContentText(context.getString(R.string.notification_reselect_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_reselect_text))
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            // The keyboard is gone until this is acted on, so it earns a heads-up rather than a
            // quiet line in the shade.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_reselect_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_reselect_channel_description)
                setShowBadge(true)
            }
        )
    }

    private companion object {
        const val TAG = "PackageReplaced"
        const val CHANNEL_ID = "physiboard_reselect_channel"
        const val NOTIFICATION_ID = 3
    }
}
