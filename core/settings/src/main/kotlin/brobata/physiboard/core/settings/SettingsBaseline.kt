package brobata.physiboard.core.settings

/**
 * The versioned, one-shot reset that lets a default found to be wrong after release be
 * re-applied to an ALREADY-INSTALLED store, not just a fresh one. spec: settings-catalog.md SS4
 * (the factory baseline mechanism) and SS2.15 (`settings_baseline_version`, int marker, code
 * default 0, current baseline version 1).
 *
 * [Settings]' Kotlin field defaults already give a fresh install today's best-known values the
 * moment [SettingsCodec.fromMap] reads an empty map (see that class's own top-of-file KDoc), so
 * the gap this closes is narrower than 2.x's asset-driven reset: a value that shipped wrong and
 * was EXPLICITLY WRITTEN to an existing store never picks up a later fix to the Kotlin default,
 * because [SettingsCodec.fromMap] only falls back to the default when a key is absent, never when
 * it disagrees with a present one. Raising [CURRENT_VERSION] and adding an entry to
 * [CORRECTIONS] is how a corrected value gets forced back onto every existing install, exactly
 * once per version.
 *
 * [CURRENT_VERSION] ships as 1 with an empty correction table: no 3.0 default has been found
 * wrong yet (this is the mechanism, not a fix), so there is nothing to re-seed. The marker itself
 * still does real work on the first process start of every install, 3.0-fresh or imported from
 * 2.x: [storedVersion] reads 0 when the key is absent, [apply] runs once, writes no correction
 * (version 1's table is empty) and stamps [SettingsKeys.BASELINE_VERSION] to "1" so the check is
 * a cheap no-op on every later start, the same "runs once per baseline version, at every process
 * start" shape SS4.2 describes for 2.x.
 */
object SettingsBaseline {

    /** The baseline version this build ships with; version 10 turns the mix-up fix on. */
    const val CURRENT_VERSION: Int = 10

    /** One entry per baseline version above 0: the flat-map keys that version forces back to a corrected value. */
    val CORRECTIONS: Map<Int, Map<String, String>> = mapOf(
        1 to emptyMap(),
        // Version 2, 2026-09-26: the suggestion row ships hidden (StatusBarPrefs' KDoc has the
        // reasoning). An install that already stored ALWAYS would keep it for ever, because a
        // stored value never picks up a later change to a default, which is exactly the case
        // this mechanism exists for. Anyone who wants the row switches it back on afterwards and
        // that choice stands: a correction runs once per version, not at every start.
        2 to mapOf(SettingsKeys.STATUS_BAR_VISIBILITY to StatusBarVisibility.NEVER.storedValue),
        // Version 3, 2026-09-28: and back on again. Version 2 switched the row off because it was
        // drawing over apps that refuse to be resized for a keyboard; the row now steps aside by
        // itself in any field whose app asked for no suggestions, which covers those apps, so
        // there is no longer a reason to deny it everywhere else. Anyone who preferred it off
        // switches it off and that choice stands, the same as before.
        3 to mapOf(SettingsKeys.STATUS_BAR_VISIBILITY to StatusBarVisibility.ALWAYS.storedValue),
        // Version 4, 2026-09-29: the three layout-switch chords off, whatever a 2.x store said.
        // They intercept Alt, Shift, Enter and Space, and this project's rule is that such a
        // thing ships off until it has survived real use on the maintainer's own phone. Their
        // 2.x settings carried `alt_shift_layout_switch` true and the importer honoured it, which
        // was harmless while one layout existed and the chords had nowhere to go. Shipping
        // eighteen layouts made them live, and Alt with Shift began silently switching the
        // keyboard out from under them. An explicit choice in the settings screen still stands.
        4 to mapOf(
            SettingsKeys.ALT_SHIFT_LAYOUT_SWITCH to "false",
            SettingsKeys.ALT_ENTER_LAYOUT_SWITCH to "false",
            SettingsKeys.CTRL_SPACE_LAYOUT_SWITCH to "false",
        ),
        // Version 5, 2026-10-05: the suggestion row hidden again, by the maintainer's choice
        // rather than a bug: apps keep the whole screen while typing, autocorrect does the
        // work, and Sym brings up emoji, symbols and the clipboard. Switching it back on stands.
        5 to mapOf(SettingsKeys.STATUS_BAR_VISIBILITY to StatusBarVisibility.NEVER.storedValue),
        // Version 6, 2026-10-07: dictation stops after 5 s without new words instead of 15 s
        // ("15 seconds of silence sounds like forever"). The 15 s default shipped only to the
        // maintainer's dev build, where it was written to the store, so it is replaced once.
        6 to mapOf(SettingsKeys.DICTATION_STOP_AFTER_SILENCE to "5000"),
        // Version 7, the same evening: 2.5 s after the last word, the maintainer's number once the
        // stop counted from the last word rather than the engine's late tidy-up. Typed in, any
        // value from 1 to 60 s.
        7 to mapOf(SettingsKeys.DICTATION_STOP_AFTER_SILENCE to "2500"),
        // Version 8, 2026-10-07: one list of Sym pages the maintainer can follow. Sym steps
        // through Emoji (the picker), Symbols and GIFs, in that order, then closes; every other
        // page is off and still reachable from the chooser. The dev build stored GIFs off, which
        // left them behind a double tap nobody found, and kaomoji goes back to opt-in.
        8 to mapOf(
            SettingsKeys.SYM_PAGES_CONFIG to JsonRows.encode(StoredValues.symPagesConfig(SYM_PAGES_V8)),
            SettingsKeys.EMOJI_PICKER_KAOMOJI to "false",
        ),
        // Version 9, 2026-10-09: the haptic language's events (Shift, Sym, picks, autocorrect,
        // the settings app's ticks) off, by the maintainer's choice; the dev build had stored
        // them on. Dictation's cues are their own setting and keep buzzing.
        9 to mapOf(SettingsKeys.EVENT_HAPTICS to "false"),
        // Version 10, 2026-10-09: the mix-up fix (its/it's, your/you're) on for everyone, the
        // maintainer's call after daily use on the Titan. It shipped off in 3.0 and 3.1, so a
        // stored "false" is almost always the old default, not a choice; turning it off again
        // afterwards stands.
        10 to mapOf(SettingsKeys.FIX_WORD_MIXUPS to "true"),
    )

    /**
     * Keys a version leaves alone on a store the 2.x importer filled in this same start (stored
     * version 0 and `legacy_import_state` "imported"): version 8 fixes a 3.0 default the dev build
     * stored, not a 2.x user's own choice of pages and order, which the import carried over.
     */
    val KEEP_ON_FRESH_IMPORT: Map<Int, Set<String>> = mapOf(8 to setOf(SettingsKeys.SYM_PAGES_CONFIG))

    /** The 2.x importer's marker (`:app`'s LegacyImporter.STATE_KEY) and its value after an import. */
    const val LEGACY_IMPORT_STATE_KEY: String = "legacy_import_state"
    const val LEGACY_IMPORT_STATE_IMPORTED: String = "imported"

    /**
     * Version 8's page list, spelt out rather than read from [SymPagesConfig]'s defaults, so a
     * later change to those defaults cannot change what version 8 wrote.
     */
    private val SYM_PAGES_V8: SymPagesConfig
        get() = SymPagesConfig(
            // GIFs off for 3.0.0 (shared KLIPY test key); no released install ran version 8 before this.
            emojiEnabled = false, symbolsEnabled = true, clipboardEnabled = false, emojiPickerEnabled = true, gifEnabled = false,
            custom1Enabled = false, custom2Enabled = false, custom3Enabled = false,
            order = listOf(
                SymPage.EMOJI_PICKER, SymPage.SYMBOLS, SymPage.GIF, SymPage.CLIPBOARD, SymPage.EMOJI,
                SymPage.CUSTOM_1, SymPage.CUSTOM_2, SymPage.CUSTOM_3,
            ),
        )

    /** The baseline version already applied to [flatMap], or 0 when the marker was never written. Mirrors [SettingsCodec.schemaVersionOf]'s style. */
    fun storedVersion(flatMap: Map<String, String>): Int = flatMap[SettingsKeys.BASELINE_VERSION]?.toIntOrNull() ?: 0

    /**
     * [flatMap] with every pending version's corrections overwritten in and the marker moved to
     * [toVersion]. A no-op (returns [flatMap] unchanged) when [storedVersion] is already at or
     * past [toVersion]. Pure and deterministic: no I/O, so the caller decides whether and when to
     * persist the result.
     *
     * Corrections apply in ascending version order, so a later pending version's value for a key
     * wins over an earlier pending version's value for the same key. Each key is overwritten, not
     * merged around: a key the map already carries a different (wrong) value for is replaced,
     * which is the entire point (SS4: "a default found to be wrong after release can never be
     * re-seeded on an existing install").
     *
     * [corrections] and [toVersion] default to the production table so the two-argument call the
     * storage layer uses (`apply(flatMap, storedVersion)`) always applies this build's real
     * baseline; tests pass a synthetic table to exercise the overwrite semantics without needing
     * a real wrong default to exist yet.
     */
    fun apply(
        flatMap: Map<String, String>,
        storedVersion: Int,
        corrections: Map<Int, Map<String, String>> = CORRECTIONS,
        toVersion: Int = CURRENT_VERSION,
        keepOnFreshImport: Map<Int, Set<String>> = KEEP_ON_FRESH_IMPORT,
    ): Map<String, String> {
        if (storedVersion >= toVersion) return flatMap
        val freshImport = storedVersion == 0 && flatMap[LEGACY_IMPORT_STATE_KEY] == LEGACY_IMPORT_STATE_IMPORTED
        val result = flatMap.toMutableMap()
        for (version in (storedVersion + 1)..toVersion) {
            val kept = if (freshImport) keepOnFreshImport[version].orEmpty() else emptySet()
            corrections[version]?.let { table -> table.forEach { (key, value) -> if (key !in kept) result[key] = value } }
        }
        result[SettingsKeys.BASELINE_VERSION] = toVersion.toString()
        return result
    }
}
