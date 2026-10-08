package brobata.physiboard.core.speech

/**
 * The session-shaping settings this module reads, each the spec's own default. spec: dictation.md
 * SS13. `:ime` builds one of these from the settings store and hands it to [DictationEngine] on
 * every call; nothing in here is Android.
 *
 * [androidApiLevel] is a plain `Int` (`Build.VERSION.SDK_INT`'s value, never the class itself) so
 * this module never needs an `android.*` import to make the API-33 decisions in SS5.
 */
data class DictationSettings(
    /** `dictation_stop_after_silence_ms`: 15 s by the maintainer's decision; 0 means never (the session runs until stopped, within the safety limits of SS6.4). */
    val stopAfterSilenceMs: Long = 2_500L,
    val androidApiLevel: Int = 0,
    /** `dictation_mask_offensive`, the per-request profanity masking (spec SS5). */
    val maskOffensive: Boolean = false,
    /** `dictation_auto_punctuation`: ask the engine to punctuate and capitalise, Android 13 and later only (spec SS5). */
    val autoPunctuation: Boolean = true,
    /** `dictation_prefer_offline`: ask for the engine's on-device recognizer first (spec SS4.3). */
    val preferOffline: Boolean = true,
    /** `dictation_pause_media`: hold exclusive audio focus for the whole session so music pauses once and resumes once (spec SS6.7). */
    val pauseMedia: Boolean = true,
    /** `dictation_stop_on_typing`: any key other than a modifier stops the session and then does its usual work (spec SS3). */
    val stopOnTyping: Boolean = true,
    /** app-shell.md SS31: private mode forces the on-device recognizer and allows no online fallback. */
    val privateMode: Boolean = false,
    /** `dictation_haptics`: the start and stop cues (spec SS8.1); the system haptic toggle gates them again at play time. */
    val hapticsEnabled: Boolean = true,
    /** `dictation_haptic_strength`: which of SS8.1's three pulse tables the cues use. */
    val hapticStrength: CueStrength = CueStrength.STRONG,
    /** `dictation_engine` (spec SS4.2): empty for the system default, `ondevice`, or a flattened `package/class` component. */
    val engineId: String = "",
)

/**
 * One request to the recognizer, as `:core:speech` decided it. `:ime` only translates these fields
 * onto the platform intent; it decides none of them. spec: dictation.md SS5.
 */
data class RecognizerRequest(
    /** SS5: ask for one long segmented session (Android 13+), one result per utterance, no restart between them. */
    val segmented: Boolean,
    /** SS4.3: ask for the engine's on-device recognizer only. */
    val preferOffline: Boolean,
    /** SS5: ask the engine to punctuate and capitalise (Android 13+). */
    val enableFormatting: Boolean,
    val maskOffensive: Boolean,
    /** SS5: the complete-silence length the engine is given, as an int on the wire (D23); the keyboard's own silence limit plus a margin. */
    val completeSilenceMs: Long,
    /** SS5: the minimum length of the request, as an int on the wire; the engine "will not stop recognizing speech before this amount of time". */
    val minimumLengthMs: Long,
)

/**
 * Plans the request for a new session from the settings and what earlier sessions learned about
 * the engine. spec: dictation.md SS5, SS6.3.
 */
object RecognizerRequestPlanner {
    private const val MIN_API_LEVEL_FOR_SEGMENTED = 33
    private const val MIN_API_LEVEL_FOR_FORMATTING = 33

    fun plan(settings: DictationSettings, segmentedRefusalLatch: Boolean): RecognizerRequest = RecognizerRequest(
        segmented = settings.androidApiLevel >= MIN_API_LEVEL_FOR_SEGMENTED && !segmentedRefusalLatch,
        // app-shell.md SS31: private mode never lets audio leave the phone.
        preferOffline = settings.preferOffline || settings.privateMode,
        enableFormatting = settings.autoPunctuation && settings.androidApiLevel >= MIN_API_LEVEL_FOR_FORMATTING,
        maskOffensive = settings.maskOffensive,
        completeSilenceMs = DictationTiming.engineSilenceMs(settings.stopAfterSilenceMs),
        minimumLengthMs = DictationTiming.engineSilenceMs(settings.stopAfterSilenceMs),
    )
}

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
    /** spec SS7.5: undo the engine's capital on a mid-sentence first word; off for languages that capitalise nouns (German, Luxembourgish), where it cannot be told from formatting. */
    val undoEngineSegmentCapitals: Boolean = true,
) {
    companion object {
        /** spec SS7.5: the languages whose nouns carry a capital of their own. */
        fun undoEngineCapitalsFor(languageTag: String?): Boolean {
            val language = languageTag?.trim()?.replace('_', '-')?.substringBefore('-')?.lowercase() ?: return true
            return language != "de" && language != "lb"
        }
    }
}
