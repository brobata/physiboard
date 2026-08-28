package brobata.physiboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Moves settings out of the preferences file named after the upstream project.
 *
 * 2.0 renames the Java package and this file with it. The app's own identity - its applicationId -
 * does not change, so the data directory is untouched and the old file is simply sitting there to
 * be read.
 *
 * What carries over is deliberate. **Content the user authored** always comes across: key
 * mappings, launcher shortcuts, custom themes, their fitted notification ring, per-app lists. It is
 * theirs, it cost them time, and it is never the part that goes wrong. **Behavioural toggles are
 * not copied** - they fall through to the first-run defaults instead, because a config accumulated
 * across a fork's worth of renamed and retired settings is exactly what 2.0 exists to clear out.
 *
 * The old file is left in place. It is the undo: [restoreLegacySettings] replays it verbatim for
 * anyone who wants their old behaviour back. 2.1 deletes it.
 */
object SettingsMigration {

    const val LEGACY_PREFS = "pastiera_prefs"
    const val PREFS = "physiboard_prefs"

    private const val KEY_MIGRATED = "prefs_migrated_v2"
    private const val KEY_NOTICE_SEEN = "v2_migration_notice_seen"

    /**
     * Keys renamed on the way across. The status bar's buttons were stored under the retired
     * presentation mode's name; the bar that survived 2.0 is simply the status bar.
     */
    private val RENAMES: Map<String, String> = mapOf(
        "pastierina_status_bar_slots_left" to "status_bar_slots_left",
        "pastierina_status_bar_slots_right" to "status_bar_slots_right"
    )

    /**
     * User-authored content and device state that cannot be recreated by hand. Everything here
     * survives migration exactly as it was.
     */
    private val CONTENT_KEYS: Set<String> = setOf(
        // things the user built
        "launcher_shortcuts",
        "launcher_shortcuts_enabled",
        "quick_launcher_command_customizations",
        "sym_mappings_custom",
        "sym_pages_config",
        "custom_input_styles",
        "app_enter_behavior_overrides",
        "app_raw_mode_packages",
        "status_bar_apps",
        "additional_ime_subtypes",
        "notification_ring_app_colors",
        // a ring fitted to this particular panel - see the 1.2.3 fit screen
        "notification_ring_cx",
        "notification_ring_cy",
        "notification_ring_radius",
        "notification_ring_stroke",
        // hand-built themes
        "keyboard_theme_saved_themes",
        "keyboard_theme_drafts",
        "keyboard_theme_hardware",
        "keyboard_theme_software",
        "keyboard_theme_dark_hardware",
        "keyboard_theme_dark_software",
        "keyboard_theme_light_hardware",
        "keyboard_theme_light_software",
        "keyboard_theme_layout_overrides_hardware",
        "keyboard_theme_layout_overrides_software",
        "keyboard_theme_assignment_mode_hardware",
        "keyboard_theme_assignment_mode_software",
        // captured from the device once, and the only way back to stock
        "side_key_original_package",
        "side_key_original_activity",
        "side_key_original_captured",
        "fn_ctrl_original_enable",
        "fn_ctrl_original_function",
        "fn_ctrl_captured"
    ) + RENAMES.keys

    /** Settings for features 2.0 removed. Nothing reads these any more. */
    private val DROPPED_PREFIXES: List<String> = listOf(
        "clicks_",
        "static_variation_bar_",
        "dynamic_variation_bar_",
        "pastierina_"
    )

    private val DROPPED_KEYS: Set<String> = setOf(
        "status_bar_variations_visible",
        "global_variation_layout_override"
    )

    /**
     * Brings a set of preference entries read from a backup into 2.0 shape: retired settings are
     * dropped and renamed ones move. Backups people already hold name the old file, so restoring
     * one has to translate rather than write settings nothing reads.
     */
    fun <T> translateRestoredEntries(entries: Map<String, T>): Map<String, T> {
        val result = LinkedHashMap<String, T>(entries.size)
        entries.forEach { (key, value) ->
            RENAMES[key]?.let { result[it] = value; return@forEach }
            if (key in DROPPED_KEYS) return@forEach
            if (DROPPED_PREFIXES.any(key::startsWith)) return@forEach
            result[key] = value
        }
        return result
    }

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun legacyPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    /**
     * True while an upgrading user still needs telling why their keyboard asked to be enabled
     * again. Only ever true for someone who had 1.x installed.
     */
    fun shouldShowMigrationNotice(context: Context): Boolean =
        hasLegacySettings(context) && !preferences(context).getBoolean(KEY_NOTICE_SEEN, false)

    fun markMigrationNoticeSeen(context: Context) {
        preferences(context).edit().putBoolean(KEY_NOTICE_SEEN, true).apply()
    }

    fun hasLegacySettings(context: Context): Boolean =
        legacyPreferences(context).all.isNotEmpty()

    /**
     * Runs once, before anything reads a setting. Must be called from Application.onCreate: the
     * input method service starts independently of the activity, and whichever wakes first has to
     * find the settings already in place.
     */
    fun migrateIfNeeded(context: Context) {
        val target = preferences(context)
        if (target.getBoolean(KEY_MIGRATED, false)) return

        val editor = target.edit()
        legacyPreferences(context).all.forEach { (key, value) ->
            if (key !in CONTENT_KEYS) return@forEach
            put(editor, RENAMES[key] ?: key, value)
        }
        editor.putBoolean(KEY_MIGRATED, true)
        // commit, not apply: the IME can read a setting microseconds from now and apply() is async.
        editor.commit()
    }

    /**
     * Replays the old file in full - behaviour included - for a user who preferred what they had.
     * Retired settings are still dropped and renamed ones still move; nothing else is filtered.
     */
    fun restoreLegacySettings(context: Context) {
        val editor = preferences(context).edit()
        legacyPreferences(context).all.forEach { (key, value) ->
            if (key in DROPPED_KEYS) return@forEach
            if (DROPPED_PREFIXES.any(key::startsWith) && key !in RENAMES) return@forEach
            put(editor, RENAMES[key] ?: key, value)
        }
        editor.putBoolean(KEY_MIGRATED, true)
        editor.commit()
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            // string sets are silently lost by a naive when - status_bar_apps and
            // additional_ime_subtypes are both stored this way
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
