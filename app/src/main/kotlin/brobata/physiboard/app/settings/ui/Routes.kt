package brobata.physiboard.app.settings.ui

/**
 * Every destination the settings app's [androidx.navigation.NavHost] knows, named for the
 * catalogue screen it renders (settings-catalog.md SS9.2, "the map"). Kept as plain strings (no
 * nav-compose type-safety codegen) so the search catalogue in [SearchCatalog] can point at a
 * route by name without a dependency cycle.
 *
 * Home is the category index (app-shell.md SS6). Each category either opens its one screen
 * directly ([AUTO_CORRECTION], [INPUT_LANGUAGES], [LONG_PRESS], [CUSTOMIZE_SYM_KEYBOARD],
 * [VOICE], [PRIVACY], [T2E_TOOLS], [ABOUT]) or a small category screen of its own ([TYPING],
 * [KEYS], [APPS], [LOOK], [BACKUP], [HELP]) that gathers the screens that belong together.
 */
object Routes {
    // The category screens of the home index (docs/plans/settings-reorganization.md).
    const val TYPING = "typing"
    const val KEYS = "keys"
    const val APPS = "apps"
    const val LOOK = "look"
    const val BACKUP = "backup"
    const val HELP = "help"

    const val T2E_TOOLS = "t2e_tools"
    const val SCREEN_TRACKPAD = "screen_trackpad"
    const val FN_LAYER = "fn_layer"
    const val PUNCTUATION_SPACING = "punctuation_spacing"
    const val AUTO_CORRECTION = "auto_correction"
    const val VOICE = "voice"
    const val THEME = "theme"
    const val CUSTOMIZE_COLORS = "customize_colors"
    const val SOUND_HAPTICS = "sound_haptics"
    const val ENTER_KEY_BEHAVIOUR = "enter_key_behaviour"
    const val QUICK_LAUNCHER = "quick_launcher"
    const val INPUT_LANGUAGES = "input_languages"
    const val TEXT_EXPANSION = "text_expansion"
    const val TEST_FIELD = "test_field"

    // keys-and-modifiers.md SS8, layers-sym-alt.md SS8.3: the long-press screen and the accent lists.
    const val LONG_PRESS = "long_press"
    const val CUSTOMIZE_VARIATIONS = "customize_variations"

    // broker-privileged-toolbox.md, device-backlight-ring.md: the T2E Tools toolbox screens.
    const val SMART_BACKLIGHT = "smart_backlight"
    const val REMOVE_BLOAT = "remove_bloat"
    const val SCREEN_DENSITY = "screen_density"
    const val SYSTEM_TWEAKS = "system_tweaks"
    const val NOTIFICATION_RING = "notification_ring"
    const val RING_FIT = "ring_fit"
    const val KEY_MAPPING = "key_mapping"

    // app-shell.md: the shell's own screens. HOME is the real home (SS6); SETUP and WHATS_NEW are
    // also reachable as ordinary destinations (Setup from About's "Show Tutorial", SS3) in
    // addition to being a possible NavHost start destination (see SettingsNavHost's report).
    const val HOME = "home"
    const val SETUP = "setup"
    const val WHATS_NEW = "whats_new"
    const val STATUS = "status"
    const val ABOUT = "about"
    const val DIAGNOSTICS = "diagnostics"
    // dictionaries-languages.md SS11, app-shell.md SS15 card 3 / SS22.3: the "App Language" screen, reached from About.
    const val APP_LANGUAGE = "app_language"

    // expansion-clipboard-pickers-launcher.md: the list editors the placeholder rows pointed at.
    const val MANAGE_SNIPPETS = "manage_snippets"
    const val CUSTOM_SUBSTITUTIONS = "custom_substitutions"
    const val ASSIGNED_LAUNCHER_KEYS = "assigned_launcher_keys"
    const val QUICK_LAUNCHER_ENTRIES = "quick_launcher_entries"
    const val CUSTOMIZE_ENTRIES = "customize_entries"
    const val CLIPBOARD_HISTORY = "clipboard_history"

    // app-shell.md SS31, expansion-clipboard-pickers-launcher.md SS3.7: private mode and clean links.
    const val PRIVACY = "privacy"

    // dictionaries-languages.md SS6, SS7, SS8.2; status-bar.md SS9.3-9.4; trackpad-caret-nav.md
    // SS5.8: the list editors this module's feature work adds.
    const val PERSONAL_DICTIONARY = "personal_dictionary"
    const val INSTALLED_DICTIONARIES = "installed_dictionaries"
    const val INPUT_STYLES = "input_styles"
    const val SAVED_THEMES = "saved_themes"
    const val THEME_LAYOUT_OVERRIDES = "theme_layout_overrides"

    // layers-sym-alt.md SS9.6, SS9.7: the Keyboard Layout screen and its layout viewer.
    const val KEYBOARD_LAYOUT = "keyboard_layout"
    fun layoutViewer(layoutId: String) = "layout_viewer/${android.net.Uri.encode(layoutId)}"
    const val LAYOUT_VIEWER_PATTERN = "layout_viewer/{layoutId}"

    // layers-sym-alt.md SS5.9: "Customize SYM Keyboard". The four query args are only set when the
    // keyboard itself opens this screen (SS5.8's pencil / long-press); plain in-app navigation uses
    // [CUSTOMIZE_SYM_KEYBOARD] bare, which the pattern's own defaults answer as "no initial page".
    const val CUSTOMIZE_SYM_KEYBOARD = "customize_sym_keyboard"
    const val CUSTOMIZE_SYM_KEYBOARD_PATTERN =
        "customize_sym_keyboard?page={page}&keyCode={keyCode}&openPicker={openPicker}&returnAfterPicker={returnAfterPicker}"

    /** [page] is 1 (emoji) or 2 (symbols) to open that page's editor directly, else 0. spec SS5.8's intent extras. */
    fun customizeSymKeyboard(page: Int = 0, keyCode: Int = -1, openPicker: Boolean = false, returnAfterPicker: Boolean = false) =
        "customize_sym_keyboard?page=$page&keyCode=$keyCode&openPicker=$openPicker&returnAfterPicker=$returnAfterPicker"

    /** `theme_layout_overrides/{index}`; -1 adds a new override, else edits `layoutOverrides[index]`. */
    fun themeLayoutOverride(index: Int) = "theme_layout_overrides/$index"
    const val THEME_LAYOUT_OVERRIDE_PATTERN = "theme_layout_overrides/{index}"

    /** `custom_substitutions/{code}`: one language's "Custom Substitutions" list. */
    fun customSubstitutions(code: String) = "custom_substitutions/${android.net.Uri.encode(code)}"
    const val CUSTOM_SUBSTITUTIONS_PATTERN = "custom_substitutions/{code}"

    /** `app_picker/{kind}`; see [PerAppListKind]. */
    fun appPicker(kind: String) = "app_picker/$kind"
    const val APP_PICKER_PATTERN = "app_picker/{kind}"
}

/**
 * The per-app lists a screen offers. The store also holds the strip's app list and "the dip list"
 * (`status_bar_apps`, `app_keyboard_nudge_packages`); both only ever applied to the suggestion
 * strip, which is gone (c61c240), so no screen offers them. They stay in the store and in backups.
 */
object PerAppListKind {
    const val EXACT_TYPING = "exact_typing"
    const val ENTER_OVERRIDES = "enter_overrides"
}
