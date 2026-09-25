package brobata.physiboard.core.shell

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SettingsCodec
import brobata.physiboard.core.settings.SettingsKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The backup file's own metadata (settings-catalog.md SS7.1): what wrote it and when, so a future
 * reader can tell a stale backup from a current one without guessing.
 */
data class BackupMeta(val versionCode: Int, val versionName: String, val timestampIso: String)

/** A decoded backup: its meta plus the flat settings map it carried. spec: SS7.1, adapted for 3.0's one settings store (app-shell.md SS30). */
data class BackupFile(val meta: BackupMeta, val entries: Map<String, String>)

/**
 * Encodes and decodes PhysiBoard's backup file as one JSON document over the
 * [SettingsCodec] flat map. spec: settings-catalog.md SS7, kept for 3.0 per app-shell.md SS30
 * ("Backup: files list, zip layout, meta, validity checks, rollback, dictionary refresh | Keep")
 * but simplified to match 3.0's single settings store: one preference file, not several, so the
 * archive carries one `entries` object instead of one preferences JSON file per store, and it never carries
 * `embedded_adb` (SS13 Keep/Drop: "a pairing secret ... is not a setting").
 *
 * Zipping this document together with any binary side-files (`keyboard_layouts/`, dictionaries)
 * is the host's job; this module only knows the settings half of the archive.
 */
object BackupCodec {
    private const val META_KEY = "backup_meta"
    private const val ENTRIES_KEY = "entries"
    private const val VERSION_CODE_KEY = "versionCode"
    private const val VERSION_NAME_KEY = "versionName"
    private const val TIMESTAMP_KEY = "timestamp"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true; prettyPrintIndent = "  " }

    fun encode(settings: Settings, meta: BackupMeta): String {
        val root = JsonObject(
            mapOf(
                META_KEY to JsonObject(
                    mapOf(
                        VERSION_CODE_KEY to JsonPrimitive(meta.versionCode),
                        VERSION_NAME_KEY to JsonPrimitive(meta.versionName),
                        TIMESTAMP_KEY to JsonPrimitive(meta.timestampIso),
                    ),
                ),
                ENTRIES_KEY to JsonObject(SettingsCodec.toMap(settings).mapValues { JsonPrimitive(it.value) }),
            ),
        )
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * spec: SS7.2 step 2: "Missing or unreadable [backup_meta.json]: the restore fails ... and
     * nothing is applied." Modeled here as a null result; the caller's job is to show "Not a
     * PhysiBoard backup: backup_meta.json is missing or unreadable".
     */
    fun decode(text: String): BackupFile? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val metaObj = root[META_KEY] as? JsonObject ?: return null
        val versionCode = (metaObj[VERSION_CODE_KEY] as? JsonPrimitive)?.intOrNull ?: return null
        val versionName = (metaObj[VERSION_NAME_KEY] as? JsonPrimitive)?.contentOrNull ?: return null
        val timestamp = (metaObj[TIMESTAMP_KEY] as? JsonPrimitive)?.contentOrNull ?: return null
        val entries = (root[ENTRIES_KEY] as? JsonObject)?.entries
            ?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }
            ?.toMap()
            .orEmpty()
        return BackupFile(BackupMeta(versionCode, versionName, timestamp), entries)
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
