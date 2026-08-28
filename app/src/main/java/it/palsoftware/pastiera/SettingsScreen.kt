package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Engineering
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import android.widget.Toast
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.update.checkForUpdate
import it.palsoftware.pastiera.update.showUpdateDialog
import it.palsoftware.pastiera.update.shouldUseGithubUpdateChecks

/**
 * Sealed class describing the settings navigation state.
 */
enum class SettingsDestination {
    Main,
    Status,
    KeyboardTiming,
    TextInput,
    Accessibility,
    AutoCorrection,
    Customization,
    NavMode,
    ScreenTrackpad,
    Advanced,
    About,
    CustomInputStyles,
    AppLanguage,
    DeviceSymLayerEditor,
    Modifiers,
    AppRawMode,
    SmartBacklight,
    Voice,
    Toolbox,
    BloatRemover,
    DisplayDensity,
    SystemTweaks,
    NotificationRing,
    KeyboardHub,
    Extras,
    KeyMapping
}

private val settingsNavigationStackSaver =
    listSaver<SnapshotStateList<SettingsDestination>, String>(
        save = { stack -> stack.map(SettingsDestination::name) },
        restore = { routes ->
            mutableStateListOf<SettingsDestination>().apply {
                addAll(routes.map(SettingsDestination::valueOf))
            }
        }
    )

/**
 * App settings screen.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    initialDestination: String? = null,
    initialCustomizationDestination: String? = null,
    initialKeyboardThemeTarget: String? = null
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    
    var checkingForUpdates by remember { mutableStateOf(false) }
    var navigationDirection by remember { mutableStateOf(NavigationDirection.Push) }
    var requestedCustomizationDestination by rememberSaveable {
        mutableStateOf(
            initialCustomizationDestination
                ?: if (initialDestination == SettingsActivity.DESTINATION_KEYBOARD_THEME)
                    SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME
                else null
        )
    }
    var requestedNavModeKeyCode by rememberSaveable { mutableStateOf<Int?>(null) }
    val navigationStack = rememberSaveable(saver = settingsNavigationStackSaver) {
        mutableStateListOf<SettingsDestination>().apply {
            if (initialDestination == SettingsActivity.DESTINATION_CUSTOMIZATION) {
                if (initialCustomizationDestination == null) {
                    add(SettingsDestination.Main)
                }
                add(SettingsDestination.Customization)
            } else if (initialDestination == SettingsActivity.DESTINATION_DEVICE_SYM_LAYER_EDITOR) {
                add(SettingsDestination.DeviceSymLayerEditor)
            } else if (initialDestination == SettingsActivity.DESTINATION_MODIFIERS) {
                add(SettingsDestination.Modifiers)
            } else if (initialDestination == SettingsActivity.DESTINATION_SMART_BACKLIGHT) {
                add(SettingsDestination.Main)
                add(SettingsDestination.SmartBacklight)
            } else if (initialDestination == SettingsActivity.DESTINATION_TOOLBOX) {
                add(SettingsDestination.Toolbox)
            } else if (initialDestination == SettingsActivity.DESTINATION_KEYBOARD_HUB) {
                add(SettingsDestination.KeyboardHub)
            } else if (initialDestination == SettingsActivity.DESTINATION_EXTRAS) {
                add(SettingsDestination.Extras)
            } else if (initialDestination == SettingsActivity.DESTINATION_REMOVE_BLOAT) {
                add(SettingsDestination.Toolbox)
                add(SettingsDestination.BloatRemover)
            } else if (initialDestination == SettingsActivity.DESTINATION_SCREEN_TRACKPAD) {
                add(SettingsDestination.Main)
                add(SettingsDestination.ScreenTrackpad)
            } else if (initialDestination == SettingsActivity.DESTINATION_INPUT_LANGUAGES) {
                add(SettingsDestination.Main)
                add(SettingsDestination.CustomInputStyles)
            } else if (initialDestination == SettingsActivity.DESTINATION_KEYBOARD_THEME) {
                add(SettingsDestination.Main)
                add(SettingsDestination.Customization)
            } else if (initialDestination == SettingsActivity.DESTINATION_SMART_FEATURES) {
                add(SettingsDestination.Main)
                add(SettingsDestination.TextInput)
            } else if (initialDestination == SettingsActivity.DESTINATION_AUTO_CORRECT) {
                add(SettingsDestination.Main)
                add(SettingsDestination.AutoCorrection)
            } else if (initialDestination == SettingsActivity.DESTINATION_VOICE) {
                add(SettingsDestination.Main)
                add(SettingsDestination.Voice)
            } else if (initialDestination == SettingsActivity.DESTINATION_RAW_MODE) {
                add(SettingsDestination.Main)
                add(SettingsDestination.AppRawMode)
            } else if (initialDestination == SettingsActivity.DESTINATION_FN_LAYER) {
                add(SettingsDestination.Main)
                add(SettingsDestination.NavMode)
            } else {
                add(SettingsDestination.Main)
            }
        }
    }
    val currentDestination by remember {
        derivedStateOf { navigationStack.last() }
    }
    
    fun navigateTo(destination: SettingsDestination) {
        if (currentDestination == destination) return
        navigationDirection = NavigationDirection.Push
        navigationStack.add(destination)
    }
    
    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationDirection = NavigationDirection.Pop
            navigationStack.removeAt(navigationStack.lastIndex)
        } else {
            activity?.finish()
        }
    }

    fun openCustomization(destination: String?) {
        requestedCustomizationDestination = destination
        navigateTo(SettingsDestination.Customization)
    }
    
    // Automatic update check on screen open (only once, respecting dismissed releases)
    if (shouldUseGithubUpdateChecks(context)) {
        LaunchedEffect(Unit) {
            checkForUpdate(
                context = context,
                currentVersion = BuildConfig.VERSION_NAME,
                releaseChannel = BuildConfig.RELEASE_CHANNEL,
                ignoreDismissedReleases = true
            ) { hasUpdate, latestVersion, downloadUrl, releasePageUrl ->
                if (hasUpdate && latestVersion != null) {
                    showUpdateDialog(context, latestVersion, downloadUrl, releasePageUrl)
                }
            }
        }
    }
    
    // Handle system back button
    BackHandler { navigateBack() }
    
    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (navigationDirection == NavigationDirection.Push) {
                // Forward navigation: new screen enters from right, old screen exits to left
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(250)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(250)
                )
            } else {
                // Back navigation: current screen exits to right, previous screen enters from left
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(250)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(250)
                )
            }
        },
        label = "settings_navigation",
        contentKey = { it }
    ) { destination ->
        when (destination) {
            SettingsDestination.Main -> {
                SettingsMainScreen(
                    modifier = modifier,
                    context = context,
                    checkingForUpdates = checkingForUpdates,
                    onCheckingForUpdatesChange = { checkingForUpdates = it },
                    onStatusClick = { navigateTo(SettingsDestination.Status) },
                    onToolboxClick = { navigateTo(SettingsDestination.Toolbox) },
                    onKeyboardHubClick = { navigateTo(SettingsDestination.KeyboardHub) },
                    onExtrasClick = { navigateTo(SettingsDestination.Extras) },
                    onModifiersClick = { navigateTo(SettingsDestination.Modifiers) },
                    onTextInputClick = { navigateTo(SettingsDestination.TextInput) },
                    onVoiceClick = { navigateTo(SettingsDestination.Voice) },
                    onScreenTrackpadClick = { navigateTo(SettingsDestination.ScreenTrackpad) },
                    onSoundHapticsClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_SOUNDS)
                    },
                    onAccessibilityClick = { navigateTo(SettingsDestination.Accessibility) },
                    onAutoCorrectionClick = { navigateTo(SettingsDestination.AutoCorrection) },
                    onAppRawModeClick = { navigateTo(SettingsDestination.AppRawMode) },
                    onSmartBacklightClick = { navigateTo(SettingsDestination.SmartBacklight) },
                    onCustomizationClick = { openCustomization(null) },
                    onStatusBarButtonsClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS)
                    },
                    onKeyboardThemeClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME)
                    },
                    onQuickLauncherClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS)
                    },
                    onNavModeClick = {
                        requestedNavModeKeyCode = null
                        navigateTo(SettingsDestination.NavMode)
                    },
                    onEnterBehaviorClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR)
                    },
                    onAdvancedClick = { navigateTo(SettingsDestination.Advanced) },
                    onAboutClick = { navigateTo(SettingsDestination.About) },
                    onBackClick = { navigateBack() },
                    onCustomInputStylesClick = { navigateTo(SettingsDestination.CustomInputStyles) },
                    onAppLanguageClick = { navigateTo(SettingsDestination.AppLanguage) }
                )
            }
            SettingsDestination.Status -> {
                StatusScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.ScreenTrackpad -> {
                ScreenTrackpadSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.KeyboardTiming -> {
                KeyboardTimingSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.TextInput -> {
                TextInputSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onNavModeSettingsClick = { navigateTo(SettingsDestination.NavMode) }
                )
            }
            SettingsDestination.Accessibility -> {
                AccessibilitySettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.AutoCorrection -> {
                AutoCorrectionCategoryScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.AppRawMode -> {
                AppRawModeScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.SmartBacklight -> {
                SmartBacklightScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.Toolbox -> {
                SettingsHubScreen(
                    modifier = modifier,
                    title = stringResource(R.string.toolbox_title),
                    intro = stringResource(R.string.toolbox_intro),
                    onBack = { navigateBack() },
                    rows = listOf(
                        HubRow(
                            icon = Icons.Filled.LightMode,
                            title = stringResource(R.string.smart_backlight_title),
                            description = stringResource(R.string.smart_backlight_row_description),
                            onClick = { navigateTo(SettingsDestination.SmartBacklight) }
                        ),
                        HubRow(
                            icon = Icons.Filled.DeleteSweep,
                            title = stringResource(R.string.bloat_title),
                            description = stringResource(R.string.bloat_row_description),
                            onClick = { navigateTo(SettingsDestination.BloatRemover) }
                        ),
                        HubRow(
                            icon = Icons.Filled.FormatSize,
                            title = stringResource(R.string.density_title),
                            description = stringResource(R.string.density_row_description),
                            onClick = { navigateTo(SettingsDestination.DisplayDensity) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.tweaks_title),
                            description = stringResource(R.string.tweaks_row_description),
                            onClick = { navigateTo(SettingsDestination.SystemTweaks) }
                        ),
                        HubRow(
                            icon = Icons.Filled.NotificationsActive,
                            title = stringResource(R.string.ring_title),
                            description = stringResource(R.string.ring_row_description),
                            onClick = { navigateTo(SettingsDestination.NotificationRing) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Keyboard,
                            title = stringResource(R.string.keymap_title),
                            description = stringResource(R.string.keymap_row_description),
                            onClick = { navigateTo(SettingsDestination.KeyMapping) }
                        )
                    )
                )
            }
            SettingsDestination.KeyMapping -> {
                KeyMappingScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onFnLayer = {
                        requestedNavModeKeyCode = null
                        navigateTo(SettingsDestination.NavMode)
                    },
                    onVoice = { navigateTo(SettingsDestination.Voice) },
                    onTrackpad = { navigateTo(SettingsDestination.ScreenTrackpad) }
                )
            }
            SettingsDestination.KeyboardHub -> {
                SettingsHubScreen(
                    modifier = modifier,
                    title = stringResource(R.string.keyboard_hub_title),
                    intro = stringResource(R.string.keyboard_hub_intro),
                    onBack = { navigateBack() },
                    rows = listOf(
                        HubRow(
                            icon = Icons.Filled.TextFields,
                            title = stringResource(R.string.settings_category_text_input),
                            description = stringResource(R.string.keyboard_hub_typing_description),
                            onClick = { navigateTo(SettingsDestination.TextInput) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Spellcheck,
                            title = stringResource(R.string.settings_category_auto_correction),
                            description = stringResource(R.string.keyboard_hub_autocorrect_description),
                            onClick = { navigateTo(SettingsDestination.AutoCorrection) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Mic,
                            title = stringResource(R.string.settings_category_voice),
                            description = stringResource(R.string.keyboard_hub_voice_description),
                            onClick = { navigateTo(SettingsDestination.Voice) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Gesture,
                            title = stringResource(R.string.screen_trackpad_title),
                            description = stringResource(R.string.screen_trackpad_description),
                            onClick = { navigateTo(SettingsDestination.ScreenTrackpad) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.keyboard_theme_title),
                            description = stringResource(R.string.keyboard_hub_theme_description),
                            onClick = {
                                openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME)
                            }
                        ),
                        HubRow(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            title = stringResource(R.string.settings_category_sounds),
                            description = stringResource(R.string.settings_sounds_description),
                            onClick = {
                                openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_SOUNDS)
                            }
                        ),
                        HubRow(
                            icon = ImageVector.vectorResource(R.drawable.modifier_keys_24),
                            title = stringResource(R.string.modifiers_title),
                            description = stringResource(R.string.modifiers_description),
                            onClick = { navigateTo(SettingsDestination.Modifiers) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Block,
                            title = stringResource(R.string.app_raw_mode_title),
                            description = stringResource(R.string.app_raw_mode_row_description),
                            onClick = { navigateTo(SettingsDestination.AppRawMode) }
                        ),
                        HubRow(
                            icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                            title = stringResource(R.string.app_enter_behaviour_title),
                            description = stringResource(R.string.app_enter_behaviour_description),
                            onClick = {
                                openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR)
                            }
                        )
                    )
                )
            }
            SettingsDestination.Extras -> {
                SettingsHubScreen(
                    modifier = modifier,
                    title = stringResource(R.string.extras_title),
                    intro = stringResource(R.string.extras_intro),
                    onBack = { navigateBack() },
                    rows = listOf(
                        HubRow(
                            icon = Icons.AutoMirrored.Filled.ManageSearch,
                            title = stringResource(R.string.starter_launcher_shortcuts_title),
                            description = stringResource(R.string.starter_launcher_shortcuts_description),
                            onClick = {
                                openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS)
                            }
                        ),
                        HubRow(
                            icon = Icons.Filled.SmartButton,
                            title = stringResource(R.string.status_bar_buttons_title),
                            description = stringResource(R.string.status_bar_buttons_description),
                            onClick = {
                                openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS)
                            }
                        ),
                        HubRow(
                            icon = Icons.Filled.Tune,
                            title = stringResource(R.string.settings_category_customization),
                            description = stringResource(R.string.extras_customization_description),
                            onClick = { openCustomization(null) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Language,
                            title = stringResource(R.string.custom_input_styles_title),
                            description = stringResource(R.string.extras_languages_description),
                            onClick = { navigateTo(SettingsDestination.CustomInputStyles) }
                        ),
                        HubRow(
                            icon = Icons.Filled.TouchApp,
                            title = stringResource(R.string.settings_category_accessibility),
                            description = stringResource(R.string.extras_accessibility_description),
                            onClick = { navigateTo(SettingsDestination.Accessibility) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Engineering,
                            title = stringResource(R.string.settings_category_advanced),
                            description = stringResource(R.string.toolbox_advanced_description),
                            onClick = { navigateTo(SettingsDestination.Advanced) }
                        ),
                        HubRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.about_title),
                            description = stringResource(
                                R.string.settings_about_version_summary,
                                BuildConfig.VERSION_NAME
                            ),
                            onClick = { navigateTo(SettingsDestination.About) }
                        )
                    )
                )
            }
            SettingsDestination.SystemTweaks -> {
                SystemTweaksScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenPairing = { navigateTo(SettingsDestination.SmartBacklight) }
                )
            }
            SettingsDestination.NotificationRing -> {
                NotificationRingScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenPairing = { navigateTo(SettingsDestination.SmartBacklight) }
                )
            }
            SettingsDestination.DisplayDensity -> {
                DisplayDensityScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenPairing = { navigateTo(SettingsDestination.SmartBacklight) }
                )
            }
            SettingsDestination.BloatRemover -> {
                BloatRemoverScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenPairing = { navigateTo(SettingsDestination.SmartBacklight) }
                )
            }
            SettingsDestination.Voice -> {
                VoiceSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenSoundHaptics = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_SOUNDS)
                    }
                )
            }
            SettingsDestination.Customization -> {
                key(requestedCustomizationDestination, initialKeyboardThemeTarget) {
                    CustomizationSettingsScreen(
                        modifier = modifier,
                        onBack = { navigateBack() },
                        initialDestination = requestedCustomizationDestination,
                        initialKeyboardThemeTarget = initialKeyboardThemeTarget,
                        onOpenModifiers = { navigateTo(SettingsDestination.Modifiers) }
                    )
                }
            }
            SettingsDestination.NavMode -> {
                NavModeSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    initialKeyCode = requestedNavModeKeyCode
                )
            }
            SettingsDestination.Advanced -> {
                AdvancedSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenInputLanguages = { navigateTo(SettingsDestination.CustomInputStyles) }
                )
            }
            SettingsDestination.About -> {
                AboutScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onAppLanguageClick = { navigateTo(SettingsDestination.AppLanguage) }
                )
            }
            SettingsDestination.CustomInputStyles -> {
                CustomInputStylesScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.AppLanguage -> {
                AppLanguageSettingsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.DeviceSymLayerEditor -> {
                DeviceSymLayerEditorStubScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.Modifiers -> {
                ModifierSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenSymLayers = {
                        context.startActivity(Intent(context, SymCustomizationActivity::class.java))
                    },
                    onOpenSymShortcuts = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS)
                    },
                    onOpenNavMode = {
                        requestedNavModeKeyCode = null
                        navigateTo(SettingsDestination.NavMode)
                    }
                )
            }
        }
    }
}

private enum class NavigationDirection {
    Push,
    Pop
}

@Composable
private fun SettingsMainScreen(
    modifier: Modifier,
    context: Context,
    checkingForUpdates: Boolean,
    onCheckingForUpdatesChange: (Boolean) -> Unit,
    onStatusClick: () -> Unit,
    onToolboxClick: () -> Unit,
    onKeyboardHubClick: () -> Unit,
    onExtrasClick: () -> Unit,
    onModifiersClick: () -> Unit,
    onTextInputClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onScreenTrackpadClick: () -> Unit,
    onSoundHapticsClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
    onAutoCorrectionClick: () -> Unit,
    onAppRawModeClick: () -> Unit,
    onSmartBacklightClick: () -> Unit,
    onCustomizationClick: () -> Unit,
    onStatusBarButtonsClick: () -> Unit,
    onKeyboardThemeClick: () -> Unit,
    onQuickLauncherClick: () -> Unit,
    onNavModeClick: () -> Unit,
    onEnterBehaviorClick: () -> Unit,
    onAdvancedClick: () -> Unit,
    onAboutClick: () -> Unit,
    onBackClick: () -> Unit,
    onCustomInputStylesClick: () -> Unit,
    onAppLanguageClick: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchTargetHandler: (SettingsSearchTarget) -> Unit = { target ->
        searchQuery = ""
        when (target) {
            SettingsSearchTarget.MODIFIERS -> onModifiersClick()
            SettingsSearchTarget.TEXT_INPUT -> onTextInputClick()
            SettingsSearchTarget.VOICE -> onVoiceClick()
            SettingsSearchTarget.ACCESSIBILITY -> onAccessibilityClick()
            SettingsSearchTarget.AUTO_CORRECTION -> onAutoCorrectionClick()
            SettingsSearchTarget.APP_RAW_MODE -> onAppRawModeClick()
            SettingsSearchTarget.CUSTOMIZATION -> onCustomizationClick()
            SettingsSearchTarget.STATUS_BAR_BUTTONS -> onStatusBarButtonsClick()
            SettingsSearchTarget.KEYBOARD_THEME -> onKeyboardThemeClick()
            SettingsSearchTarget.QUICK_LAUNCHER -> onQuickLauncherClick()
            SettingsSearchTarget.TOOLBOX -> onToolboxClick()
            SettingsSearchTarget.REMOVE_BLOAT -> onToolboxClick()
            SettingsSearchTarget.NAV_MODE -> onNavModeClick()
            SettingsSearchTarget.SCREEN_TRACKPAD -> onScreenTrackpadClick()
            SettingsSearchTarget.ENTER_BEHAVIOR -> onEnterBehaviorClick()
            SettingsSearchTarget.ADVANCED -> onAdvancedClick()
            SettingsSearchTarget.ABOUT -> onAboutClick()
            SettingsSearchTarget.CUSTOM_INPUT_STYLES -> onCustomInputStylesClick()
            SettingsSearchTarget.APP_LANGUAGE -> onAppLanguageClick()
        }
    }
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val query = searchQuery.trim()
            if (query.isNotEmpty()) {
                val results = SettingsCatalog.entries.filter { entry ->
                    val title = stringResource(entry.titleRes)
                    val screen = stringResource(entry.screenTitleRes)
                    title.contains(query, ignoreCase = true) ||
                        screen.contains(query, ignoreCase = true) ||
                        entry.keywords.contains(query, ignoreCase = true)
                }
                if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_search_no_results, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                results.forEach { entry ->
                    val title = stringResource(entry.titleRes)
                    val screen = stringResource(entry.screenTitleRes)
                    SettingsCategoryRow(
                        icon = Icons.Filled.Search,
                        title = title,
                        description = if (screen != title) {
                            stringResource(R.string.settings_search_result_in, screen)
                        } else null,
                        onClick = { searchTargetHandler(entry.target) }
                    )
                }
                return@Column
            }

            SettingsCategoryRow(
                icon = Icons.Filled.CheckCircle,
                title = stringResource(R.string.settings_category_status),
                description = stringResource(R.string.settings_status_row_description),
                onClick = onStatusClick
            )

            SettingsCategoryRow(
                icon = Icons.Filled.Build,
                title = stringResource(R.string.toolbox_title),
                description = stringResource(R.string.toolbox_row_description),
                onClick = onToolboxClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.Keyboard,
                title = stringResource(R.string.keyboard_hub_title),
                description = stringResource(R.string.keyboard_hub_row_description),
                onClick = onKeyboardHubClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.MoreHoriz,
                title = stringResource(R.string.extras_title),
                description = stringResource(R.string.extras_row_description),
                onClick = onExtrasClick
            )

            SettingsGroupDivider(stringResource(R.string.settings_group_pastiera))

            if (shouldUseGithubUpdateChecks(context)) {
                SettingsCategoryRow(
                    icon = Icons.Filled.Code,
                    title = if (checkingForUpdates) {
                        stringResource(R.string.settings_update_checking)
                    } else {
                        stringResource(R.string.settings_update_section_title)
                    },
                    description = stringResource(R.string.settings_update_section_description),
                    enabled = !checkingForUpdates,
                    onClick = {
                        onCheckingForUpdatesChange(true)
                        checkForUpdate(
                            context = context,
                            currentVersion = BuildConfig.VERSION_NAME,
                            releaseChannel = BuildConfig.RELEASE_CHANNEL,
                            ignoreDismissedReleases = false
                        ) { hasUpdate, latestVersion, downloadUrl, releasePageUrl ->
                            onCheckingForUpdatesChange(false)
                            when {
                                latestVersion == null -> Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_update_check_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                hasUpdate -> showUpdateDialog(context, latestVersion, downloadUrl, releasePageUrl)
                                else -> Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_update_up_to_date),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (description == null) 56.dp else 64.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconTint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsGroupDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
