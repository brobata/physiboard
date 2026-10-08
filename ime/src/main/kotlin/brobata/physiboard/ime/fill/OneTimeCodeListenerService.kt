package brobata.physiboard.ime.fill

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import brobata.physiboard.core.actions.fill.FillLabels
import brobata.physiboard.core.actions.fill.OneTimeCode
import brobata.physiboard.core.actions.fill.OneTimeCodeExtractor
import brobata.physiboard.core.actions.fill.OneTimeCodeStore
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.ime.SettingsSourceOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Reads one-time codes out of notifications for the Fill page. spec: layers-sym-alt.md SS4.7,
 * app-shell.md SS31.6.
 *
 * Bound by the system only after the user gives PhysiBoard's "one-time codes" entry notification
 * access (its own entry, apart from the notification ring's, so each is its own choice). It then
 * sees every notification posted; for each, while `otp_from_notifications` is on and private mode
 * is off, it reads the title and text, keeps a code if [OneTimeCodeExtractor] finds one, and
 * drops the text. The code goes to [OneTimeCodeHolder], in memory only. Nothing read here is
 * written anywhere, logged, put in a trace or sent off the phone; a failure is logged by its kind
 * alone, never with the text. Screen off clears every code.
 */
class OneTimeCodeListenerService : NotificationListenerService() {

    private var scope = MainScope()
    private var settingsJob: Job? = null

    /** `otp_from_notifications` on and private mode off; false until the settings are read. */
    @Volatile
    private var reading = false

    private var screenOffRegistered = false
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) OneTimeCodeHolder.clear()
        }
    }

    override fun onListenerConnected() {
        runCatching {
            scope.cancel()
            scope = MainScope()
            val source = (applicationContext as? SettingsSourceOwner)?.settingsSource ?: return@runCatching
            settingsJob = scope.launch {
                source.settings.map(::readingAllowed).distinctUntilChanged().collect { allowed ->
                    reading = allowed
                    if (allowed) readActiveNotifications() else OneTimeCodeHolder.clear()
                }
            }
            if (!screenOffRegistered) {
                ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
                screenOffRegistered = true
            }
        }.onFailure { error -> Log.e(TAG, "listener connect failed: ${error.javaClass.simpleName}") }
    }

    override fun onListenerDisconnected() {
        // Access withdrawn (or the system unbinding): the codes go with it.
        reading = false
        settingsJob?.cancel()
        scope.cancel()
        unregisterScreenOff()
        OneTimeCodeHolder.clear()
    }

    override fun onDestroy() {
        reading = false
        scope.cancel()
        unregisterScreenOff()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!reading) return
        runCatching { sbn?.let(::read) }
            .onFailure { error -> Log.e(TAG, "notification read failed: ${error.javaClass.simpleName}") }
    }

    private fun unregisterScreenOff() {
        if (!screenOffRegistered) return
        screenOffRegistered = false
        runCatching { unregisterReceiver(screenOff) }
    }

    /** A code that arrived while reading was off, or before the keyboard's process started, is still useful while it is fresh. */
    private fun readActiveNotifications() {
        runCatching { activeNotifications?.forEach(::read) }
            .onFailure { error -> Log.e(TAG, "active notifications read failed: ${error.javaClass.simpleName}") }
    }

    private fun read(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        // A group summary repeats its children, whose own notifications carry the same text.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val wallNow = System.currentTimeMillis()
        val extras = notification.extras ?: return
        val title = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
        ).joinToString(" ")
        for ((text, postedAtWall) in texts(extras, sbn.postTime)) {
            if (wallNow - postedAtWall >= OneTimeCodeStore.LIFETIME_MS) continue
            val code = OneTimeCodeExtractor.extract(text, title) ?: continue
            val ageMs = (wallNow - postedAtWall).coerceAtLeast(0)
            OneTimeCodeHolder.add(OneTimeCode(code, sourceName(sbn, extras), sbn.packageName, OneTimeCodeHolder.now() - ageMs))
            return
        }
    }

    /**
     * The texts a notification shows, most specific first, each with when it was posted: a
     * conversation's messages newest first (each has its own time), the expanded text, the
     * one-line text, an inbox's lines (last first).
     */
    private fun texts(extras: Bundle, postTime: Long): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        @Suppress("DEPRECATION")
        val messages: Array<Parcelable>? = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        messages?.mapNotNull { it as? Bundle }?.reversed()?.forEach { message ->
            val text = message.getCharSequence(MESSAGE_TEXT)?.toString() ?: return@forEach
            out.add(text to (message.getLong(MESSAGE_TIME, postTime).takeIf { it > 0 } ?: postTime))
        }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { out.add(it.toString() to postTime) }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { out.add(it.toString() to postTime) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.reversed()?.forEach { out.add(it.toString() to postTime) }
        return out.filter { it.first.isNotBlank() }.distinctBy { it.first }
    }

    /** The app's own name, as the notification shade shows it; else a readable part of its package. */
    private fun sourceName(sbn: StatusBarNotification, extras: Bundle): String {
        @Suppress("DEPRECATION")
        val info = extras.getParcelable<ApplicationInfo>(EXTRA_APP_INFO)
        val label = runCatching { info?.loadLabel(packageManager)?.toString() }.getOrNull()
            ?: runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrNull()
        return label?.takeIf { it.isNotBlank() && it != sbn.packageName } ?: FillLabels.fallbackSourceName(sbn.packageName)
    }

    private fun readingAllowed(settings: Settings): Boolean = settings.symPages.otpFromNotifications && !settings.privacy.privateMode

    private companion object {
        const val TAG = "PhysiBoardFill"

        /** The keys a conversation message's bundle uses (Notification.MessagingStyle.Message). */
        const val MESSAGE_TEXT = "text"
        const val MESSAGE_TIME = "time"

        /** Where Notification.Builder puts the posting app's ApplicationInfo. */
        const val EXTRA_APP_INFO = "android.appInfo"
    }
}
