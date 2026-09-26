package brobata.physiboard.app.settings

import android.content.Context
import android.content.Intent
import brobata.physiboard.core.dict.DictionaryBroadcastActions
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.DictionaryManifestItem
import brobata.physiboard.core.dict.DictionaryOrigin
import brobata.physiboard.core.dict.LocalDictionaryFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.security.MessageDigest

/**
 * The local half of the installed-dictionaries screen: where files actually live on this device,
 * matching what `:ime`'s `DictionaryAssetLoader` already reads (bundled `dictionaries/<lang>.pbd`
 * assets), extended with the two writable tiers. spec: dictionaries-languages.md SS3.
 *
 * SPEC GAP: SS3 names the bundled directory `common/dictionaries_serialized/` and the extension
 * `.dict`; the 3.0 build already ships bundled dictionaries as `dictionaries/<lang>.pbd` (see
 * `app/src/main/assets/dictionaries/en.pbd` and `:ime`'s `DictionaryAssetLoader`, both pre-existing
 * and outside this module's scope). This store follows that established convention for the two
 * writable tiers too (`files/dictionaries/downloaded/`, `files/dictionaries/imported/`, extension
 * `.pbd`) so every tier agrees on one naming scheme, rather than reintroducing the spec's `.dict`
 * name for only two of the three tiers.
 *
 * Manifest fetch and download are network I/O and stay in [DictionaryDownloader]; parsing and the
 * installed/missing/updatable decision are pure and live in `:core:dict`'s `DictionaryCatalog`.
 */
class DictionaryFileStore(private val context: Context) {
    private val downloadedDir = File(context.filesDir, "dictionaries/downloaded").apply { mkdirs() }
    private val importedDir = File(context.filesDir, "dictionaries/imported").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Every dictionary this device already has, bundled first, matching SS6's dedup order. */
    suspend fun listLocal(): List<LocalDictionaryFile> = withContext(Dispatchers.IO) {
        val bundled = runCatching { context.assets.list("dictionaries") }.getOrNull().orEmpty()
            .filter { it.endsWith(".pbd", ignoreCase = true) }
            .map { LocalDictionaryFile(fileName(it), it.removeSuffix(".pbd"), DictionaryOrigin.BUNDLED) }
        val downloaded = downloadedDir.listFiles { f -> f.extension == "pbd" }.orEmpty().map {
            val meta = readSidecar(it)
            LocalDictionaryFile(fileName(it.name), it.nameWithoutExtension, DictionaryOrigin.DOWNLOADED, meta?.get("manifestUpdatedAt"))
        }
        val imported = importedDir.listFiles { f -> f.extension == "pbd" }.orEmpty().map {
            LocalDictionaryFile(fileName(it.name), it.nameWithoutExtension, DictionaryOrigin.IMPORTED)
        }
        bundled + downloaded + imported
    }

    /** The manifest-shaped `<lang>_base.dict` name a [DictionaryRow] is keyed on, from this store's own `<lang>.pbd` name. */
    private fun fileName(pbdFileName: String): String = "${pbdFileName.removeSuffix(".pbd")}_base.dict"

    /** SS5.3 step 5: installs verified [bytes] into the downloaded tier with its sidecar. */
    suspend fun installDownloaded(item: DictionaryManifestItem, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching {
            stageAtomically(downloadedDir, item.languageCode, bytes)
            writeSidecar(
                File(downloadedDir, "${item.languageCode}.meta.json"),
                mapOf(
                    "origin" to "download",
                    "sha256" to item.sha256,
                    "bytes" to item.bytes.toString(),
                    "manifestUpdatedAt" to item.updatedAt,
                    "installedAt" to System.currentTimeMillis().toString(),
                ),
            )
            true
        }.getOrDefault(false)
        if (ok) notifyDictionaryChanged()
        ok
    }

    /** SS5.6 step 4: installs an imported file (shadowing any bundled or downloaded copy of the same language). */
    suspend fun installImported(languageCode: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching {
            stageAtomically(importedDir, languageCode, bytes)
            writeSidecar(
                File(importedDir, "$languageCode.meta.json"),
                mapOf("origin" to "import", "bytes" to bytes.size.toString(), "installedAt" to System.currentTimeMillis().toString()),
            )
            true
        }.getOrDefault(false)
        if (ok) notifyDictionaryChanged()
        ok
    }

    /**
     * SS3's "Staging": copies [bytes] to `<tier>/<lang>.pbd.part`, deletes any existing file, then
     * renames the `.part` into place, so a process killed mid-write leaves a `.part` file and
     * never a truncated dictionary. The `.part` file is removed if any step after the write fails.
     */
    private fun stageAtomically(dir: File, languageCode: String, bytes: ByteArray) {
        val part = File(dir, "$languageCode.pbd.part")
        val target = File(dir, "$languageCode.pbd")
        try {
            part.writeBytes(bytes)
            target.delete()
            if (!part.renameTo(target)) error("rename of $part to $target failed")
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    /** SS5.7: checks the imported tier first, then downloaded; a bundled file can never be uninstalled. */
    suspend fun uninstall(languageCode: String): UninstallOutcome = withContext(Dispatchers.IO) {
        val importedFile = File(importedDir, "$languageCode.pbd")
        val downloadedFile = File(downloadedDir, "$languageCode.pbd")
        val target = when {
            importedFile.exists() -> importedFile
            downloadedFile.exists() -> downloadedFile
            else -> return@withContext UninstallOutcome.NOT_FOUND
        }
        val sidecar = File(target.parentFile, "$languageCode.meta.json")
        val deleted = runCatching { target.delete() }.getOrDefault(false)
        if (!deleted) return@withContext UninstallOutcome.FAILED
        sidecar.delete()
        notifyDictionaryChanged()
        UninstallOutcome.SUCCESS
    }

    /** SS2.2: a dictionary decodes iff [DictionaryIndex.fromPbdBytes] accepts its bytes. */
    fun decodesAsDictionary(bytes: ByteArray): Boolean = DictionaryIndex.fromPbdBytes(bytes) != null

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun readSidecar(pbdFile: File): Map<String, String>? {
        val sidecar = File(pbdFile.parentFile, "${pbdFile.nameWithoutExtension}.meta.json")
        if (!sidecar.exists()) return null
        val obj = runCatching { json.parseToJsonElement(sidecar.readText()) as? JsonObject }.getOrNull() ?: return null
        return obj.entries.associate { (k, v) -> k to (v as? JsonPrimitive)?.content.orEmpty() }
    }

    private fun writeSidecar(file: File, fields: Map<String, String>) {
        val obj = JsonObject(fields.mapValues { JsonPrimitive(it.value) })
        file.writeText(json.encodeToString(JsonObject.serializer(), obj))
    }

    /**
     * spec SS17's Keep/Drop fix ("Per-process dictionary cache never invalidated | Fix | Reload
     * on install, import, uninstall"): tells any running keyboard process to drop its cached
     * dictionary for the affected language and reload it immediately, rather than waiting for the
     * next process start (SS4.1's 2.x behavior, which SS17 explicitly overrides).
     */
    private fun notifyDictionaryChanged() {
        val intent = Intent(DictionaryBroadcastActions.DICTIONARY_CHANGED).setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}

enum class UninstallOutcome { SUCCESS, NOT_FOUND, FAILED }
