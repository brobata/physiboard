package brobata.physiboard.core.suggestions

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Owns where dictionary files live on the device, and which one wins.
 *
 * Three tiers, highest precedence first:
 *
 *  1. [importedDir]   - a file the user chose themselves. Never replaced by anything we ship.
 *  2. [downloadedDir] - fetched from the project manifest. Updatable without an app release.
 *  3. APK assets      - the bundled languages, read-only, replaced only by an app update.
 *
 * Downloads and imports used to share one `custom/` folder. The comment on the old migration
 * said it "isolates user-imported dictionaries from bundled assets", which it did - but not
 * from each other, so downloading a language silently overwrote a dictionary the user had
 * imported for it, with no warning and no record that theirs had ever existed.
 *
 * Each installed file carries a [Meta] sidecar. Without one nothing on the device can say where
 * a file came from or whether it is stale: the manifest's `updatedAt` was parsed and then
 * compared against nothing, so an installed dictionary could never be updated.
 */
object DictionaryStore {

    private const val TAG = "DictionaryStore"
    private const val ROOT = "dictionaries_serialized"
    private const val SUFFIX = "_base.dict"

    const val ORIGIN_DOWNLOAD = "download"
    const val ORIGIN_IMPORT = "import"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class Meta(
        val origin: String,
        val sha256: String? = null,
        val bytes: Long = 0L,
        val sourceReleaseTag: String? = null,
        val manifestUpdatedAt: String? = null,
        val installedAt: Long = 0L
    )

    fun fileNameFor(languageCode: String): String = "${languageCode.lowercase()}$SUFFIX"

    fun languageOf(fileName: String): String? =
        if (fileName.endsWith(SUFFIX)) fileName.removeSuffix(SUFFIX).lowercase() else null

    fun importedDir(context: Context): File =
        File(context.filesDir, "$ROOT/imported").apply { mkdirs() }

    fun downloadedDir(context: Context): File =
        File(context.filesDir, "$ROOT/downloaded").apply { mkdirs() }

    /** Pre-split location; kept only so [migrateLegacy] can drain it. */
    private fun legacyCustomDir(context: Context): File = File(context.filesDir, "$ROOT/custom")

    /** Tiers in precedence order. */
    private fun tiers(context: Context): List<File> =
        listOf(importedDir(context), downloadedDir(context))

    /** The installed file that should be loaded for [languageCode], or null to fall back to assets. */
    fun resolve(context: Context, languageCode: String): File? {
        val name = fileNameFor(languageCode)
        return tiers(context)
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.length() > 0 }
    }

    fun originOf(context: Context, languageCode: String): String? {
        val name = fileNameFor(languageCode)
        if (File(importedDir(context), name).isFile) return ORIGIN_IMPORT
        if (File(downloadedDir(context), name).isFile) return ORIGIN_DOWNLOAD
        return null
    }

    /** Every language with an installed file, across both writable tiers. */
    fun installedLanguages(context: Context): Set<String> =
        tiers(context)
            .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
            .filter { it.isFile }
            .mapNotNull { languageOf(it.name) }
            .toSet()

    fun readMeta(context: Context, languageCode: String): Meta? {
        val name = fileNameFor(languageCode)
        for (dir in tiers(context)) {
            if (!File(dir, name).isFile) continue
            val metaFile = metaFileFor(dir, languageCode)
            if (!metaFile.isFile) return null
            return runCatching { json.decodeFromString<Meta>(metaFile.readText()) }
                .onFailure { Log.e(TAG, "Unreadable sidecar for $languageCode", it) }
                .getOrNull()
        }
        return null
    }

    private fun writeMeta(dir: File, languageCode: String, meta: Meta): Boolean =
        runCatching { metaFileFor(dir, languageCode).writeText(json.encodeToString(meta)) }
            .onFailure { Log.e(TAG, "Failed writing sidecar for $languageCode", it) }
            .isSuccess

    private fun metaFileFor(dir: File, languageCode: String) =
        File(dir, "${languageCode.lowercase()}.meta.json")

    /**
     * Moves a verified [source] into the tier for [origin] and records its sidecar.
     *
     * The caller is expected to have already checked the file's hash and parsed it; this only
     * places it. An import never overwrites a download and a download never overwrites an
     * import, because each origin owns its own directory.
     */
    fun install(
        context: Context,
        source: File,
        languageCode: String,
        origin: String,
        meta: Meta
    ): Boolean {
        val dir = if (origin == ORIGIN_IMPORT) importedDir(context) else downloadedDir(context)
        val dest = File(dir, fileNameFor(languageCode))
        val staged = File(dir, "${fileNameFor(languageCode)}.part")

        return runCatching {
            source.inputStream().use { input ->
                staged.outputStream().use { output -> input.copyTo(output) }
            }
            // Rename last: a kill mid-copy leaves a .part to sweep, never a truncated
            // dictionary in place of a working one.
            if (dest.exists()) dest.delete()
            if (!staged.renameTo(dest)) throw IllegalStateException("rename failed")
            writeMeta(dir, languageCode, meta.copy(origin = origin, installedAt = System.currentTimeMillis()))
            true
        }.onFailure {
            Log.e(TAG, "Failed installing $languageCode from $origin", it)
            staged.delete()
        }.getOrDefault(false)
    }

    /** Removes the installed file and sidecar for [languageCode] in [origin]'s tier. */
    fun remove(context: Context, languageCode: String, origin: String): Boolean {
        val dir = if (origin == ORIGIN_IMPORT) importedDir(context) else downloadedDir(context)
        val removed = File(dir, fileNameFor(languageCode)).delete()
        metaFileFor(dir, languageCode).delete()
        return removed
    }

    /**
     * True when the manifest advertises a build newer than what is installed.
     *
     * An imported dictionary is never considered updatable - it is not ours to replace.
     * A download with no sidecar predates this split, so it is treated as unknown rather than
     * stale; re-downloading every language on upgrade would be a rude surprise on mobile data.
     */
    fun hasUpdate(context: Context, languageCode: String, manifestUpdatedAt: String?): Boolean {
        if (manifestUpdatedAt == null) return false
        if (originOf(context, languageCode) != ORIGIN_DOWNLOAD) return false
        val installed = readMeta(context, languageCode)?.manifestUpdatedAt ?: return false
        return manifestUpdatedAt > installed
    }

    /**
     * Drains the old shared `custom/` folder into [downloadedDir].
     *
     * Everything there arrived through the download path - the only writer that existed - so
     * classifying it as a download is accurate. Files are moved, not copied, and anything
     * already present in the destination wins.
     */
    fun migrateLegacy(context: Context) {
        val legacy = legacyCustomDir(context)
        if (!legacy.isDirectory) return
        val dest = downloadedDir(context)

        legacy.listFiles()?.forEach { file ->
            if (!file.isFile || !file.name.endsWith(SUFFIX)) return@forEach
            val target = File(dest, file.name)
            if (target.exists()) {
                file.delete()
                return@forEach
            }
            if (!file.renameTo(target)) {
                runCatching {
                    file.copyTo(target, overwrite = false)
                    file.delete()
                }.onFailure { Log.e(TAG, "Failed migrating ${file.name}", it) }
            }
        }
        if (legacy.listFiles()?.isEmpty() == true) legacy.delete()
    }
}
