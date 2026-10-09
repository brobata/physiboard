package brobata.physiboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import brobata.physiboard.app.settings.ui.LocalHaptics
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.PhysiBoardColors
import brobata.physiboard.app.settings.ui.PhysiBoardTheme
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsApp
import brobata.physiboard.app.settings.ui.rememberSettingsController
import brobata.physiboard.core.actions.picker.SymCustomizationLink
import brobata.physiboard.core.shell.LaunchDestination
import brobata.physiboard.core.shell.LaunchRouting
import brobata.physiboard.ime.feedback.HapticPlayer
import kotlinx.coroutines.flow.first

/**
 * The settings app (settings-catalog.md SS9, "the settings app"): a single activity holding the
 * whole push/pop screen stack (SettingsApp / SettingsNavHost). The milestone-3 typing field this
 * class used to be the entire content of now lives at the "Test field" row
 * (rebuild-from-scratch.md, "Leave MainActivity's typing field reachable from a Test field row").
 *
 * Launch routing (app-shell.md SS3) waits for the gated settings flow's first real emission (past
 * the 2.x import) before drawing anything, then builds the NavHost with that one destination:
 * `NavHost`'s start destination is fixed when the graph is built, so deciding it from a `State`
 * that can still flip from the shipped defaults to the imported values would either flash setup
 * for an existing install or leave the graph pointed at the wrong screen once the real value
 * arrives.
 */
class MainActivity : ComponentActivity() {
    /** Set once launch routing has picked the first screen; the splash holds until then (SS22.6). */
    @Volatile
    private var firstScreenReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // app-shell.md SS22.6: the keycap mark on the page colour while the settings load, held
        // until launch routing has a destination, and never longer than SPLASH_HOLD_MAX_MS.
        val splashStart = android.os.SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition {
            !firstScreenReady && android.os.SystemClock.uptimeMillis() - splashStart < SPLASH_HOLD_MAX_MS
        }
        super.onCreate(savedInstanceState)
        // spec: app-shell.md SS22.1, "no-action-bar Material window with status and navigation
        // bars in the splash colours (dark or light variant), and edge-to-edge is enabled on
        // every activity." SystemBarStyle.auto switches the scrim and icon contrast with the
        // system's own dark/light state, which is what the theme itself follows too.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(PhysiBoardColors.Cloud.toArgb(), PhysiBoardColors.Ink.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(PhysiBoardColors.Cloud.toArgb(), PhysiBoardColors.Ink.toArgb()),
        )
        val application = application as PhysiBoardApplication
        setContent {
            val controller = rememberSettingsController(application.settingsSource.settings, application.settingsStore)
            // keys-and-modifiers.md SS13.5: the app speaks the same haptic language as the
            // keyboard, behind the same `event_haptics` switch and the system's touch feedback.
            val haptics = remember { HapticPlayer(this) }
            DisposableEffect(haptics) { onDispose { haptics.release() } }
            val feedback = controller.current.value.feedback
            SideEffect {
                haptics.eventHaptics = feedback.eventHaptics
                haptics.keyHaptics = feedback.keyHaptics
                haptics.keyStrength = feedback.keyHapticStrength
            }
            var startDestination by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                // spec: layers-sym-alt.md SS5.8: the Sym grid's pencil and a key long-press open
                // this activity straight at "Customize SYM Keyboard" (and its picker, for a long
                // press) rather than the ordinary launch routing below.
                val symExtras = intent?.extras
                if (symExtras != null && symExtras.containsKey(SymCustomizationLink.EXTRA_INITIAL_SYM_PAGE)) {
                    startDestination = Routes.customizeSymKeyboard(
                        page = symExtras.getInt(SymCustomizationLink.EXTRA_INITIAL_SYM_PAGE, 0),
                        keyCode = symExtras.getInt(SymCustomizationLink.EXTRA_INITIAL_SYM_KEY_CODE, -1),
                        openPicker = symExtras.getBoolean(SymCustomizationLink.EXTRA_OPEN_SYM_PICKER, false),
                        returnAfterPicker = symExtras.getBoolean(SymCustomizationLink.EXTRA_RETURN_AFTER_PICKER, false),
                    )
                    return@LaunchedEffect
                }
                val shell = application.settingsSource.settings.first().shell
                startDestination = when (LaunchRouting.decide(shell.tutorialCompleted, shell.lastSeenWhatsNewVersion.ifBlank { null }, BuildConfig.VERSION_NAME)) {
                    LaunchDestination.SETUP -> Routes.SETUP
                    LaunchDestination.WHATS_NEW -> Routes.WHATS_NEW
                    LaunchDestination.HOME -> Routes.HOME
                }
            }
            PhysiBoardTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalSettingsController provides controller, LocalHaptics provides haptics) {
                        startDestination?.let {
                            SideEffect { firstScreenReady = true }
                            SettingsApp(startDestination = it)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        /** The longest the splash waits for the settings; after that the page draws as it always did. */
        const val SPLASH_HOLD_MAX_MS = 1_500L
    }
}
