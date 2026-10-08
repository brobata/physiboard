package brobata.physiboard.app.settings

import android.content.Context
import brobata.physiboard.core.settings.LegacyImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The one-shot 2.x import (rebuild-from-scratch.md, "Settings": "reads the 2.x SharedPreferences
 * file ..., maps them by name, and records the import so it never runs twice"). The mapping itself
 * is [LegacyImport], pure and tested; this class only finds the file, reads it the way Android
 * reads it, and records the outcome in the store next to the imported rows.
 *
 * The 2.x file is `physiboard_prefs` (settings-catalog.md SS1); the applicationId did not change,
 * so after an in-place update it sits in this app's own `shared_prefs/`. It is left where it is:
 * the user dictionary and the broker pairing still live beside it at their 2.x paths (the plan's
 * "Broker key files and the user dictionary stay at their 2.x paths"), and nothing here deletes
 * anything. A build with another applicationId suffix (the sideload build) never finds the file
 * and records that.
 */
class LegacyImporter(private val context: Context, private val store: SettingsStore) {

    sealed interface Outcome {
        /** The marker was already there; nothing was read or written. */
        data object AlreadyRan : Outcome

        /** No 2.x store on disk; recorded so this is not checked again. */
        data object NoLegacyStore : Outcome

        data class Imported(val carried: Set<String>, val ignored: Set<String>, val sideFilesFound: List<String>) : Outcome
    }

    suspend fun runOnce(): Outcome = withContext(Dispatchers.IO) {
        if (store.rawValue(STATE_KEY) != null) return@withContext Outcome.AlreadyRan
        val legacyFile = File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS_NAME.xml")
        if (!legacyFile.isFile) {
            store.putRaw(mapOf(STATE_KEY to STATE_ABSENT, AT_KEY to System.currentTimeMillis().toString()))
            return@withContext Outcome.NoLegacyStore
        }
        val legacy: Map<String, Any?> = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).all
        val result = LegacyImport.import(legacy)
        val sideFiles = SIDE_FILES.filter { File(context.filesDir, it).exists() } +
            SIDE_STORES.filter { File(context.applicationInfo.dataDir, "shared_prefs/$it.xml").isFile }.map { "shared_prefs/$it" }
        store.replaceAll(
            result.settings,
            markers = mapOf(
                STATE_KEY to STATE_IMPORTED,
                AT_KEY to System.currentTimeMillis().toString(),
                CARRIED_KEY to result.carried.sorted().joinToString(","),
                IGNORED_KEY to result.ignored.sorted().joinToString(","),
                SIDE_FILES_KEY to sideFiles.joinToString(","),
            ),
        )
        Outcome.Imported(result.carried, result.ignored, sideFiles)
    }

    companion object {
        /** settings-catalog.md SS1: the 2.x main store. */
        const val LEGACY_PREFS_NAME: String = "physiboard_prefs"

        /** The markers, outside the schema's vocabulary so the codec ignores them and [SettingsStore.update] preserves them. */
        const val STATE_KEY: String = brobata.physiboard.core.settings.SettingsBaseline.LEGACY_IMPORT_STATE_KEY
        const val AT_KEY: String = "legacy_import_at"
        const val CARRIED_KEY: String = "legacy_import_carried"
        const val IGNORED_KEY: String = "legacy_import_ignored"
        const val SIDE_FILES_KEY: String = "legacy_import_side_files"
        const val STATE_IMPORTED: String = brobata.physiboard.core.settings.SettingsBaseline.LEGACY_IMPORT_STATE_IMPORTED
        const val STATE_ABSENT: String = "absent"

        /**
         * The files settings-catalog.md names beside the store (SS4.2 "not touched", SS7.1 the
         * backup's `files/`). They are recorded, not moved: each belongs to the subsystem that
         * will read it at its 2.x path.
         */
        val SIDE_FILES: List<String> = listOf("personal_dictionary.json", "ctrl_key_mappings.json", "variations.json", "user_defaults.json", "locale_layout_mapping.json", "keyboard_layouts")

        /** The four smaller stores of settings-catalog.md SS1; none is a user setting, all stay in place. */
        val SIDE_STORES: List<String> = listOf("embedded_adb", "physiboard_toolbox", "app_list_cache_prefs", "recent_emojis_prefs")
    }
}
