package brobata.physiboard.core.shell

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SettingsCodec
import brobata.physiboard.core.settings.SettingsKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The backup file's own metadata (settings-catalog.md SS7.1): what wrote it and when, so a future
 * reader can tell a stale backup from a current one without guessing. [components] is the spec's
 * `"components"` list: the relative path of every other entry the writer put in the archive
 * (`prefs/<file>.json`, `files/<relative path>`), so a reader can tell what was meant to be there
 * without listing the zip itself. Defaults to empty for back-compat with a meta document written
 * before this field existed.
 */
data class BackupMeta(
    val versionCode: Int,
    val versionName: String,
    val timestampIso: String,
    val components: List<String> = emptyList(),
)

/** A decoded backup: its meta plus the flat settings map carried by the archive's `prefs/` file(s). spec: SS7.1, adapted for 3.0's one settings store (app-shell.md SS30). */
data class BackupFile(val meta: BackupMeta, val entries: Map<String, String>)

/**
 * Encodes and decodes the two JSON document shapes settings-catalog.md SS7.1 defines for the
 * backup archive: `backup_meta.json` and one `prefs/<file>.json`. Both are pure JSON-tree reads
 * and writes, no `java.util.zip` or `Context` involved; assembling them into an actual ZIP
 * alongside the `files/` side-file copies (`ctrl_key_mappings.json`, `variations.json`,
 * `keyboard_layouts/`, ...) is `:app`'s job (`BackupArchive.kt`), since that needs real file and
 * zip I/O (app-shell.md SS30, "Backup: files list, zip layout, meta ... | Keep").
 *
 * 3.0 keeps one settings store, and every row in it is a `stringPreferencesKey` (settings-catalog.md
 * SS13, "one preference file with typed rows", typed by [SettingsCodec] itself rather than by
 * SharedPreferences' boxed types), so there is no boolean/int/long/float/string_set distinction
 * left to preserve at this layer: every `prefs/<file>.json` entry's spec-shaped `"type"` is
 * `"string"`, carrying the same flat string [SettingsCodec] already reads and writes everywhere
 * else. This is a deliberate 3.0 simplification of SS7.1's `"type"` field, not an omission.
 */
object BackupCodec {
    private const val VERSION_CODE_KEY = "versionCode"
    private const val VERSION_NAME_KEY = "versionName"
    private const val TIMESTAMP_KEY = "timestamp"
    private const val COMPONENTS_KEY = "components"
    private const val NAME_KEY = "name"
    private const val ENTRIES_KEY = "entries"
    private const val TYPE_KEY = "type"
    private const val VALUE_KEY = "value"
    private const val STRING_TYPE = "string"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true; prettyPrintIndent = "  " }

    /** `backup_meta.json` (SS7.1): `{"versionCode", "versionName", "timestamp", "components"}`, pretty-printed. */
    fun encodeMeta(meta: BackupMeta): String {
        val root = JsonObject(
            mapOf(
                VERSION_CODE_KEY to JsonPrimitive(meta.versionCode),
                VERSION_NAME_KEY to JsonPrimitive(meta.versionName),
                TIMESTAMP_KEY to JsonPrimitive(meta.timestampIso),
                COMPONENTS_KEY to JsonArray(meta.components.map { JsonPrimitive(it) }),
            ),
        )
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * spec: SS7.2 step 2: "Missing or unreadable [backup_meta.json]: the restore fails ... and
     * nothing is applied." Modeled here as a null result; the caller's job is to show "Not a
     * PhysiBoard backup: backup_meta.json is missing or unreadable".
     */
    fun decodeMeta(text: String): BackupMeta? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val versionCode = (root[VERSION_CODE_KEY] as? JsonPrimitive)?.intOrNull ?: return null
        val versionName = (root[VERSION_NAME_KEY] as? JsonPrimitive)?.contentOrNull ?: return null
        val timestamp = (root[TIMESTAMP_KEY] as? JsonPrimitive)?.contentOrNull ?: return null
        val components = (root[COMPONENTS_KEY] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        return BackupMeta(versionCode, versionName, timestamp, components)
    }

    /** `prefs/<file>.json` (SS7.1): `{"name": file, "entries": {key: {"type", "value"}}}`. */
    fun encodePrefsFile(fileName: String, settings: Settings): String {
        val entries = SettingsCodec.toMap(settings).mapValues { (_, value) ->
            JsonObject(mapOf(TYPE_KEY to JsonPrimitive(STRING_TYPE), VALUE_KEY to JsonPrimitive(value)))
        }
        val root = JsonObject(mapOf(NAME_KEY to JsonPrimitive(fileName), ENTRIES_KEY to JsonObject(entries)))
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * spec: SS7.2 step 3: "Read every `prefs` JSON file. A file that does not parse is recorded
     * as unreadable and skipped." Modeled as a null result on anything unreadable; the caller
     * counts it. Returns the file's own `"name"` alongside its entries so a caller juggling more
     * than one `prefs/` file (3.0 only ever writes one) can tell them apart.
     */
    fun decodePrefsFile(text: String): Pair<String, Map<String, String>>? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val name = (root[NAME_KEY] as? JsonPrimitive)?.contentOrNull ?: return null
        val entries = (root[ENTRIES_KEY] as? JsonObject)?.entries
            ?.mapNotNull { (key, value) ->
                val entryObject = value as? JsonObject ?: return@mapNotNull null
                (entryObject[VALUE_KEY] as? JsonPrimitive)?.contentOrNull?.let { key to it }
            }
            ?.toMap()
            .orEmpty()
        return name to entries
    }
}

/**
 * Pure zip-slip guard (settings-catalog.md SS7.2 step 1: "Any entry whose resolved path escapes
 * the target directory aborts the restore"). Normalizes a zip entry's name as plain path
 * arithmetic, with no `File` or filesystem access, so it is checkable without `Context` and
 * cannot be fooled by a symlink existing (or not) at extraction time: it rejects anything that
 * climbs above its own root before `:app`'s `BackupArchive` ever resolves it against
 * `context.filesDir`.
 */
object ZipEntryPaths {
    /** False for an absolute path, a Windows drive path, or a relative path whose `..` segments climb above its own root. */
    fun isSafeRelativePath(entryName: String): Boolean {
        if (entryName.isEmpty()) return false
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/")) return false
        if (normalized.length >= 2 && normalized[1] == ':') return false // e.g. "C:/..."
        var depth = 0
        for (segment in normalized.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    depth--
                    if (depth < 0) return false
                }
                else -> depth++
            }
        }
        return true
    }

    /**
     * True when a backup's `files/<relativePath>` entry may be written under `filesDir`:
     * [relativePath] must be safe measured from its own root AND name one of [allowedNames] or
     * something inside one of them.
     *
     * Both halves are needed and the first is the one that was missing. [isSafeRelativePath] run
     * over the whole entry name measures depth from the archive's root, while these files are
     * written from `filesDir` -- the `files/` segment, one level deeper. So
     * `files/keyboard_layouts/../../shared_prefs/embedded_adb.xml` kept the archive-root depth at
     * zero or above, passed that check, began with `keyboard_layouts/` and passed the name list,
     * and resolved to the app's shared preferences. A backup is a file the user can be handed by
     * anyone, so that was a tampered archive overwriting the ADB pairing key (2026-09-29).
     */
    fun isRestorableSideFilePath(relativePath: String, allowedNames: List<String>): Boolean =
        isSafeRelativePath(relativePath) &&
            allowedNames.any { allowed ->
                relativePath == allowed || relativePath.startsWith(allowed.removeSuffix("/") + "/")
            }
}

/** The result of applying a decoded backup onto the phone's current settings. spec: SS7.2 step 7's counts. */
data class RestoreOutcome(val settings: Settings, val appliedCount: Int, val skippedCount: Int)

/**
 * Restores a decoded backup over the current settings (settings-catalog.md SS7.2 step 5), with
 * 3.0's replacement for the fixed 1.x restore schema: "every key the importer [here, the codec]
 * knows" (app-shell.md SS30). A key the codec never writes on its own (an unknown row, a typo, a
 * key from a future build) is skipped and counted rather than merged in blind, because
 * [SettingsCodec.fromMap] would silently ignore it anyway and the snackbar needs a true count.
 */
object BackupRestore {
    /** Every key a fresh [Settings] round-trips through the codec, plus the one dynamic prefix (`auto_correct_custom_<lang>`) the codec also accepts. */
    private val knownFixedKeys: Set<String> by lazy { SettingsCodec.toMap(Settings()).keys }

    private fun isKnownKey(key: String): Boolean =
        key in knownFixedKeys || key.startsWith(SettingsKeys.AUTO_CORRECT_CUSTOM_PREFIX)

    fun restore(current: Settings, backup: BackupFile): RestoreOutcome {
        val currentMap = SettingsCodec.toMap(current).toMutableMap()
        var applied = 0
        var skipped = 0
        for ((key, value) in backup.entries) {
            if (isKnownKey(key)) {
                currentMap[key] = value
                applied++
            } else {
                skipped++
            }
        }
        return RestoreOutcome(SettingsCodec.fromMap(currentMap), applied, skipped)
    }
}
