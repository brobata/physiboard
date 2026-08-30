package brobata.physiboard

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The factory baseline: the settings a working PhysiBoard starts from.
 *
 * Settings carried up through 1.x and the 2.0 rework arrived in states no screen could reach and
 * no combination was ever tested in. Every one of these phones is the same hardware on the same
 * firmware, so there is one configuration known to work, and this is it - captured from a working
 * Titan 2 Elite into `assets/common/default_settings.json`.
 *
 * The preference store is cleared once and replaced with that baseline: themes, SYM pages, the
 * notification ring's fit, shortcuts, the lot. It is deliberately not a merge. A half-reset store
 * is how the situation arose.
 *
 * Two things beside the preference store go with it:
 *  - the local `ctrl_key_mappings.json`, so the Fn layer falls back to the shipped default rather
 *    than whatever a phone had accumulated.
 *  - `pastiera_prefs`, the pre-2.0 file. Deleted outright: while it exists the 1.x values can come
 *    back, and this release exists because they should not. Deleting it also retires the migration
 *    notice and its "Restore my old settings" button, both of which read it.
 *
 * Not touched, because none of it is a setting: the personal dictionary somebody taught, their
 * custom layouts and variations. Words you added survive this.
 *
 * The store is written to [SNAPSHOT_FILE] first, so the reset stays recoverable.
 */
object SettingsBaseline {

    private const val TAG = "SettingsBaseline"
    private const val ASSET = "common/default_settings.json"

    /** Written before the store is cleared. Not read by the app; it is the manual way back. */
    const val SNAPSHOT_FILE = "settings_before_reset.json"

    private const val KEY_BASELINE_VERSION = "settings_baseline_version"

    /** Raise this to reset every install once more. Only do that for another 2.0-sized mistake. */
    private const val BASELINE_VERSION = 1

    /**
     * The only exception to "clear it all", and none of it is a setting.
     *
     * Two kinds. Bookkeeping: clearing `prefs_migrated_v2` re-runs the 1.x migration and drags the
     * old values straight back in, which is the exact thing being purged, and the rest would
     * replay onboarding at somebody who finished it long ago or re-show a release note they have
     * read.
     *
     * And the `*_original_*` / `*_prev_captured` records, which are what a system setting pointed
     * at BEFORE PhysiBoard changed it. Those writes outlive an uninstall, and these records are
     * the only way Reset to stock can put them back. Losing them strands the change on the phone
     * for good, so they are kept even though everything around them is thrown away.
     */
    private val BOOKKEEPING_KEYS = setOf(
        "prefs_migrated_v2",
        "v2_migration_notice_seen",
        "tutorial_completed",
        "last_seen_whats_new_version",
        "dismissed_releases",
        "hardware_bar_height_migrated",
        "impact_defaults_applied",
        "nav_mode_mappings_updated",
        "smart_backlight_applied",
        KEY_BASELINE_VERSION,
        // what it was before PhysiBoard touched it - see above
        "side_key_original_package",
        "side_key_original_activity",
        "side_key_original_captured",
        "fn_ctrl_captured",
        "fn_ctrl_prev_captured",
        "fn_ctrl_original_enable",
        "fn_ctrl_original_function",
        "qs_backlight_prev_captured",
    )

    /**
     * Runs once per baseline version, from Application.onCreate. It must run after the 1.x
     * migration: that migration is what drags the old values in, and this clears up after it.
     */
    fun applyIfNeeded(context: Context) {
        val prefs = SettingsMigration.preferences(context)
        if (prefs.getInt(KEY_BASELINE_VERSION, 0) >= BASELINE_VERSION) return

        val existing = prefs.all
        val baseline = loadBaseline(context)
        if (baseline == null) {
            // Never leave the flag unset on a parse failure or the reset retries on every launch.
            prefs.edit().putInt(KEY_BASELINE_VERSION, BASELINE_VERSION).commit()
            Log.e(TAG, "baseline asset unreadable; store left as it was")
            return
        }

        val fresh = existing.isEmpty()
        if (!fresh) snapshot(context, existing)

        val keep = existing.filterKeys { it in BOOKKEEPING_KEYS }

        val editor = prefs.edit()
        editor.clear()
        keep.forEach { (key, value) -> put(editor, key, value) }
        var applied = 0
        baseline.forEach { (key, value) ->
            put(editor, key, value)
            applied++
        }
        editor.putInt(KEY_BASELINE_VERSION, BASELINE_VERSION)
        // commit, not apply: the IME can read a setting microseconds from now.
        editor.commit()

        if (!fresh) {
            discardLocalNavModeMappings(context)
            discardLegacyStore(context)
        }

        // Log.e survives the release build's log stripping; this happens once and is worth a line.
        Log.e(
            TAG,
            if (fresh) "seeded a new install with $applied baseline values"
            else "reset ${existing.size} stored values to the $applied-value baseline; " +
                "kept ${keep.size} bookkeeping keys, snapshot in $SNAPSHOT_FILE"
        )
    }

    /**
     * Drops the phone's own copy of the Fn-layer mappings. The app rewrites it from
     * `assets/common/ctrl/ctrl_key_mappings.json` on the next read, which is the layout this
     * baseline was captured with.
     */
    private fun discardLocalNavModeMappings(context: Context) {
        runCatching {
            val file = File(context.filesDir, "ctrl_key_mappings.json")
            if (file.exists() && !file.delete()) Log.e(TAG, "could not delete $file")
        }.onFailure { Log.e(TAG, "could not discard local nav mode mappings", it) }
    }

    /**
     * Removes the pre-2.0 preferences file. It is the source the 1.x migration reads, so leaving
     * it is leaving a route back to the values this reset exists to clear.
     */
    private fun discardLegacyStore(context: Context) {
        runCatching {
            context.deleteSharedPreferences(SettingsMigration.LEGACY_PREFS)
        }.onFailure { Log.e(TAG, "could not delete the pre-2.0 preferences", it) }
    }

    /** The shipped baseline, as typed values. Null when the asset is missing or malformed. */
    private fun loadBaseline(context: Context): Map<String, Any>? {
        val text = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrElse {
            Log.e(TAG, "cannot read $ASSET", it)
            return null
        }
        return runCatching {
            val entries = JSONObject(text).getJSONObject("entries")
            buildMap {
                entries.keys().forEach { key ->
                    val entry = entries.getJSONObject(key)
                    typedValue(entry)?.let { put(key, it) }
                }
            }
        }.getOrElse {
            Log.e(TAG, "cannot parse $ASSET", it)
            null
        }
    }

    /** Mirrors the shape the backup writer produces, so a backup can be used to author one. */
    private fun typedValue(entry: JSONObject): Any? = when (entry.optString("type")) {
        "boolean" -> entry.optBoolean("value")
        "int" -> entry.optInt("value")
        "long" -> entry.optLong("value")
        "float" -> entry.optDouble("value").toFloat()
        "string" -> entry.optString("value")
        "string_set" -> entry.optJSONArray("value")?.let { array ->
            (0 until array.length()).mapTo(mutableSetOf()) { array.optString(it) }
        }
        else -> null
    }

    private fun snapshot(context: Context, values: Map<String, Any?>) {
        runCatching {
            val json = JSONObject()
            values.forEach { (key, value) ->
                json.put(key, if (value is Set<*>) JSONArray(value.toList()) else value)
            }
            File(context.filesDir, SNAPSHOT_FILE).writeText(json.toString(2))
        }.onFailure {
            // Not fatal: the reset is still the right outcome, the user just loses the way back.
            Log.e(TAG, "could not snapshot settings before reset", it)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value as Set<String>)
        }
    }
}
