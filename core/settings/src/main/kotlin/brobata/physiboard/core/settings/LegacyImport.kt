package brobata.physiboard.core.settings

import brobata.physiboard.core.settings.JsonRows.double
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What one import produced. [carried] is every 2.x key that contributed to [settings] (under its
 * 3.0 name where renamed), [ignored] every key that did not: dropped rows, 2.x-only markers,
 * unknown keys, and rows whose value could not be read at all (a null, or a JSON row that does
 * not parse). A row of the wrong type is carried where the value could still be read (an int
 * stored as a string) and ignored otherwise; the codec's own fallbacks then default the field.
 */
data class LegacyImportResult(
    val settings: Settings,
    val carried: Set<String>,
    val ignored: Set<String>,
)

/**
 * The pure half of the one-shot 2.x importer (rebuild-from-scratch.md, "Settings"): maps the
 * contents of the 2.x `physiboard_prefs` store, as the untyped `key -> value` map Android hands
 * back, onto the 3.0 [Settings]. spec: settings-catalog.md SS2 (every key and its type), SS5.2
 * (renames), SS6.3 (legacy fallbacks on read), SS4.2 (the misnamed capture rows), SS13 (which
 * rows survive).
 *
 * Tolerance is the rule: an unknown key is ignored, a wrong type is read where it can be and
 * defaulted where it cannot, a dropped row is ignored, and nothing here ever throws on input.
 *
 * One kept row is deliberately not carried: `screen_trackpad_enabled`. Every 2.x Titan holds it
 * `true` from the factory asset, not from a choice, and the trackpad intercepts Space ahead of
 * the pipeline with no screen yet to switch it back off (see [TrackpadPrefs.enabled]). The
 * trigger, activation, step and hint rows are carried, so the user's tuning is ready when the
 * switch exists.
 */
object LegacyImport {

    /** 2.x names that map onto a 3.0 key by rename alone (settings-catalog.md SS5.2, SS4.2). The properly named row wins when both exist. */
    private val RENAMES: Map<String, String> = mapOf(
        "pastierina_status_bar_slots_left" to SettingsKeys.STATUS_BAR_SLOTS_LEFT,
        "pastierina_status_bar_slots_right" to SettingsKeys.STATUS_BAR_SLOTS_RIGHT,
        "fn_ctrl_original_enable" to SettingsKeys.FN_CTRL_PREV_ENABLE,
        "fn_ctrl_original_function" to SettingsKeys.FN_CTRL_PREV_FUNCTION,
        "fn_ctrl_captured" to SettingsKeys.FN_CTRL_PREV_CAPTURED,
    )

    /** 2.x rows that only feed a 3.0 row through a derivation (SS6.3), never copied as themselves. */
    private const val SHOW_STATUS_BAR = "show_status_bar"
    private const val SLOT_LEFT = "status_bar_slot_left"
    private const val SLOT_RIGHT_1 = "status_bar_slot_right_1"
    private const val SLOT_RIGHT_2 = "status_bar_slot_right_2"

    /** Every flat-map key [SettingsCodec] reads, so the importer never has to be told twice what 3.0 keeps. */
    private val KNOWN_KEYS: Set<String> = SettingsCodec.toMap(Settings()).keys - SettingsKeys.SCHEMA_VERSION -
        // keys-and-modifiers.md SS12.1: 2.x's own counter under this same key name reflects 2.x's
        // migration history, not 3.0's; carrying it over could tell 3.0 a Fn Layer default it has
        // never actually backfilled was already migrated. A 2.x import always starts this at 0,
        // the same as a fresh install (settings-catalog.md SS13's "2.x-only markers" are dropped).
        SettingsKeys.NAV_MODE_DEFAULT_MAPPINGS_VERSION -
        // settings-catalog.md SS2.5: both marked "Transient" -- request-scoped bookkeeping for
        // whatever Sym page the *old* phone happened to be mid-edit on, not a preference to carry
        // to a new one. A stale value would at worst reopen the wrong (or, SS5.8, the first
        // enabled) page once; importing it anyway would still be surprising for something the
        // catalogue itself does not call durable.
        setOf(SettingsKeys.RESTORE_SYM_PAGE, SettingsKeys.PENDING_RESTORE_SYM_PAGE) + setOf(
        // Rows the default Settings does not write (nullable or blank-means-absent fields).
        SettingsKeys.RING_DEFAULT_COLOR,
        SettingsKeys.FN_CTRL_PREV_ENABLE,
        SettingsKeys.FN_CTRL_PREV_FUNCTION,
        SettingsKeys.QS_BACKLIGHT_PREV,
        SettingsKeys.RING_BACKLIGHT_PREV,
        SettingsKeys.LAUNCHER_SHORTCUTS,
        SettingsKeys.LAUNCHER_COMMAND_CUSTOMIZATIONS,
    )

    fun import(legacy: Map<String, Any?>): LegacyImportResult {
        val flat = LinkedHashMap<String, String>()
        val carried = LinkedHashSet<String>()
        val ignored = LinkedHashSet<String>()
        val derivation = HashMap<String, String>()

        for ((rawKey, rawValue) in legacy) {
            val value = flatten(rawValue)
            if (value == null) {
                ignored += rawKey
                continue
            }
            when {
                rawKey == SettingsKeys.TRACKPAD_ENABLED -> ignored += rawKey
                rawKey == SHOW_STATUS_BAR || rawKey == SLOT_LEFT || rawKey == SLOT_RIGHT_1 || rawKey == SLOT_RIGHT_2 -> derivation[rawKey] = value
                rawKey in KNOWN_KEYS || rawKey.startsWith(SettingsKeys.AUTO_CORRECT_CUSTOM_PREFIX) -> {
                    flat[rawKey] = value
                    carried += rawKey
                }
                rawKey in RENAMES -> {
                    val target = RENAMES.getValue(rawKey)
                    if (target !in legacy) {
                        flat[target] = value
                        carried += rawKey
                    } else {
                        ignored += rawKey
                    }
                }
                else -> ignored += rawKey
            }
        }

        deriveVisibility(flat, derivation, carried)
        deriveSlots(flat, derivation, carried)
        translateLauncherBehavior(flat)
        liftThemeBarHeight(flat)

        val settings = SettingsCodec.fromMap(flat)
        return LegacyImportResult(settings, carried, ignored)
    }

    /** The untyped 2.x value as the codec's string form; null for a value no row can be read from. */
    private fun flatten(value: Any?): String? = when (value) {
        null -> null
        is Boolean, is Int, is Long, is Float, is Double -> value.toString()
        is String -> value
        is Collection<*> -> JsonRows.encode(JsonRows.stringListOf(value.filterNotNull().map { it.toString() }))
        else -> null
    }

    /** spec SS6.3: `status_bar_visibility` absent reads through `show_status_bar`: true or absent gives ALWAYS, false gives NEVER. */
    private fun deriveVisibility(flat: MutableMap<String, String>, derivation: Map<String, String>, carried: MutableSet<String>) {
        if (StatusBarVisibility.fromStored(flat[SettingsKeys.STATUS_BAR_VISIBILITY]) != null) return
        val legacy = derivation[SHOW_STATUS_BAR] ?: return
        flat[SettingsKeys.STATUS_BAR_VISIBILITY] = if (legacy.equals("false", ignoreCase = true)) StatusBarVisibility.NEVER.storedValue else StatusBarVisibility.ALWAYS.storedValue
        carried += SHOW_STATUS_BAR
    }

    /** spec SS6.3: the slot arrays, "absent or unparsable", derive from the three single-slot rows. Arrays are authoritative (SS11). */
    private fun deriveSlots(flat: MutableMap<String, String>, derivation: Map<String, String>, carried: MutableSet<String>) {
        if (StoredValues.buttons(flat[SettingsKeys.STATUS_BAR_SLOTS_LEFT]) == null) {
            derivation[SLOT_LEFT]?.let {
                flat[SettingsKeys.STATUS_BAR_SLOTS_LEFT] = JsonRows.encode(JsonRows.stringListOf(listOf(it)))
                carried += SLOT_LEFT
            }
        }
        if (StoredValues.buttons(flat[SettingsKeys.STATUS_BAR_SLOTS_RIGHT]) == null) {
            val singles = listOf(SLOT_RIGHT_1, SLOT_RIGHT_2).filter { it in derivation }
            if (singles.isNotEmpty()) {
                flat[SettingsKeys.STATUS_BAR_SLOTS_RIGHT] = JsonRows.encode(JsonRows.stringListOf(singles.map { derivation.getValue(it) }))
                carried += singles
            }
        }
    }

    /** 2.x called PhysiBoard's own launcher `pastiera`; 3.0 stores it under its own name. */
    private fun translateLauncherBehavior(flat: MutableMap<String, String>) {
        if (flat[SettingsKeys.LAUNCHER_BEHAVIOR] == "pastiera") flat[SettingsKeys.LAUNCHER_BEHAVIOR] = LauncherBehavior.PHYSIBOARD.storedValue
    }

    /**
     * spec settings-catalog.md SS6.1 and SS13 ("Hardware bar height lift: drop; the importer can
     * lift on the way in"): a theme still holding `suggestions_height_scale` exactly 1.0 was saved
     * before the strip's height was raised, and reads as 1.4. Any other value is left alone.
     */
    private fun liftThemeBarHeight(flat: MutableMap<String, String>) {
        flat[SettingsKeys.THEME]?.let { text ->
            JsonRows.parseObject(text)?.let { flat[SettingsKeys.THEME] = JsonRows.encode(lift(it)) }
        }
        flat[SettingsKeys.SAVED_THEMES]?.let { text ->
            JsonRows.parseArray(text)?.let { arr ->
                flat[SettingsKeys.SAVED_THEMES] = JsonRows.encode(
                    JsonArray(
                        arr.map { el ->
                            val obj = el as? JsonObject ?: return@map el
                            val theme = obj["theme"] as? JsonObject ?: return@map el
                            JsonObject(obj + ("theme" to lift(theme)))
                        },
                    ),
                )
            }
        }
    }

    private fun lift(theme: JsonObject): JsonObject =
        if (theme.double("suggestions_height_scale") == 1.0) JsonObject(theme + ("suggestions_height_scale" to JsonPrimitive(1.4))) else theme
}
