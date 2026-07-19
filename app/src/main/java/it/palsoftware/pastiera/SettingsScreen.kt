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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.TouchApp
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
 * Sealed class per rappresentare lo stato della navigazione nelle settings.
 */
enum class SettingsDestination {
    Main,
    KeyboardTiming,
    TextInput,
    Accessibility,
    AutoCorrection,
    Customization,
    NavMode,
    Advanced,
    About,
    CustomInputStyles
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
        mutableStateOf(initialCustomizationDestination)
    }
    val navigationStack = rememberSaveable(saver = settingsNavigationStackSaver) {
        mutableStateListOf<SettingsDestination>().apply {
            if (initialDestination == SettingsActivity.DESTINATION_CUSTOMIZATION) {
                if (initialCustomizationDestination == null) {
                    add(SettingsDestination.Main)
                }
                add(SettingsDestination.Customization)
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
        contentKey = { it::class }
    ) { destination ->
        when (destination) {
            SettingsDestination.Main -> {
                SettingsMainScreen(
                    modifier = modifier,
                    context = context,
                    checkingForUpdates = checkingForUpdates,
                    onCheckingForUpdatesChange = { checkingForUpdates = it },
                    onKeyboardTimingClick = { navigateTo(SettingsDestination.KeyboardTiming) },
                    onTextInputClick = { navigateTo(SettingsDestination.TextInput) },
                    onAccessibilityClick = { navigateTo(SettingsDestination.Accessibility) },
                    onAutoCorrectionClick = { navigateTo(SettingsDestination.AutoCorrection) },
                    onCustomizationClick = { openCustomization(null) },
                    onKeyboardThemeClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME)
                    },
                    onQuickLauncherClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS)
                    },
                    onNavModeClick = { navigateTo(SettingsDestination.NavMode) },
                    onEnterBehaviorClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR)
                    },
                    onAdvancedClick = { navigateTo(SettingsDestination.Advanced) },
                    onAboutClick = { navigateTo(SettingsDestination.About) },
                    onBackClick = { navigateBack() },
                    onCustomInputStylesClick = { navigateTo(SettingsDestination.CustomInputStyles) }
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
            SettingsDestination.Customization -> {
                key(requestedCustomizationDestination, initialKeyboardThemeTarget) {
                    CustomizationSettingsScreen(
                        modifier = modifier,
                        onBack = { navigateBack() },
                        initialDestination = requestedCustomizationDestination,
                        initialKeyboardThemeTarget = initialKeyboardThemeTarget
                    )
                }
            }
            SettingsDestination.NavMode -> {
                NavModeSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.Advanced -> {
                AdvancedSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.About -> {
                AboutScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.CustomInputStyles -> {
                CustomInputStylesScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
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
    onKeyboardTimingClick: () -> Unit,
    onTextInputClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
    onAutoCorrectionClick: () -> Unit,
    onCustomizationClick: () -> Unit,
    onKeyboardThemeClick: () -> Unit,
    onQuickLauncherClick: () -> Unit,
    onNavModeClick: () -> Unit,
    onEnterBehaviorClick: () -> Unit,
    onAdvancedClick: () -> Unit,
    onAboutClick: () -> Unit,
    onBackClick: () -> Unit,
    onCustomInputStylesClick: () -> Unit
) {
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
            SettingsGroupDivider(stringResource(R.string.settings_group_keyboard))

            SettingsCategoryRow(
                icon = Icons.Filled.Keyboard,
                title = stringResource(R.string.settings_category_keyboard_timing),
                onClick = onKeyboardTimingClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.custom_input_styles_title),
                onClick = onCustomInputStylesClick
            )

            SettingsGroupDivider(stringResource(R.string.settings_group_typing))

            SettingsCategoryRow(
                icon = Icons.Filled.TextFields,
                title = stringResource(R.string.settings_category_text_input),
                onClick = onTextInputClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.Spellcheck,
                title = stringResource(R.string.settings_category_auto_correction),
                onClick = onAutoCorrectionClick
            )

            SettingsGroupDivider(stringResource(R.string.settings_group_customization))

            SettingsCategoryRow(
                icon = Icons.Filled.Tune,
                title = stringResource(R.string.settings_category_customization),
                onClick = onCustomizationClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.keyboard_theme_title),
                description = stringResource(R.string.keyboard_theme_description),
                onClick = onKeyboardThemeClick
            )

            SettingsGroupDivider(stringResource(R.string.settings_group_utility))

            SettingsCategoryRow(
                icon = Icons.AutoMirrored.Filled.ManageSearch,
                title = stringResource(R.string.starter_launcher_shortcuts_title),
                description = stringResource(R.string.starter_launcher_shortcuts_description),
                onClick = onQuickLauncherClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.KeyboardCommandKey,
                title = stringResource(R.string.nav_mode_title),
                description = stringResource(R.string.settings_nav_mode_configure),
                onClick = onNavModeClick
            )
            SettingsCategoryRow(
                icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                title = stringResource(R.string.app_enter_behaviour_title),
                description = stringResource(R.string.app_enter_behaviour_description),
                onClick = onEnterBehaviorClick
            )

            SettingsGroupDivider(stringResource(R.string.settings_group_system))

            SettingsCategoryRow(
                icon = Icons.Filled.Engineering,
                title = stringResource(R.string.settings_category_advanced),
                onClick = onAdvancedClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.TouchApp,
                title = stringResource(R.string.settings_category_accessibility),
                onClick = onAccessibilityClick
            )

            SettingsGroupDivider(stringResource(R.string.settings_group_pastiera))

            SettingsCategoryRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.about_title),
                description = stringResource(
                    R.string.settings_about_version_summary,
                    BuildConfig.VERSION_NAME
                ),
                onClick = onAboutClick
            )

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
    icon: ImageVector,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (description == null) 64.dp else 72.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
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
                        maxLines = 1
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
