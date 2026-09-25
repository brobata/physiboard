package brobata.physiboard.core.dict

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The hosted dictionary manifest. spec: dictionaries-languages.md SS5.1 ("Manifest contract").
 * Fetching the manifest URL (`https://brobata.github.io/physiboard-dict/dicts-manifest.json`)
 * and the file download itself are network I/O and belong to `:app` (rebuild-from-scratch.md,
 * "Downloading is network glue"); parsing the body and deciding install state is pure and lives
 * here so it is a JVM test, not a device test.
 */
data class DictionaryManifest(
    val schemaVersion: Int,
    val generatedAt: String,
    val releaseTag: String,
    val items: List<DictionaryManifestItem>,
)

/** One manifest entry. spec: dictionaries-languages.md SS5.1's "Item contract" table. */
data class DictionaryManifestItem(
    val id: String,
    val filename: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val updatedAt: String,
    val name: String,
    val shortDescription: String,
    val languageTag: String,
) {
    /** SS5.1: "the language code is what remains after removing `_base.dict` (or, failing that, `.dict`)." */
    val languageCode: String
        get() = when {
            filename.endsWith("_base.dict", ignoreCase = true) -> filename.dropLast("_base.dict".length)
            filename.endsWith(".dict", ignoreCase = true) -> filename.dropLast(".dict".length)
            else -> filename
        }
}

/**
 * Parses the manifest body. spec: SS5.1, "All fields are required; unknown fields are ignored; a
 * missing field is a parse error." A parse error, here, is simply "not parsed": every outcome is
 * nullable so the caller (SS5.2's outcome table) can turn it into the right snackbar without this
 * module knowing about snackbars.
 */
object DictionaryManifestCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String?): DictionaryManifest? {
        if (body.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val schemaVersion = root.int("schemaVersion") ?: return null
        val generatedAt = root.string("generatedAt") ?: return null
        val releaseTag = root.string("releaseTag") ?: return null
        val itemsArray = root["items"] as? JsonArray ?: return null
        val items = itemsArray.map { parseItem(it as? JsonObject ?: return null) ?: return null }
        return DictionaryManifest(schemaVersion, generatedAt, releaseTag, items)
    }

    private fun parseItem(obj: JsonObject): DictionaryManifestItem? = DictionaryManifestItem(
        id = obj.string("id") ?: return null,
        filename = obj.string("filename") ?: return null,
        url = obj.string("url") ?: return null,
        bytes = obj.long("bytes") ?: return null,
        sha256 = obj.string("sha256") ?: return null,
        updatedAt = obj.string("updatedAt") ?: return null,
        name = obj.string("name") ?: return null,
        shortDescription = obj.string("shortDescription") ?: return null,
        languageTag = obj.string("languageTag") ?: return null,
    )

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
}

/** Where a locally present dictionary file came from. spec: dictionaries-languages.md SS3, SS5. */
enum class DictionaryOrigin { BUNDLED, DOWNLOADED, IMPORTED }

/**
 * A dictionary file this device already has, before the manifest is consulted. [manifestUpdatedAt]
 * is the sidecar's `manifestUpdatedAt` (SS5.3 step 5), present only for a downloaded file.
 */
data class LocalDictionaryFile(
    val fileName: String,
    val languageCode: String,
    val origin: DictionaryOrigin,
    val manifestUpdatedAt: String? = null,
)

/** One row of the installed-dictionaries screen (dictionaries-languages.md SS6), computed rather than drawn. */
data class DictionaryRow(
    val languageCode: String,
    val fileName: String,
    val displayName: String,
    val installed: Boolean,
    val installedOrigin: DictionaryOrigin?,
    val manifestItem: DictionaryManifestItem?,
    val updatable: Boolean,
) {
    /** SS6: "Installed" (any local file), "Imported" (any file in a writable tier), "Available online". */
    val badges: Set<String>
        get() = buildSet {
            if (installed) add("Installed")
            if (installedOrigin == DictionaryOrigin.IMPORTED || installedOrigin == DictionaryOrigin.DOWNLOADED) add("Imported")
            if (!installed && manifestItem != null) add("Available online")
        }

    /** SS6: "the delete icon (installed, not bundled)". */
    val canUninstall: Boolean get() = installed && installedOrigin != DictionaryOrigin.BUNDLED

    /** SS6: "the download icon (online, not installed)". */
    val canDownload: Boolean get() = !installed && manifestItem != null
}

/**
 * The pure half of the installed-dictionaries screen: merging local files with the manifest and
 * deciding what is installed, missing or updatable. spec: dictionaries-languages.md SS6 (the merge
 * and dedup rule) and SS5.5 (the updatable rule). Fetching and downloading stay in `:app`.
 */
object DictionaryCatalog {

    /**
     * SS6: "The local list is built as bundled files first, then writable-tier files, and
     * deduplicated by file name keeping the first," then merged with the manifest by lowercase
     * file name. [displayNameFor] supplies the local-language display name (SS6: "the language's
     * own name in its own language... computed from the language code"), since that table is a
     * device/locale concern this module does not own.
     */
    fun merge(
        local: List<LocalDictionaryFile>,
        manifest: List<DictionaryManifestItem>,
        displayNameFor: (String) -> String,
    ): List<DictionaryRow> {
        val dedupedLocal = LinkedHashMap<String, LocalDictionaryFile>()
        for (file in local) dedupedLocal.putIfAbsent(file.fileName.lowercase(), file)

        val manifestByFileName = manifest.associateBy { it.filename.lowercase() }
        val rows = LinkedHashMap<String, DictionaryRow>()

        for ((key, file) in dedupedLocal) {
            val item = manifestByFileName[key]
            rows[key] = DictionaryRow(
                languageCode = file.languageCode,
                fileName = file.fileName,
                displayName = item?.name ?: displayNameFor(file.languageCode),
                installed = true,
                installedOrigin = file.origin,
                manifestItem = item,
                updatable = isUpdatable(file, item),
            )
        }
        for (item in manifest) {
            val key = item.filename.lowercase()
            if (key in rows) continue
            rows[key] = DictionaryRow(
                languageCode = item.languageCode,
                fileName = item.filename,
                displayName = item.name,
                installed = false,
                installedOrigin = null,
                manifestItem = item,
                updatable = false,
            )
        }
        return rows.values.sortedBy { it.displayName.lowercase() }
    }

    /**
     * spec SS5.5: "A downloaded dictionary is 'updatable' when the manifest's `updatedAt`... is
     * greater than the sidecar's `manifestUpdatedAt`", compared as a plain string; an imported
     * dictionary is never updatable; a downloaded file with no sidecar reads as unknown, not stale.
     */
    fun isUpdatable(local: LocalDictionaryFile, manifestItem: DictionaryManifestItem?): Boolean {
        if (local.origin != DictionaryOrigin.DOWNLOADED) return false
        val stamped = local.manifestUpdatedAt ?: return false
        val current = manifestItem?.updatedAt ?: return false
        return current > stamped
    }
}
