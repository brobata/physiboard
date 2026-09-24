package brobata.physiboard.core.speech

/**
 * The session-shaping settings this module reads. There is no `:settings` module yet
 * (docs/plans/rebuild-from-scratch.md build order step 6, and this task's own instruction not to
 * build one), so every field here is a caller-supplied value with the spec's own default; a real
 * settings store later means constructing this from that store instead of the defaults, not
 * changing anything in [DictationEngine]. spec: dictation.md SS13.
 *
 * [androidApiLevel] is a plain `Int` (`Build.VERSION.SDK_INT`'s value, never the class itself) so
 * this module never needs an `android.*` import to make the API-33 decision in SS6.3.
 */
data class DictationSettings(
    val pauseMs: Long = 2_000L,
    val segmentedSessionEnabled: Boolean = true,
    val androidApiLevel: Int = 0,
)

/**
 * The text-shaping settings [UtteranceFinisher] and [DictationPartialDisplay] read. Kept separate
 * from [DictationSettings] because these gate a pure text transform rather than the session's
 * lifecycle, and a caller resolves [capitalizationAllowed] itself from the field (raw-mode app,
 * password field) rather than this module knowing why capitalisation is off, matching
 * rebuild-from-scratch.md "The editor is not a reliable narrator" point 4 ("trust is a value the
 * pipeline is given, not an assumption it makes"). spec: dictation.md SS7.1, SS7.5.
 */
data class DictationTextSettings(
    val capitalizeFirstLetter: Boolean = true,
    val capitalizeAfterSentenceEnd: Boolean = true,
    val capitalizationAllowed: Boolean = true,
)

/** spec: dictation.md SS6.3, SS6.4: which timer is currently deciding how long the session waits. */
enum class DictationMode { SEGMENTED, RESTART_LOOP }

/**
 * Decides segmented vs restart-loop mode once, at session start. spec: dictation.md SS6.3: "Used
 * for a session when all of: Android 13 or later; `dictation_continuous_session` on; the engine
 * has not refused segmented sessions since the recognizer was created; the pause is greater than
 * 0." Any of those failing falls back to SS6.4's restart loop.
 */
object DictationModeDecision {
    private const val MIN_API_LEVEL_FOR_SEGMENTED = 33

    fun decide(settings: DictationSettings, segmentedRefusalLatch: Boolean): DictationMode =
        if (settings.androidApiLevel >= MIN_API_LEVEL_FOR_SEGMENTED &&
            settings.segmentedSessionEnabled &&
            !segmentedRefusalLatch &&
            settings.pauseMs > 0L
        ) {
            DictationMode.SEGMENTED
        } else {
            DictationMode.RESTART_LOOP
        }
}
