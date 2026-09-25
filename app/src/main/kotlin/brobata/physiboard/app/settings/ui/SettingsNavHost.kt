package brobata.physiboard.app.settings.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import brobata.physiboard.app.settings.ui.screens.AboutScreen
import brobata.physiboard.app.settings.ui.screens.AppPickerScreen
import brobata.physiboard.app.settings.ui.screens.AutoCorrectionScreen
import brobata.physiboard.app.settings.ui.screens.DiagnosticsScreen
import brobata.physiboard.app.settings.ui.screens.HomeScreen
import brobata.physiboard.app.settings.ui.screens.SetupScreen
import brobata.physiboard.app.settings.ui.screens.StatusScreen
import brobata.physiboard.app.settings.ui.screens.WhatsNewScreen
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
import brobata.physiboard.app.settings.ui.screens.AssignedLauncherKeysScreen
import brobata.physiboard.app.settings.ui.screens.ClipboardHistoryScreen
import brobata.physiboard.app.settings.ui.screens.CustomSubstitutionsEditScreen
import brobata.physiboard.app.settings.ui.screens.CustomSubstitutionsScreen
import brobata.physiboard.app.settings.ui.screens.CustomizeEntriesScreen
import brobata.physiboard.app.settings.ui.screens.ManageSnippetsScreen
import brobata.physiboard.app.settings.ui.screens.QuickLauncherEntriesScreen

/**
 * The whole settings app as one push/pop stack (settings-catalog.md SS9.1, "a push/pop stack
 * inside one activity"). [Routes.HOME] is now the real home (app-shell.md SS6); [startDestination]
 * lets the caller (`MainActivity`) route to [Routes.SETUP] or [Routes.WHATS_NEW] instead on a cold
 * start (SS3). [Routes.SETTINGS] still carries "T2E Tools", "Keyboard" and "Extras" as ordinary
 * rows alongside "About", "Diagnostics" and "Updates" (SS9), which is the settings-catalog's
 * intended shape, not a stand-in.
 */
@Composable
fun SettingsApp(startDestination: String = Routes.HOME) {
    val navController: NavHostController = rememberNavController()
    val controller = LocalSettingsController.current
    fun navigate(route: String) { navController.navigate(route) }
    fun back() { navController.popBackStack() }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.HOME) { HomeScreen(onNavigate = ::navigate) }
        composable(Routes.SETUP) {
            SetupScreen(onComplete = { navController.navigate(Routes.HOME) { popUpTo(0) } })
        }
        composable(Routes.WHATS_NEW) {
            WhatsNewScreen(onDone = { navController.navigate(Routes.HOME) { popUpTo(0) } })
        }
        composable(Routes.STATUS) { StatusScreen(onBack = ::back) }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = ::back,
                onShowTutorial = {
                    // spec: SS3, "the row resets tutorial_completed to false and opens the setup screen directly."
                    controller.update { it.copy(shell = it.shell.copy(tutorialCompleted = false)) }
                    navController.navigate(Routes.SETUP)
                },
            )
        }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = ::back) }
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
        composable(Routes.AUTO_CORRECTION) { AutoCorrectionScreen(onBack = ::back, onNavigate = ::navigate) }
        composable(Routes.VOICE) { VoiceScreen(onBack = ::back) }
        composable(Routes.STATUS_BAR_THEME) { StatusBarThemeScreen(onBack = ::back, onNavigate = ::navigate) }
        composable(Routes.CUSTOMIZE_COLORS) { StripThemeScreen(onBack = ::back) }
        composable(Routes.SOUND_HAPTICS) { SoundHapticsScreen(onBack = ::back) }
        composable(Routes.ENTER_KEY_BEHAVIOUR) { EnterKeyBehaviourScreen(onBack = ::back, onNavigate = ::navigate) }

        composable(Routes.QUICK_LAUNCHER) { QuickLauncherScreen(onBack = ::back, onNavigate = ::navigate) }
        composable(Routes.INPUT_LANGUAGES) { InputLanguagesScreen(onBack = ::back) }
        composable(Routes.TEXT_EXPANSION) { TextExpansionScreen(onBack = ::back, onNavigate = ::navigate) }

        // expansion-clipboard-pickers-launcher.md: the list editors.
        composable(Routes.MANAGE_SNIPPETS) { ManageSnippetsScreen(onBack = ::back) }
        composable(Routes.CUSTOM_SUBSTITUTIONS) { CustomSubstitutionsScreen(onBack = ::back, onOpen = { code -> navigate(Routes.customSubstitutions(code)) }) }
        composable(
            Routes.CUSTOM_SUBSTITUTIONS_PATTERN,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { entry ->
            CustomSubstitutionsEditScreen(code = entry.arguments?.getString("code").orEmpty(), onBack = ::back)
        }
        composable(Routes.ASSIGNED_LAUNCHER_KEYS) { AssignedLauncherKeysScreen(onBack = ::back) }
        composable(Routes.QUICK_LAUNCHER_ENTRIES) { QuickLauncherEntriesScreen(onBack = ::back) }
        composable(Routes.CUSTOMIZE_ENTRIES) { CustomizeEntriesScreen(onBack = ::back) }
        composable(Routes.CLIPBOARD_HISTORY) { ClipboardHistoryScreen(onBack = ::back) }

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
