package brobata.physiboard.app.shell

import android.content.Context
import android.util.Log
import brobata.physiboard.core.shell.PendingUpdate
import brobata.physiboard.core.shell.UpdateRecord
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

    /** The file a download is written to before it has passed its checks. */
    fun partialFile(): File = synchronized(LOCK) {
        dir.mkdirs()
        File(dir, PARTIAL)
    }

    /** The checked APK for [ready], or null when it is missing from disk. */
    fun apkFor(ready: PendingUpdate): File? = File(dir, ready.fileName).takeIf { it.isFile && it.length() > 0 }

    /** Moves a checked download into place as [ready], dropping any older APK. */
    fun keep(partial: File, ready: PendingUpdate): Boolean = synchronized(LOCK) {
        val target = File(dir, ready.fileName)
        deleteFiles(includingPartial = false)
        if (!partial.renameTo(target)) {
            partial.delete()
            return false
        }
        write(UpdateRecord(ready = ready, refusedTag = null))
        true
    }

    /** Forgets the downloaded update (installed, superseded, or no longer wanted) and deletes its file. */
    fun discardReady() = synchronized(LOCK) {
        deleteFiles(includingPartial = true)
        val record = read()
        if (record.ready != null) write(record.copy(ready = null))
    }

    /**
     * [tag]'s APK failed a check or Android refused it: delete it and do not download it again. A
     * different release already downloaded and checked is kept; only a half-finished download goes.
     */
    fun refuse(tag: String) = synchronized(LOCK) {
        val record = read()
        if (record.ready?.tag.let { it == null || it == tag }) {
            deleteFiles(includingPartial = true)
            write(UpdateRecord(ready = null, refusedTag = tag))
        } else {
            File(dir, PARTIAL).delete()
            write(record.copy(refusedTag = tag))
        }
    }

    private fun deleteFiles(includingPartial: Boolean) {
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".apk") || (includingPartial && file.name == PARTIAL)) file.delete()
        }
    }

    private companion object {
        const val TAG = "UpdateStore"
        const val DIR = "updates"
        const val RECORD = "record.json"
        const val PARTIAL = "download.part"
        val LOCK = Any()
    }
}
