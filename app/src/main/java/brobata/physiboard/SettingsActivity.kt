package brobata.physiboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import brobata.physiboard.ui.theme.PastieraTheme

class SettingsActivity : LocalizedComponentActivity() {
    companion object {
        const val EXTRA_DESTINATION = "brobata.physiboard.SETTINGS_DESTINATION"
        const val DESTINATION_CUSTOMIZATION = "customization"
        const val DESTINATION_DEVICE_SYM_LAYER_EDITOR = "device_sym_layer_editor"
        const val DESTINATION_MODIFIERS = "modifiers"
        const val DESTINATION_SMART_BACKLIGHT = "smart_backlight"
        const val DESTINATION_INPUT_LANGUAGES = "input_languages"
        const val DESTINATION_KEYBOARD_THEME = "keyboard_theme_destination"
        const val DESTINATION_SMART_FEATURES = "smart_features_destination"
        const val DESTINATION_AUTO_CORRECT = "auto_correct_destination"
        const val DESTINATION_VOICE = "voice_destination"
        const val DESTINATION_RAW_MODE = "raw_mode_destination"
        const val DESTINATION_FN_LAYER = "fn_layer_destination"
        const val DESTINATION_SCREEN_TRACKPAD = "screen_trackpad_destination"
        const val DESTINATION_TOOLBOX = "toolbox_destination"
        const val DESTINATION_REMOVE_BLOAT = "remove_bloat_destination"
        const val DESTINATION_KEYBOARD_HUB = "keyboard_hub_destination"
        const val DESTINATION_EXTRAS = "extras_destination"
        const val EXTRA_CUSTOMIZATION_DESTINATION = "brobata.physiboard.CUSTOMIZATION_DESTINATION"
        const val CUSTOMIZATION_DESTINATION_VARIATIONS = "variations"
        const val CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS = "launcher_shortcuts"
        const val CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR = "app_enter_behavior"
        const val CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS = "status_bar_buttons"
        const val CUSTOMIZATION_DESTINATION_KEYBOARD_THEME = "keyboard_theme"
        const val CUSTOMIZATION_DESTINATION_SOUNDS = "sounds"
        const val EXTRA_KEYBOARD_THEME_TARGET = "brobata.physiboard.KEYBOARD_THEME_TARGET"
        const val KEYBOARD_THEME_TARGET_SOFTWARE = "software"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            applySlideInFromRightTransition()
        }
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    initialDestination = intent.getStringExtra(EXTRA_DESTINATION),
                    initialCustomizationDestination = intent.getStringExtra(EXTRA_CUSTOMIZATION_DESTINATION),
                    initialKeyboardThemeTarget = intent.getStringExtra(EXTRA_KEYBOARD_THEME_TARGET)
                )
            }
        }
    }
    
    override fun finish() {
        super.finish()
        applySlideOutToRightTransition()
    }
}
