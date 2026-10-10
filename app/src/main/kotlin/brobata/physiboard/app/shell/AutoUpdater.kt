package brobata.physiboard.app.shell

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.core.settings.UpdateMode
import brobata.physiboard.core.shell.AutoUpdatePolicy
import brobata.physiboard.core.shell.AutoUpdatePolicy.AfterSession
import brobata.physiboard.core.shell.AutoUpdatePolicy.Ready
import brobata.physiboard.core.shell.AutoUpdatePolicy.Route
import brobata.physiboard.core.shell.AutoUpdatePolicy.Timing
import brobata.physiboard.core.shell.ApkVerification
import brobata.physiboard.core.shell.GithubChecks
import brobata.physiboard.core.shell.InstalledApp
import brobata.physiboard.core.shell.PendingUpdate
import brobata.physiboard.core.shell.ResolvedRelease
import brobata.physiboard.core.shell.UpdateAssets
import brobata.physiboard.device.privileged.PrivilegedServicesOwner
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.ime.KeyboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Automatic updates (app-shell.md SS32): the Android half of [AutoUpdatePolicy]. A check that finds
 * a newer release hands it here; this downloads it in the background on an unmetered network,
 * checks it, and then either installs it the next time the screen has been off for two minutes
 * ("Install automatically"), or posts "ready, tap to install" ("Download and ask me"). "Off", a
 * build that is not the release app, and an F-Droid install keep 3.2's announcement and nothing
 * more.
 */
object AutoUpdater {
    private const val TAG = "AutoUpdater"
    const val DOWNLOAD_WORK = "physiboard_update_download"
    const val INSTALL_WORK = "physiboard_update_install"

    /** A checked APK waits for the screen to go off. Read by the screen watch on the main thread; it only ever enqueues work. */
    @Volatile
    private var armed = false

    /** SS32.1: the release app, its build flag, and not an F-Droid install. */
    fun buildMayInstall(context: Context): Boolean {
        val installer = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull()
        return AutoUpdatePolicy.buildMayInstall(context.packageName, BuildConfig.AUTO_UPDATE_INSTALLS) &&
            GithubChecks.allowed(buildFlagOn = true, installerPackageName = installer)
    }

    suspend fun mode(context: Context): UpdateMode = (context.applicationContext as PhysiBoardApplication).settingsStore.current().updates.mode

    /**
     * Every check that found [release] calls this. True when the updater took it on (a download
     * started, or it is already downloaded); false when the release should only be announced, the
     * 3.2 way. [background] is true for the six-hourly check, which re-posts "ready" in ask mode.
     */
    suspend fun releaseFound(context: Context, release: ResolvedRelease, background: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val store = UpdateStore(app)
        val record = store.read()
        when (val found = AutoUpdatePolicy.onFound(mode(app), buildMayInstall(app), release, record)) {
            AutoUpdatePolicy.Found.Announce -> false
            is AutoUpdatePolicy.Found.Download -> {
                enqueueDownload(app, found.assets, release.pageUrl)
                true
            }
            AutoUpdatePolicy.Found.AlreadyReady -> {
                // From the background check, "Download and ask me" asks again, as 3.2 re-announced a
                // release on every daily check; a swiped notification is not the end of it.
                record.ready?.let { onReady(app, it, freshlyDownloaded = background) }
                true
            }
        }
    }

    /** The tag of a downloaded, checked update still waiting, for the update dialog's "Install now". */
    suspend fun readyTag(context: Context): String? = withContext(Dispatchers.IO) {
        val store = UpdateStore(context)
        store.read().ready?.takeIf { store.apkFor(it) != null }?.tag
    }

    /** SS32.2: one download at a time, on an unmetered network, with room on the phone. */
    private fun enqueueDownload(context: Context, assets: UpdateAssets, pageUrl: String) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(
                workDataOf(
                    UpdateDownloadWorker.KEY_TAG to assets.tag,
                    UpdateDownloadWorker.KEY_APK_URL to assets.apkUrl,
                    UpdateDownloadWorker.KEY_CHECKSUM_URL to assets.checksumUrl,
                    UpdateDownloadWorker.KEY_PAGE_URL to pageUrl,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOAD_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** A checked APK is in place: wait for quiet, ask, or let it go, as the setting says now. */
    suspend fun onReady(context: Context, ready: PendingUpdate, freshlyDownloaded: Boolean) {
        when (AutoUpdatePolicy.whenReady(mode(context), buildMayInstall(context))) {
            Ready.DISCARD -> {
                armed = false
                UpdateStore(context).discardReady()
            }
            Ready.ASK -> {
                armed = false
                if (freshlyDownloaded) UpdateNotifications.ready(context, ready.tag)
            }
            Ready.INSTALL_WHEN_QUIET -> {
                armed = true
                // KEEP: a process WorkManager started to run the install must not cancel that very run.
                if (!isInteractive(context)) scheduleInstall(context, AutoUpdatePolicy.QUIET_DELAY_MS, ExistingWorkPolicy.KEEP)
            }
        }
    }

    /** REPLACE restarts the wait (the screen just went off again); KEEP leaves a wait or a run already under way alone. */
    private fun scheduleInstall(context: Context, delayMs: Long, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val request = OneTimeWorkRequestBuilder<UpdateInstallWorker>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(INSTALL_WORK, policy, request)
    }

    /**
     * Once per process start, from [PhysiBoardApplication]: start the screen watch, drop an update
     * the installed app has caught up with (the one just installed), and arm for one still waiting.
     */
    fun onProcessStart(app: PhysiBoardApplication, scope: CoroutineScope) {
        registerScreenWatch(app)
        scope.launch(Dispatchers.IO) {
            runCatching { refresh(app) }.onFailure { Log.e(TAG, "update refresh failed", it) }
        }
    }

    private suspend fun refresh(context: Context) {
        val store = UpdateStore(context)
        val ready = store.read().ready ?: return
        val installed = ApkInspector.installedApp(context) ?: return
        if (AutoUpdatePolicy.isStale(ready, installed.versionCode) || store.apkFor(ready) == null) {
            armed = false
            store.discardReady()
            if (AutoUpdatePolicy.isStale(ready, installed.versionCode)) UpdateNotifications.cancel(context)
            return
        }
        onReady(context, ready, freshlyDownloaded = false)
    }

    /**
     * SS32.4: the screen going off starts a two-minute wait; coming back on cancels it. The
     * receiver lives as long as the process, which is as long as PhysiBoard is the keyboard.
     */
    private fun registerScreenWatch(app: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!armed) return
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> scheduleInstall(app, AutoUpdatePolicy.QUIET_DELAY_MS)
                    Intent.ACTION_SCREEN_ON -> WorkManager.getInstance(app).cancelUniqueWork(INSTALL_WORK)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
            .onFailure { Log.e(TAG, "screen watch not registered", it) }
    }

    private fun isInteractive(context: Context): Boolean = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /**
     * The install itself, run by [UpdateInstallWorker] after the quiet wait (SS32.3, SS32.4).
     * Every condition is read again here, at the last moment: the setting, the installed version,
     * the screen, dictation, and the file, which is checked in full once more before any route.
     */
    suspend fun installWhenQuiet(context: Context) {
        val store = UpdateStore(context)
        val ready = store.read().ready ?: return
        val installed = ApkInspector.installedApp(context) ?: return
        if (AutoUpdatePolicy.isStale(ready, installed.versionCode)) {
            armed = false
            store.discardReady()
            return
        }
        when (AutoUpdatePolicy.whenReady(mode(context), buildMayInstall(context))) {
            Ready.DISCARD -> {
                armed = false
                store.discardReady()
                return
            }
            Ready.ASK -> {
                armed = false
                UpdateNotifications.ready(context, ready.tag)
                return
            }
            Ready.INSTALL_WHEN_QUIET -> Unit
        }
        when (AutoUpdatePolicy.timing(isInteractive(context), KeyboardActivity.dictationActive)) {
            Timing.WAIT_FOR_SCREEN_OFF -> return
            Timing.WAIT_FOR_DICTATION -> {
                scheduleInstall(context, AutoUpdatePolicy.BUSY_RETRY_MS)
                return
            }
            Timing.NOW -> Unit
        }
        val apk = checkedApk(context, store, ready, installed) ?: return
        val broker = (context.applicationContext as PrivilegedServicesOwner).privileged.broker
        for (route in AutoUpdatePolicy.routes(brokerReachable = broker.blocker() == null)) {
            if (route != Route.NOTIFY && isInteractive(context)) return
            when (route) {
                Route.BROKER -> {
                    Log.i(TAG, "installing ${ready.tag} through Titan tools")
                    // The stream is closed short the moment the screen comes on, so pm installs nothing.
                    when (val result = broker.installApk(apk) { !isInteractive(context) }) {
                        is ShellResult.Ok -> return
                        is ShellResult.Failed -> Log.w(TAG, "Titan tools could not install ${ready.tag}: ${result.message}")
                    }
                }
                Route.SESSION -> {
                    // D10: without "Install unknown apps" Android always asks the user; go straight
                    // to the notification instead of a session that can only be abandoned.
                    if (!context.packageManager.canRequestPackageInstalls()) continue
                    Log.i(TAG, "installing ${ready.tag} through Android's installer")
                    val committed = runCatching { SessionInstaller.commit(context, apk, ready.sha256, backgroundStatusTarget(context)) }
                        .onFailure { Log.w(TAG, "Android's installer could not take ${ready.tag}", it) }
                    if (committed.isSuccess) return
                }
                Route.NOTIFY -> {
                    armed = false
                    UpdateNotifications.ready(context, ready.tag)
                }
            }
        }
    }

    /**
     * The downloaded file, checked in full again (SS32.3), or null after refusing it. The installed
     * app is read again by the caller, so "newer" means newer than what runs now.
     */
    fun checkedApk(context: Context, store: UpdateStore, ready: PendingUpdate, installed: InstalledApp): File? {
        val apk = store.apkFor(ready) ?: run {
            store.discardReady()
            return null
        }
        val rejection = ApkVerification.verify(ready.sha256, ApkInspector.sha256(apk), ApkInspector.archiveFacts(context, apk), installed)
        if (rejection != null) {
            Log.e(TAG, "the update ${ready.tag} was deleted: ${rejection.reason}")
            refuse(context, ready.tag)
            return null
        }
        return apk
    }

    /** SS32.3: a release whose APK failed is deleted, never downloaded again, and announced the 3.2 way. */
    fun refuse(context: Context, tag: String) {
        armed = false
        UpdateStore(context).refuse(tag)
        UpdateNotifications.announce(context, ResolvedRelease(tag, UpdateNotifications.releasePage(tag), apkDownloadUrl = null))
    }

    private fun backgroundStatusTarget(context: Context) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION_STATUS),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    ).intentSender

    /** Android's answer to a background session (SS32.3). */
    fun onBackgroundSessionResult(context: Context, status: Int, sessionId: Int, message: String?) {
        val outcome = SessionInstaller.outcome(status)
        Log.i(TAG, "Android's installer answered $outcome ($status) ${message.orEmpty()}")
        val store = UpdateStore(context)
        val ready = store.read().ready
        when (AutoUpdatePolicy.afterSession(outcome, interactive = false)) {
            AfterSession.DONE -> store.discardReady()
            AfterSession.NOTIFY_READY -> {
                SessionInstaller.abandon(context, sessionId)
                armed = false
                ready?.let { UpdateNotifications.ready(context, it.tag) }
            }
            AfterSession.KEEP, AfterSession.CONFIRM -> Unit
            AfterSession.REFUSE -> ready?.let { refuse(context, it.tag) }
        }
    }
}
