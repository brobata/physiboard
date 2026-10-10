package brobata.physiboard.app.settings.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import brobata.physiboard.app.settings.ui.screens.AboutScreen
import brobata.physiboard.app.settings.ui.screens.AppsScreen
import brobata.physiboard.app.settings.ui.screens.BackupScreen
import brobata.physiboard.app.settings.ui.screens.HelpScreen
import brobata.physiboard.app.settings.ui.screens.KeysScreen
import brobata.physiboard.app.settings.ui.screens.LookScreen
import brobata.physiboard.app.settings.ui.screens.ThemeColorsScreen
import brobata.physiboard.app.settings.ui.screens.ThemeScreen
import brobata.physiboard.app.settings.ui.screens.TypingScreen
import brobata.physiboard.app.settings.ui.screens.AppLanguageScreen
import brobata.physiboard.app.settings.ui.screens.AppPickerScreen
import brobata.physiboard.app.settings.ui.screens.AutoCorrectionScreen
import brobata.physiboard.app.settings.ui.screens.DiagnosticsScreen
import brobata.physiboard.app.settings.ui.screens.HomeScreen
import brobata.physiboard.app.settings.ui.screens.SetupScreen
import brobata.physiboard.app.settings.ui.screens.StatusScreen
import brobata.physiboard.app.settings.ui.screens.WhatsNewScreen
import brobata.physiboard.app.settings.ui.screens.EnterKeyBehaviourScreen
import brobata.physiboard.app.settings.ui.screens.AccessibilityServiceScreen
import brobata.physiboard.app.settings.ui.screens.FnLayerScreen
import brobata.physiboard.app.settings.ui.screens.InputLanguagesScreen
import brobata.physiboard.app.settings.ui.screens.PunctuationSpacingScreen
import brobata.physiboard.app.settings.ui.screens.QuickLauncherScreen
import brobata.physiboard.app.settings.ui.screens.ScreenTrackpadScreen
import brobata.physiboard.app.settings.ui.screens.LongPressScreen
import brobata.physiboard.app.settings.ui.screens.CustomizeVariationsScreen
import brobata.physiboard.app.settings.ui.screens.SoundHapticsScreen
import brobata.physiboard.app.settings.ui.screens.T2EToolsScreen
import brobata.physiboard.app.settings.ui.screens.TestFieldScreen
import brobata.physiboard.app.settings.ui.screens.TextExpansionScreen
import brobata.physiboard.app.settings.ui.screens.VoiceScreen
import brobata.physiboard.app.settings.ui.screens.AssignedLauncherKeysScreen
import brobata.physiboard.app.settings.ui.screens.ClipboardHistoryScreen
import brobata.physiboard.app.settings.ui.screens.PrivacyScreen
import brobata.physiboard.app.settings.ui.screens.CustomSubstitutionsEditScreen
import brobata.physiboard.app.settings.ui.screens.CustomSubstitutionsScreen
import brobata.physiboard.app.settings.ui.screens.CustomizeEntriesScreen
import brobata.physiboard.app.settings.ui.screens.ManageSnippetsScreen
import brobata.physiboard.app.settings.ui.screens.QuickLauncherEntriesScreen
import brobata.physiboard.app.settings.ui.screens.KeyMappingScreen
import brobata.physiboard.app.settings.ui.screens.NotificationRingScreen
import brobata.physiboard.app.settings.ui.screens.RemoveBloatScreen
import brobata.physiboard.app.settings.ui.screens.RingFitScreen
import brobata.physiboard.app.settings.ui.screens.ScreenDensityScreen
import brobata.physiboard.app.settings.ui.screens.SmartBacklightScreen
import brobata.physiboard.app.settings.ui.screens.SystemTweaksScreen
import brobata.physiboard.app.settings.ui.screens.PersonalDictionaryScreen
import brobata.physiboard.app.settings.ui.screens.InstalledDictionariesScreen
import brobata.physiboard.app.settings.ui.screens.InputStylesScreen
import brobata.physiboard.app.settings.ui.screens.KeyboardLayoutScreen
import brobata.physiboard.app.settings.ui.screens.LayoutViewerScreen
import brobata.physiboard.app.settings.ui.screens.SavedThemesScreen
import brobata.physiboard.app.settings.ui.screens.ThemeLayoutOverridesScreen
import brobata.physiboard.app.settings.ui.screens.ThemeLayoutOverrideEditScreen
import brobata.physiboard.app.settings.ui.screens.CustomizeSymKeyboardScreen

/**
 * The whole settings app as one push/pop stack (settings-catalog.md SS9.1, "a push/pop stack
 * inside one activity"). [Routes.HOME] is the home screen and the category index (app-shell.md
 * SS6); [startDestination] lets the caller (`MainActivity`) route to [Routes.SETUP] or
 * [Routes.WHATS_NEW] instead on a cold start (SS3). The index's categories and the screens each
 * one opens are in docs/plans/settings-reorganization.md.
 */
@Composable
fun SettingsApp(startDestination: String = Routes.HOME) {
    val navController: NavHostController = rememberNavController()
    fun navigate(route: String) { navController.navigate(route) }
    fun back() { navController.popBackStack() }

    val reduced = rememberReducedMotion()
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val undo = remember(scope) { UndoController(scope, haptic) }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalUndo provides undo) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                // spec: app-shell.md SS22.2. Every push slides the new screen in from the right while the
                // one underneath drifts a fifth of the way left and dims a little, so the stack reads as
                // depth; a pop reverses both. Springs, critically damped (SettingsMotion), so a page never
                // wobbles and an interrupted transition carries its speed into the next one. The same
                // pop transition is what Android 14's predictive back gesture scrubs through: holding the
                // back swipe shows the screen underneath sliding into place. With animations removed
                // (reduced motion) a screen simply appears.
                enterTransition = { if (reduced) EnterTransition.None else slideInHorizontally(SettingsMotion.page) { fullWidth -> fullWidth } },
                exitTransition = { if (reduced) ExitTransition.None else slideOutHorizontally(SettingsMotion.page) { fullWidth -> -fullWidth / 5 } + fadeOut(SettingsMotion.pageFade, targetAlpha = 0.6f) },
                popEnterTransition = { if (reduced) EnterTransition.None else slideInHorizontally(SettingsMotion.page) { fullWidth -> -fullWidth / 5 } + fadeIn(SettingsMotion.pageFade, initialAlpha = 0.6f) },
                popExitTransition = { if (reduced) ExitTransition.None else slideOutHorizontally(SettingsMotion.page) { fullWidth -> fullWidth } },
            ) {
                composable(Routes.HOME) { HomeScreen(onNavigate = ::navigate) }
                composable(Routes.SETUP) {
                    SetupScreen(onComplete = { navController.navigate(Routes.HOME) { popUpTo(0) } })
                }
                composable(Routes.WHATS_NEW) {
                    WhatsNewScreen(onDone = { navController.navigate(Routes.HOME) { popUpTo(0) } })
                }
                composable(Routes.STATUS) { StatusScreen(onBack = ::back) }
                composable(Routes.ABOUT) { AboutScreen(onBack = ::back) }
                composable(Routes.HELP) {
                    HelpScreen(
                        onBack = ::back,
                        onNavigate = ::navigate,
                        onShowTutorial = {
                            // spec: SS3. Opens the first-run pages directly and leaves tutorial_completed as
                            // it is (3.2): backing out of a review must not make the launcher open them later.
                            navController.navigate(Routes.SETUP)
                        },
                    )
                }
                composable(Routes.BACKUP) { BackupScreen(onBack = ::back) }
                composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = ::back) }
                composable(Routes.APP_LANGUAGE) { AppLanguageScreen(onBack = ::back) }
                // The category screens of the home index.
                composable(Routes.TYPING) { TypingScreen(onBack = ::back, onNavigate = ::navigate) }
                composable(Routes.KEYS) { KeysScreen(onBack = ::back, onNavigate = ::navigate) }
                composable(Routes.APPS) { AppsScreen(onBack = ::back, onNavigate = ::navigate) }
                composable(Routes.LOOK) { LookScreen(onBack = ::back, onNavigate = ::navigate) }
                composable(Routes.T2E_TOOLS) { T2EToolsScreen(onBack = ::back, onNavigate = ::navigate) }

                // broker-privileged-toolbox.md, device-backlight-ring.md: the T2E Tools toolbox screens.
                composable(Routes.SMART_BACKLIGHT) { SmartBacklightScreen(onBack = ::back, onNavigateToolbox = { navigate(Routes.T2E_TOOLS) }) }
                // spec: broker-privileged-toolbox.md SS3, SS12.1: "that button navigates to the Smart
                // keyboard backlight screen", not the hub these three previously sent it to.
                composable(Routes.REMOVE_BLOAT) { RemoveBloatScreen(onBack = ::back, onNavigateToolbox = { navigate(Routes.SMART_BACKLIGHT) }) }
                composable(Routes.SCREEN_DENSITY) { ScreenDensityScreen(onBack = ::back, onNavigateToolbox = { navigate(Routes.SMART_BACKLIGHT) }) }
                composable(Routes.SYSTEM_TWEAKS) { SystemTweaksScreen(onBack = ::back, onNavigateToolbox = { navigate(Routes.SMART_BACKLIGHT) }) }
                composable(Routes.NOTIFICATION_RING) {
                    NotificationRingScreen(onBack = ::back, onNavigateFit = { navigate(Routes.RING_FIT) }, onNavigateToolbox = { navigate(Routes.T2E_TOOLS) })
                }
                composable(Routes.RING_FIT) { RingFitScreen(onDone = ::back) }
                composable(Routes.KEY_MAPPING) { KeyMappingScreen(onBack = ::back, onNavigate = ::navigate) }

                composable(Routes.SCREEN_TRACKPAD) { ScreenTrackpadScreen(onBack = ::back) }
                composable(Routes.FN_LAYER) { FnLayerScreen(onBack = ::back) }
                composable(Routes.ACCESSIBILITY_SERVICE) { AccessibilityServiceScreen(onBack = ::back) }
                composable(Routes.PUNCTUATION_SPACING) { PunctuationSpacingScreen(onBack = ::back) }
                composable(Routes.AUTO_CORRECTION) { AutoCorrectionScreen(onBack = ::back, onNavigate = ::navigate) }
                composable(Routes.VOICE) { VoiceScreen(onBack = ::back) }
                composable(Routes.THEME) { ThemeScreen(onBack = ::back, onNavigate = ::navigate) }
                composable(Routes.CUSTOMIZE_COLORS) { ThemeColorsScreen(onBack = ::back) }
                composable(Routes.SOUND_HAPTICS) { SoundHapticsScreen(onBack = ::back) }
                composable(Routes.LONG_PRESS) { LongPressScreen(onBack = ::back, onNavigate = ::navigate) }
                composable(Routes.CUSTOMIZE_VARIATIONS) { CustomizeVariationsScreen(onBack = ::back) }
                composable(Routes.ENTER_KEY_BEHAVIOUR) { EnterKeyBehaviourScreen(onBack = ::back, onNavigate = ::navigate) }

                composable(Routes.QUICK_LAUNCHER) { QuickLauncherScreen(onBack = ::back, onNavigate = ::navigate) }
                composable(Routes.INPUT_LANGUAGES) { InputLanguagesScreen(onBack = ::back, onNavigate = ::navigate) }
                composable(Routes.TEXT_EXPANSION) { TextExpansionScreen(onBack = ::back, onNavigate = ::navigate) }

                // dictionaries-languages.md SS6, SS7, SS8.2; status-bar.md SS9.2-9.4: the list editors.
                composable(Routes.PERSONAL_DICTIONARY) { PersonalDictionaryScreen(onBack = ::back) }
                composable(Routes.INSTALLED_DICTIONARIES) { InstalledDictionariesScreen(onBack = ::back) }
                composable(Routes.INPUT_STYLES) { InputStylesScreen(onBack = ::back) }
                // layers-sym-alt.md SS9.6, SS9.7: the Keyboard Layout screen and its layout viewer.
                composable(Routes.KEYBOARD_LAYOUT) { KeyboardLayoutScreen(onBack = ::back, onView = { layoutId -> navigate(Routes.layoutViewer(layoutId)) }) }
                composable(
                    Routes.LAYOUT_VIEWER_PATTERN,
                    arguments = listOf(navArgument("layoutId") { type = NavType.StringType }),
                ) { entry ->
                    LayoutViewerScreen(layoutId = entry.arguments?.getString("layoutId").orEmpty(), onBack = ::back)
                }
                composable(Routes.SAVED_THEMES) { SavedThemesScreen(onBack = ::back) }
                composable(Routes.THEME_LAYOUT_OVERRIDES) { ThemeLayoutOverridesScreen(onBack = ::back, onOpen = { index -> navigate(Routes.themeLayoutOverride(index)) }) }
                composable(
                    Routes.THEME_LAYOUT_OVERRIDE_PATTERN,
                    arguments = listOf(navArgument("index") { type = NavType.IntType }),
                ) { entry ->
                    ThemeLayoutOverrideEditScreen(index = entry.arguments?.getInt("index") ?: -1, onBack = ::back)
                }

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
                composable(Routes.PRIVACY) { PrivacyScreen(onBack = ::back) }

                composable(Routes.TEST_FIELD) { TestFieldScreen(onBack = ::back) }

                composable(
                    Routes.APP_PICKER_PATTERN,
                    arguments = listOf(navArgument("kind") { type = NavType.StringType }),
                ) { entry ->
                    AppPickerScreen(kind = entry.arguments?.getString("kind").orEmpty(), onBack = ::back)
                }

                // layers-sym-alt.md SS5.9: "Customize SYM Keyboard"; the query args are only non-default
                // when the keyboard itself opened this screen (SS5.8's intent extras).
                composable(
                    Routes.CUSTOMIZE_SYM_KEYBOARD_PATTERN,
                    arguments = listOf(
                        navArgument("page") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("keyCode") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("openPicker") { type = NavType.BoolType; defaultValue = false },
                        navArgument("returnAfterPicker") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { entry ->
                    val context = androidx.compose.ui.platform.LocalContext.current
                    CustomizeSymKeyboardScreen(
                        initialPage = entry.arguments?.getInt("page") ?: 0,
                        initialKeyCode = entry.arguments?.getInt("keyCode") ?: -1,
                        openPickerImmediately = entry.arguments?.getBoolean("openPicker") ?: false,
                        returnAfterPicker = entry.arguments?.getBoolean("returnAfterPicker") ?: false,
                        onBack = ::back,
                        onFinishActivity = { (context as? android.app.Activity)?.finish() },
                        onNavigate = ::navigate,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = undo.snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
        ) { data -> UndoSnackbar(data) }
    }
}
