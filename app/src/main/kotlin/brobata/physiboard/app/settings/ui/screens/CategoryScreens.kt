package brobata.physiboard.app.settings.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import brobata.physiboard.app.R
import brobata.physiboard.app.settings.ui.AboutExpander
import brobata.physiboard.app.settings.ui.InfoText
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.PerAppListKind
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.Summaries
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.actions.launcher.AssignableKeys
import brobata.physiboard.core.actions.launcher.LauncherShortcuts
import brobata.physiboard.core.settings.StatusBarPrefs
import brobata.physiboard.core.shell.AppLocale

/**
 * "Keys & shortcuts" (docs/plans/settings-reorganization.md): everything that changes what a key
 * does, as opposed to what gets typed. Key mapping is the map of the whole keyboard; the Fn layer
 * and the screen trackpad are the two ways to move the cursor; the quick launcher is the one way
 * to open apps from the keys. Each row says where it stands now.
 */
@Composable
fun KeysScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val settings = LocalSettingsController.current.current.value
    val launcherKey = LauncherShortcuts.parse(settings.launcher.assignedKeysJson)
        .applyDefault(defaultAlreadyAssigned = settings.launcher.assignedKeysJson.isNotBlank())
        .shortcuts.quickLauncherKeycode
        ?.let { "Sym + ${AssignableKeys.label(it)}" }

    SettingsScreenScaffold(title = "Keys & shortcuts", onBack = onBack) {
        RowList {
            header("")
            item {
                NavigateRow("Key mapping", "What every key does, in the phone and in PhysiBoard", icon = Icons.Outlined.KeyboardCommandKey) {
                    onNavigate(Routes.KEY_MAPPING)
                }
            }
            header("Moving the cursor")
            item {
                NavigateRow(
                    "Fn layer",
                    "Fn with a letter moves the cursor, selects and edits",
                    icon = Icons.Outlined.Functions,
                    value = Summaries.onOff(settings.keys.navModeEnabled),
                ) { onNavigate(Routes.FN_LAYER) }
            }
            item {
                NavigateRow(
                    "Screen trackpad",
                    "Hold a key and swipe on the screen to move the cursor",
                    icon = Icons.Outlined.TouchApp,
                    value = Summaries.onOff(settings.trackpad.enabled),
                ) { onNavigate(Routes.SCREEN_TRACKPAD) }
            }
            header("Opening apps")
            item {
                NavigateRow(
                    "Quick launcher",
                    "Find and open apps and actions from the keyboard",
                    icon = Icons.Outlined.RocketLaunch,
                    value = launcherKey,
                ) { onNavigate(Routes.QUICK_LAUNCHER) }
            }
        }
    }
}

/**
 * "Apps" (docs/plans/settings-reorganization.md): the rules that apply to some apps and not
 * others. Terminal mode turns every typing aid off in the apps picked; Enter key decides whether
 * Enter sends or starts a new line in messaging apps.
 */
@Composable
fun AppsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val perApp = LocalSettingsController.current.current.value.perApp
    val terminalCount = perApp.exactTypingPackages.size

    SettingsScreenScaffold(title = "Apps", onBack = onBack) {
        RowList {
            plainItem { InfoText("Rules for particular apps. Everywhere else, PhysiBoard behaves as the rest of these settings say.") }
            header("")
            item {
                NavigateRow(
                    "Terminal mode",
                    "Raw typing for terminals, SSH and code: nothing corrected or capitalised",
                    icon = Icons.Outlined.Terminal,
                    value = if (terminalCount == 0) "None" else if (terminalCount == 1) "1 app" else "$terminalCount apps",
                ) { onNavigate(Routes.appPicker(PerAppListKind.EXACT_TYPING)) }
            }
            item {
                NavigateRow(
                    "Enter key",
                    "Whether Enter sends or starts a new line, per app",
                    icon = Icons.AutoMirrored.Outlined.KeyboardReturn,
                    value = if (perApp.enterBehaviorEnabled) Summaries.enterPresetShort(perApp.enterPreset) else "Off",
                ) { onNavigate(Routes.ENTER_KEY_BEHAVIOUR) }
            }
        }
    }
}

/**
 * "Look & feel" (docs/plans/settings-reorganization.md): what PhysiBoard looks and sounds like.
 * The Sym pages' theme, key sounds and vibration, the modifier badge drawn at the text cursor
 * (moved here from the Theme page: it has its own colours, not the theme's), and the language
 * this settings app is written in.
 */
@Composable
fun LookScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val controller = LocalSettingsController.current
    val settings = controller.current.value
    val statusBar = settings.statusBar
    fun set(transform: (StatusBarPrefs) -> StatusBarPrefs) = controller.update { it.copy(statusBar = transform(it.statusBar)) }
    val appLanguage = AppLocale.resolve(settings.languages.appLanguageTag)?.let { AppLocale.nativeName(it) }
        ?: stringResource(R.string.app_language_system_default)

    SettingsScreenScaffold(title = "Look & feel", onBack = onBack) {
        RowList {
            header("")
            item {
                NavigateRow("Theme", "The colours of the Sym pages and panels", icon = Icons.Outlined.Palette, value = Summaries.themeName(settings)) {
                    onNavigate(Routes.THEME)
                }
            }
            item {
                NavigateRow(
                    "Sound & haptics",
                    "Key sounds and vibration",
                    icon = Icons.Outlined.Vibration,
                    value = Summaries.soundLabel(settings.feedback.typingSoundMode).replaceFirstChar { it.uppercase() },
                ) { onNavigate(Routes.SOUND_HAPTICS) }
            }
            header("Modifier badge")
            item {
                SwitchRow(
                    "Show Shift, Alt, Ctrl and Sym at the cursor",
                    description = "A small badge beside the text cursor while a modifier is on.",
                    checked = statusBar.caretModifierBadge,
                    onCheckedChange = { checked -> set { p -> p.copy(caretModifierBadge = checked) } },
                )
                AboutExpander(
                    title = "Where it shows",
                    text = "Only in apps that tell the keyboard where the cursor is; most text boxes do, some web pages and terminals do not. " +
                        "The badge is drawn in the first colour after a single press and in the second while the modifier is locked. " +
                        "In private mode it reads PRIVATE.",
                )
            }
            if (statusBar.caretModifierBadge) {
                item { ThemeColorRow("Colour after one press", null, statusBar.caretBadgeArmedColor) { v -> set { p -> p.copy(caretBadgeArmedColor = v) } } }
                item { ThemeColorRow("Colour while locked", null, statusBar.caretBadgeLockedColor) { v -> set { p -> p.copy(caretBadgeLockedColor = v) } } }
            }
            header("")
            item {
                NavigateRow(stringResource(R.string.app_language_title), "The language of this settings app", icon = Icons.Outlined.Language, value = appLanguage) {
                    onNavigate(Routes.APP_LANGUAGE)
                }
            }
        }
    }
}
