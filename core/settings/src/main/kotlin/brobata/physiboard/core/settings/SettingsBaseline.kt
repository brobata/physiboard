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

    /** The baseline version this build ships with; version 3 puts the suggestion row back after version 2 hid it. */
    const val CURRENT_VERSION: Int = 3

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
    ): Map<String, String> {
        if (storedVersion >= toVersion) return flatMap
        val result = flatMap.toMutableMap()
        for (version in (storedVersion + 1)..toVersion) {
            corrections[version]?.let { result.putAll(it) }
        }
        result[SettingsKeys.BASELINE_VERSION] = toVersion.toString()
        return result
    }
}
