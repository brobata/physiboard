package brobata.physiboard.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SettingsBaseline
import brobata.physiboard.core.settings.SettingsCodec
import brobata.physiboard.ime.SettingsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The one preference file 3.0 keeps (settings-catalog.md SS13, "one preference file with typed
 * rows"), as a Preferences DataStore holding exactly the flat `key -> string` map
 * [SettingsCodec] defines. Typing is re-applied on every read by the codec, so this class knows
 * nothing about any row: it persists strings and exposes the typed value as a [Flow].
 *
 * Rows outside the codec's vocabulary (the importer's `legacy_import_*` markers, or a row a newer
 * build wrote) are preserved across every write here, so an older build never erases what a
 * newer one recorded.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) : SettingsSource {

    override val settings: Flow<Settings> = dataStore.data.map { SettingsCodec.fromMap(it.asStringMap()) }

    suspend fun current(): Settings = settings.first()

    /** Replaces the codec's rows with [transform]'s result and leaves every other row alone. */
    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs ->
            val before = SettingsCodec.toMap(SettingsCodec.fromMap(prefs.asStringMap()))
            val after = SettingsCodec.toMap(transform(SettingsCodec.fromMap(prefs.asStringMap())))
            prefs.replaceRows(before, after)
        }
    }

    /**
     * The importer's write: every codec row becomes [settings]' and [markers] are written in the
     * same transaction, so a process death cannot leave the marker without the rows or the rows
     * without the marker.
     */
    suspend fun replaceAll(settings: Settings, markers: Map<String, String>) {
        dataStore.edit { prefs ->
            val before = SettingsCodec.toMap(SettingsCodec.fromMap(prefs.asStringMap()))
            prefs.replaceRows(before, SettingsCodec.toMap(settings))
            for ((k, v) in markers) prefs[stringPreferencesKey(k)] = v
        }
    }

    /**
     * settings-catalog.md SS4.2, SS1.1: run the versioned baseline reset (after the 2.x import,
     * before the first read) so a default found to be wrong after release can be forced back onto
     * an existing install, not just a fresh one. [SettingsBaseline.apply] is already a safe no-op
     * once the marker is current, so calling this on every process start is correct and simpler
     * than a separate run-once guard; the before/after equality check below just avoids a write
     * (and the flow re-emission it would cause) on the common case where nothing changed. Reads
     * and writes the whole flat map in one `dataStore.edit` transaction so a process death cannot
     * leave a partially-corrected store or a marker without its corrections.
     */
    suspend fun applyBaselineOnce() {
        dataStore.edit { prefs ->
            val before = prefs.asStringMap()
            val after = SettingsBaseline.apply(before, SettingsBaseline.storedVersion(before))
            if (after != before) {
                for (key in before.keys) if (key !in after) prefs.remove(stringPreferencesKey(key))
                for ((k, v) in after) prefs[stringPreferencesKey(k)] = v
            }
        }
    }

    /** A raw row outside the schema (a marker), or null. */
    suspend fun rawValue(key: String): String? = dataStore.data.first()[stringPreferencesKey(key)]

    suspend fun putRaw(entries: Map<String, String>) {
        dataStore.edit { prefs -> for ((k, v) in entries) prefs[stringPreferencesKey(k)] = v }
    }

    private fun MutablePreferences.replaceRows(before: Map<String, String>, after: Map<String, String>) {
        for (key in before.keys) if (key !in after) remove(stringPreferencesKey(key))
        for ((k, v) in after) this[stringPreferencesKey(k)] = v
    }

    companion object {
        /** The file under `datastore/`; a different name from the 2.x `physiboard_prefs` so the importer's source is never its own target. */
        const val FILE_NAME: String = "physiboard_settings"

        /**
         * Opens the process's one store. A corrupt file is replaced with an empty one rather than
         * crashing the keyboard: the codec turns an empty map into the first-run defaults, which
         * is the honest fallback (settings-catalog.md SS4.2 step 2 makes the same choice for a
         * malformed baseline asset).
         */
        fun open(context: Context): SettingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(FILE_NAME) },
            ),
        )

        private fun Preferences.asStringMap(): Map<String, String> = asMap().entries.associate { (key, value) -> key.name to value.toString() }
    }
}
