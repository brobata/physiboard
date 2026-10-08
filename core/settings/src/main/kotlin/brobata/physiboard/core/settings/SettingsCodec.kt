package brobata.physiboard.core.settings

import brobata.physiboard.core.actions.emoji.SkinTone
import brobata.physiboard.core.actions.feedback.TypingSoundMode
import brobata.physiboard.core.keys.LongPressMode
import brobata.physiboard.core.pointer.keyboardswipe.SwipeToDeleteProvider
import brobata.physiboard.core.pointer.keyboardswipe.TrackpadGestureProvider
import brobata.physiboard.core.pointer.trackpad.ActivationMode
import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.settings.JsonRows.boolean
import brobata.physiboard.core.settings.JsonRows.double
import brobata.physiboard.core.settings.JsonRows.int
import brobata.physiboard.core.settings.JsonRows.string
import brobata.physiboard.core.text.DashStyle
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.EnterSendMethod
import brobata.physiboard.core.text.ExtraSendShortcut
import brobata.physiboard.core.text.MessagingPreset
import brobata.physiboard.core.text.SmartQuoteStyle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The flat-map keys. They are the 2.x preference names wherever a row survives unchanged
 * (settings-catalog.md SS2), so a backup, the importer and a debug dump all read the same
 * vocabulary; a row whose meaning changed gets a 3.0 name. Nothing outside this module should
 * need these except a storage layer that wants to list them.
 */
object SettingsKeys {
    const val SCHEMA_VERSION = "schema_version"

    // SS2.1 typing
    const val AUTO_CAP_FIRST = "auto_capitalize_first_letter"
    const val AUTO_CAP_AFTER_PERIOD = "auto_capitalize_after_period"
    const val AUTO_CAP_RESTRICTED = "auto_capitalize_restricted_fields"
    const val DOUBLE_SPACE_PERIOD = "double_space_to_period"
    const val CLEAR_ALT_ON_SPACE = "clear_alt_on_space"
    const val SHIFT_BACKSPACE_DELETE = "shift_backspace_delete"
    const val ALT_BACKSPACE_DELETE = "alt_backspace_delete"
    const val BACKSPACE_AT_START_DELETE = "backspace_at_start_delete"
    const val AUTO_SPACE_PUNCTUATION = "auto_space_punctuation"
    const val SPACE_AFTER_PUNCTUATION = "space_after_punctuation"
    const val COMMA_SPACE = "comma_space"
    const val HYPHEN_TO_DASH = "spaced_hyphen_to_en_dash"
    const val DASH_STYLE = "spaced_hyphen_dash_style"
    const val SMART_QUOTES = "smart_quotes"
    const val SMART_QUOTES_STYLE = "smart_quotes_style"
    const val FRENCH_SPACING = "french_punctuation_spacing"
    const val FRENCH_SPACING_ONLY_FRENCH = "french_punctuation_only_french"
    const val SWIPE_TO_DELETE = "swipe_to_delete"

    // SS2.2 correction
    const val AUTO_CORRECT_ENABLED = "auto_correct_enabled"
    const val AUTO_CORRECT_LANGUAGES = "auto_correct_enabled_languages"
    const val AUTO_CORRECT_CUSTOM_PREFIX = "auto_correct_custom_"
    const val AUTO_REPLACE_ON_SPACE_ENTER = "auto_replace_on_space_enter"
    const val MAX_AUTO_REPLACE_DISTANCE = "max_auto_replace_distance"
    const val SUGGESTIONS_ENABLED = "suggestions_enabled"
    const val ACCENT_MATCHING = "accent_matching_enabled"
    const val KEYBOARD_PROXIMITY = "use_keyboard_proximity"
    const val FIX_WORD_MIXUPS = "fix_word_mixups"

    // SS2.3 languages
    const val KEYBOARD_LAYOUT = "keyboard_layout"
    const val LAYOUT_AUTO_BY_LOCALE = "keyboard_layout_auto_by_locale"
    const val ALT_SHIFT_LAYOUT_SWITCH = "alt_shift_layout_switch"
    const val ALT_ENTER_LAYOUT_SWITCH = "alt_enter_layout_switch"
    const val CTRL_SPACE_LAYOUT_SWITCH = "ctrl_space_layout_switch"
    const val TOAST_ON_LAYOUT_SWITCH = "toast_on_layout_switch"
    const val CUSTOM_INPUT_STYLES = "custom_input_styles"
    const val INPUT_STYLE_SUGGESTION_LOCALES = "input_style_suggestion_locales"
    const val HIDDEN_SYSTEM_INPUT_STYLES = "hidden_system_input_styles"
    const val APP_LANGUAGE_TAG = "app_language_tag"

    // SS2.4 keys
    const val LONG_PRESS_MODE = "long_press_modifier"
    const val LONG_PRESS_THRESHOLD = "long_press_threshold"
    const val LONG_PRESS_VARIATION_CHOOSER = "long_press_variation_chooser"
    const val CUSTOM_VARIATIONS = "custom_variations"
    const val NAV_MODE_ENABLED = "nav_mode_enabled"
    const val NAV_MODE_CTRL_HOLD = "nav_mode_ctrl_hold_enabled"
    const val LAYOUT_AWARE_CTRL = "layout_aware_ctrl_shortcuts"
    const val SYM_EDIT_SHORTCUTS = "sym_edit_shortcuts"
    const val NAV_MODE_MAPPINGS_UPDATED = "nav_mode_mappings_updated"
    const val NAV_MODE_DEFAULT_MAPPINGS_VERSION = "nav_mode_default_mappings_version"
    const val BOUNCE_KEYS_ENABLED = "bounce_keys_enabled"
    const val BOUNCE_KEYS_DELAY_MS = "bounce_keys_delay_ms"
    const val BOUNCE_KEYS_CHARACTER_KEYS_ENABLED = "bounce_keys_character_keys_enabled"
    const val BOUNCE_KEYS_MODIFIER_KEYS_ENABLED = "bounce_keys_modifier_keys_enabled"
    const val BOUNCE_KEYS_SPACE_ENABLED = "bounce_keys_space_enabled"
    const val BOUNCE_KEYS_ENTER_ENABLED = "bounce_keys_enter_enabled"
    const val BOUNCE_KEYS_BACKSPACE_ENABLED = "bounce_keys_backspace_enabled"
    const val OVERLAPPING_KEYS_ENABLED = "overlapping_keys_enabled"

    // SS2.5 sym
    const val SYM_PAGES_CONFIG = "sym_pages_config"
    const val SYM_MAPPINGS_CUSTOM = "sym_mappings_custom"
    const val SYM_MAPPINGS_PAGE2_CUSTOM = "sym_mappings_page2_custom"
    const val SYM_CUSTOM_PAGES = "sym_custom_pages"
    const val RESTORE_SYM_PAGE = "restore_sym_page"
    const val PENDING_RESTORE_SYM_PAGE = "pending_restore_sym_page"
    const val SYM_AUTO_CLOSE = "sym_auto_close"
    const val SYM_AUTO_CLOSE_ON_TOUCH = "sym_auto_close_on_touch"
    const val SYM_DOUBLE_TAP_CHOOSER = "sym_double_tap_chooser"
    const val EMOJI_PICKER_EXPANDED = "emoji_picker_expanded_height"
    const val EMOJI_PICKER_KAOMOJI = "emoji_picker_kaomoji"
    const val EMOJI_DEFAULT_SKIN_TONE = "emoji_default_skin_tone"

    // SS2.6 status bar
    const val STATUS_BAR_VISIBILITY = "status_bar_visibility"
    const val STATUS_BAR_APPS = "status_bar_apps"
    const val STATUS_BAR_HEIGHT = "status_bar_height_dp"
    const val STATUS_BAR_SLOTS_LEFT = "status_bar_slots_left"
    const val STATUS_BAR_SLOTS_RIGHT = "status_bar_slots_right"
    const val CARET_BADGE = "caret_modifier_badge"
    const val CARET_BADGE_ARMED_COLOR = "caret_badge_armed_color"
    const val CARET_BADGE_LOCKED_COLOR = "caret_badge_locked_color"
    const val THEME = "keyboard_theme_hardware"
    const val SAVED_THEMES = "keyboard_theme_saved_themes"
    const val LAYOUT_OVERRIDES = "keyboard_theme_layout_overrides_hardware"
    const val ROUNDED_CORNER_INSETS = "titan2_elite_rounded_corner_insets"
    const val HIDE_STRIP_WHERE_NOTHING_TO_SUGGEST = "hide_status_bar_where_nothing_to_suggest"
    const val ACCESSIBILITY_LIVE_ANNOUNCEMENTS = "accessibility_live_announcements_enabled"
    const val ACCESSIBILITY_ANNOUNCEMENT_DELAY_MS = "accessibility_suggestions_announcement_delay_ms"
    const val OVERLAY_DEBUG_LOGGING = "ime_overlay_debug_logging"

    // SS2.7 per app
    const val RAW_MODE_PACKAGES = "app_raw_mode_packages"
    const val NUDGE_PACKAGES = "app_keyboard_nudge_packages"
    const val ENTER_BEHAVIOR_ENABLED = "app_enter_behavior_enabled"
    const val ENTER_PRESET = "app_enter_behavior_preset"
    const val ENTER_OVERRIDES = "app_enter_behavior_overrides"

    // SS2.8 dictation
    const val FN_LONG_PRESS_SPEECH = "fn_long_press_speech"
    const val DICTATION_HAPTICS = "dictation_haptics"
    const val DICTATION_HAPTIC_STRENGTH = "dictation_haptic_strength"
    const val DICTATION_STOP_AFTER_SILENCE = "dictation_stop_after_silence_ms"
    const val DICTATION_MASK_OFFENSIVE = "dictation_mask_offensive"
    const val DICTATION_ENGINE = "dictation_engine"
    const val DICTATION_PREFER_OFFLINE = "dictation_prefer_offline"
    const val DICTATION_PAUSE_MEDIA = "dictation_pause_media"
    const val DICTATION_STOP_ON_TYPING = "dictation_stop_on_typing"
    const val DICTATION_AUTO_PUNCTUATION = "dictation_auto_punctuation"
    const val SYM_LONG_PRESS_ASSISTANT = "sym_long_press_assistant"
    const val SIDE_KEY_ASSISTANT = "side_key_assistant"
    const val ASSISTANT_ACTION = "assistant_action"

    // SS2.9 trackpad
    const val TRACKPAD_ENABLED = "screen_trackpad_enabled"
    const val TRACKPAD_TRIGGER = "screen_trackpad_trigger_key"
    const val TRACKPAD_ACTIVATION = "screen_trackpad_activation"
    const val TRACKPAD_STEP = "screen_trackpad_step_px"
    const val TRACKPAD_HINT = "screen_trackpad_show_hint"

    // SS3.7 keyboard-surface swipe (trackpad-caret-nav.md)
    const val KEYBOARD_SWIPE_ENABLED = "trackpad_gestures_enabled"
    const val KEYBOARD_SWIPE_PROVIDER = "trackpad_provider"
    const val KEYBOARD_SWIPE_THRESHOLD = "trackpad_swipe_threshold"
    const val KEYBOARD_SWIPE_SUGGESTION_THRESHOLD = "trackpad_suggestion_swipe_threshold"
    const val KEYBOARD_SWIPE_DELETE_THRESHOLD = "trackpad_delete_swipe_threshold"
    const val KEYBOARD_SWIPE_ADD_WORD = "trackpad_gesture_add_word_enabled"
    const val KEYBOARD_SWIPE_ADD_WORD_FULL_WIDTH = "trackpad_gesture_add_word_full_width_enabled"
    const val SWIPE_TO_DELETE_PROVIDER = "swipe_to_delete_provider"

    // SS2.10 device
    const val SMART_BACKLIGHT = "smart_backlight_enabled"
    const val RING_ENABLED = "notification_ring_enabled"
    const val RING_MINUTES = "notification_ring_minutes"
    const val RING_BRIGHTNESS = "notification_ring_brightness"
    const val RING_ICONS = "notification_ring_icons"
    const val RING_KEYBOARD_DARK = "notification_ring_keyboard_dark"
    const val RING_DEFAULT_COLOR = "notification_ring_default_color"
    const val RING_APP_COLORS = "notification_ring_app_colors"
    const val RING_CX = "notification_ring_cx"
    const val RING_CY = "notification_ring_cy"
    const val RING_RADIUS = "notification_ring_radius"
    const val RING_STROKE = "notification_ring_stroke"

    // SS2.12 expansion and launcher
    const val SNIPPETS_ENABLED = "snippets_enabled"
    const val SNIPPETS_PREFIX = "snippets_prefix"
    const val SNIPPETS = "snippets_v1"
    const val SNIPPETS_PRESENTATION = "snippets_presentation"
    const val SNIPPETS_EXACT_ON_SPACE = "snippets_exact_on_space"
    const val SNIPPETS_PREFIX_WITH_SPACE = "snippets_accept_prefix_with_space"
    const val SNIPPETS_ACCEPT_TAB = "snippets_accept_with_tab"
    const val SNIPPETS_ACCEPT_ENTER = "snippets_accept_with_enter"
    const val CLIPBOARD_HISTORY = "clipboard_history_enabled"
    const val CLIPBOARD_RETENTION = "clipboard_retention_time"
    const val LAUNCHER_BEHAVIOR = "quick_launcher_behavior"
    const val LAUNCHER_AUTO_START_SINGLE = "quick_launcher_auto_start_single"
    const val LAUNCHER_LIMIT_RESULTS = "quick_launcher_limit_results"
    const val LAUNCHER_RESPECT_LAYOUT = "quick_launcher_respect_keyboard_layout"
    const val LAUNCHER_TYPO_TOLERANT = "quick_launcher_typo_tolerant_ranking"
    const val POWER_SHORTCUTS = "power_shortcuts_enabled"
    const val LAUNCHER_SHORTCUTS_ENABLED = "launcher_shortcuts_enabled"
    const val LAUNCHER_SHORTCUTS = "launcher_shortcuts"
    const val LAUNCHER_COMMAND_CUSTOMIZATIONS = "quick_launcher_command_customizations"
    const val COMMAND_SURFACE_SOURCES = "command_surface_sources"
    const val QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT = "quick_launcher_static_top_highlight"
    const val QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR = "quick_launcher_static_top_highlight_color"

    // SS2.13 feedback
    const val TAP_HAPTIC_USE_SYSTEM = "tap_haptic_use_system"
    const val TAP_HAPTIC_DURATION = "tap_haptic_duration_ms"
    const val TYPING_SOUND_MODE = "typing_sound_mode"
    const val TYPING_SOUND_OUTPUT_MODE = "typing_sound_output_mode"

    // SS2.17 privacy (3.0's own rows)
    const val PRIVATE_MODE = "private_mode"
    const val CLEAN_LINKS = "clean_links"

    // SS2.15 shell
    const val TUTORIAL_COMPLETED = "tutorial_completed"
    const val LAST_SEEN_WHATS_NEW = "last_seen_whats_new_version"
    const val DISMISSED_RELEASES = "dismissed_releases"
    const val UNTESTED_DEVICE_NOTICE_SEEN = "untested_device_notice_seen"
    const val BASELINE_VERSION = "settings_baseline_version"

    // captures
    const val FN_CTRL_PREV_CAPTURED = "fn_ctrl_prev_captured"
    const val FN_CTRL_PREV_ENABLE = "fn_ctrl_prev_enable"
    const val FN_CTRL_PREV_FUNCTION = "fn_ctrl_prev_function"
    const val SIDE_KEY_ORIGINAL_CAPTURED = "side_key_original_captured"
    const val SIDE_KEY_ORIGINAL_PACKAGE = "side_key_original_package"
    const val SIDE_KEY_ORIGINAL_ACTIVITY = "side_key_original_activity"
    const val QS_BACKLIGHT_PREV_CAPTURED = "qs_backlight_prev_captured"
    const val QS_BACKLIGHT_PREV = "qs_backlight_prev"
    const val RING_BACKLIGHT_PREV_CAPTURED = "ring_backlight_prev_captured"
    const val RING_BACKLIGHT_PREV = "ring_backlight_prev"
    const val SMART_BACKLIGHT_APPLIED = "smart_backlight_applied"
}

/**
 * [Settings] to and from a flat `key -> string` map, so the storage layer is a dumb string
 * store and every shape rule is testable here. spec: settings-catalog.md SS13 ("one preference
 * file with typed rows"); the typing of each row is SS2's "Type" column, re-applied on read.
 *
 * [fromMap] never throws: a missing, malformed or out-of-range value reads as that field's
 * default (SS3.2's rule for JSON rows, SS2's "clamped on read" for ranges, and the enum rows'
 * "anything else reads as ..." fallbacks). Unknown keys are ignored, which is how a store written
 * by a newer build stays readable.
 */
object SettingsCodec {

    fun toMap(settings: Settings): Map<String, String> = buildMap {
        put(SettingsKeys.SCHEMA_VERSION, Settings.SCHEMA_VERSION.toString())
        writeTyping(settings.typing)
        writeCorrection(settings.correction)
        writeLanguages(settings.languages)
        writeKeys(settings.keys)
        writeSymPages(settings.symPages)
        writeStatusBar(settings.statusBar)
        writePerApp(settings.perApp)
        writeDictation(settings.dictation)
        writeTrackpad(settings.trackpad)
        writeKeyboardSwipe(settings.keyboardSwipe)
        writeDevice(settings.device)
        writeExpansion(settings.expansion)
        writeLauncher(settings.launcher)
        writeFeedback(settings.feedback)
        writePrivacy(settings.privacy)
        writeShell(settings.shell)
        writeCaptures(settings.captures)
    }

    fun fromMap(map: Map<String, String>): Settings {
        val r = FlatReader(map)
        return Settings(
            typing = readTyping(r),
            correction = readCorrection(r),
            languages = readLanguages(r),
            keys = readKeys(r),
            symPages = readSymPages(r),
            statusBar = readStatusBar(r),
            perApp = readPerApp(r),
            dictation = readDictation(r),
            trackpad = readTrackpad(r),
            keyboardSwipe = readKeyboardSwipe(r),
            device = readDevice(r),
            expansion = readExpansion(r),
            launcher = readLauncher(r),
            feedback = readFeedback(r),
            privacy = readPrivacy(r),
            shell = readShell(r),
            captures = readCaptures(r),
        )
    }

    /** The schema number a map was written under, or null when the map never carried one (a 2.x-shaped or empty map). */
    fun schemaVersionOf(map: Map<String, String>): Int? = map[SettingsKeys.SCHEMA_VERSION]?.toIntOrNull()

    // ---------------------------------------------------------------------------------------------
    // Typing (SS2.1)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeTyping(t: TypingPrefs) {
        put(SettingsKeys.AUTO_CAP_FIRST, t.capitalizeAtTextStart.toString())
        put(SettingsKeys.AUTO_CAP_AFTER_PERIOD, t.capitalizeAfterSentenceEnd.toString())
        put(SettingsKeys.AUTO_CAP_RESTRICTED, t.capitalizeRestrictedFields.toString())
        put(SettingsKeys.DOUBLE_SPACE_PERIOD, t.doubleSpaceToPeriod.toString())
        put(SettingsKeys.CLEAR_ALT_ON_SPACE, t.clearAltOnSpace.toString())
        put(SettingsKeys.SHIFT_BACKSPACE_DELETE, t.shiftBackspaceDeletesForward.toString())
        put(SettingsKeys.ALT_BACKSPACE_DELETE, t.altBackspaceDeletesForward.toString())
        put(SettingsKeys.BACKSPACE_AT_START_DELETE, t.backspaceAtStartDeletesForward.toString())
        put(SettingsKeys.AUTO_SPACE_PUNCTUATION, t.removeSpaceBefore)
        put(SettingsKeys.SPACE_AFTER_PUNCTUATION, t.spaceBeforeNextText)
        put(SettingsKeys.COMMA_SPACE, t.commaSpace.toString())
        put(SettingsKeys.HYPHEN_TO_DASH, t.spacedHyphenToDash.toString())
        put(SettingsKeys.DASH_STYLE, StoredValues.dashStyle(t.dashStyle))
        put(SettingsKeys.SMART_QUOTES, t.smartQuotes.toString())
        put(SettingsKeys.SMART_QUOTES_STYLE, StoredValues.smartQuoteStyle(t.smartQuoteStyle))
        put(SettingsKeys.FRENCH_SPACING, t.frenchPunctuationSpacing.toString())
        put(SettingsKeys.FRENCH_SPACING_ONLY_FRENCH, t.frenchPunctuationOnlyFrench.toString())
        put(SettingsKeys.SWIPE_TO_DELETE, t.swipeToDelete.toString())
    }

    private fun readTyping(r: FlatReader): TypingPrefs {
        val d = TypingPrefs()
        return TypingPrefs(
            capitalizeAtTextStart = r.bool(SettingsKeys.AUTO_CAP_FIRST, d.capitalizeAtTextStart),
            capitalizeAfterSentenceEnd = r.bool(SettingsKeys.AUTO_CAP_AFTER_PERIOD, d.capitalizeAfterSentenceEnd),
            capitalizeRestrictedFields = r.bool(SettingsKeys.AUTO_CAP_RESTRICTED, d.capitalizeRestrictedFields),
            doubleSpaceToPeriod = r.bool(SettingsKeys.DOUBLE_SPACE_PERIOD, d.doubleSpaceToPeriod),
            clearAltOnSpace = r.bool(SettingsKeys.CLEAR_ALT_ON_SPACE, d.clearAltOnSpace),
            shiftBackspaceDeletesForward = r.bool(SettingsKeys.SHIFT_BACKSPACE_DELETE, d.shiftBackspaceDeletesForward),
            altBackspaceDeletesForward = r.bool(SettingsKeys.ALT_BACKSPACE_DELETE, d.altBackspaceDeletesForward),
            backspaceAtStartDeletesForward = r.bool(SettingsKeys.BACKSPACE_AT_START_DELETE, d.backspaceAtStartDeletesForward),
            removeSpaceBefore = StoredValues.punctuationSubset(r.string(SettingsKeys.AUTO_SPACE_PUNCTUATION)),
            spaceBeforeNextText = StoredValues.punctuationSubset(r.string(SettingsKeys.SPACE_AFTER_PUNCTUATION)),
            commaSpace = r.bool(SettingsKeys.COMMA_SPACE, d.commaSpace),
            spacedHyphenToDash = r.bool(SettingsKeys.HYPHEN_TO_DASH, d.spacedHyphenToDash),
            dashStyle = StoredValues.dashStyle(r.string(SettingsKeys.DASH_STYLE)),
            smartQuotes = r.bool(SettingsKeys.SMART_QUOTES, d.smartQuotes),
            smartQuoteStyle = StoredValues.smartQuoteStyle(r.string(SettingsKeys.SMART_QUOTES_STYLE)),
            frenchPunctuationSpacing = r.bool(SettingsKeys.FRENCH_SPACING, d.frenchPunctuationSpacing),
            frenchPunctuationOnlyFrench = r.bool(SettingsKeys.FRENCH_SPACING_ONLY_FRENCH, d.frenchPunctuationOnlyFrench),
            swipeToDelete = r.bool(SettingsKeys.SWIPE_TO_DELETE, d.swipeToDelete),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Correction (SS2.2)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeCorrection(c: CorrectionPrefs) {
        put(SettingsKeys.AUTO_CORRECT_ENABLED, c.textReplacementsEnabled.toString())
        put(SettingsKeys.AUTO_CORRECT_LANGUAGES, c.textReplacementLanguages.joinToString(","))
        for ((code, set) in c.customSubstitutions) {
            put(SettingsKeys.AUTO_CORRECT_CUSTOM_PREFIX + code, JsonRows.encode(StoredValues.substitutionSet(set)))
        }
        put(SettingsKeys.AUTO_REPLACE_ON_SPACE_ENTER, c.autoReplaceOnSpaceEnter.toString())
        put(SettingsKeys.MAX_AUTO_REPLACE_DISTANCE, c.maxAutoReplaceDistance.toString())
        put(SettingsKeys.SUGGESTIONS_ENABLED, c.suggestionsEnabled.toString())
        put(SettingsKeys.ACCENT_MATCHING, c.accentMatching.toString())
        put(SettingsKeys.KEYBOARD_PROXIMITY, c.useKeyboardProximity.toString())
        put(SettingsKeys.FIX_WORD_MIXUPS, c.fixWordMixups.toString())
    }

    private fun readCorrection(r: FlatReader): CorrectionPrefs {
        val d = CorrectionPrefs()
        val substitutions = r.keysWithPrefix(SettingsKeys.AUTO_CORRECT_CUSTOM_PREFIX).mapNotNull { key ->
            val code = key.removePrefix(SettingsKeys.AUTO_CORRECT_CUSTOM_PREFIX)
            if (code.isBlank()) return@mapNotNull null
            StoredValues.substitutionSet(JsonRows.parseObject(r.string(key)))?.let { code to it }
        }.toMap()
        return CorrectionPrefs(
            textReplacementsEnabled = r.bool(SettingsKeys.AUTO_CORRECT_ENABLED, d.textReplacementsEnabled),
            textReplacementLanguages = StoredValues.languageList(r.string(SettingsKeys.AUTO_CORRECT_LANGUAGES)),
            customSubstitutions = substitutions,
            autoReplaceOnSpaceEnter = r.bool(SettingsKeys.AUTO_REPLACE_ON_SPACE_ENTER, d.autoReplaceOnSpaceEnter),
            maxAutoReplaceDistance = r.int(SettingsKeys.MAX_AUTO_REPLACE_DISTANCE, d.maxAutoReplaceDistance, 0..3),
            suggestionsEnabled = r.bool(SettingsKeys.SUGGESTIONS_ENABLED, d.suggestionsEnabled),
            accentMatching = r.bool(SettingsKeys.ACCENT_MATCHING, d.accentMatching),
            useKeyboardProximity = r.bool(SettingsKeys.KEYBOARD_PROXIMITY, d.useKeyboardProximity),
            fixWordMixups = r.bool(SettingsKeys.FIX_WORD_MIXUPS, d.fixWordMixups),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Languages (SS2.3)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeLanguages(l: LanguagePrefs) {
        put(SettingsKeys.KEYBOARD_LAYOUT, l.keyboardLayout)
        put(SettingsKeys.LAYOUT_AUTO_BY_LOCALE, l.layoutAutoByLocale.toString())
        put(SettingsKeys.ALT_SHIFT_LAYOUT_SWITCH, l.altShiftLayoutSwitch.toString())
        put(SettingsKeys.ALT_ENTER_LAYOUT_SWITCH, l.altEnterLayoutSwitch.toString())
        put(SettingsKeys.CTRL_SPACE_LAYOUT_SWITCH, l.ctrlSpaceLayoutSwitch.toString())
        put(SettingsKeys.TOAST_ON_LAYOUT_SWITCH, l.toastOnLayoutSwitch.toString())
        put(SettingsKeys.CUSTOM_INPUT_STYLES, l.inputStyles.joinToString(";"))
        put(SettingsKeys.INPUT_STYLE_SUGGESTION_LOCALES, JsonRows.encode(JsonObject(l.suggestionLocales.mapValues { JsonRows.stringListOf(it.value) })))
        put(SettingsKeys.HIDDEN_SYSTEM_INPUT_STYLES, JsonRows.encode(JsonRows.stringListOf(l.hiddenSystemInputStyles)))
        put(SettingsKeys.APP_LANGUAGE_TAG, l.appLanguageTag)
    }

    private fun readLanguages(r: FlatReader): LanguagePrefs {
        val d = LanguagePrefs()
        val suggestionLocales = JsonRows.parseObject(r.string(SettingsKeys.INPUT_STYLE_SUGGESTION_LOCALES))
            ?.entries?.mapNotNull { (k, v) -> JsonRows.stringList(v)?.let { k to it } }?.toMap()
        return LanguagePrefs(
            keyboardLayout = r.string(SettingsKeys.KEYBOARD_LAYOUT)?.takeIf { it.isNotBlank() } ?: d.keyboardLayout,
            layoutAutoByLocale = r.bool(SettingsKeys.LAYOUT_AUTO_BY_LOCALE, d.layoutAutoByLocale),
            altShiftLayoutSwitch = r.bool(SettingsKeys.ALT_SHIFT_LAYOUT_SWITCH, d.altShiftLayoutSwitch),
            altEnterLayoutSwitch = r.bool(SettingsKeys.ALT_ENTER_LAYOUT_SWITCH, d.altEnterLayoutSwitch),
            ctrlSpaceLayoutSwitch = r.bool(SettingsKeys.CTRL_SPACE_LAYOUT_SWITCH, d.ctrlSpaceLayoutSwitch),
            toastOnLayoutSwitch = r.bool(SettingsKeys.TOAST_ON_LAYOUT_SWITCH, d.toastOnLayoutSwitch),
            inputStyles = r.string(SettingsKeys.CUSTOM_INPUT_STYLES)?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.inputStyles,
            suggestionLocales = suggestionLocales ?: d.suggestionLocales,
            hiddenSystemInputStyles = JsonRows.stringList(JsonRows.parse(r.string(SettingsKeys.HIDDEN_SYSTEM_INPUT_STYLES))) ?: d.hiddenSystemInputStyles,
            appLanguageTag = r.string(SettingsKeys.APP_LANGUAGE_TAG)?.trim() ?: d.appLanguageTag,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Keys (SS2.4)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeKeys(k: KeyPrefs) {
        put(SettingsKeys.LONG_PRESS_MODE, StoredValues.longPressMode(k.longPressMode))
        put(SettingsKeys.LONG_PRESS_THRESHOLD, k.longPressThresholdMs.toString())
        put(SettingsKeys.LONG_PRESS_VARIATION_CHOOSER, k.variationChooser.toString())
        put(SettingsKeys.CUSTOM_VARIATIONS, JsonRows.encode(StoredValues.customVariations(k.customVariations)))
        put(SettingsKeys.NAV_MODE_ENABLED, k.navModeEnabled.toString())
        put(SettingsKeys.NAV_MODE_CTRL_HOLD, k.navModeCtrlHoldEnabled.toString())
        put(SettingsKeys.LAYOUT_AWARE_CTRL, k.layoutAwareCtrlShortcuts.toString())
        put(SettingsKeys.SYM_EDIT_SHORTCUTS, k.symEditShortcuts.toString())
        put(SettingsKeys.NAV_MODE_MAPPINGS_UPDATED, k.navModeMappingsUpdatedAtMs.toString())
        put(SettingsKeys.NAV_MODE_DEFAULT_MAPPINGS_VERSION, k.navModeDefaultMappingsVersion.toString())
        put(SettingsKeys.BOUNCE_KEYS_ENABLED, k.bounceKeysEnabled.toString())
        put(SettingsKeys.BOUNCE_KEYS_DELAY_MS, k.bounceKeysDelayMs.toString())
        put(SettingsKeys.BOUNCE_KEYS_CHARACTER_KEYS_ENABLED, k.bounceKeysCharacterKeysEnabled.toString())
        put(SettingsKeys.BOUNCE_KEYS_MODIFIER_KEYS_ENABLED, k.bounceKeysModifierKeysEnabled.toString())
        put(SettingsKeys.BOUNCE_KEYS_SPACE_ENABLED, k.bounceKeysSpaceEnabled.toString())
        put(SettingsKeys.BOUNCE_KEYS_ENTER_ENABLED, k.bounceKeysEnterEnabled.toString())
        put(SettingsKeys.BOUNCE_KEYS_BACKSPACE_ENABLED, k.bounceKeysBackspaceEnabled.toString())
        put(SettingsKeys.OVERLAPPING_KEYS_ENABLED, k.overlappingKeysEnabled.toString())
    }

    private fun readKeys(r: FlatReader): KeyPrefs {
        val d = KeyPrefs()
        return KeyPrefs(
            longPressMode = StoredValues.longPressMode(r.string(SettingsKeys.LONG_PRESS_MODE)),
            longPressThresholdMs = r.long(SettingsKeys.LONG_PRESS_THRESHOLD, d.longPressThresholdMs, 50L..1000L),
            variationChooser = r.bool(SettingsKeys.LONG_PRESS_VARIATION_CHOOSER, d.variationChooser),
            customVariations = StoredValues.customVariations(JsonRows.parseObject(r.string(SettingsKeys.CUSTOM_VARIATIONS))) ?: d.customVariations,
            navModeEnabled = r.bool(SettingsKeys.NAV_MODE_ENABLED, d.navModeEnabled),
            navModeCtrlHoldEnabled = r.bool(SettingsKeys.NAV_MODE_CTRL_HOLD, d.navModeCtrlHoldEnabled),
            layoutAwareCtrlShortcuts = r.bool(SettingsKeys.LAYOUT_AWARE_CTRL, d.layoutAwareCtrlShortcuts),
            symEditShortcuts = r.bool(SettingsKeys.SYM_EDIT_SHORTCUTS, d.symEditShortcuts),
            navModeMappingsUpdatedAtMs = r.long(SettingsKeys.NAV_MODE_MAPPINGS_UPDATED, d.navModeMappingsUpdatedAtMs),
            navModeDefaultMappingsVersion = r.int(SettingsKeys.NAV_MODE_DEFAULT_MAPPINGS_VERSION, d.navModeDefaultMappingsVersion),
            bounceKeysEnabled = r.bool(SettingsKeys.BOUNCE_KEYS_ENABLED, d.bounceKeysEnabled),
            bounceKeysDelayMs = r.long(SettingsKeys.BOUNCE_KEYS_DELAY_MS, d.bounceKeysDelayMs, 20L..500L),
            bounceKeysCharacterKeysEnabled = r.bool(SettingsKeys.BOUNCE_KEYS_CHARACTER_KEYS_ENABLED, d.bounceKeysCharacterKeysEnabled),
            bounceKeysModifierKeysEnabled = r.bool(SettingsKeys.BOUNCE_KEYS_MODIFIER_KEYS_ENABLED, d.bounceKeysModifierKeysEnabled),
            bounceKeysSpaceEnabled = r.bool(SettingsKeys.BOUNCE_KEYS_SPACE_ENABLED, d.bounceKeysSpaceEnabled),
            bounceKeysEnterEnabled = r.bool(SettingsKeys.BOUNCE_KEYS_ENTER_ENABLED, d.bounceKeysEnterEnabled),
            bounceKeysBackspaceEnabled = r.bool(SettingsKeys.BOUNCE_KEYS_BACKSPACE_ENABLED, d.bounceKeysBackspaceEnabled),
            overlappingKeysEnabled = r.bool(SettingsKeys.OVERLAPPING_KEYS_ENABLED, d.overlappingKeysEnabled),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Sym pages (SS2.5)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeSymPages(s: SymPagePrefs) {
        put(SettingsKeys.SYM_PAGES_CONFIG, JsonRows.encode(StoredValues.symPagesConfig(s.pages)))
        put(SettingsKeys.SYM_MAPPINGS_CUSTOM, JsonRows.encode(StoredValues.symMappings(s.customEmojiPage)))
        put(SettingsKeys.SYM_MAPPINGS_PAGE2_CUSTOM, JsonRows.encode(StoredValues.symMappings(s.customSymbolsPage)))
        put(SettingsKeys.SYM_CUSTOM_PAGES, JsonRows.encode(StoredValues.customSymPages(s.customPages)))
        put(SettingsKeys.SYM_AUTO_CLOSE, s.autoClose.toString())
        put(SettingsKeys.SYM_AUTO_CLOSE_ON_TOUCH, s.autoCloseOnTouch.toString())
        put(SettingsKeys.SYM_DOUBLE_TAP_CHOOSER, s.doubleTapChooser.toString())
        put(SettingsKeys.EMOJI_PICKER_EXPANDED, s.emojiPickerExpandedHeight.toString())
        put(SettingsKeys.EMOJI_PICKER_KAOMOJI, s.kaomojiEnabled.toString())
        put(SettingsKeys.EMOJI_DEFAULT_SKIN_TONE, s.defaultSkinTone.storedValue)
        put(SettingsKeys.RESTORE_SYM_PAGE, s.restoreSymPage.toString())
        put(SettingsKeys.PENDING_RESTORE_SYM_PAGE, s.pendingRestoreSymPage.toString())
    }

    private fun readSymPages(r: FlatReader): SymPagePrefs {
        val d = SymPagePrefs()
        return SymPagePrefs(
            pages = StoredValues.symPagesConfig(JsonRows.parseObject(r.string(SettingsKeys.SYM_PAGES_CONFIG))) ?: d.pages,
            customEmojiPage = StoredValues.symMappings(JsonRows.parseObject(r.string(SettingsKeys.SYM_MAPPINGS_CUSTOM))) ?: d.customEmojiPage,
            customSymbolsPage = StoredValues.symMappings(JsonRows.parseObject(r.string(SettingsKeys.SYM_MAPPINGS_PAGE2_CUSTOM))) ?: d.customSymbolsPage,
            customPages = StoredValues.customSymPages(JsonRows.parseObject(r.string(SettingsKeys.SYM_CUSTOM_PAGES))) ?: d.customPages,
            autoClose = r.bool(SettingsKeys.SYM_AUTO_CLOSE, d.autoClose),
            autoCloseOnTouch = r.bool(SettingsKeys.SYM_AUTO_CLOSE_ON_TOUCH, d.autoCloseOnTouch),
            doubleTapChooser = r.bool(SettingsKeys.SYM_DOUBLE_TAP_CHOOSER, d.doubleTapChooser),
            emojiPickerExpandedHeight = r.bool(SettingsKeys.EMOJI_PICKER_EXPANDED, d.emojiPickerExpandedHeight),
            kaomojiEnabled = r.bool(SettingsKeys.EMOJI_PICKER_KAOMOJI, d.kaomojiEnabled),
            defaultSkinTone = SkinTone.fromStored(r.string(SettingsKeys.EMOJI_DEFAULT_SKIN_TONE)),
            restoreSymPage = r.int(SettingsKeys.RESTORE_SYM_PAGE, d.restoreSymPage),
            pendingRestoreSymPage = r.int(SettingsKeys.PENDING_RESTORE_SYM_PAGE, d.pendingRestoreSymPage),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Status bar (SS2.6)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeStatusBar(s: StatusBarPrefs) {
        put(SettingsKeys.STATUS_BAR_VISIBILITY, s.visibility.storedValue)
        put(SettingsKeys.STATUS_BAR_APPS, JsonRows.encode(JsonRows.stringListOf(s.apps.sorted())))
        put(SettingsKeys.STATUS_BAR_HEIGHT, s.heightDp.toString())
        put(SettingsKeys.STATUS_BAR_SLOTS_LEFT, JsonRows.encode(JsonRows.stringListOf(s.leftButtons.map { it.id })))
        put(SettingsKeys.STATUS_BAR_SLOTS_RIGHT, JsonRows.encode(JsonRows.stringListOf(s.rightButtons.map { it.id })))
        put(SettingsKeys.CARET_BADGE, s.caretModifierBadge.toString())
        put(SettingsKeys.CARET_BADGE_ARMED_COLOR, s.caretBadgeArmedColor.toString())
        put(SettingsKeys.CARET_BADGE_LOCKED_COLOR, s.caretBadgeLockedColor.toString())
        put(SettingsKeys.THEME, JsonRows.encode(StoredValues.theme(s.theme)))
        put(SettingsKeys.SAVED_THEMES, JsonRows.encode(JsonArray(s.savedThemes.map { StoredValues.namedTheme(it) })))
        put(SettingsKeys.LAYOUT_OVERRIDES, JsonRows.encode(StoredValues.layoutOverrides(s.layoutOverrides)))
        put(SettingsKeys.ROUNDED_CORNER_INSETS, s.roundedCornerInsets.toString())
        put(SettingsKeys.HIDE_STRIP_WHERE_NOTHING_TO_SUGGEST, s.hideWhereNothingToSuggest.toString())
        put(SettingsKeys.ACCESSIBILITY_LIVE_ANNOUNCEMENTS, s.accessibilityLiveAnnouncementsEnabled.toString())
        put(SettingsKeys.ACCESSIBILITY_ANNOUNCEMENT_DELAY_MS, s.accessibilitySuggestionsAnnouncementDelayMs.toString())
        put(SettingsKeys.OVERLAY_DEBUG_LOGGING, s.overlayDebugLoggingEnabled.toString())
    }

    private fun readStatusBar(r: FlatReader): StatusBarPrefs {
        val d = StatusBarPrefs()
        return StatusBarPrefs(
            visibility = StatusBarVisibility.fromStored(r.string(SettingsKeys.STATUS_BAR_VISIBILITY)) ?: d.visibility,
            apps = r.stringSet(SettingsKeys.STATUS_BAR_APPS) ?: d.apps,
            heightDp = r.int(SettingsKeys.STATUS_BAR_HEIGHT, d.heightDp, 1..Int.MAX_VALUE),
            leftButtons = StoredValues.buttons(r.string(SettingsKeys.STATUS_BAR_SLOTS_LEFT)) ?: d.leftButtons,
            rightButtons = StoredValues.buttons(r.string(SettingsKeys.STATUS_BAR_SLOTS_RIGHT)) ?: d.rightButtons,
            caretModifierBadge = r.bool(SettingsKeys.CARET_BADGE, d.caretModifierBadge),
            caretBadgeArmedColor = r.int(SettingsKeys.CARET_BADGE_ARMED_COLOR, d.caretBadgeArmedColor),
            caretBadgeLockedColor = r.int(SettingsKeys.CARET_BADGE_LOCKED_COLOR, d.caretBadgeLockedColor),
            theme = StoredValues.theme(JsonRows.parseObject(r.string(SettingsKeys.THEME))) ?: d.theme,
            savedThemes = StoredValues.namedThemes(JsonRows.parseArray(r.string(SettingsKeys.SAVED_THEMES))) ?: d.savedThemes,
            layoutOverrides = StoredValues.layoutOverrides(JsonRows.parseArray(r.string(SettingsKeys.LAYOUT_OVERRIDES))) ?: d.layoutOverrides,
            roundedCornerInsets = r.bool(SettingsKeys.ROUNDED_CORNER_INSETS, d.roundedCornerInsets),
            hideWhereNothingToSuggest = r.bool(SettingsKeys.HIDE_STRIP_WHERE_NOTHING_TO_SUGGEST, d.hideWhereNothingToSuggest),
            accessibilityLiveAnnouncementsEnabled = r.bool(SettingsKeys.ACCESSIBILITY_LIVE_ANNOUNCEMENTS, d.accessibilityLiveAnnouncementsEnabled),
            // spec SS5.6: "never negative".
            accessibilitySuggestionsAnnouncementDelayMs = r.long(SettingsKeys.ACCESSIBILITY_ANNOUNCEMENT_DELAY_MS, d.accessibilitySuggestionsAnnouncementDelayMs, 0..Long.MAX_VALUE),
            overlayDebugLoggingEnabled = r.bool(SettingsKeys.OVERLAY_DEBUG_LOGGING, d.overlayDebugLoggingEnabled),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Per app (SS2.7)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writePerApp(p: PerAppPrefs) {
        put(SettingsKeys.RAW_MODE_PACKAGES, JsonRows.encode(JsonRows.stringListOf(p.exactTypingPackages.sorted())))
        put(SettingsKeys.NUDGE_PACKAGES, JsonRows.encode(JsonRows.stringListOf(p.nudgePackages.sorted())))
        put(SettingsKeys.ENTER_BEHAVIOR_ENABLED, p.enterBehaviorEnabled.toString())
        put(SettingsKeys.ENTER_PRESET, StoredValues.messagingPreset(p.enterPreset))
        put(SettingsKeys.ENTER_OVERRIDES, JsonRows.encode(StoredValues.enterOverrides(p.enterOverrides)))
    }

    private fun readPerApp(r: FlatReader): PerAppPrefs {
        val d = PerAppPrefs()
        return PerAppPrefs(
            exactTypingPackages = r.stringSet(SettingsKeys.RAW_MODE_PACKAGES) ?: d.exactTypingPackages,
            nudgePackages = r.stringSet(SettingsKeys.NUDGE_PACKAGES) ?: d.nudgePackages,
            enterBehaviorEnabled = r.bool(SettingsKeys.ENTER_BEHAVIOR_ENABLED, d.enterBehaviorEnabled),
            enterPreset = r.string(SettingsKeys.ENTER_PRESET)?.let { StoredValues.messagingPreset(it) } ?: d.enterPreset,
            enterOverrides = StoredValues.enterOverrides(JsonRows.parseArray(r.string(SettingsKeys.ENTER_OVERRIDES))) ?: d.enterOverrides,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Dictation (SS2.8)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeDictation(x: DictationPrefs) {
        put(SettingsKeys.FN_LONG_PRESS_SPEECH, x.fnLongPressSpeech.toString())
        put(SettingsKeys.DICTATION_HAPTICS, x.haptics.toString())
        put(SettingsKeys.DICTATION_HAPTIC_STRENGTH, x.hapticStrength.storedValue)
        put(SettingsKeys.DICTATION_STOP_AFTER_SILENCE, x.stopAfterSilenceMs.toString())
        put(SettingsKeys.DICTATION_MASK_OFFENSIVE, x.maskOffensive.toString())
        put(SettingsKeys.DICTATION_ENGINE, x.engine)
        put(SettingsKeys.DICTATION_PREFER_OFFLINE, x.preferOffline.toString())
        put(SettingsKeys.DICTATION_PAUSE_MEDIA, x.pauseMedia.toString())
        put(SettingsKeys.DICTATION_STOP_ON_TYPING, x.stopOnTyping.toString())
        put(SettingsKeys.DICTATION_AUTO_PUNCTUATION, x.autoPunctuation.toString())
        put(SettingsKeys.SYM_LONG_PRESS_ASSISTANT, x.symLongPressAssistant.toString())
        put(SettingsKeys.SIDE_KEY_ASSISTANT, x.sideKeyAssistant.toString())
        put(SettingsKeys.ASSISTANT_ACTION, x.assistantAction.storedValue)
    }

    private fun readDictation(r: FlatReader): DictationPrefs {
        val d = DictationPrefs()
        return DictationPrefs(
            fnLongPressSpeech = r.bool(SettingsKeys.FN_LONG_PRESS_SPEECH, d.fnLongPressSpeech),
            haptics = r.bool(SettingsKeys.DICTATION_HAPTICS, d.haptics),
            hapticStrength = HapticStrength.fromStored(r.string(SettingsKeys.DICTATION_HAPTIC_STRENGTH)) ?: d.hapticStrength,
            stopAfterSilenceMs = r.int(SettingsKeys.DICTATION_STOP_AFTER_SILENCE, d.stopAfterSilenceMs, 0..60000),
            maskOffensive = r.bool(SettingsKeys.DICTATION_MASK_OFFENSIVE, d.maskOffensive),
            engine = r.string(SettingsKeys.DICTATION_ENGINE)?.trim() ?: d.engine,
            preferOffline = r.bool(SettingsKeys.DICTATION_PREFER_OFFLINE, d.preferOffline),
            pauseMedia = r.bool(SettingsKeys.DICTATION_PAUSE_MEDIA, d.pauseMedia),
            stopOnTyping = r.bool(SettingsKeys.DICTATION_STOP_ON_TYPING, d.stopOnTyping),
            autoPunctuation = r.bool(SettingsKeys.DICTATION_AUTO_PUNCTUATION, d.autoPunctuation),
            symLongPressAssistant = r.bool(SettingsKeys.SYM_LONG_PRESS_ASSISTANT, d.symLongPressAssistant),
            sideKeyAssistant = r.bool(SettingsKeys.SIDE_KEY_ASSISTANT, d.sideKeyAssistant),
            assistantAction = AssistantAction.fromStored(r.string(SettingsKeys.ASSISTANT_ACTION)) ?: d.assistantAction,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Trackpad (SS2.9)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeTrackpad(t: TrackpadPrefs) {
        put(SettingsKeys.TRACKPAD_ENABLED, t.enabled.toString())
        put(SettingsKeys.TRACKPAD_TRIGGER, t.triggerKey.preferenceValue)
        put(SettingsKeys.TRACKPAD_ACTIVATION, t.activation.preferenceValue)
        put(SettingsKeys.TRACKPAD_STEP, t.stepPx.toString())
        put(SettingsKeys.TRACKPAD_HINT, t.showHint.toString())
    }

    private fun readTrackpad(r: FlatReader): TrackpadPrefs {
        val d = TrackpadPrefs()
        return TrackpadPrefs(
            enabled = r.bool(SettingsKeys.TRACKPAD_ENABLED, d.enabled),
            triggerKey = TriggerKey.fromPreference(r.string(SettingsKeys.TRACKPAD_TRIGGER)),
            activation = ActivationMode.fromPreference(r.string(SettingsKeys.TRACKPAD_ACTIVATION)),
            stepPx = r.int(SettingsKeys.TRACKPAD_STEP, d.stepPx, 8..64),
            showHint = r.bool(SettingsKeys.TRACKPAD_HINT, d.showHint),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Keyboard-surface swipe (SS3.7)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeKeyboardSwipe(k: KeyboardSwipePrefs) {
        put(SettingsKeys.KEYBOARD_SWIPE_ENABLED, k.gesturesEnabled.toString())
        put(SettingsKeys.KEYBOARD_SWIPE_PROVIDER, k.provider.preferenceValue)
        put(SettingsKeys.KEYBOARD_SWIPE_THRESHOLD, k.swipeThresholdPx.toString())
        k.suggestionSwipeThresholdPx?.let { put(SettingsKeys.KEYBOARD_SWIPE_SUGGESTION_THRESHOLD, it.toString()) }
        k.deleteSwipeThresholdPx?.let { put(SettingsKeys.KEYBOARD_SWIPE_DELETE_THRESHOLD, it.toString()) }
        put(SettingsKeys.KEYBOARD_SWIPE_ADD_WORD, k.gestureAddWordEnabled.toString())
        put(SettingsKeys.KEYBOARD_SWIPE_ADD_WORD_FULL_WIDTH, k.gestureAddWordFullWidthEnabled.toString())
        put(SettingsKeys.SWIPE_TO_DELETE_PROVIDER, k.swipeToDeleteProvider.preferenceValue)
    }

    /** spec SS3.7: every threshold is "float 120..750"; [suggestionSwipeThresholdPx]/[deleteSwipeThresholdPx] fall back to the legacy value when unset. */
    private fun readKeyboardSwipe(r: FlatReader): KeyboardSwipePrefs {
        val d = KeyboardSwipePrefs()
        return KeyboardSwipePrefs(
            gesturesEnabled = r.bool(SettingsKeys.KEYBOARD_SWIPE_ENABLED, d.gesturesEnabled),
            provider = TrackpadGestureProvider.fromPreference(r.string(SettingsKeys.KEYBOARD_SWIPE_PROVIDER)),
            swipeThresholdPx = (r.float(SettingsKeys.KEYBOARD_SWIPE_THRESHOLD) ?: d.swipeThresholdPx).coerceIn(120f, 750f),
            suggestionSwipeThresholdPx = r.float(SettingsKeys.KEYBOARD_SWIPE_SUGGESTION_THRESHOLD)?.coerceIn(120f, 750f),
            deleteSwipeThresholdPx = r.float(SettingsKeys.KEYBOARD_SWIPE_DELETE_THRESHOLD)?.coerceIn(120f, 750f),
            gestureAddWordEnabled = r.bool(SettingsKeys.KEYBOARD_SWIPE_ADD_WORD, d.gestureAddWordEnabled),
            gestureAddWordFullWidthEnabled = r.bool(SettingsKeys.KEYBOARD_SWIPE_ADD_WORD_FULL_WIDTH, d.gestureAddWordFullWidthEnabled),
            swipeToDeleteProvider = SwipeToDeleteProvider.fromPreference(r.string(SettingsKeys.SWIPE_TO_DELETE_PROVIDER)),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Device (SS2.10)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeDevice(x: DevicePrefs) {
        put(SettingsKeys.SMART_BACKLIGHT, x.smartBacklightEnabled.toString())
        put(SettingsKeys.RING_ENABLED, x.ringEnabled.toString())
        put(SettingsKeys.RING_MINUTES, x.ringMinutes.toString())
        put(SettingsKeys.RING_BRIGHTNESS, x.ringBrightness.storedValue)
        put(SettingsKeys.RING_ICONS, x.ringShowIcons.toString())
        put(SettingsKeys.RING_KEYBOARD_DARK, x.ringKeyboardDark.toString())
        x.ringDefaultColor?.let { put(SettingsKeys.RING_DEFAULT_COLOR, it.toString()) }
        put(SettingsKeys.RING_APP_COLORS, JsonRows.encode(JsonObject(x.ringAppColors.mapValues { JsonPrimitive(it.value) })))
        x.ringFit?.let {
            put(SettingsKeys.RING_CX, it.cx.toString())
            put(SettingsKeys.RING_CY, it.cy.toString())
            put(SettingsKeys.RING_RADIUS, it.radius.toString())
            put(SettingsKeys.RING_STROKE, it.stroke.toString())
        }
    }

    private fun readDevice(r: FlatReader): DevicePrefs {
        val d = DevicePrefs()
        val appColors = JsonRows.parseObject(r.string(SettingsKeys.RING_APP_COLORS))
            ?.entries?.mapNotNull { (k, v) -> v.let { with(JsonRows) { it.asInt() } }?.let { k to it } }?.toMap()
        // spec SS2.10: "the override exists only when `_radius` is present".
        val radius = r.float(SettingsKeys.RING_RADIUS)
        val fit = if (radius == null) {
            if (r.has(SettingsKeys.RING_RADIUS)) null else d.ringFit
        } else {
            RingFit(
                cx = r.float(SettingsKeys.RING_CX) ?: 0f,
                cy = r.float(SettingsKeys.RING_CY) ?: 0f,
                radius = radius,
                stroke = r.float(SettingsKeys.RING_STROKE) ?: 0f,
            )
        }
        return DevicePrefs(
            smartBacklightEnabled = r.bool(SettingsKeys.SMART_BACKLIGHT, d.smartBacklightEnabled),
            ringEnabled = r.bool(SettingsKeys.RING_ENABLED, d.ringEnabled),
            ringMinutes = r.int(SettingsKeys.RING_MINUTES, d.ringMinutes, 1..60),
            ringBrightness = RingBrightness.fromStored(r.string(SettingsKeys.RING_BRIGHTNESS)) ?: d.ringBrightness,
            ringShowIcons = r.bool(SettingsKeys.RING_ICONS, d.ringShowIcons),
            ringKeyboardDark = r.bool(SettingsKeys.RING_KEYBOARD_DARK, d.ringKeyboardDark),
            ringDefaultColor = r.intOrNull(SettingsKeys.RING_DEFAULT_COLOR),
            ringAppColors = appColors ?: d.ringAppColors,
            ringFit = fit,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Expansion (SS2.12)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeExpansion(e: ExpansionPrefs) {
        put(SettingsKeys.SNIPPETS_ENABLED, e.snippetsEnabled.toString())
        put(SettingsKeys.SNIPPETS_PREFIX, e.snippetPrefix)
        put(SettingsKeys.SNIPPETS, JsonRows.encode(JsonRows.stringMapOf(e.snippets)))
        put(SettingsKeys.SNIPPETS_PRESENTATION, e.presentation.storedValue)
        put(SettingsKeys.SNIPPETS_EXACT_ON_SPACE, e.expandExactOnSpace.toString())
        put(SettingsKeys.SNIPPETS_PREFIX_WITH_SPACE, e.acceptPrefixWithSpace.toString())
        put(SettingsKeys.SNIPPETS_ACCEPT_TAB, e.acceptWithTab.toString())
        put(SettingsKeys.SNIPPETS_ACCEPT_ENTER, e.acceptWithEnter.toString())
        put(SettingsKeys.CLIPBOARD_HISTORY, e.clipboardHistoryEnabled.toString())
        put(SettingsKeys.CLIPBOARD_RETENTION, e.clipboardRetentionMinutes.toString())
    }

    private fun readExpansion(r: FlatReader): ExpansionPrefs {
        val d = ExpansionPrefs()
        return ExpansionPrefs(
            snippetsEnabled = r.bool(SettingsKeys.SNIPPETS_ENABLED, d.snippetsEnabled),
            snippetPrefix = StoredValues.snippetPrefix(r.string(SettingsKeys.SNIPPETS_PREFIX)),
            snippets = StoredValues.snippets(JsonRows.parseObject(r.string(SettingsKeys.SNIPPETS))) ?: d.snippets,
            presentation = SnippetPresentation.fromStored(r.string(SettingsKeys.SNIPPETS_PRESENTATION)) ?: d.presentation,
            expandExactOnSpace = r.bool(SettingsKeys.SNIPPETS_EXACT_ON_SPACE, d.expandExactOnSpace),
            acceptPrefixWithSpace = r.bool(SettingsKeys.SNIPPETS_PREFIX_WITH_SPACE, d.acceptPrefixWithSpace),
            acceptWithTab = r.bool(SettingsKeys.SNIPPETS_ACCEPT_TAB, d.acceptWithTab),
            acceptWithEnter = r.bool(SettingsKeys.SNIPPETS_ACCEPT_ENTER, d.acceptWithEnter),
            clipboardHistoryEnabled = r.bool(SettingsKeys.CLIPBOARD_HISTORY, d.clipboardHistoryEnabled),
            clipboardRetentionMinutes = r.long(SettingsKeys.CLIPBOARD_RETENTION, d.clipboardRetentionMinutes, 0L..Long.MAX_VALUE),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Privacy (SS2.17)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writePrivacy(p: PrivacyPrefs) {
        put(SettingsKeys.PRIVATE_MODE, p.privateMode.toString())
        put(SettingsKeys.CLEAN_LINKS, p.cleanLinks.toString())
    }

    private fun readPrivacy(r: FlatReader): PrivacyPrefs {
        val d = PrivacyPrefs()
        return PrivacyPrefs(
            privateMode = r.bool(SettingsKeys.PRIVATE_MODE, d.privateMode),
            cleanLinks = r.bool(SettingsKeys.CLEAN_LINKS, d.cleanLinks),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Launcher (SS2.12, SS2.4, SS2.7)
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeLauncher(l: LauncherPrefs) {
        put(SettingsKeys.LAUNCHER_BEHAVIOR, l.behavior.storedValue)
        put(SettingsKeys.LAUNCHER_AUTO_START_SINGLE, l.openUniqueMatch.toString())
        put(SettingsKeys.LAUNCHER_LIMIT_RESULTS, l.limitResults.toString())
        put(SettingsKeys.LAUNCHER_RESPECT_LAYOUT, l.respectKeyboardLayout.toString())
        put(SettingsKeys.LAUNCHER_TYPO_TOLERANT, l.typoTolerantRanking.toString())
        put(SettingsKeys.POWER_SHORTCUTS, l.symShortcutsEnabled.toString())
        put(SettingsKeys.LAUNCHER_SHORTCUTS_ENABLED, l.homeScreenShortcutsEnabled.toString())
        if (l.assignedKeysJson.isNotBlank()) put(SettingsKeys.LAUNCHER_SHORTCUTS, l.assignedKeysJson)
        if (l.commandCustomizationsJson.isNotBlank()) put(SettingsKeys.LAUNCHER_COMMAND_CUSTOMIZATIONS, l.commandCustomizationsJson)
        if (l.commandSurfaceSourcesJson.isNotBlank()) put(SettingsKeys.COMMAND_SURFACE_SOURCES, l.commandSurfaceSourcesJson)
        put(SettingsKeys.QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT, l.quickLauncherStaticTopHighlight.toString())
        put(SettingsKeys.QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR, l.quickLauncherStaticTopHighlightColor.toString())
    }

    private fun readLauncher(r: FlatReader): LauncherPrefs {
        val d = LauncherPrefs()
        return LauncherPrefs(
            behavior = LauncherBehavior.fromStored(r.string(SettingsKeys.LAUNCHER_BEHAVIOR)) ?: d.behavior,
            openUniqueMatch = r.bool(SettingsKeys.LAUNCHER_AUTO_START_SINGLE, d.openUniqueMatch),
            limitResults = r.bool(SettingsKeys.LAUNCHER_LIMIT_RESULTS, d.limitResults),
            respectKeyboardLayout = r.bool(SettingsKeys.LAUNCHER_RESPECT_LAYOUT, d.respectKeyboardLayout),
            typoTolerantRanking = r.bool(SettingsKeys.LAUNCHER_TYPO_TOLERANT, d.typoTolerantRanking),
            symShortcutsEnabled = r.bool(SettingsKeys.POWER_SHORTCUTS, d.symShortcutsEnabled),
            homeScreenShortcutsEnabled = r.bool(SettingsKeys.LAUNCHER_SHORTCUTS_ENABLED, d.homeScreenShortcutsEnabled),
            assignedKeysJson = StoredValues.jsonObjectText(r.string(SettingsKeys.LAUNCHER_SHORTCUTS)),
            commandCustomizationsJson = StoredValues.jsonObjectText(r.string(SettingsKeys.LAUNCHER_COMMAND_CUSTOMIZATIONS)),
            commandSurfaceSourcesJson = StoredValues.jsonObjectText(r.string(SettingsKeys.COMMAND_SURFACE_SOURCES)),
            quickLauncherStaticTopHighlight = r.bool(SettingsKeys.QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT, d.quickLauncherStaticTopHighlight),
            quickLauncherStaticTopHighlightColor = r.int(SettingsKeys.QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR, d.quickLauncherStaticTopHighlightColor),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Feedback (SS2.13), shell (SS2.15), captures
    // ---------------------------------------------------------------------------------------------

    private fun MutableMap<String, String>.writeFeedback(f: FeedbackPrefs) {
        put(SettingsKeys.TAP_HAPTIC_USE_SYSTEM, f.tapHapticUseSystem.toString())
        put(SettingsKeys.TAP_HAPTIC_DURATION, f.tapHapticDurationMs.toString())
        put(SettingsKeys.TYPING_SOUND_MODE, f.typingSoundMode.storedValue)
        put(SettingsKeys.TYPING_SOUND_OUTPUT_MODE, f.typingSoundOutputMode.storedValue)
    }

    private fun readFeedback(r: FlatReader): FeedbackPrefs {
        val d = FeedbackPrefs()
        return FeedbackPrefs(
            tapHapticUseSystem = r.bool(SettingsKeys.TAP_HAPTIC_USE_SYSTEM, d.tapHapticUseSystem),
            tapHapticDurationMs = r.long(SettingsKeys.TAP_HAPTIC_DURATION, d.tapHapticDurationMs, 5L..80L),
            typingSoundMode = TypingSoundMode.fromStored(r.string(SettingsKeys.TYPING_SOUND_MODE)),
            typingSoundOutputMode = TypingSoundOutputMode.fromStored(r.string(SettingsKeys.TYPING_SOUND_OUTPUT_MODE)),
        )
    }

    private fun MutableMap<String, String>.writeShell(s: ShellState) {
        put(SettingsKeys.TUTORIAL_COMPLETED, s.tutorialCompleted.toString())
        put(SettingsKeys.LAST_SEEN_WHATS_NEW, s.lastSeenWhatsNewVersion)
        put(SettingsKeys.DISMISSED_RELEASES, s.dismissedReleases.joinToString(","))
        put(SettingsKeys.UNTESTED_DEVICE_NOTICE_SEEN, s.untestedDeviceNoticeSeen.toString())
    }

    private fun readShell(r: FlatReader): ShellState {
        val d = ShellState()
        return ShellState(
            tutorialCompleted = r.bool(SettingsKeys.TUTORIAL_COMPLETED, d.tutorialCompleted),
            lastSeenWhatsNewVersion = r.string(SettingsKeys.LAST_SEEN_WHATS_NEW)?.trim() ?: d.lastSeenWhatsNewVersion,
            dismissedReleases = r.string(SettingsKeys.DISMISSED_RELEASES)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.dismissedReleases,
            untestedDeviceNoticeSeen = r.bool(SettingsKeys.UNTESTED_DEVICE_NOTICE_SEEN, d.untestedDeviceNoticeSeen),
        )
    }

    private fun MutableMap<String, String>.writeCaptures(c: DeviceCaptures) {
        put(SettingsKeys.FN_CTRL_PREV_CAPTURED, c.fnCtrlPrevCaptured.toString())
        c.fnCtrlPrevEnable?.let { put(SettingsKeys.FN_CTRL_PREV_ENABLE, it.toString()) }
        c.fnCtrlPrevFunction?.let { put(SettingsKeys.FN_CTRL_PREV_FUNCTION, it.toString()) }
        put(SettingsKeys.SIDE_KEY_ORIGINAL_CAPTURED, c.sideKeyOriginalCaptured.toString())
        put(SettingsKeys.SIDE_KEY_ORIGINAL_PACKAGE, c.sideKeyOriginalPackage)
        put(SettingsKeys.SIDE_KEY_ORIGINAL_ACTIVITY, c.sideKeyOriginalActivity)
        put(SettingsKeys.QS_BACKLIGHT_PREV_CAPTURED, c.qsBacklightPrevCaptured.toString())
        c.qsBacklightPrev?.let { put(SettingsKeys.QS_BACKLIGHT_PREV, it.toString()) }
        put(SettingsKeys.RING_BACKLIGHT_PREV_CAPTURED, c.ringBacklightPrevCaptured.toString())
        c.ringBacklightPrev?.let { put(SettingsKeys.RING_BACKLIGHT_PREV, it.toString()) }
        put(SettingsKeys.SMART_BACKLIGHT_APPLIED, c.smartBacklightApplied.toString())
    }

    private fun readCaptures(r: FlatReader): DeviceCaptures {
        val d = DeviceCaptures()
        return DeviceCaptures(
            fnCtrlPrevCaptured = r.bool(SettingsKeys.FN_CTRL_PREV_CAPTURED, d.fnCtrlPrevCaptured),
            fnCtrlPrevEnable = r.intOrNull(SettingsKeys.FN_CTRL_PREV_ENABLE),
            fnCtrlPrevFunction = r.intOrNull(SettingsKeys.FN_CTRL_PREV_FUNCTION),
            sideKeyOriginalCaptured = r.bool(SettingsKeys.SIDE_KEY_ORIGINAL_CAPTURED, d.sideKeyOriginalCaptured),
            sideKeyOriginalPackage = r.string(SettingsKeys.SIDE_KEY_ORIGINAL_PACKAGE) ?: d.sideKeyOriginalPackage,
            sideKeyOriginalActivity = r.string(SettingsKeys.SIDE_KEY_ORIGINAL_ACTIVITY) ?: d.sideKeyOriginalActivity,
            qsBacklightPrevCaptured = r.bool(SettingsKeys.QS_BACKLIGHT_PREV_CAPTURED, d.qsBacklightPrevCaptured),
            qsBacklightPrev = r.intOrNull(SettingsKeys.QS_BACKLIGHT_PREV),
            ringBacklightPrevCaptured = r.bool(SettingsKeys.RING_BACKLIGHT_PREV_CAPTURED, d.ringBacklightPrevCaptured),
            ringBacklightPrev = r.intOrNull(SettingsKeys.RING_BACKLIGHT_PREV),
            smartBacklightApplied = r.bool(SettingsKeys.SMART_BACKLIGHT_APPLIED, d.smartBacklightApplied),
        )
    }
}

/** Typed, forgiving reads over the flat map: every accessor answers the default for a missing or malformed value. */
internal class FlatReader(private val map: Map<String, String>) {
    fun has(key: String): Boolean = map.containsKey(key)
    fun string(key: String): String? = map[key]
    fun keysWithPrefix(prefix: String): List<String> = map.keys.filter { it.startsWith(prefix) }.sorted()

    fun bool(key: String, default: Boolean): Boolean = when (map[key]?.trim()?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> default
    }

    fun int(key: String, default: Int, range: IntRange? = null): Int {
        val parsed = intOrNull(key) ?: return default
        return if (range == null) parsed else parsed.coerceIn(range)
    }

    fun intOrNull(key: String): Int? = map[key]?.trim()?.let { it.toIntOrNull() ?: it.toLongOrNull()?.toInt() ?: it.toDoubleOrNull()?.toInt() }

    fun long(key: String, default: Long, range: LongRange? = null): Long {
        val parsed = map[key]?.trim()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() } ?: return default
        return if (range == null) parsed else parsed.coerceIn(range)
    }

    fun float(key: String): Float? = map[key]?.trim()?.toFloatOrNull()

    /**
     * A JSON array of strings, or, when the value does not look like JSON, a comma-separated
     * list (the shape a 2.x backup coerces "a single string" into a set from, settings-catalog.md
     * SS7.2 step 5). A value that looks like JSON but does not parse answers null.
     */
    fun stringSet(key: String): Set<String>? {
        val raw = map[key]?.trim() ?: return null
        if (raw.startsWith("[")) return JsonRows.stringList(JsonRows.parse(raw))?.toSet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
}

/**
 * The stored spelling of every enum and JSON row, one place per shape so [SettingsCodec] and
 * [LegacyImport] cannot disagree. Read functions answer null for a value of the wrong shape, or
 * the catalogue's "anything else reads as" fallback where SS2 names one.
 */
internal object StoredValues {
    fun dashStyle(v: DashStyle): String = when (v) {
        DashStyle.EN_DASH -> "en_dash"
        DashStyle.EM_DASH -> "em_dash"
    }

    fun dashStyle(stored: String?): DashStyle = if (stored == "em_dash") DashStyle.EM_DASH else DashStyle.EN_DASH

    fun smartQuoteStyle(v: SmartQuoteStyle): String = when (v) {
        SmartQuoteStyle.GERMAN_GUILLEMETS -> "german_guillemets"
        SmartQuoteStyle.FRENCH_GUILLEMETS -> "french_guillemets"
        SmartQuoteStyle.FRENCH_GUILLEMETS_NARROW_SPACED -> "french_guillemets_narrow_spaced"
        SmartQuoteStyle.GERMAN_LOW_HIGH -> "german_low_high"
        SmartQuoteStyle.ENGLISH_CURLY -> "english_curly"
    }

    fun smartQuoteStyle(stored: String?): SmartQuoteStyle =
        SmartQuoteStyle.entries.firstOrNull { smartQuoteStyle(it) == stored } ?: SmartQuoteStyle.GERMAN_GUILLEMETS

    fun longPressMode(v: LongPressMode): String = when (v) {
        LongPressMode.ALT -> "alt"
        LongPressMode.SHIFT -> "shift"
        LongPressMode.VARIATIONS -> "variations"
        LongPressMode.SYM -> "sym"
        LongPressMode.SYM_SYMBOLS -> "sym_symbols"
        LongPressMode.SYM_EMOJI -> "sym_emoji"
    }

    fun longPressMode(stored: String?): LongPressMode = LongPressMode.entries.firstOrNull { longPressMode(it) == stored } ?: LongPressMode.ALT

    fun messagingPreset(v: MessagingPreset): String = when (v) {
        MessagingPreset.APP_DEFAULT -> "app_default"
        MessagingPreset.SEND_SHIFT_NEWLINE -> "enter_send_shift_newline"
        MessagingPreset.NEWLINE_CTRL_SEND -> "enter_newline_ctrl_send"
        MessagingPreset.CUSTOM -> "custom"
    }

    /** spec SS2.7: "anything else, including the UI's `enter_newline_only`, reads as `app_default`". */
    fun messagingPreset(stored: String?): MessagingPreset = MessagingPreset.entries.firstOrNull { messagingPreset(it) == stored } ?: MessagingPreset.APP_DEFAULT

    fun enterBehavior(v: EnterBehavior): String = when (v) {
        EnterBehavior.APP_DEFAULT -> "app_default"
        EnterBehavior.NEWLINE -> "enter_newline"
        EnterBehavior.SEND_SHIFT_NEWLINE -> "enter_send_shift_newline"
        EnterBehavior.NEWLINE_CTRL_SEND -> "enter_newline_ctrl_send"
    }

    fun enterBehavior(stored: String?): EnterBehavior = EnterBehavior.entries.firstOrNull { enterBehavior(it) == stored } ?: EnterBehavior.APP_DEFAULT

    fun enterSendMethod(v: EnterSendMethod): String = when (v) {
        EnterSendMethod.AUTO -> "auto"
        EnterSendMethod.EDITOR_ACTION -> "editor_action"
        EnterSendMethod.CTRL_ENTER -> "ctrl_enter"
        EnterSendMethod.PLAIN_ENTER -> "plain_enter"
    }

    fun enterSendMethod(stored: String?): EnterSendMethod = EnterSendMethod.entries.firstOrNull { enterSendMethod(it) == stored } ?: EnterSendMethod.AUTO

    fun extraSendShortcut(v: ExtraSendShortcut): String = when (v) {
        ExtraSendShortcut.NONE -> "none"
        ExtraSendShortcut.SYM_ENTER -> "sym_enter"
    }

    fun extraSendShortcut(stored: String?): ExtraSendShortcut = if (stored == "sym_enter") ExtraSendShortcut.SYM_ENTER else ExtraSendShortcut.NONE

    /** spec text-input.md SS1: an ordered subset of the eleven candidates in canonical order; other characters dropped. */
    fun punctuationSubset(stored: String?): String {
        if (stored == null) return ""
        val alphabet = ".,;:!?\\/\")]}"
        return alphabet.filter { it in stored }
    }

    /** A comma-separated language list; blanks dropped, `x-pastiera` (the 2.x hidden set) dropped, order kept. */
    fun languageList(stored: String?): List<String> =
        stored?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() && it != "x-pastiera" }?.distinct() ?: emptyList()

    fun substitutionSet(set: SubstitutionSet): JsonObject = JsonObject(
        buildMap {
            put("__name", JsonPrimitive(set.displayName))
            for ((wrong, right) in set.rules) put(wrong, JsonPrimitive(right))
        },
    )

    fun substitutionSet(obj: JsonObject?): SubstitutionSet? {
        obj ?: return null
        val name = obj.string("__name") ?: ""
        val rules = obj.entries.filter { it.key != "__name" }.mapNotNull { (k, v) -> with(JsonRows) { v.asString() }?.let { k to it } }.toMap()
        return SubstitutionSet(name, rules)
    }

    fun symPagesConfig(c: SymPagesConfig): JsonObject = JsonObject(
        mapOf(
            "emojiEnabled" to JsonPrimitive(c.emojiEnabled),
            "symbolsEnabled" to JsonPrimitive(c.symbolsEnabled),
            "clipboardEnabled" to JsonPrimitive(c.clipboardEnabled),
            "emojiPickerEnabled" to JsonPrimitive(c.emojiPickerEnabled),
            "gifEnabled" to JsonPrimitive(c.gifEnabled),
            "custom1Enabled" to JsonPrimitive(c.custom1Enabled),
            "custom2Enabled" to JsonPrimitive(c.custom2Enabled),
            "custom3Enabled" to JsonPrimitive(c.custom3Enabled),
            "symPageOrder" to JsonRows.stringListOf(c.order.map { it.id }),
        ),
    )

    /**
     * spec settings-catalog.md SS2.5 and SS6.3: a missing `symPageOrder` derives from the legacy
     * `emojiFirst` (emoji, symbols, clipboard, reversed when false, then emoji_picker); unknown
     * page ids (including the dropped `device`) are skipped and missing pages appended last, so
     * a config written before the GIF page existed reads with `gif` last and `gifEnabled` false,
     * and one written before the user's own pages existed reads with `custom1` to `custom3` last
     * and switched off (SS4.6).
     */
    fun symPagesConfig(obj: JsonObject?): SymPagesConfig? {
        obj ?: return null
        val d = SymPagesConfig()
        val storedOrder = JsonRows.stringList(obj["symPageOrder"])?.mapNotNull { SymPage.fromId(it.trim()) }
        val order = storedOrder ?: run {
            // "reversed when `emojiFirst` is false" moves emoji behind the other two (SS12 test 20:
            // symbols, clipboard, emoji), it does not mirror the list.
            val base = if (obj.boolean("emojiFirst") == false) listOf(SymPage.SYMBOLS, SymPage.CLIPBOARD, SymPage.EMOJI) else listOf(SymPage.EMOJI, SymPage.SYMBOLS, SymPage.CLIPBOARD)
            base + SymPage.EMOJI_PICKER
        }
        val complete = order.distinct() + SymPage.entries.filter { it !in order }
        return SymPagesConfig(
            emojiEnabled = obj.boolean("emojiEnabled") ?: d.emojiEnabled,
            symbolsEnabled = obj.boolean("symbolsEnabled") ?: d.symbolsEnabled,
            clipboardEnabled = obj.boolean("clipboardEnabled") ?: d.clipboardEnabled,
            emojiPickerEnabled = obj.boolean("emojiPickerEnabled") ?: d.emojiPickerEnabled,
            // A config written before the GIF page existed never chose it: it reads off, whatever
            // today's default for a fresh install is (layers-sym-alt.md SS4.1).
            gifEnabled = obj.boolean("gifEnabled") ?: false,
            custom1Enabled = obj.boolean("custom1Enabled") ?: d.custom1Enabled,
            custom2Enabled = obj.boolean("custom2Enabled") ?: d.custom2Enabled,
            custom3Enabled = obj.boolean("custom3Enabled") ?: d.custom3Enabled,
            order = complete,
        )
    }

    /**
     * `custom_variations`: `{"a": ["ą", "à"], "A": ["Ą"], ...}`. spec layers-sym-alt.md SS8.3.
     * Keys that are not exactly one character and values that are not arrays are skipped;
     * non-string members of an array are skipped too.
     */
    fun customVariations(m: Map<String, List<String>>): JsonObject = JsonObject(m.mapValues { JsonRows.stringListOf(it.value) })

    fun customVariations(obj: JsonObject?): Map<String, List<String>>? =
        obj?.entries?.mapNotNull { (key, value) ->
            if (key.length != 1) return@mapNotNull null
            JsonRows.stringList(value)?.let { key to it }
        }?.toMap()

    /**
     * `sym_custom_pages`: `{"pages": [{"name": "...", "mappings": {"KEYCODE_Q": "..."}}, ...]}`.
     * spec layers-sym-alt.md SS4.6. Always read back as exactly [CustomSymPage.COUNT] pages: a
     * missing or malformed page reads empty, extra pages are ignored, a name is trimmed and cut.
     */
    fun customSymPages(pages: List<CustomSymPage>): JsonObject = JsonObject(
        mapOf(
            "pages" to JsonArray(
                pages.map { page -> JsonObject(mapOf("name" to JsonPrimitive(page.name), "mappings" to JsonRows.stringMapOf(page.mappings))) },
            ),
        ),
    )

    fun customSymPages(obj: JsonObject?): List<CustomSymPage>? {
        val stored = obj?.get("pages") as? JsonArray ?: return null
        return List(CustomSymPage.COUNT) { index ->
            val page = stored.getOrNull(index) as? JsonObject ?: return@List CustomSymPage()
            CustomSymPage(
                name = page.string("name")?.trim()?.take(CustomSymPage.MAX_NAME_LENGTH).orEmpty(),
                mappings = JsonRows.stringMap(page["mappings"]).orEmpty(),
            )
        }
    }

    fun symMappings(m: Map<String, String>): JsonObject = JsonObject(mapOf("mappings" to JsonRows.stringMapOf(m)))
    fun symMappings(obj: JsonObject?): Map<String, String>? = obj?.let { JsonRows.stringMap(it["mappings"]) }

    /** spec SS6.3: unknown button ids read as `none`, and `none` is an empty slot, so both vanish from the list. */
    fun buttons(stored: String?): List<BarButton>? = JsonRows.stringList(JsonRows.parse(stored))?.mapNotNull { BarButton.fromId(it) }

    fun theme(t: StripTheme): JsonObject = JsonObject(
        mapOf(
            "background" to JsonPrimitive(t.background),
            "suggestion" to JsonPrimitive(t.suggestion),
            "status_bar_button" to JsonPrimitive(t.statusBarButton),
            "accent" to JsonPrimitive(t.accent),
            "text_and_icons" to JsonPrimitive(t.textAndIcons),
            "divider" to JsonPrimitive(t.divider),
            "led_inactive" to JsonPrimitive(t.ledInactive),
            "led_active" to JsonPrimitive(t.ledActive),
            "led_locked" to JsonPrimitive(t.ledLocked),
            "key_corner_radius_ratio" to JsonPrimitive(t.keyCornerRadiusRatio),
            "chrome_corner_radius_ratio" to JsonPrimitive(t.chromeCornerRadiusRatio),
            "suggestions_height_scale" to JsonPrimitive(t.suggestionsHeightScale),
            "show_leds" to JsonPrimitive(t.showLeds),
        ),
    )

    /**
     * spec SS3.1: missing fields take the target's default (Slate Dark), unknown fields are
     * ignored. `suggestion` and `status_bar_button` fall back to `normal_key` and `special_key`
     * when a stored theme carries those but not the strip's own fields.
     */
    fun theme(obj: JsonObject?): StripTheme? {
        obj ?: return null
        val d = StripTheme.SLATE_DARK
        return StripTheme(
            background = obj.int("background") ?: d.background,
            suggestion = obj.int("suggestion") ?: obj.int("normal_key") ?: d.suggestion,
            statusBarButton = obj.int("status_bar_button") ?: obj.int("special_key") ?: d.statusBarButton,
            accent = obj.int("accent") ?: d.accent,
            textAndIcons = obj.int("text_and_icons") ?: d.textAndIcons,
            divider = obj.int("divider") ?: d.divider,
            ledInactive = obj.int("led_inactive") ?: d.ledInactive,
            ledActive = obj.int("led_active") ?: d.ledActive,
            ledLocked = obj.int("led_locked") ?: d.ledLocked,
            keyCornerRadiusRatio = obj.double("key_corner_radius_ratio") ?: d.keyCornerRadiusRatio,
            chromeCornerRadiusRatio = obj.double("chrome_corner_radius_ratio") ?: d.chromeCornerRadiusRatio,
            suggestionsHeightScale = obj.double("suggestions_height_scale") ?: d.suggestionsHeightScale,
            showLeds = obj.boolean("show_leds") ?: d.showLeds,
        )
    }

    fun namedTheme(t: NamedTheme): JsonObject = JsonObject(mapOf("name" to JsonPrimitive(t.name), "theme" to theme(t.theme)))

    /** spec SS2.6: a blank name saves as "Custom"; an entry without a theme object is skipped. */
    fun namedThemes(arr: JsonArray?): List<NamedTheme>? = arr?.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val theme = theme(obj["theme"] as? JsonObject) ?: return@mapNotNull null
        NamedTheme(obj.string("name")?.takeIf { it.isNotBlank() } ?: "Custom", theme)
    }

    /** spec: settings-catalog.md SS2.6, `{"locale"?: tag, "layout"?: id, "theme": theme}`; an entry with neither locale nor layout is dropped. */
    fun layoutOverrides(overrides: List<ThemeLayoutOverride>): JsonArray = JsonArray(
        overrides.filter { it.locale != null || it.layout != null }.map { o ->
            val fields = buildMap {
                o.locale?.let { put("locale", JsonPrimitive(it)) }
                o.layout?.let { put("layout", JsonPrimitive(it)) }
                put("theme", theme(o.theme))
            }
            JsonObject(fields)
        },
    )

    /** An entry without a parseable theme, or with neither `locale` nor `layout`, is skipped. */
    fun layoutOverrides(arr: JsonArray?): List<ThemeLayoutOverride>? = arr?.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val locale = obj.string("locale")
        val layout = obj.string("layout")
        if (locale == null && layout == null) return@mapNotNull null
        val theme = theme(obj["theme"] as? JsonObject) ?: return@mapNotNull null
        ThemeLayoutOverride(locale, layout, theme)
    }

    fun enterOverrides(rows: List<EnterOverrideRow>): JsonArray = JsonArray(
        rows.map {
            JsonObject(
                mapOf(
                    "packageName" to JsonPrimitive(it.packageName),
                    "behavior" to JsonPrimitive(enterBehavior(it.behavior)),
                    "sendStrategy" to JsonPrimitive(enterSendMethod(it.sendMethod)),
                    "additionalSendShortcut" to JsonPrimitive(extraSendShortcut(it.extraSendShortcut)),
                ),
            )
        },
    )

    /** spec SS2.7: "duplicates by package dropped, blank package dropped" (the first entry wins, SS12 test 22). */
    fun enterOverrides(arr: JsonArray?): List<EnterOverrideRow>? {
        arr ?: return null
        val seen = HashSet<String>()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val pkg = obj.string("packageName")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!seen.add(pkg)) return@mapNotNull null
            EnterOverrideRow(
                packageName = pkg,
                behavior = enterBehavior(obj.string("behavior")),
                sendMethod = enterSendMethod(obj.string("sendStrategy")),
                extraSendShortcut = extraSendShortcut(obj.string("additionalSendShortcut")),
            )
        }
    }

    /**
     * spec expansion-clipboard-pickers-launcher.md SS2: "exactly one character that is not
     * whitespace, not a letter or digit, and not a colon. A stored value that fails this is
     * ignored and `!` is used."
     */
    fun snippetPrefix(stored: String?): String {
        val p = stored ?: return "!"
        val c = p.singleOrNull() ?: return "!"
        val valid = !c.isWhitespace() && !c.isLetterOrDigit() && c != ':'
        return if (valid) p else "!"
    }

    /** spec SS2.12: shortcuts lower-cased, blank replacements dropped. */
    fun snippets(obj: JsonObject?): Map<String, String>? = obj?.let { JsonRows.stringMap(it) }
        ?.filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
        ?.mapKeys { it.key.lowercase() }

    /** A JSON object document kept verbatim, or blank when the text is not a JSON object. */
    fun jsonObjectText(stored: String?): String = stored?.takeIf { JsonRows.parseObject(it) != null } ?: ""
}
