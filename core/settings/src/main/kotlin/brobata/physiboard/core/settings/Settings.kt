package brobata.physiboard.core.settings

import brobata.physiboard.core.actions.feedback.TypingSoundMode
import brobata.physiboard.core.keys.LongPressMode
import brobata.physiboard.core.pointer.keyboardswipe.SwipeToDeleteProvider
import brobata.physiboard.core.pointer.keyboardswipe.TrackpadGestureProvider
import brobata.physiboard.core.pointer.trackpad.ActivationMode
import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.text.DashStyle
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.EnterSendMethod
import brobata.physiboard.core.text.ExtraSendShortcut
import brobata.physiboard.core.text.MessagingPreset
import brobata.physiboard.core.text.SmartQuoteStyle

/**
 * Every preference 3.0 keeps, as one immutable value. spec: settings-catalog.md SS2 (the key
 * inventory) and SS13 (which rows survive).
 *
 * Each group's default is the catalogue's FIRST-RUN BASELINE (SS4.1, the value every Titan 2
 * Elite ends up with), not the bare "code default" column. The code default is what a key reads
 * as when it is absent from an empty 2.x store, and no Titan ever ran with one: shipping it here
 * already put a build on the maintainer's phone with automatic correction switched off (see
 * `KeyboardPipeline.KeyboardSettings`' own history). Two things are deliberately NOT the baseline:
 * [TrackpadPrefs.enabled] (baseline true, here false) because it intercepts Space ahead of the
 * pipeline (trackpad-caret-nav.md SS2.2), and [DictationPrefs.sideKeyAssistant] (baseline true,
 * here false) because dictation.md SS15's Keep/Drop says "do not set `side_key_assistant`
 * without binding". Personal data the catalogue flags in the baseline (SS4.1, "two of these are
 * personal data") is left out of the defaults: the two ring colours and the PersaLink WebAPK id.
 *
 * [schemaVersion] is the contract number of the flat map [SettingsCodec] writes; a reader that
 * meets a newer number than it knows must not pretend to understand the rows.
 */
data class Settings(
    val typing: TypingPrefs = TypingPrefs(),
    val correction: CorrectionPrefs = CorrectionPrefs(),
    val languages: LanguagePrefs = LanguagePrefs(),
    val keys: KeyPrefs = KeyPrefs(),
    val symPages: SymPagePrefs = SymPagePrefs(),
    val statusBar: StatusBarPrefs = StatusBarPrefs(),
    val perApp: PerAppPrefs = PerAppPrefs(),
    val dictation: DictationPrefs = DictationPrefs(),
    val trackpad: TrackpadPrefs = TrackpadPrefs(),
    val keyboardSwipe: KeyboardSwipePrefs = KeyboardSwipePrefs(),
    val device: DevicePrefs = DevicePrefs(),
    val expansion: ExpansionPrefs = ExpansionPrefs(),
    val launcher: LauncherPrefs = LauncherPrefs(),
    val feedback: FeedbackPrefs = FeedbackPrefs(),
    val shell: ShellState = ShellState(),
    val captures: DeviceCaptures = DeviceCaptures(),
) {
    companion object {
        /** The flat-map contract this build writes and reads. Bump when a row changes meaning, not when one is added. */
        const val SCHEMA_VERSION: Int = 1
    }
}

/**
 * The typing pipeline's switches. spec: settings-catalog.md SS2.1; the behavior each one gates is
 * text-input.md's. `mid_word_quote_to_apostrophe`, `physical_keyboard_currency_symbol` and
 * `auto_show_keyboard` are dropped (text-input.md SS20 Keep/Drop, catalogue SS13: no soft
 * keyboard on the Titan); `swipe_to_delete` stays because keys-and-modifiers.md keeps "the
 * consumption" and `:core:keys` models the switch. `swipe_to_delete_provider` and
 * `swipe_incremental_threshold` are NOT dropped, despite once being listed here as such: they
 * live under [KeyboardSwipePrefs] instead (`swipeToDeleteProvider`, `deleteSwipeThresholdPx`),
 * the unified keyboard-surface-swipe home trackpad-caret-nav.md SS3.7 gives every swipe
 * threshold, "see trackpad document" per text-input.md's own SS15 row for this key.
 */
data class TypingPrefs(
    val capitalizeAtTextStart: Boolean = true,
    val capitalizeAfterSentenceEnd: Boolean = true,
    val capitalizeRestrictedFields: Boolean = false,
    val doubleSpaceToPeriod: Boolean = true,
    val clearAltOnSpace: Boolean = true,
    val shiftBackspaceDeletesForward: Boolean = false,
    val altBackspaceDeletesForward: Boolean = false,
    val backspaceAtStartDeletesForward: Boolean = false,
    /** The "Remove before" list, `auto_space_punctuation`: a subset of `.,;:!?\/")]}` in that order. spec: text-input.md SS6.3. */
    val removeSpaceBefore: String = "",
    /** The "Before next text" list, `space_after_punctuation`. spec: text-input.md SS6.6. */
    val spaceBeforeNextText: String = "",
    val commaSpace: Boolean = false,
    val spacedHyphenToDash: Boolean = false,
    val dashStyle: DashStyle = DashStyle.EN_DASH,
    val smartQuotes: Boolean = false,
    val smartQuoteStyle: SmartQuoteStyle = SmartQuoteStyle.GERMAN_GUILLEMETS,
    val frenchPunctuationSpacing: Boolean = false,
    val frenchPunctuationOnlyFrench: Boolean = false,
    val swipeToDelete: Boolean = false,
)

/**
 * Autocorrect and suggestions. spec: settings-catalog.md SS2.2, autocorrect-suggestions.md SS13.
 * `auto_replace_on_space_enter`, `max_auto_replace_distance` and `use_keyboard_proximity` carry
 * the baseline (true, 2, true; catalogue SS4.1). `use_edit_type_ranking` is dropped
 * (autocorrect-suggestions.md Keep/Drop), as are the strip-gesture and threshold rows and the
 * debug logging row. `user_dictionary_entries` is user content owned by `:core:dict`, not a
 * setting, and is not carried here.
 */
data class CorrectionPrefs(
    val textReplacementsEnabled: Boolean = true,
    /**
     * `auto_correct_enabled_languages`. Empty means "not chosen": the catalogue's absent rule
     * (system language if it has a rule set, else `en`) applies. The 2.x hidden `x-pastiera` set
     * never appears here; the importer strips it (autocorrect-suggestions.md Keep/Drop, "Hidden
     * `x-pastiera` set in the UI: fix or drop").
     */
    val textReplacementLanguages: List<String> = emptyList(),
    /** `auto_correct_custom_<code>`, one entry per language code. */
    val customSubstitutions: Map<String, SubstitutionSet> = emptyMap(),
    val autoReplaceOnSpaceEnter: Boolean = true,
    val maxAutoReplaceDistance: Int = 2,
    val suggestionsEnabled: Boolean = true,
    val accentMatching: Boolean = true,
    val useKeyboardProximity: Boolean = true,
)

/** One language's user substitutions: the catalogue's JSON object whose `__name` is the display name and every other field `wrong: right`. */
data class SubstitutionSet(val displayName: String = "", val rules: Map<String, String> = emptyMap())

/**
 * Dictionaries, layouts and input styles. spec: settings-catalog.md SS2.3, dictionaries-languages.md
 * Keep/Drop. `additional_ime_subtypes` (dropped with the legacy Languages activity), the
 * `keyboard_layout_list` cycle order (no screen), the profile override, `titan2_layout_enabled`,
 * `global_variation_layout_override` and the two reload-trigger timestamps are not carried.
 */
data class LanguagePrefs(
    val keyboardLayout: String = "qwerty",
    val layoutAutoByLocale: Boolean = true,
    /**
     * The project's default-ON rule: all three layout-switch chords intercept a key combination
     * the user presses constantly (Alt+Shift, Alt+Enter, Ctrl+Space) ahead of the app ever seeing
     * it, so every one of them ships OFF here regardless of the 2.x baseline these rows carried
     * (dictionaries-languages.md SS9.1's table has `alt_shift_layout_switch` and
     * `ctrl_space_layout_switch` both true) until each has survived real use on the maintainer's
     * own Titan. Switching a style from the settings screen or the strip's language button does
     * not go through these switches at all (`KeyboardSession.switchToNextInputStyle`), so all
     * three staying off never blocks switching itself.
     */
    val altShiftLayoutSwitch: Boolean = false,
    val altEnterLayoutSwitch: Boolean = false,
    val ctrlSpaceLayoutSwitch: Boolean = false,
    val toastOnLayoutSwitch: Boolean = true,
    /** `custom_input_styles`, split on `;`. Empty means the app's predefined list. */
    val inputStyles: List<String> = emptyList(),
    /** `input_style_suggestion_locales`: `<locale>:<layout>` to extra suggestion locale tags. */
    val suggestionLocales: Map<String, List<String>> = emptyMap(),
    /** `hidden_system_input_styles`. */
    val hiddenSystemInputStyles: List<String> = emptyList(),
    /** `app_language_tag`, BCP-47; blank means the system language. */
    val appLanguageTag: String = "",
)

/**
 * Keys and modifiers. spec: settings-catalog.md SS2.4, keys-and-modifiers.md SS18 and SS22. The
 * tap-latch and latch-stays-on-space rows, `fn_speech_scan_code` (hard-coded 251) and
 * `alt_ctrl_speech_shortcut` (the chord is removed) are dropped.
 *
 * SPEC GAP: `long_press_threshold` has two 2.x fallbacks (300 and 500, catalogue SS11) and the
 * keys document says "fix the 300/500 default mismatch, one default only" without naming it.
 * 500 is chosen: it is what the Alt/Sym layer (the reader that matters on a hardware keyboard)
 * used, what text-input.md's Keep/Drop cites ("threshold 500 ms"), and `:core:keys`'
 * `LongPressSettings` default.
 *
 * The bounce and accidental-press filters (keys-and-modifiers.md SS10, SS11) were left out of an
 * earlier revision of this class on the reading that SS22 calls them "undecided... keep only with
 * a screen", but `:core:keys`' `BounceFilter`/`AccidentalPressFilter` are fully implemented and
 * tested either way and SS18 lists their preference keys as real settings rows regardless of
 * screen ("Rows marked 'none' for screen have no user interface in 2.x... the values are still
 * honoured, backed up, and restored"); a maintainer who already built and tested the filter would
 * not leave them uncallable for want of a settings screen, so the fields are kept here (still with
 * no dedicated screen, matching 2.x, until `:app` adds one).
 */
data class KeyPrefs(
    val longPressMode: LongPressMode = LongPressMode.ALT,
    val longPressThresholdMs: Long = 500,
    val navModeEnabled: Boolean = true,
    val navModeCtrlHoldEnabled: Boolean = false,
    val layoutAwareCtrlShortcuts: Boolean = false,
    val symEditShortcuts: Boolean = true,
    /** `nav_mode_mappings_updated`. spec: trackpad-caret-nav.md SS5.9: "any change makes the running keyboard reload the map" (`ctrl_key_mappings.json`, `:core:keys` `CtrlMappingCodec`). 0 means unset (never saved). */
    val navModeMappingsUpdatedAtMs: Long = 0L,
    /**
     * `nav_mode_default_mappings_version`. spec: keys-and-modifiers.md SS12.1: which version of
     * the shipped Fn Layer defaults the private `ctrl_key_mappings.json` was last migrated
     * against (`:core:keys` `CtrlMappingMigration`). 0 means never migrated: a fresh install's
     * file is already current (seeded from the up to date asset), so this only matters for a file
     * saved before a later default was added.
     */
    val navModeDefaultMappingsVersion: Int = 0,
    /** `bounce_keys_enabled`. spec: keys-and-modifiers.md SS10. */
    val bounceKeysEnabled: Boolean = false,
    /** `bounce_keys_delay_ms`, clamped 20 to 500 by `:core:keys` `BounceKeySettings.clampedDelayMs`. */
    val bounceKeysDelayMs: Long = 80,
    val bounceKeysCharacterKeysEnabled: Boolean = true,
    val bounceKeysModifierKeysEnabled: Boolean = false,
    val bounceKeysSpaceEnabled: Boolean = true,
    val bounceKeysEnterEnabled: Boolean = true,
    val bounceKeysBackspaceEnabled: Boolean = true,
    /** `overlapping_keys_enabled`. spec: keys-and-modifiers.md SS11. */
    val overlappingKeysEnabled: Boolean = false,
)

/** The Sym pages 3.0 keeps. spec: layers-sym-alt.md Keep/Drop: the device page (5) is dropped. */
enum class SymPage(val id: String) {
    EMOJI("emoji"), SYMBOLS("symbols"), CLIPBOARD("clipboard"), EMOJI_PICKER("emoji_picker");

    companion object {
        fun fromId(id: String?): SymPage? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Which Sym pages exist and in what order, the `sym_pages_config` contract without its dropped
 * `deviceEnabled` and legacy `emojiFirst` members. spec: settings-catalog.md SS2.5; the baseline
 * is "emoji off, symbols on, clipboard off, picker on, order emoji_picker, symbols, clipboard,
 * emoji". A page missing from [order] is appended last when read.
 */
data class SymPagesConfig(
    val emojiEnabled: Boolean = false,
    val symbolsEnabled: Boolean = true,
    val clipboardEnabled: Boolean = false,
    val emojiPickerEnabled: Boolean = true,
    val order: List<SymPage> = listOf(SymPage.EMOJI_PICKER, SymPage.SYMBOLS, SymPage.CLIPBOARD, SymPage.EMOJI),
)

/**
 * The Sym layer's own settings. spec: settings-catalog.md SS2.5. `alt_character_layer_binding`
 * (orphaned) and the three transient page markers are dropped.
 */
data class SymPagePrefs(
    val pages: SymPagesConfig = SymPagesConfig(),
    /** `sym_mappings_custom`: `KEYCODE_Q` and the like to the text that key produces on page 1. */
    val customEmojiPage: Map<String, String> = emptyMap(),
    /** `sym_mappings_page2_custom`, same shape, page 2. */
    val customSymbolsPage: Map<String, String> = emptyMap(),
    val autoClose: Boolean = true,
    val autoCloseOnTouch: Boolean = true,
    val emojiPickerExpandedHeight: Boolean = false,
    /**
     * `restore_sym_page`. spec: layers-sym-alt.md SS5.8: "the page to reopen at next input start."
     * Written only by the customisation screen, on a normal finish; read and cleared by the
     * keyboard on its next field start (settings-catalog.md SS2.5: "Transient").
     */
    val restoreSymPage: Int = 0,
    /**
     * `pending_restore_sym_page`. spec: layers-sym-alt.md SS5.8: the page recorded on the way into
     * the customisation screen, promoted to [restoreSymPage] only if that screen finishes
     * normally; left stranded, and never promoted, if it is destroyed instead (the user switched
     * to another app), which is why nothing ever reads this field back except to promote it.
     */
    val pendingRestoreSymPage: Int = 0,
)

/** `status_bar_visibility`. spec: status-bar.md SS3. */
enum class StatusBarVisibility(val storedValue: String) {
    ALWAYS("ALWAYS"), NEVER("NEVER"), APPS("APPS");

    companion object {
        fun fromStored(value: String?): StatusBarVisibility? = entries.firstOrNull { it.storedValue == value }
    }
}

/**
 * The buttons a strip slot can hold. spec: status-bar.md SS9 Keep/Drop ("keep clipboard,
 * microphone, emoji, symbols, language, settings, undo, redo"; `hamburger` undecided, kept as a
 * value so an imported slot is not lost; `software_keyboard_mode` dropped; `none` is an empty
 * slot in 2.x and simply absent from the list here).
 */
enum class BarButton(val id: String) {
    CLIPBOARD("clipboard"), EMOJI("emoji"), MICROPHONE("microphone"), LANGUAGE("language"),
    HAMBURGER("hamburger"), SETTINGS("settings"), SYMBOLS("symbols"), UNDO("undo"), REDO("redo");

    companion object {
        fun fromId(id: String?): BarButton? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The fields of a stored theme the strip actually uses (status-bar.md SS9.1); every on-screen
 * keyboard geometry field is dropped (SS9 Keep/Drop, "reduce to the fields the strip uses").
 * Colours are signed 32-bit ARGB. The default is the catalogue's baseline hardware theme
 * (settings-catalog.md SS3.1, "Baseline hardware value" column).
 */
data class StripTheme(
    val background: Int = 0xFF111111.toInt(),
    val suggestion: Int = 0xFF171717.toInt(),
    val statusBarButton: Int = 0xFF1C1C1E.toInt(),
    val accent: Int = 0xFF409CFF.toInt(),
    val textAndIcons: Int = 0xFFF8F8F8.toInt(),
    val divider: Int = 0xFF303030.toInt(),
    val ledInactive: Int = 0xFF303030.toInt(),
    val ledActive: Int = 0xFF409CFF.toInt(),
    val ledLocked: Int = 0xFFFF9F0A.toInt(),
    val keyCornerRadiusRatio: Double = 0.18186983466148376,
    val chromeCornerRadiusRatio: Double = 0.3499999940395355,
    val suggestionsHeightScale: Double = 0.8999999761581421,
    val showLeds: Boolean = false,
) {
    companion object {
        /**
         * "Slate Dark", the 2.x code-default theme, which is what a missing field in a stored
         * theme reads as (settings-catalog.md SS3.1, "Missing fields take the target's default").
         * `suggestion` and `status_bar_button` default to the normal-key and special-key colours.
         */
        val SLATE_DARK: StripTheme = StripTheme(
            background = 0xFF000000.toInt(),
            suggestion = 0xFF15191D.toInt(),
            statusBarButton = 0xFF2B3138.toInt(),
            accent = 0xFF6496FF.toInt(),
            textAndIcons = 0xFFEFEFEF.toInt(),
            divider = 0xFF2C3136.toInt(),
            ledInactive = 0xFF303030.toInt(),
            ledActive = 0xFF6496FF.toInt(),
            ledLocked = 0xFFF76300.toInt(),
            keyCornerRadiusRatio = 0.10,
            chromeCornerRadiusRatio = 0.10,
            suggestionsHeightScale = 1.4,
            showLeds = false,
        )
    }
}

/** A saved custom theme. Names compare case-insensitively; a blank name saves as "Custom" (settings-catalog.md SS2.6). */
data class NamedTheme(val name: String, val theme: StripTheme)

/**
 * One entry of `keyboard_theme_layout_overrides_hardware`: a theme for a matching locale and/or
 * layout, beating the chosen theme when it matches. spec: settings-catalog.md SS2.6 (the row),
 * status-bar.md SS9.2 ("the most specific matching override... beats the chosen theme") and
 * SS2.6's scoring (exact locale 16 points, language 8, layout 4); resolution itself is
 * [StripThemeResolution.resolve]. An entry with neither field set matches nothing and is dropped
 * on write (status-bar.md SS2.6).
 */
data class ThemeLayoutOverride(val locale: String? = null, val layout: String? = null, val theme: StripTheme = StripTheme())

/**
 * The strip, its theme and the caret badge. spec: settings-catalog.md SS2.6, status-bar.md.
 * Dropped: the `show_status_bar` and single-slot mirrors (the importer folds them in), the
 * software theme, the light/dark slots, assignment modes, drafts, the
 * preview scale, `modifier_indicator_mode`, `accessibility_read_second_row_enabled` (its legacy
 * modifier container is itself dropped, SS19), the debug row, and `pastierina_*`. The suggestion
 * row's own two announcement settings (SS5.6, SS15, kept: "Accessibility announcement
 * throttling") live here rather than a dedicated Accessibility screen, since 3.0 has none yet.
 *
 * [visibility] defaults to ALWAYS, not the asset's APPS: the catalogue (SS1.1, SS11 first row)
 * says a fresh install ends at ALWAYS because the impact stamp overwrote the asset, and SS13
 * folds that stamp's intent into the first-run defaults. [apps] is the seeded twenty (status-bar.md
 * SS3).
 */
data class StatusBarPrefs(
    val visibility: StatusBarVisibility = StatusBarVisibility.ALWAYS,
    val apps: Set<String> = SEEDED_STATUS_BAR_APPS,
    val heightDp: Int = 56,
    val leftButtons: List<BarButton> = listOf(BarButton.CLIPBOARD),
    val rightButtons: List<BarButton> = listOf(BarButton.MICROPHONE),
    val caretModifierBadge: Boolean = true,
    val caretBadgeArmedColor: Int = 0xFF111827.toInt(),
    val caretBadgeLockedColor: Int = 0xFFDC2626.toInt(),
    val theme: StripTheme = StripTheme(),
    val savedThemes: List<NamedTheme> = emptyList(),
    /** `keyboard_theme_layout_overrides_hardware`; see [ThemeLayoutOverride]. */
    val layoutOverrides: List<ThemeLayoutOverride> = emptyList(),
    /** `titan2_elite_rounded_corner_insets`, kept as the hidden preference status-bar.md SS9 allows. */
    val roundedCornerInsets: Boolean = true,
    /**
     * Collapse the strip in a field that allows no suggestions, where its slots can never fill.
     * On by default: the maintainer's terminal showed an empty band across the bottom of the
     * screen (2026-09-26). Off keeps the strip's buttons reachable in every field.
     */
    val hideWhereNothingToSuggest: Boolean = true,
    /** `accessibility_live_announcements_enabled`, spec SS5.6, SS15: default false. */
    val accessibilityLiveAnnouncementsEnabled: Boolean = false,
    /** `accessibility_suggestions_announcement_delay_ms`, spec SS5.6, SS15: default 500 ms. */
    val accessibilitySuggestionsAnnouncementDelayMs: Long = 500,
    /** `ime_overlay_debug_logging`, spec SS11, SS15: default false. */
    val overlayDebugLoggingEnabled: Boolean = false,
) {
    companion object {
        /**
         * SPEC GAP: status-bar.md SS3 names the twenty seeded apps by product name (Gmail, Google
         * Messages, AOSP Messaging, WhatsApp, WhatsApp Business, Messenger, Messenger Lite,
         * Facebook, Instagram, Telegram, Signal, Discord, Slack, Teams, Outlook, Snapchat, X,
         * Reddit, LinkedIn, Google Chat) and says "their package names are in the settings
         * catalog", but the catalogue never lists them. These are the public Play Store ids of
         * those twenty products; status-bar.md T5 confirms `com.whatsapp` and `com.google.android.gm`.
         */
        val SEEDED_STATUS_BAR_APPS: Set<String> = setOf(
            "com.google.android.gm",
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.facebook.katana",
            "com.instagram.android",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.discord",
            "com.Slack",
            "com.microsoft.teams",
            "com.microsoft.office.outlook",
            "com.snapchat.android",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.linkedin.android",
            "com.google.android.apps.dynamite",
        )
    }
}

/** One row of `app_enter_behavior_overrides`. spec: settings-catalog.md SS2.7, per-app-behavior.md SS3.12. */
data class EnterOverrideRow(
    val packageName: String,
    val behavior: EnterBehavior = EnterBehavior.APP_DEFAULT,
    val sendMethod: EnterSendMethod = EnterSendMethod.AUTO,
    val extraSendShortcut: ExtraSendShortcut = ExtraSendShortcut.NONE,
)

/**
 * Per-app lists and Enter rules. spec: settings-catalog.md SS2.7, per-app-behavior.md. The
 * software keyboard mode rows and `quick_launcher_default_assigned` are dropped. [exactTypingPackages]
 * defaults empty: the asset's PersaLink WebAPK id is personal data (catalogue SS4.1). The four
 * seeded Enter overrides are the asset's, each `enter_send_shift_newline` / `auto` / `none`.
 */
data class PerAppPrefs(
    val exactTypingPackages: Set<String> = emptySet(),
    val nudgePackages: Set<String> = setOf("com.microsoft.teams"),
    val enterBehaviorEnabled: Boolean = true,
    val enterPreset: MessagingPreset = MessagingPreset.SEND_SHIFT_NEWLINE,
    val enterOverrides: List<EnterOverrideRow> = listOf(
        EnterOverrideRow("com.whatsapp", EnterBehavior.SEND_SHIFT_NEWLINE),
        EnterOverrideRow("com.discord", EnterBehavior.SEND_SHIFT_NEWLINE),
        EnterOverrideRow("com.google.android.apps.messaging", EnterBehavior.SEND_SHIFT_NEWLINE),
        EnterOverrideRow("com.instagram.android", EnterBehavior.SEND_SHIFT_NEWLINE),
    ),
)

/** `dictation_haptic_strength`. spec: dictation.md SS9. */
enum class HapticStrength(val storedValue: String) {
    LIGHT("light"), STANDARD("standard"), STRONG("strong");

    companion object {
        fun fromStored(value: String?): HapticStrength? = entries.firstOrNull { it.storedValue == value }
    }
}

/** `assistant_action`. spec: dictation.md SS11. */
enum class AssistantAction(val storedValue: String) {
    AUTO("auto"), VOICE_COMMAND("voice_command"), HANDS_FREE("hands_free"), ASSIST("assist");

    companion object {
        fun fromStored(value: String?): AssistantAction? = entries.firstOrNull { it.storedValue == value }
    }
}

/**
 * Dictation and the assistant. spec: settings-catalog.md SS2.8, dictation.md SS15 ("First-run
 * defaults for this subsystem: Fn-hold on, cues on, masking off, 2000 ms pause; but do not set
 * `side_key_assistant` without binding"). [engine] is blank (system default) rather than the
 * asset's Google component: the component is a fact about the maintainer's phone (D4), and a
 * component that is not installed falls to the system default anyway (dictation.md SS5).
 */
data class DictationPrefs(
    val fnLongPressSpeech: Boolean = true,
    val haptics: Boolean = true,
    val hapticStrength: HapticStrength = HapticStrength.STRONG,
    val endSilenceMs: Int = 2000,
    val maskOffensive: Boolean = false,
    /** `""` system default, `ondevice`, or a flattened recognition-service component name. */
    val engine: String = "",
    val continuousSession: Boolean = true,
    val autoPunctuation: Boolean = true,
    val symLongPressAssistant: Boolean = false,
    val sideKeyAssistant: Boolean = false,
    val assistantAction: AssistantAction = AssistantAction.AUTO,
)

/**
 * The screen trackpad. spec: settings-catalog.md SS2.9. [enabled] is the one baseline row this
 * schema refuses: the trackpad intercepts Space before the pipeline sees it (trackpad-caret-nav.md
 * SS2.2) and a misfiring hold swallows the keystroke for good, so it stays off until a screen can
 * switch it off again.
 */
data class TrackpadPrefs(
    val enabled: Boolean = false,
    val triggerKey: TriggerKey = TriggerKey.SPACE,
    val activation: ActivationMode = ActivationMode.HOLD,
    val stepPx: Int = 32,
    val showHint: Boolean = true,
)

/**
 * The keyboard-surface swipe (upstream "trackpad gestures"), a different gesture on a different
 * surface than [TrackpadPrefs]'s screen trackpad. spec: trackpad-caret-nav.md SS3.7: "None of
 * these has a screen in 2.x; they are listed because the keyboard still reads them and backup
 * carries them." `:core:pointer`'s `KeyboardSwipeSettings` is the pure type these values feed.
 */
data class KeyboardSwipePrefs(
    val gesturesEnabled: Boolean = false,
    val provider: TrackpadGestureProvider = TrackpadGestureProvider.NATIVE_IME,
    val swipeThresholdPx: Float = 500f,
    /** `trackpad_suggestion_swipe_threshold`; null means "use [swipeThresholdPx]" (SS3.7: "the legacy value, else 500"). */
    val suggestionSwipeThresholdPx: Float? = null,
    /** `trackpad_delete_swipe_threshold`; null means "use [swipeThresholdPx]". */
    val deleteSwipeThresholdPx: Float? = null,
    val gestureAddWordEnabled: Boolean = true,
    val gestureAddWordFullWidthEnabled: Boolean = true,
    val swipeToDeleteProvider: SwipeToDeleteProvider = SwipeToDeleteProvider.NATIVE_IME,
)

/** `notification_ring_brightness`. spec: device-backlight-ring.md SS5. */
enum class RingBrightness(val storedValue: String) {
    DIM("DIM"), NORMAL("NORMAL"), BRIGHT("BRIGHT");

    companion object {
        fun fromStored(value: String?): RingBrightness? = entries.firstOrNull { it.storedValue == value }
    }
}

/** A hand-fitted ring in window pixels; exists only when the radius was stored (settings-catalog.md SS2.10). */
data class RingFit(val cx: Float, val cy: Float, val radius: Float, val stroke: Float)

/**
 * Backlight and the notification ring. spec: settings-catalog.md SS2.10. [ringAppColors] defaults
 * empty (the asset's two entries are personal data); [ringFit] defaults to the D2 measurement
 * because every Elite panel is the same hardware within a few pixels.
 */
data class DevicePrefs(
    val smartBacklightEnabled: Boolean = true,
    val ringEnabled: Boolean = true,
    val ringMinutes: Int = 2,
    val ringBrightness: RingBrightness = RingBrightness.NORMAL,
    val ringShowIcons: Boolean = false,
    val ringKeyboardDark: Boolean = true,
    /** `notification_ring_default_color`; null means the ring policy's own default (device-backlight-ring.md). */
    val ringDefaultColor: Int? = null,
    val ringAppColors: Map<String, Int> = emptyMap(),
    val ringFit: RingFit? = RingFit(cx = 78.4834f, cy = 80.4834f, radius = 45.9375f, stroke = 9.625f),
)

/** `snippets_presentation`. spec: expansion-clipboard-pickers-launcher.md SS2. */
enum class SnippetPresentation(val storedValue: String) {
    OFF("off"), FLOATING_POPUP("floating_popup"), SUGGESTION_BAR("suggestion_bar");

    companion object {
        fun fromStored(value: String?): SnippetPresentation? = entries.firstOrNull { it.storedValue == value }
    }
}

/** Text expansion and clipboard history. spec: settings-catalog.md SS2.12. */
data class ExpansionPrefs(
    val snippetsEnabled: Boolean = false,
    val snippetPrefix: String = "!",
    /** `snippets_v1`: shortcut (lower-cased) to replacement; blank replacements are dropped. */
    val snippets: Map<String, String> = emptyMap(),
    val presentation: SnippetPresentation = SnippetPresentation.FLOATING_POPUP,
    val expandExactOnSpace: Boolean = true,
    val acceptPrefixWithSpace: Boolean = false,
    val acceptWithTab: Boolean = true,
    val acceptWithEnter: Boolean = false,
    val clipboardHistoryEnabled: Boolean = true,
    val clipboardRetentionMinutes: Long = 5,
)

/** `quick_launcher_behavior`; the 2.x value `pastiera` is PhysiBoard's own launcher. */
enum class LauncherBehavior(val storedValue: String) {
    PHYSIBOARD("physiboard"), NIAGARA("niagara");

    companion object {
        fun fromStored(value: String?): LauncherBehavior? = entries.firstOrNull { it.storedValue == value }
    }
}

/**
 * The quick launcher and key assignments. spec: settings-catalog.md SS2.12, SS2.4, and
 * expansion-clipboard-pickers-launcher.md Keep/Drop: the Appearance rows, the animation duration
 * and `command_surface_sources` are dropped (constants until a screen exists).
 *
 * [assignedKeysJson] and [commandCustomizationsJson] are carried as the JSON documents the
 * catalogue defines (SS2.7 `launcher_shortcuts`, SS2.12 `quick_launcher_command_customizations`):
 * their nested launch specs belong to the launcher subsystem's own parser, and "keep the JSON
 * contract" is the Keep/Drop verdict. Blank means never written, so the launcher applies its
 * first-read rule (Space gets the quick launcher, catalogue SS6.3).
 */
data class LauncherPrefs(
    val behavior: LauncherBehavior = LauncherBehavior.PHYSIBOARD,
    val openUniqueMatch: Boolean = false,
    val limitResults: Boolean = false,
    val respectKeyboardLayout: Boolean = true,
    val typoTolerantRanking: Boolean = true,
    /** `power_shortcuts_enabled`: Sym plus an assigned key launches. */
    val symShortcutsEnabled: Boolean = true,
    /** `launcher_shortcuts_enabled`: assigned keys fire bare on the home screen. */
    val homeScreenShortcutsEnabled: Boolean = false,
    val assignedKeysJson: String = "",
    val commandCustomizationsJson: String = "",
    /**
     * `command_surface_sources` (expansion-clipboard-pickers-launcher.md SS8.6), carried as its
     * JSON document like the two above now that the "QuickLauncher entries" screen exists in 3.0;
     * blank means every source at its default (apps and PhysiBoard on, the other three off).
     */
    val commandSurfaceSourcesJson: String = "",
    /**
     * `quick_launcher_static_top_highlight`. spec: expansion-clipboard-pickers-launcher.md SS7.5:
     * off by default, meaning the top match is tinted from its own color or one derived from its
     * icon; on, it always gets [quickLauncherStaticTopHighlightColor] instead.
     */
    val quickLauncherStaticTopHighlight: Boolean = false,
    /** `quick_launcher_static_top_highlight_color`, spec SS7.5/SS7.7: default `0x7A4285F4`. */
    val quickLauncherStaticTopHighlightColor: Int = 0x7A4285F4.toInt(),
)

/**
 * Sound and haptics. spec: settings-catalog.md SS2.13. `typing_sound_mode` and
 * `typing_sound_output_mode` are carried (expansion-clipboard-pickers-launcher.md SS9.1, SS9.3);
 * the custom-pack import fields (`typing_sound_custom_file_name`,
 * `typing_sound_custom_display_name`, `typing_sound_updated_at`) are still dropped, since 3.0 has
 * no pack-import flow, matching how `custom` is reachable in 2.x only through the preference
 * itself or a restored backup ("hidden to declutter", SS9.1). The dictation cue lives in
 * [DictationPrefs].
 */
data class FeedbackPrefs(
    val tapHapticUseSystem: Boolean = true,
    val tapHapticDurationMs: Long = 25,
    val typingSoundMode: TypingSoundMode = TypingSoundMode.OFF,
    val typingSoundOutputMode: TypingSoundOutputMode = TypingSoundOutputMode.MEDIA,
)

/** `typing_sound_output_mode`. spec: settings-catalog.md SS2.13; "anything else reads as `media`". */
enum class TypingSoundOutputMode(val storedValue: String) {
    MEDIA("media"), SYSTEM("system"), NOTIFICATION("notification");

    companion object {
        fun fromStored(value: String?): TypingSoundOutputMode = entries.firstOrNull { it.storedValue == value } ?: MEDIA
    }
}

/** The app shell's own markers (settings-catalog.md SS2.15) that must survive a reinstall. The migration and baseline markers are 2.x-only. */
data class ShellState(
    val tutorialCompleted: Boolean = false,
    val lastSeenWhatsNewVersion: String = "",
    val dismissedReleases: List<String> = emptyList(),
    val untestedDeviceNoticeSeen: Boolean = false,
)

/**
 * What the phone's system settings were before PhysiBoard changed them. These are not user
 * settings but they are the only way "Reset device settings to stock" (settings-catalog.md SS9.5)
 * can put the originals back, so they must survive the import. spec: SS2.4 (`fn_ctrl_prev_*`),
 * SS2.8 (`side_key_original_*`), SS2.10 (the backlight captures). A null value is the 2.x "unset"
 * sentinel: the flag says captured but no value was stored (SS4.2's misnamed keep-list).
 */
data class DeviceCaptures(
    val fnCtrlPrevCaptured: Boolean = false,
    val fnCtrlPrevEnable: Int? = null,
    val fnCtrlPrevFunction: Int? = null,
    val sideKeyOriginalCaptured: Boolean = false,
    val sideKeyOriginalPackage: String = "",
    val sideKeyOriginalActivity: String = "",
    val qsBacklightPrevCaptured: Boolean = false,
    val qsBacklightPrev: Int? = null,
    val ringBacklightPrevCaptured: Boolean = false,
    val ringBacklightPrev: Int? = null,
    val smartBacklightApplied: Boolean = false,
)
