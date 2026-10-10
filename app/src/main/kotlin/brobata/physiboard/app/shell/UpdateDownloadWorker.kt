package brobata.physiboard.app.shell

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import brobata.physiboard.core.settings.UpdateMode
import brobata.physiboard.core.shell.ApkVerification
import brobata.physiboard.core.shell.PendingUpdate
import brobata.physiboard.core.shell.ReleaseAsset
import brobata.physiboard.core.shell.ResolvedRelease
import brobata.physiboard.core.shell.UpdateAssetSelection

/**
 * Downloads one release's APK and checksum and checks the APK (app-shell.md SS32.2, SS32.3).
 * Enqueued by [AutoUpdater.releaseFound] with an unmetered-network constraint. A file that fails
 * any check is deleted at once and its release is announced instead; a network failure is tried
 * again a few times with WorkManager's backoff, then left to the next six-hourly check.
 */
class UpdateDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val tag = inputData.getString(KEY_TAG) ?: return Result.success()
        val pageUrl = inputData.getString(KEY_PAGE_URL) ?: UpdateNotifications.releasePage(tag)
        val version = tag.removePrefix("v").removePrefix("V")
        val apkName = UpdateAssetSelection.apkName(version)
        val release = ResolvedRelease(
            tag = tag,
            pageUrl = pageUrl,
            apkDownloadUrl = inputData.getString(KEY_APK_URL),
            assets = listOf(
                ReleaseAsset(apkName, inputData.getString(KEY_APK_URL).orEmpty()),
                ReleaseAsset("$apkName.sha256", inputData.getString(KEY_CHECKSUM_URL).orEmpty()),
            ),
        )
        // The same selection the check made, run again on what came through the queue.
        val assets = UpdateAssetSelection.select(release) ?: return Result.success()
        if (!AutoUpdater.buildMayInstall(context) || AutoUpdater.mode(context) == UpdateMode.OFF) return Result.success()

        val store = UpdateStore(context)
        val record = store.read()
        if (record.ready?.tag == tag || record.refusedTag == tag) return Result.success()
        val installed = ApkInspector.installedApp(context) ?: return Result.success()

        val expected = when (val r = UpdateDownloader.fetchChecksum(assets)) {
            is UpdateDownloader.Result.Ok -> r.value
            is UpdateDownloader.Result.Blocked -> return Result.success()
            is UpdateDownloader.Result.Failed -> return retryOrGiveUp("checksum", r.message)
            is UpdateDownloader.Result.Invalid -> return refused(tag, release, r.message)
        }
        val partial = store.partialFile(tag)
        val actual = when (val r = UpdateDownloader.fetchApk(assets, partial)) {
            is UpdateDownloader.Result.Ok -> r.value
            is UpdateDownloader.Result.Blocked -> return Result.success()
            is UpdateDownloader.Result.Failed -> return retryOrGiveUp("APK", r.message)
            is UpdateDownloader.Result.Invalid -> return refused(tag, release, r.message)
        }
        val facts = ApkInspector.archiveFacts(context, partial)
        val rejection = ApkVerification.verify(expected, actual, facts, installed)
        if (rejection != null) {
            partial.delete()
            return refused(tag, release, rejection.reason)
        }
        val ready = PendingUpdate(tag, facts!!.versionCode, actual, assets.apkName)
        if (!store.keep(partial, ready)) {
            Log.e(TAG, "could not move the checked download into place")
            return Result.success()
        }
        Log.i(TAG, "$tag downloaded and checked")
        AutoUpdater.onReady(context, ready, freshlyDownloaded = true)
        return Result.success()
    }

    private fun retryOrGiveUp(what: String, message: String): Result {
        Log.w(TAG, "the $what download failed (attempt ${runAttemptCount + 1}): $message")
        return if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.success()
    }

    private fun refused(tag: String, release: ResolvedRelease, reason: String): Result {
        Log.e(TAG, "$tag was not kept: $reason")
        UpdateStore(applicationContext).refuse(tag)
        UpdateNotifications.announce(applicationContext, release)
        return Result.success()
    }

    companion object {
        const val KEY_TAG = "tag"
        const val KEY_APK_URL = "apk_url"
        const val KEY_CHECKSUM_URL = "checksum_url"
        const val KEY_PAGE_URL = "page_url"
        private const val MAX_ATTEMPTS = 4
        private const val TAG = "UpdateDownload"
    }
}
