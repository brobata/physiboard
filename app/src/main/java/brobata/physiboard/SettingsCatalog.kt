package brobata.physiboard

import androidx.annotation.StringRes

/**
 * Navigation targets reachable from settings search. Each maps to an existing
 * main-screen navigation action, so search never invents new routes.
 */
enum class SettingsSearchTarget {
    MODIFIERS,
    TEXT_INPUT,
    ACCESSIBILITY,
    AUTO_CORRECTION,
    APP_RAW_MODE,
    CUSTOMIZATION,
    STATUS_BAR_BUTTONS,
    KEYBOARD_THEME,
    QUICK_LAUNCHER,
    NAV_MODE,
    SCREEN_TRACKPAD,
    ENTER_BEHAVIOR,
    TOOLBOX,
    REMOVE_BLOAT,
    ADVANCED,
    ABOUT,
    CUSTOM_INPUT_STYLES,
    APP_LANGUAGE,
    VOICE
}

/**
 * One searchable entry: a screen or an individual setting, the screen that
 * hosts it, and extra match keywords (synonyms users actually type).
 */
data class SettingsSearchEntry(
    @StringRes val titleRes: Int,
    @StringRes val screenTitleRes: Int,
    val target: SettingsSearchTarget,
    val keywords: String = ""
)

/**
 * Static index of searchable settings. Single source of truth for settings
 * search; grows toward powering the top-level grouping as well.
 */
object SettingsCatalog {
    val entries: List<SettingsSearchEntry> = listOf(
        // Screens
        SettingsSearchEntry(R.string.screen_trackpad_title, R.string.screen_trackpad_title, SettingsSearchTarget.SCREEN_TRACKPAD, "trackpad cursor swipe screen spacebar hold arrow select"),
        SettingsSearchEntry(R.string.modifiers_title, R.string.modifiers_title, SettingsSearchTarget.MODIFIERS, "shift alt ctrl sym sticky latch lock"),
        SettingsSearchEntry(R.string.settings_category_text_input, R.string.settings_category_text_input, SettingsSearchTarget.TEXT_INPUT, "typing punctuation spaces"),
        SettingsSearchEntry(R.string.settings_category_accessibility, R.string.settings_category_accessibility, SettingsSearchTarget.ACCESSIBILITY, "a11y vibration haptics"),
        SettingsSearchEntry(R.string.settings_category_auto_correction, R.string.settings_category_auto_correction, SettingsSearchTarget.AUTO_CORRECTION, "autocorrect spell dictionary suggestions typo"),
        SettingsSearchEntry(R.string.settings_category_customization, R.string.settings_category_customization, SettingsSearchTarget.CUSTOMIZATION, "customize variations shortcuts"),
        SettingsSearchEntry(R.string.settings_category_advanced, R.string.settings_category_advanced, SettingsSearchTarget.ADVANCED, "trackpad clipboard backup restore export import debug"),
        SettingsSearchEntry(R.string.reset_to_stock_title, R.string.settings_category_advanced, SettingsSearchTarget.ADVANCED, "reset stock uninstall undo revert fn ctrl backlight system default factory"),
        SettingsSearchEntry(R.string.about_title, R.string.about_title, SettingsSearchTarget.ABOUT, "version credits license update"),
        SettingsSearchEntry(R.string.custom_input_styles_title, R.string.custom_input_styles_title, SettingsSearchTarget.CUSTOM_INPUT_STYLES, "language layout azerty qwertz input style"),
        SettingsSearchEntry(R.string.app_language_title, R.string.app_language_title, SettingsSearchTarget.APP_LANGUAGE, "language locale translate"),
        SettingsSearchEntry(R.string.nav_mode_title, R.string.nav_mode_title, SettingsSearchTarget.NAV_MODE, "navigation arrows cursor dpad scroll"),
        SettingsSearchEntry(R.string.keyboard_theme_title, R.string.keyboard_theme_title, SettingsSearchTarget.KEYBOARD_THEME, "theme dark light color appearance"),
        SettingsSearchEntry(R.string.status_bar_buttons_title, R.string.status_bar_buttons_title, SettingsSearchTarget.STATUS_BAR_BUTTONS, "microphone mic emoji hamburger bottom bar status slots"),
        SettingsSearchEntry(R.string.starter_launcher_shortcuts_title, R.string.starter_launcher_shortcuts_title, SettingsSearchTarget.QUICK_LAUNCHER, "quick launcher apps shortcut launch"),
        SettingsSearchEntry(R.string.app_enter_behaviour_title, R.string.app_enter_behaviour_title, SettingsSearchTarget.ENTER_BEHAVIOR, "enter send newline whatsapp per app"),

        // Device toolbox
        SettingsSearchEntry(R.string.toolbox_title, R.string.toolbox_title, SettingsSearchTarget.TOOLBOX, "device toolbox titan unihertz system tools"),
        SettingsSearchEntry(R.string.bloat_title, R.string.toolbox_title, SettingsSearchTarget.REMOVE_BLOAT, "bloat bloatware debloat uninstall remove disable unihertz vendor apps agui"),
        SettingsSearchEntry(R.string.density_title, R.string.toolbox_title, SettingsSearchTarget.TOOLBOX, "density dpi screen size scale smaller bigger fit more zoom"),
        SettingsSearchEntry(R.string.tweaks_title, R.string.toolbox_title, SettingsSearchTarget.TOOLBOX, "animation speed faster notification history one handed pixel tweaks"),
        SettingsSearchEntry(R.string.keymap_title, R.string.toolbox_title, SettingsSearchTarget.TOOLBOX, "key mapping keys remap fn sym orange side button shortcut bindings"),

        // Voice / dictation
        SettingsSearchEntry(R.string.settings_category_voice, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "voice dictation microphone speech talk transcribe"),
        SettingsSearchEntry(R.string.fn_long_press_speech_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "voice dictation microphone speech fn hold"),
        SettingsSearchEntry(R.string.alt_ctrl_speech_shortcut_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "voice dictation speech shortcut"),
        SettingsSearchEntry(R.string.dictation_haptics_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "vibrate vibration haptic dictation voice"),
        SettingsSearchEntry(R.string.dictation_haptic_strength_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "vibration strength stronger firmer haptic dictation voice"),
        SettingsSearchEntry(R.string.dictation_end_silence_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "pause silence timeout cutoff dictation voice"),
        SettingsSearchEntry(R.string.dictation_mask_offensive_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "profanity censor swear offensive f***"),
        SettingsSearchEntry(R.string.sym_long_press_assistant_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "assistant gemini sym hold long press voice ask siri"),
        SettingsSearchEntry(R.string.side_key_assistant_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "assistant gemini orange side key func1 shortcut voice ask"),
        SettingsSearchEntry(R.string.assistant_action_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "assistant action intent listening gemini voice command hands free assist"),
        SettingsSearchEntry(R.string.dictation_engine_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "engine recognizer speech service google on-device offline dictation voice"),
        SettingsSearchEntry(R.string.dictation_continuous_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "continuous segmented session pause cutoff dictation voice"),
        SettingsSearchEntry(R.string.dictation_auto_punctuation_title, R.string.settings_category_voice, SettingsSearchTarget.VOICE, "punctuation comma period capitalization formatting dictation voice"),

        // Typing behavior
        SettingsSearchEntry(R.string.auto_capitalize_title, R.string.settings_category_text_input, SettingsSearchTarget.TEXT_INPUT, "capital uppercase sentence autocap"),
        SettingsSearchEntry(R.string.double_space_to_period_title, R.string.settings_category_text_input, SettingsSearchTarget.TEXT_INPUT, "period full stop double space"),
        SettingsSearchEntry(R.string.text_expansion_title, R.string.settings_category_text_input, SettingsSearchTarget.TEXT_INPUT, "snippet abbreviation expand shortcut"),
        SettingsSearchEntry(R.string.app_raw_mode_title, R.string.app_raw_mode_title, SettingsSearchTarget.APP_RAW_MODE, "terminal termux disable smart per app raw exceptions"),
    )
}
