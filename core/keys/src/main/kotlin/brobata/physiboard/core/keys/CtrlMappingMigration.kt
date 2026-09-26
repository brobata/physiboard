package brobata.physiboard.core.keys

/**
 * spec: keys-and-modifiers.md SS12.1, "current 3". Bump this whenever the shipped defaults for a
 * key [CtrlMappingMigration] fills in change, so an install that already saved a private mapping
 * file still receives the new default the next time it loads.
 */
const val CTRL_MAPPING_DEFAULTS_VERSION = 3

/**
 * Fills in the defaults spec keys-and-modifiers.md SS12.1 promises "any older file" still gets:
 * "any of KEYCODE_N, KEYCODE_M, KEYCODE_U, KEYCODE_I that is missing or `none` gets the
 * word-motion defaults below, and KEYCODE_B missing or `none` gets the keyboard-mode toggle
 * command." Without this, an install whose private `ctrl_key_mappings.json` predates a shipped
 * default change keeps reading its old, stale file forever: the asset only ever seeds a *new*
 * install (`ctrl_key_mappings.json` doesn't exist yet), and every later load reads the private
 * file, never the asset, however the asset itself changes.
 */
object CtrlMappingMigration {
    /** SS12.1's own "word-motion defaults below" (SS12.3's shipped table for these four keys). */
    private val WORD_MOTION_DEFAULTS: Map<Char, CtrlMapping> = mapOf(
        'N' to CtrlMapping.NamedAction("move_word_left"),
        'M' to CtrlMapping.NamedAction("move_word_right"),
        'U' to CtrlMapping.NamedAction("expand_selection_word_left"),
        'I' to CtrlMapping.NamedAction("expand_selection_word_right"),
    )
    private const val KEYBOARD_MODE_TOGGLE_COMMAND = "pastiera.toggle_software_keyboard_mode"

    /**
     * [storedVersion] is the private file's own last-migrated-to stamp
     * (`nav_mode_default_mappings_version`); a file already at or past
     * [CTRL_MAPPING_DEFAULTS_VERSION] is returned unchanged, so a key the user deliberately set to
     * `none` after migrating once is never silently overwritten again.
     */
    fun migrate(table: CtrlMappingTable, storedVersion: Int): CtrlMappingTable {
        if (storedVersion >= CTRL_MAPPING_DEFAULTS_VERSION) return table
        var entries = table.entries
        for ((letter, default) in WORD_MOTION_DEFAULTS) {
            entries = withDefaultIfMissing(entries, letter, default)
        }
        entries = withDefaultIfMissing(entries, 'B', CtrlMapping.Command(KEYBOARD_MODE_TOGGLE_COMMAND))
        return if (entries === table.entries) table else CtrlMappingTable(entries)
    }

    private fun withDefaultIfMissing(entries: Map<KeyId, CtrlMapping>, letter: Char, default: CtrlMapping): Map<KeyId, CtrlMapping> {
        val key = KeyId.Letter(letter)
        val current = entries[key]
        return if (current == null || current == CtrlMapping.None) entries + (key to default) else entries
    }
}
