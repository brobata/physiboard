package brobata.physiboard.app.settings.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import brobata.physiboard.app.settings.ui.screens.AppPickerScreen
import brobata.physiboard.app.settings.ui.screens.AutoCorrectionScreen
import brobata.physiboard.app.settings.ui.screens.EnterKeyBehaviourScreen
import brobata.physiboard.app.settings.ui.screens.ExtrasHubScreen
import brobata.physiboard.app.settings.ui.screens.FnLayerScreen
import brobata.physiboard.app.settings.ui.screens.InputLanguagesScreen
import brobata.physiboard.app.settings.ui.screens.KeyboardHubScreen
import brobata.physiboard.app.settings.ui.screens.PlaceholderScreen
import brobata.physiboard.app.settings.ui.screens.PunctuationSpacingScreen
import brobata.physiboard.app.settings.ui.screens.QuickLauncherScreen
import brobata.physiboard.app.settings.ui.screens.ScreenTrackpadScreen
import brobata.physiboard.app.settings.ui.screens.SettingsRootScreen
import brobata.physiboard.app.settings.ui.screens.SmartFeaturesScreen
import brobata.physiboard.app.settings.ui.screens.SoundHapticsScreen
import brobata.physiboard.app.settings.ui.screens.StatusBarThemeScreen
import brobata.physiboard.app.settings.ui.screens.StripThemeScreen
import brobata.physiboard.app.settings.ui.screens.T2EToolsScreen
import brobata.physiboard.app.settings.ui.screens.TestFieldScreen
import brobata.physiboard.app.settings.ui.screens.TextExpansionScreen
import brobata.physiboard.app.settings.ui.screens.VoiceScreen

/**
 * The whole settings app as one push/pop stack (settings-catalog.md SS9.1, "a push/pop stack
 * inside one activity"). `:app` has no home-tile app shell yet (that milestone is out of scope
 * here), so [Routes.SETTINGS] carries three extra rows to "T2E Tools", "Keyboard" and "Extras"
 * that stand in for the tiles until that shell exists; see this module's report.
 */
@Composable
fun SettingsApp() {
    val navController: NavHostController = rememberNavController()
    fun navigate(route: String) { navController.navigate(route) }
    fun back() { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.SETTINGS) {
        composable(Routes.SETTINGS) { SettingsRootScreen(onNavigate = ::navigate) }
        composable(Routes.T2E_TOOLS) { T2EToolsScreen(onBack = ::back, onNavigate = ::navigate) }
        composable(Routes.KEYBOARD) { KeyboardHubScreen(onBack = ::back, onNavigate = ::navigate) }
        composable(Routes.EXTRAS) { ExtrasHubScreen(onBack = ::back, onNavigate = ::navigate) }

        composable(Routes.SCREEN_TRACKPAD) { ScreenTrackpadScreen(onBack = ::back) }
        composable(Routes.FN_LAYER) { FnLayerScreen(onBack = ::back) }
        composable(Routes.SMART_FEATURES) {
            SmartFeaturesScreen(
                onBack = ::back,
                onNavigateFnLayer = { navigate(Routes.FN_LAYER) },
                onNavigatePunctuationSpacing = { navigate(Routes.PUNCTUATION_SPACING) },
            )
        }
        composable(Routes.PUNCTUATION_SPACING) { PunctuationSpacingScreen(onBack = ::back) }
        composable(Routes.AUTO_CORRECTION) { AutoCorrectionScreen(onBack = ::back) }
        composable(Routes.VOICE) { VoiceScreen(onBack = ::back) }
        composable(Routes.STATUS_BAR_THEME) { StatusBarThemeScreen(onBack = ::back, onNavigate = ::navigate) }
        composable(Routes.CUSTOMIZE_COLORS) { StripThemeScreen(onBack = ::back) }
        composable(Routes.SOUND_HAPTICS) { SoundHapticsScreen(onBack = ::back) }
        composable(Routes.ENTER_KEY_BEHAVIOUR) { EnterKeyBehaviourScreen(onBack = ::back, onNavigate = ::navigate) }

        composable(Routes.QUICK_LAUNCHER) { QuickLauncherScreen(onBack = ::back) }
        composable(Routes.INPUT_LANGUAGES) { InputLanguagesScreen(onBack = ::back) }
        composable(Routes.TEXT_EXPANSION) { TextExpansionScreen(onBack = ::back) }

        composable(Routes.TEST_FIELD) { TestFieldScreen(onBack = ::back) }

        composable(
            Routes.APP_PICKER_PATTERN,
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            AppPickerScreen(kind = entry.arguments?.getString("kind").orEmpty(), onBack = ::back)
        }
        composable(
            Routes.PLACEHOLDER_PATTERN,
            arguments = listOf(navArgument("title") { type = NavType.StringType }),
        ) { entry ->
            PlaceholderScreen(title = entry.arguments?.getString("title").orEmpty(), onBack = ::back)
        }
    }
}
