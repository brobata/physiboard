package brobata.physiboard.core.dict

/**
 * Package-internal broadcast actions the settings screens (`:app`) and the keyboard (`:ime`)
 * share, so an edit made on a settings screen reaches the running keyboard process. Kept as plain
 * string constants with no Android import so both sides can depend on one source (`:core:dict`)
 * without either module depending on the other.
 */
object DictionaryBroadcastActions {
    /**
     * autocorrect-suggestions.md SS6.1: "Settings screens announce changes with the broadcast
     * `brobata.physiboard.ACTION_USER_DICTIONARY_UPDATED`", sent whenever the personal or default
     * word file changes.
     */
    const val USER_DICTIONARY_UPDATED: String = "brobata.physiboard.ACTION_USER_DICTIONARY_UPDATED"

    /**
     * dictionaries-languages.md SS17's Keep/Drop fix ("Per-process dictionary cache never
     * invalidated | Fix | Reload on install, import, uninstall"): sent whenever a dictionary
     * file's tier changes, so the keyboard can drop its cached [DictionaryIndex] for that language
     * and reload it immediately rather than waiting for the next process start.
     */
    const val DICTIONARY_CHANGED: String = "brobata.physiboard.ACTION_DICTIONARY_CHANGED"
}
