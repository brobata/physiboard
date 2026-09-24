package brobata.physiboard.app.settings.ui

/**
 * Every destination the settings app's [androidx.navigation.NavHost] knows, named for the
 * catalogue screen it renders (settings-catalog.md SS9.2, "the map"). Kept as plain strings (no
 * nav-compose type-safety codegen) so the search catalogue in [SearchCatalog] can point at a
 * route by name without a dependency cycle.
 */
object Routes {
    const val SETTINGS = "settings"
    const val T2E_TOOLS = "t2e_tools"
    const val KEYBOARD = "keyboard"
    const val EXTRAS = "extras"

    const val SCREEN_TRACKPAD = "screen_trackpad"
    const val FN_LAYER = "fn_layer"
    const val SMART_FEATURES = "smart_features"
    const val PUNCTUATION_SPACING = "punctuation_spacing"
    const val AUTO_CORRECTION = "auto_correction"
    const val VOICE = "voice"
    const val STATUS_BAR_THEME = "status_bar_theme"
    const val CUSTOMIZE_COLORS = "customize_colors"
    const val SOUND_HAPTICS = "sound_haptics"
    const val ENTER_KEY_BEHAVIOUR = "enter_key_behaviour"
    const val QUICK_LAUNCHER = "quick_launcher"
    const val INPUT_LANGUAGES = "input_languages"
    const val TEXT_EXPANSION = "text_expansion"
    const val TEST_FIELD = "test_field"

    /** `app_picker/{kind}`; see [PerAppListKind]. */
    fun appPicker(kind: String) = "app_picker/$kind"
    const val APP_PICKER_PATTERN = "app_picker/{kind}"

    /** `placeholder/{title}`; the stub for a feature another agent is building under `:ime`/`:device:privileged`. */
    fun placeholder(title: String) = "placeholder/${android.net.Uri.encode(title)}"
    const val PLACEHOLDER_PATTERN = "placeholder/{title}"
}

/** The four per-app lists the store holds (rebuild-from-scratch.md: "the dip list" is [TEXT_BOX_UNDER_BAR]'s catalogue name). */
object PerAppListKind {
    const val EXACT_TYPING = "exact_typing"
    const val TEXT_BOX_UNDER_BAR = "text_box_under_bar"
    const val STATUS_BAR_APPS = "status_bar_apps"
    const val ENTER_OVERRIDES = "enter_overrides"
}
