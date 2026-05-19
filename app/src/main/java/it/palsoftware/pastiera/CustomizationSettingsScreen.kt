package it.palsoftware.pastiera

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R

/**
 * Customization settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { SettingsManager.getPreferences(context) }
    var pastierinaModeEnabled by remember {
        mutableStateOf(SettingsManager.getPastierinaModeActive(context))
    }
    var launcherShortcutsEnabled by remember {
        mutableStateOf(SettingsManager.getLauncherShortcutsEnabled(context))
    }
    var powerShortcutsEnabled by remember {
        mutableStateOf(SettingsManager.getPowerShortcutsEnabled(context))
    }
    var quickLauncherAutoStartSingle by remember {
        mutableStateOf(SettingsManager.getQuickLauncherAutoStartSingle(context))
    }
    var quickLauncherLimitResults by remember {
        mutableStateOf(SettingsManager.getQuickLauncherLimitResults(context))
    }
    var quickLauncherTextFieldShortcuts by remember {
        mutableStateOf(SettingsManager.getQuickLauncherTextFieldShortcuts(context))
    }
    var quickLauncherWidthPercent by remember {
        mutableStateOf(SettingsManager.getQuickLauncherWidthPercent(context))
    }
    var quickLauncherPillMode by remember {
        mutableStateOf(SettingsManager.getQuickLauncherPillMode(context))
    }
    var quickLauncherDefaultBlocked by remember {
        mutableStateOf(SettingsManager.isQuickLauncherDefaultBlockedByExistingSpaceShortcut(context))
    }
    var quickLauncherShortcutKey by remember {
        mutableStateOf(
            if (SettingsManager.isQuickLauncherDefaultBlockedByExistingSpaceShortcut(context)) {
                null
            } else {
                SettingsManager.getQuickLauncherShortcutKey(context)
            }
        )
    }
    var navigationDirection by remember { mutableStateOf(CustomizationNavigationDirection.Push) }
    val navigationStack = remember {
        mutableStateListOf<CustomizationDestination>(CustomizationDestination.Main)
    }
    val currentDestination by remember {
        derivedStateOf { navigationStack.last() }
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "pastierina_mode_active" -> {
                    pastierinaModeEnabled = SettingsManager.getPastierinaModeActive(context)
                }
                "launcher_shortcuts_enabled" -> {
                    launcherShortcutsEnabled = SettingsManager.getLauncherShortcutsEnabled(context)
                }
                "power_shortcuts_enabled" -> {
                    powerShortcutsEnabled = SettingsManager.getPowerShortcutsEnabled(context)
                }
                "quick_launcher_auto_start_single" -> {
                    quickLauncherAutoStartSingle = SettingsManager.getQuickLauncherAutoStartSingle(context)
                }
                "quick_launcher_limit_results" -> {
                    quickLauncherLimitResults = SettingsManager.getQuickLauncherLimitResults(context)
                }
                "quick_launcher_text_field_shortcuts" -> {
                    quickLauncherTextFieldShortcuts = SettingsManager.getQuickLauncherTextFieldShortcuts(context)
                }
                "quick_launcher_width_percent" -> {
                    quickLauncherWidthPercent = SettingsManager.getQuickLauncherWidthPercent(context)
                }
                "quick_launcher_pill_mode" -> {
                    quickLauncherPillMode = SettingsManager.getQuickLauncherPillMode(context)
                }
                "launcher_shortcuts" -> {
                    quickLauncherDefaultBlocked = SettingsManager.isQuickLauncherDefaultBlockedByExistingSpaceShortcut(context)
                    quickLauncherShortcutKey = if (quickLauncherDefaultBlocked) {
                        null
                    } else {
                        SettingsManager.getQuickLauncherShortcutKey(context)
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    
    fun navigateTo(destination: CustomizationDestination) {
        navigationDirection = CustomizationNavigationDirection.Push
        navigationStack.add(destination)
    }
    
    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationDirection = CustomizationNavigationDirection.Pop
            navigationStack.removeAt(navigationStack.lastIndex)
        } else {
            onBack()
        }
    }
    
    BackHandler { navigateBack() }
    
    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (navigationDirection == CustomizationNavigationDirection.Push) {
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
        label = "customization_navigation",
        contentKey = { it::class }
    ) { destination ->
        when (destination) {
            CustomizationDestination.Main -> {
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
                                IconButton(onClick = { navigateBack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.settings_back_content_description)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.settings_category_customization),
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
                        // SYM Customization
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable {
                                    val intent = Intent(context, SymCustomizationActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                    context.startActivity(intent)
                                    (context as? Activity)?.let { activity ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            activity.overrideActivityTransition(
                                                Activity.OVERRIDE_TRANSITION_OPEN,
                                                R.anim.slide_in_from_right,
                                                0
                                            )
                                        } else {
                                            @Suppress("DEPRECATION")
                                            activity.overridePendingTransition(
                                                R.anim.slide_in_from_right,
                                                0
                                            )
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_emoji_symbols_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.sym_customization_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    
                        // Variations Customization
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.Variations) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.variation_customize_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    
                        // App-specific Enter behavior
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.AppEnterBehavior) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.app_enter_behavior_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.app_enter_behavior_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Starter / Launcher shortcuts
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clickable { navigateTo(CustomizationDestination.LauncherShortcuts) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ManageSearch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.starter_launcher_shortcuts_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.starter_launcher_shortcuts_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Nav Mode Settings
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.NavMode) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardCommandKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.nav_mode_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_nav_mode_configure),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    
                        // Status Bar Buttons Settings
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.StatusBarButtons) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SmartButton,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.status_bar_buttons_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.status_bar_buttons_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Sound Settings
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.Sounds) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_category_sounds),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_sounds_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Pastierina Mode toggle
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SpaceBar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.pastierina_mode_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.pastierina_mode_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = pastierinaModeEnabled,
                                    onCheckedChange = { checked ->
                                        pastierinaModeEnabled = checked
                                        val override = if (checked) {
                                            SettingsManager.PastierinaModeOverride.FORCE_MINIMAL
                                        } else {
                                            SettingsManager.PastierinaModeOverride.FORCE_FULL
                                        }
                                        SettingsManager.setPastierinaModeOverride(context, override)
                                    }
                                )
                            }
                        }

                    }
                }
            }
            
            CustomizationDestination.Variations -> {
                VariationCustomizationScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            CustomizationDestination.AppEnterBehavior -> {
                AppEnterBehaviorScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            
            CustomizationDestination.NavMode -> {
                NavModeSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            CustomizationDestination.LauncherShortcuts -> {
                StarterLauncherShortcutsSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    launcherShortcutsEnabled = launcherShortcutsEnabled,
                    onLauncherShortcutsEnabledChanged = { enabled ->
                        launcherShortcutsEnabled = enabled
                        SettingsManager.setLauncherShortcutsEnabled(context, enabled)
                    },
                    powerShortcutsEnabled = powerShortcutsEnabled,
                    onPowerShortcutsEnabledChanged = { enabled ->
                        powerShortcutsEnabled = enabled
                        SettingsManager.setPowerShortcutsEnabled(context, enabled)
                    },
                    quickLauncherAutoStartSingle = quickLauncherAutoStartSingle,
                    onQuickLauncherAutoStartSingleChanged = { enabled ->
                        quickLauncherAutoStartSingle = enabled
                        SettingsManager.setQuickLauncherAutoStartSingle(context, enabled)
                    },
                    quickLauncherLimitResults = quickLauncherLimitResults,
                    onQuickLauncherLimitResultsChanged = { enabled ->
                        quickLauncherLimitResults = enabled
                        SettingsManager.setQuickLauncherLimitResults(context, enabled)
                    },
                    quickLauncherDefaultBlocked = quickLauncherDefaultBlocked,
                    quickLauncherShortcutKey = quickLauncherShortcutKey,
                    onOpenBehavior = { navigateTo(CustomizationDestination.LauncherShortcutBehavior) },
                    onOpenCosmetic = { navigateTo(CustomizationDestination.LauncherShortcutCosmetic) },
                    onManageAssignments = { navigateTo(CustomizationDestination.LauncherShortcutAssignments) }
                )
            }

            CustomizationDestination.LauncherShortcutBehavior -> {
                StarterLauncherBehaviorScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    quickLauncherAutoStartSingle = quickLauncherAutoStartSingle,
                    onQuickLauncherAutoStartSingleChanged = { enabled ->
                        quickLauncherAutoStartSingle = enabled
                        SettingsManager.setQuickLauncherAutoStartSingle(context, enabled)
                    },
                    quickLauncherLimitResults = quickLauncherLimitResults,
                    onQuickLauncherLimitResultsChanged = { enabled ->
                        quickLauncherLimitResults = enabled
                        SettingsManager.setQuickLauncherLimitResults(context, enabled)
                    },
                    quickLauncherTextFieldShortcuts = quickLauncherTextFieldShortcuts,
                    onQuickLauncherTextFieldShortcutsChanged = { enabled ->
                        quickLauncherTextFieldShortcuts = enabled
                        SettingsManager.setQuickLauncherTextFieldShortcuts(context, enabled)
                    }
                )
            }

            CustomizationDestination.LauncherShortcutCosmetic -> {
                StarterLauncherCosmeticScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    quickLauncherWidthPercent = quickLauncherWidthPercent,
                    onQuickLauncherWidthPercentChanged = { percent ->
                        quickLauncherWidthPercent = percent
                        SettingsManager.setQuickLauncherWidthPercent(context, percent)
                    },
                    quickLauncherPillMode = quickLauncherPillMode,
                    onQuickLauncherPillModeChanged = { enabled ->
                        quickLauncherPillMode = enabled
                        SettingsManager.setQuickLauncherPillMode(context, enabled)
                    }
                )
            }

            CustomizationDestination.LauncherShortcutAssignments -> {
                LauncherShortcutsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            
            CustomizationDestination.StatusBarButtons -> {
                StatusBarButtonsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            CustomizationDestination.Sounds -> {
                SoundSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
        }
    }
}

@Composable
private fun StarterLauncherShortcutsSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    launcherShortcutsEnabled: Boolean,
    onLauncherShortcutsEnabledChanged: (Boolean) -> Unit,
    powerShortcutsEnabled: Boolean,
    onPowerShortcutsEnabledChanged: (Boolean) -> Unit,
    quickLauncherAutoStartSingle: Boolean,
    onQuickLauncherAutoStartSingleChanged: (Boolean) -> Unit,
    quickLauncherLimitResults: Boolean,
    onQuickLauncherLimitResultsChanged: (Boolean) -> Unit,
    quickLauncherDefaultBlocked: Boolean,
    quickLauncherShortcutKey: Int?,
    onOpenBehavior: () -> Unit,
    onOpenCosmetic: () -> Unit,
    onManageAssignments: () -> Unit
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.starter_launcher_shortcuts_title),
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.starter_launcher_shortcuts_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (quickLauncherDefaultBlocked) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = stringResource(R.string.quick_launcher_default_blocked_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            LauncherShortcutTriggerRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.LineWeight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.launcher_shortcuts_title),
                description = stringResource(R.string.launcher_shortcuts_description),
                checked = launcherShortcutsEnabled,
                onCheckedChange = onLauncherShortcutsEnabledChanged
            )

            LauncherShortcutTriggerRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.power_shortcuts_title),
                description = stringResource(R.string.power_shortcuts_description),
                checked = powerShortcutsEnabled,
                onCheckedChange = onPowerShortcutsEnabledChanged
            )

            StarterLauncherNavigationRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.quick_launcher_behavior_title),
                description = stringResource(R.string.quick_launcher_behavior_description),
                onClick = onOpenBehavior
            )

            StarterLauncherNavigationRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.ManageSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.quick_launcher_cosmetic_title),
                description = stringResource(R.string.quick_launcher_cosmetic_description),
                onClick = onOpenCosmetic
            )

            StarterLauncherNavigationRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.SmartButton,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.launcher_shortcuts_configure),
                description = if (quickLauncherShortcutKey != null) {
                    stringResource(
                        R.string.launcher_shortcuts_configure_description_with_quick_launcher,
                        keyLabel(quickLauncherShortcutKey)
                    )
                } else {
                    stringResource(R.string.launcher_shortcuts_configure_description)
                },
                onClick = onManageAssignments
            )
        }
    }
}

@Composable
private fun StarterLauncherBehaviorScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    quickLauncherAutoStartSingle: Boolean,
    onQuickLauncherAutoStartSingleChanged: (Boolean) -> Unit,
    quickLauncherLimitResults: Boolean,
    onQuickLauncherLimitResultsChanged: (Boolean) -> Unit,
    quickLauncherTextFieldShortcuts: Boolean,
    onQuickLauncherTextFieldShortcutsChanged: (Boolean) -> Unit
) {
    StarterLauncherSubScreen(
        modifier = modifier,
        title = stringResource(R.string.quick_launcher_behavior_title),
        onBack = onBack
    ) {
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_auto_start_single_title),
            description = stringResource(R.string.quick_launcher_auto_start_single_description),
            checked = quickLauncherAutoStartSingle,
            onCheckedChange = onQuickLauncherAutoStartSingleChanged
        )
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_limit_results_title),
            description = stringResource(R.string.quick_launcher_limit_results_description),
            checked = quickLauncherLimitResults,
            onCheckedChange = onQuickLauncherLimitResultsChanged
        )
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_text_field_shortcuts_title),
            description = stringResource(R.string.quick_launcher_text_field_shortcuts_description),
            checked = quickLauncherTextFieldShortcuts,
            onCheckedChange = onQuickLauncherTextFieldShortcutsChanged
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = stringResource(R.string.quick_launcher_text_field_shortcuts_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun StarterLauncherCosmeticScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    quickLauncherWidthPercent: Int,
    onQuickLauncherWidthPercentChanged: (Int) -> Unit,
    quickLauncherPillMode: Boolean,
    onQuickLauncherPillModeChanged: (Boolean) -> Unit
) {
    StarterLauncherSubScreen(
        modifier = modifier,
        title = stringResource(R.string.quick_launcher_cosmetic_title),
        onBack = onBack
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_launcher_width_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.quick_launcher_width_value, quickLauncherWidthPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = quickLauncherWidthPercent.toFloat(),
                    onValueChange = { onQuickLauncherWidthPercentChanged(it.toInt()) },
                    valueRange = 50f..100f,
                    steps = 9
                )
            }
        }
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_pill_mode_title),
            description = stringResource(R.string.quick_launcher_pill_mode_description),
            checked = quickLauncherPillMode,
            onCheckedChange = onQuickLauncherPillModeChanged
        )
    }
}

@Composable
private fun StarterLauncherSubScreen(
    modifier: Modifier = Modifier,
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = title,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun LauncherShortcutTriggerRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun StarterLauncherNavigationRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
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
private fun SettingsRowKeyboardIcon() {
    Icon(
        imageVector = Icons.Filled.ManageSearch,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp)
    )
}

private fun keyLabel(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_DEL -> "Backspace"
        KeyEvent.KEYCODE_A -> "A"
        KeyEvent.KEYCODE_B -> "B"
        KeyEvent.KEYCODE_C -> "C"
        KeyEvent.KEYCODE_D -> "D"
        KeyEvent.KEYCODE_E -> "E"
        KeyEvent.KEYCODE_F -> "F"
        KeyEvent.KEYCODE_G -> "G"
        KeyEvent.KEYCODE_H -> "H"
        KeyEvent.KEYCODE_I -> "I"
        KeyEvent.KEYCODE_J -> "J"
        KeyEvent.KEYCODE_K -> "K"
        KeyEvent.KEYCODE_L -> "L"
        KeyEvent.KEYCODE_M -> "M"
        KeyEvent.KEYCODE_N -> "N"
        KeyEvent.KEYCODE_O -> "O"
        KeyEvent.KEYCODE_P -> "P"
        KeyEvent.KEYCODE_Q -> "Q"
        KeyEvent.KEYCODE_R -> "R"
        KeyEvent.KEYCODE_S -> "S"
        KeyEvent.KEYCODE_T -> "T"
        KeyEvent.KEYCODE_U -> "U"
        KeyEvent.KEYCODE_V -> "V"
        KeyEvent.KEYCODE_W -> "W"
        KeyEvent.KEYCODE_X -> "X"
        KeyEvent.KEYCODE_Y -> "Y"
        KeyEvent.KEYCODE_Z -> "Z"
        else -> keyCode.toString()
    }
}

@Composable
private fun SoundSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_category_sounds),
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
            TypingSoundSettingsRow()
        }
    }
}

private sealed class CustomizationDestination {
    object Main : CustomizationDestination()
    object Variations : CustomizationDestination()
    object AppEnterBehavior : CustomizationDestination()
    object NavMode : CustomizationDestination()
    object LauncherShortcuts : CustomizationDestination()
    object LauncherShortcutBehavior : CustomizationDestination()
    object LauncherShortcutCosmetic : CustomizationDestination()
    object LauncherShortcutAssignments : CustomizationDestination()
    object StatusBarButtons : CustomizationDestination()
    object Sounds : CustomizationDestination()
}

private enum class CustomizationNavigationDirection {
    Push,
    Pop
}
