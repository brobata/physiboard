package brobata.physiboard.app.shell

import android.content.Context
import android.util.Log
import brobata.physiboard.core.shell.PendingUpdate
import brobata.physiboard.core.shell.UpdateAssetSelection
import brobata.physiboard.core.shell.UpdateRecord
import brobata.physiboard.core.shell.VersionComparison
import java.io.File

/**
 * Where a downloaded update lives until it is installed (app-shell.md SS32.6): the folder
 * `no_backup/updates/` inside PhysiBoard's own data. Android gives every app's data folder to that
 * app alone, so no other app can read the APK or change it between the check and the install;
 * "no_backup" keeps it out of every backup and device transfer. Nothing here is ever shared,
 * exported or handed to another app as a path.
 *
 * One lock guards the folder, because the download, the install and the result receiver can run
 * at once.
 */
class UpdateStore(context: Context) {
    private val dir = File(context.applicationContext.noBackupFilesDir, DIR)
    private val recordFile = File(dir, RECORD)

    fun read(): UpdateRecord = synchronized(LOCK) {
        UpdateRecord.decode(runCatching { recordFile.takeIf { it.isFile }?.readText() }.getOrNull())
    }

    fun write(record: UpdateRecord) = synchronized(LOCK) {
        dir.mkdirs()
        val temp = File(dir, "$RECORD.tmp")
        temp.writeText(record.encode())
        if (!temp.renameTo(recordFile)) {
            recordFile.delete()
            if (!temp.renameTo(recordFile)) Log.e(TAG, "could not save the update record")
        }
    }

    /**
     * The file [tag]'s download is written to before it has passed its checks,
     * `physiboard-<version>.apk.part`. One per release, so refusing one release can never delete
     * another release's download under the job writing it.
     */
    fun partialFile(tag: String): File = synchronized(LOCK) {
        dir.mkdirs()
        partialFor(tag) ?: throw IllegalArgumentException("not a release tag: $tag")
    }

    private fun partialFor(tag: String): File? {
        val version = VersionComparison.normalize(tag)
        if (!PLAIN_VERSION.matches(version)) return null
        return File(dir, UpdateAssetSelection.apkName(version) + PARTIAL_SUFFIX)
    }

    /** The checked APK for [ready], or null when it is missing from disk. */
    fun apkFor(ready: PendingUpdate): File? = File(dir, ready.fileName).takeIf { it.isFile && it.length() > 0 }

    /** Moves a checked download into place as [ready], dropping any older APK. */
    fun keep(partial: File, ready: PendingUpdate): Boolean = synchronized(LOCK) {
        val target = File(dir, ready.fileName)
        deleteApks()
        if (!partial.renameTo(target)) {
            partial.delete()
            return false
        }
        // Only one download runs at a time, so any other partial file is a leftover of one that died.
        dir.listFiles()?.forEach { if (it.name.endsWith(PARTIAL_SUFFIX)) it.delete() }
        write(UpdateRecord(ready = ready, refusedTag = null))
        true
    }

    /**
     * Forgets the downloaded update (installed, superseded, or no longer wanted) and deletes its file.
     * A download in progress is left alone: only the download job owns its partial file, and deleting
     * it under that job would make a good newer release look broken and be refused for good.
     */
    fun discardReady() = synchronized(LOCK) {
        deleteApks()
        val record = read()
        if (record.ready != null) write(record.copy(ready = null))
    }

    /**
     * [tag]'s APK failed a check or Android refused it: delete it and do not download it again.
     * Only [tag]'s own files go: a different release already downloaded and checked is kept, and so
     * is a different release's download in progress.
     */
    fun refuse(tag: String) = synchronized(LOCK) {
        val record = read()
        partialFor(tag)?.delete()
        if (record.ready?.tag.let { it == null || it == tag }) {
            deleteApks()
            write(UpdateRecord(ready = null, refusedTag = tag))
        } else {
            write(record.copy(refusedTag = tag))
        }
    }

    private fun deleteApks() {
        dir.listFiles()?.forEach { file -> if (file.name.endsWith(".apk")) file.delete() }
    }

    private companion object {
        const val TAG = "UpdateStore"
        const val DIR = "updates"
        const val RECORD = "record.json"
        const val PARTIAL_SUFFIX = ".part"
        val PLAIN_VERSION = Regex("""\d{1,4}(\.\d{1,4}){1,3}""")
        val LOCK = Any()
    }
}
