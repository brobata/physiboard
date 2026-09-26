package brobata.physiboard.app.settings

import android.content.Context
import android.content.Intent
import brobata.physiboard.core.dict.DictionaryBroadcastActions
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.shell.BackupCodec
import brobata.physiboard.core.shell.BackupFile
import brobata.physiboard.core.shell.BackupMeta
import brobata.physiboard.core.shell.BackupRestore
import brobata.physiboard.core.shell.RestoreOutcome
import brobata.physiboard.core.shell.ZipEntryPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Assembles and unpacks the backup ZIP settings-catalog.md SS7.1 describes: `backup_meta.json`,
 * one `prefs/<file>.json`, and a `files/<relative path>` copy of whichever of
 * [LegacyImporter.SIDE_FILES] actually exist under `context.filesDir` (SS7.1's own list:
 * `ctrl_key_mappings.json`, `variations.json`, `user_defaults.json`, `locale_layout_mapping.json`,
 * and everything under `keyboard_layouts/`). `:core:shell`'s [BackupCodec] only knows the two pure
 * JSON document shapes; this object owns the zip and file I/O that needs `Context` and
 * `java.util.zip`, which are fine to use directly here (only `:core:shell` is restricted to plain
 * Kotlin).
 */
object BackupArchive {
    /** The one `prefs/` file 3.0 writes, named after [brobata.physiboard.app.settings.SettingsStore]'s own DataStore file. */
    const val PREFS_FILE_NAME: String = "physiboard_settings"

    private const val META_ENTRY = "backup_meta.json"
    private const val PREFS_ENTRY = "prefs/$PREFS_FILE_NAME.json"
    private const val PREFS_PREFIX = "prefs/"
    private const val FILES_PREFIX = "files/"

    /**
     * True for a path the backup is allowed to write: one of the named side files, or something
     * inside the one directory of them (the custom layouts). Compared on the normalised path, so
     * a name that walks upward has already been refused by the zip-slip guard above.
     */
    private fun isRestorableSideFile(relativePath: String): Boolean =
        LegacyImporter.SIDE_FILES.any { allowed ->
            relativePath == allowed || relativePath.startsWith(allowed.removeSuffix("/") + "/")
        }

    /** Writes the whole archive to [output]: `backup_meta.json`, `prefs/<file>.json`, then a `files/<name>` entry for each side file that exists. spec: SS7.1. */
    suspend fun write(context: Context, settings: Settings, metaTemplate: BackupMeta, output: OutputStream): Unit =
        withContext(Dispatchers.IO) {
            val sideEntries = sideFilesOnDisk(context.filesDir)
            val meta = metaTemplate.copy(
                components = listOf(PREFS_ENTRY) + sideEntries.map { (relativePath, _) -> FILES_PREFIX + relativePath },
            )
            ZipOutputStream(output).use { zip ->
                zip.writeTextEntry(META_ENTRY, BackupCodec.encodeMeta(meta))
                zip.writeTextEntry(PREFS_ENTRY, BackupCodec.encodePrefsFile(PREFS_FILE_NAME, settings))
                for ((relativePath, source) in sideEntries) {
                    zip.putNextEntry(ZipEntry(FILES_PREFIX + relativePath))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

    /** Every [LegacyImporter.SIDE_FILES] entry that exists under [filesRoot], as (relative path, source file), recursing into a directory entry. */
    private fun sideFilesOnDisk(filesRoot: File): List<Pair<String, File>> =
        LegacyImporter.SIDE_FILES.flatMap { name ->
            val source = File(filesRoot, name)
            when {
                !source.exists() -> emptyList()
                source.isDirectory -> source.walkTopDown().filter { it.isFile }.map { it.relativeTo(filesRoot).path to it }.toList()
                else -> listOf(name to source)
            }
        }

    private fun ZipOutputStream.writeTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /** What restoring an archive produced, for the snackbar (SS7.2 step 7). */
    sealed interface RestoreResult {
        /** [outcome] already counts skipped settings keys; [sideFileFailures] and [unreadablePrefsFiles] add to that same "could not be applied" total. */
        data class Applied(val outcome: RestoreOutcome, val sideFileFailures: Int, val unreadablePrefsFiles: Int) : RestoreResult
        data class Failed(val reason: String) : RestoreResult
    }

    /**
     * Unzips [input] and applies it over [current]. spec: SS7.2 steps 1-7, scoped to what a
     * single-preference-store 3.0 needs: the elaborate 1.x restore-schema coercion and the
     * `variations.json` merge/`.bak` rollback machinery are out of scope here (SPEC GAP, see the
     * caller's report), but the zip-slip guard, the missing-meta failure, and the dictionary
     * reload broadcast are not skipped.
     */
    suspend fun restore(context: Context, current: Settings, input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        val entries = mutableMapOf<String, ByteArray>()
        var slipViolation = false
        val readOutcome = runCatching {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        if (!ZipEntryPaths.isSafeRelativePath(entry.name)) {
                            slipViolation = true
                        } else {
                            entries[entry.name] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        if (slipViolation) return@withContext RestoreResult.Failed("Refusing to unzip entry outside target dir")
        if (readOutcome.isFailure) {
            return@withContext RestoreResult.Failed(readOutcome.exceptionOrNull()?.message ?: "Unable to read archive")
        }

        val meta = entries[META_ENTRY]?.let { BackupCodec.decodeMeta(it.decodeToString()) }
            ?: return@withContext RestoreResult.Failed("Not a PhysiBoard backup: backup_meta.json is missing or unreadable")

        var unreadablePrefsFiles = 0
        val mergedEntries = mutableMapOf<String, String>()
        for ((name, bytes) in entries) {
            if (!name.startsWith(PREFS_PREFIX) || !name.endsWith(".json")) continue
            val decoded = BackupCodec.decodePrefsFile(bytes.decodeToString())
            if (decoded == null) unreadablePrefsFiles++ else mergedEntries.putAll(decoded.second)
        }
        val outcome = BackupRestore.restore(current, BackupFile(meta, mergedEntries))

        var sideFileFailures = 0
        var userDefaultsRestored = false
        val filesRoot = context.filesDir
        for ((name, bytes) in entries) {
            if (!name.startsWith(FILES_PREFIX)) continue
            val relativePath = name.removePrefix(FILES_PREFIX)
            if (relativePath.isEmpty()) continue
            // Only the side files this app puts in an archive are written back. A backup is a
            // file the user can be handed by anyone, and without this a tampered one could name
            // any path under the app's own storage and have it written (2026-09-26 review).
            if (!isRestorableSideFile(relativePath)) {
                sideFileFailures++
                continue
            }
            val target = File(filesRoot, relativePath)
            val written = runCatching {
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }.isSuccess
            if (written) {
                if (relativePath == "user_defaults.json" || relativePath.endsWith("/user_defaults.json")) userDefaultsRestored = true
            } else {
                sideFileFailures++
            }
        }
        if (userDefaultsRestored) {
            // spec: SS7.2 step 6, "if a restored file is named user_defaults.json at any depth, the broadcast ... is sent".
            context.sendBroadcast(Intent(DictionaryBroadcastActions.USER_DICTIONARY_UPDATED).setPackage(context.packageName))
        }

        RestoreResult.Applied(outcome, sideFileFailures, unreadablePrefsFiles)
    }
}
