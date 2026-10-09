package brobata.physiboard.device.privileged.broker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import brobata.physiboard.device.privileged.PrivilegedServices
import brobata.physiboard.device.privileged.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The pairing watcher: a foreground service that discovers the pairing service and carries the
 * six-digit code back through a notification's inline reply, because Android's pairing dialog
 * and the app cannot be on screen together (spec SS23, "Pairing from a notification with inline
 * reply: keep").
 *
 * Armed by the setup card the moment it appears (via [arm]), before the user taps anything, and
 * not stopped when the card leaves the screen. Stops itself once a pairing attempt ends either
 * way. If the foreground start is refused the same notification is posted as an ordinary one
 * (SS4.1 "If the watcher's foreground start is refused by the OS"). The discovered port rides in
 * the reply action because the service may be killed before the user replies (SS4.1 step 4).
 *
 * Every entry point Android calls is guarded: a crash here would take the whole process, and
 * the keyboard with it. Needs the phone to verify: the channel's DND bypass is only honoured
 * with notification-policy access, which the app does not have (hence the card's warning).
 *
 * spec: broker-privileged-toolbox.md SS4.1.
 */
class PairingWatcherService : Service() {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            ensureChannel()
            val services = PrivilegedServices.from(this) ?: return
            stateJob = scope.launch {
                services.pairing.state.collect { state ->
                    runCatching {
                        // spec SS4.1 step 3 re-arm gap fix: cleared on a successful pairing, left
                        // alone on a failure (the user has not given up, and the watcher may need
                        // discovering again after a process death before they retry).
                        if (state is PairingState.Paired) services.diagnostics.setPairingWatcherArmed(false)
                        render(state)
                    }.onFailure { Log.e(TAG, "render crashed", it) }
                }
            }
        }.onFailure { Log.e(TAG, "onCreate crashed", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            val services = PrivilegedServices.from(this)
            if (services == null) {
                stopSelf()
                return@runCatching
            }
            when (intent?.action) {
                ACTION_ARM -> {
                    // The setup card re-arms every 1500 ms while unpaired (spec SS4.1 step 3), so
                    // this must show the notification for the state the coordinator is already
                    // in: posting "Searching" unconditionally overwrote "Pairing service found"
                    // a second after every discovery (Titan, 2026-09-25) and the code entry
                    // never appeared. A terminal state has no foreground notification, so the
                    // searching one stands in until the coordinator moves.
                    showForeground(
                        when (val current = services.pairing.state.value) {
                            is PairingState.ServiceFound -> foundNotification(current.port)
                            PairingState.Pairing -> pairingNotification()
                            else -> searchingNotification()
                        },
                    )
                    services.pairing.arm()
                    // spec SS4.1 step 3 re-arm gap fix: this is the one place the watcher is
                    // actually armed, so this is where the persisted flag is set.
                    services.diagnostics.setPairingWatcherArmed(true)
                }
                ACTION_STOP -> {
                    services.pairing.disarm()
                    services.diagnostics.setPairingWatcherArmed(false)
                    stopSelf()
                }
                ACTION_REPLY -> {
                    val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REMOTE_INPUT_KEY)?.toString()
                    val port = intent.getIntExtra(EXTRA_PORT, -1).takeIf { it > 0 }
                    if (code != null) {
                        showForeground(pairingNotification())
                        services.pairing.submitCode(code, port)
                    }
                }
                else -> Unit
            }
        }.onFailure { Log.e(TAG, "onStartCommand crashed", it) }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        stateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun render(state: PairingState) {
        when (state) {
            PairingState.Idle -> Unit
            PairingState.Searching -> showForeground(searchingNotification())
            is PairingState.ServiceFound -> showForeground(foundNotification(state.port))
            PairingState.Pairing -> showForeground(pairingNotification())
            PairingState.Paired -> finishWith("Paired successfully", "This device can now connect to wireless debugging.")
            is PairingState.Failed -> finishWith("Pairing failed", state.message)
        }
    }

    private fun finishWith(title: String, text: String) {
        if (foreground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        notificationManager().notify(RESULT_NOTIFICATION_ID, plain(title, text).build())
        stopSelf()
    }

    private fun showForeground(notification: Notification) {
        try {
            ServiceCompat.startForeground(this, WATCHER_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            foreground = true
        } catch (error: Exception) {
            // Background start restriction (Android 12+): the same notification, posted plainly.
            Log.e(TAG, "foreground start refused; posting a plain notification", error)
            notificationManager().notify(WATCHER_NOTIFICATION_ID, notification)
        }
    }

    private fun searchingNotification(): Notification = plain("Searching for pairing service…", "Turn on Wireless debugging and tap \"Pair device with pairing code\".")
        .addAction(NotificationCompat.Action.Builder(brobata.physiboard.design.R.drawable.pb_ic_mark, "Stop", servicePendingIntent(ACTION_STOP, PendingIntent.FLAG_IMMUTABLE)).build())
        .setOngoing(true)
        .build()

    private fun foundNotification(port: Int): Notification {
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel("Pairing code").build()
        val reply = Intent(this, PairingWatcherService::class.java).setAction(ACTION_REPLY).putExtra(EXTRA_PORT, port)
        val pending = PendingIntent.getService(this, REQUEST_REPLY, reply, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val action = NotificationCompat.Action.Builder(brobata.physiboard.design.R.drawable.pb_ic_mark, "Enter pairing code", pending).addRemoteInput(remoteInput).build()
        return plain("Pairing service found", "Type the six-digit code Android is showing.")
            .addAction(action)
            .addAction(NotificationCompat.Action.Builder(brobata.physiboard.design.R.drawable.pb_ic_mark, "Stop", servicePendingIntent(ACTION_STOP, PendingIntent.FLAG_IMMUTABLE)).build())
            .setOngoing(true)
            .build()
    }

    private fun pairingNotification(): Notification = plain("Pairing…", "Talking to the phone.").setOngoing(true).build()

    private fun plain(title: String, text: String): NotificationCompat.Builder = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(brobata.physiboard.design.R.drawable.pb_ic_mark)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSilent(true)
        .setOnlyAlertOnce(true)

    private fun servicePendingIntent(action: String, flags: Int): PendingIntent =
        PendingIntent.getService(this, REQUEST_STOP, Intent(this, PairingWatcherService::class.java).setAction(action), flags)

    /** spec: SS4.1 step 3: importance high, silent, no badge, no bubbles, bypass-DND requested. */
    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "ADB pairing", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            setAllowBubbles(false)
            setBypassDnd(true)
            description = "Carries the Wireless debugging pairing code."
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    companion object {
        private const val TAG = "PairingWatcher"
        const val CHANNEL_ID = "physiboard_adb_pairing"
        const val WATCHER_NOTIFICATION_ID = 40
        const val RESULT_NOTIFICATION_ID = 42
        const val ACTION_ARM = "brobata.physiboard.privileged.PAIRING_ARM"
        const val ACTION_STOP = "brobata.physiboard.privileged.PAIRING_STOP"
        const val ACTION_REPLY = "brobata.physiboard.privileged.PAIRING_REPLY"
        const val EXTRA_PORT = "port"
        const val REMOTE_INPUT_KEY = "pairing_code"
        private const val REQUEST_STOP = 1
        private const val REQUEST_REPLY = 2

        /** Arms the watcher; the setup card calls this whenever it is visible and no key is stored. spec: SS4.1 step 3. */
        fun arm(context: Context) {
            val intent = Intent(context, PairingWatcherService::class.java).setAction(ACTION_ARM)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: Exception) {
                Log.e(TAG, "startForegroundService refused; trying a plain start", error)
                runCatching { context.startService(intent) }
            }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, PairingWatcherService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
