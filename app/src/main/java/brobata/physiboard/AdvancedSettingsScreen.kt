package brobata.physiboard

import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import brobata.physiboard.BuildConfig
import brobata.physiboard.R
import brobata.physiboard.backup.BackupManager
import brobata.physiboard.backup.RestoreManager
import androidx.compose.material3.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import rikka.shizuku.Shizuku
import androidx.compose.runtime.LaunchedEffect
import android.content.pm.PackageManager
import androidx.compose.material.icons.filled.Warning

/**
 * Advanced settings screen.
 */
@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenInputLanguages: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val prefs = remember { SettingsManager.getPreferences(context) }
    
    // Store the actual value (3 to 25), but display it inverted in the slider (25 to 3)
    var swipeIncrementalThreshold by remember {
        mutableStateOf(SettingsManager.getSwipeIncrementalThreshold(context))
    }
    var clipboardRetentionTime by remember {
        mutableStateOf(SettingsManager.getClipboardRetentionTime(context).toString())
    }
    var shizukuStatus by remember { mutableStateOf(ShizukuStatus.NotConnected) }
    var showResetToStockDialog by remember { mutableStateOf(false) }
    var resetToStockInProgress by remember { mutableStateOf(false) }
    var trackpadProvider by remember { mutableStateOf(SettingsManager.getTrackpadProvider(context)) }
    var navigationDirection by remember { mutableStateOf(AdvancedNavigationDirection.Push) }
    val navigationStack = remember {
        mutableStateListOf<AdvancedDestination>(AdvancedDestination.Main)
    }
    val currentDestination by remember {
        derivedStateOf { navigationStack.last() }
    }
    
    // Listen to SharedPreferences changes to update UI when values are restored
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "swipe_incremental_threshold" -> {
                    swipeIncrementalThreshold = SettingsManager.getSwipeIncrementalThreshold(context)
                }
                "clipboard_retention_time" -> {
                    clipboardRetentionTime = SettingsManager.getClipboardRetentionTime(context).toString()
                }
                "trackpad_provider" -> {
                    trackpadProvider = SettingsManager.getTrackpadProvider(context)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Check Shizuku connection and authorization status periodically
    LaunchedEffect(Unit) {
        while (true) {
            shizukuStatus = resolveShizukuStatus()
            delay(2000) // Check every 2 seconds
        }
    }

    fun navigateTo(destination: AdvancedDestination) {
        navigationDirection = AdvancedNavigationDirection.Push
        navigationStack.add(destination)
    }
    
    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationDirection = AdvancedNavigationDirection.Pop
            navigationStack.removeAt(navigationStack.lastIndex)
        } else {
            onBack()
        }
    }
    
    BackHandler { navigateBack() }
    
    fun defaultBackupName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        return "physiboard-backup-${formatter.format(Date())}.zip"
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = BackupManager.createBackup(context, uri)
                val message = when (result) {
                    is brobata.physiboard.backup.BackupResult.Success ->
                        context.getString(R.string.backup_completed)
                    is brobata.physiboard.backup.BackupResult.Failure ->
                        context.getString(R.string.backup_failed, result.reason)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = RestoreManager.restore(context, uri)
                val message = when (result) {
                    is brobata.physiboard.backup.RestoreResult.Success ->
                        context.getString(R.string.restore_completed)
                    is brobata.physiboard.backup.RestoreResult.Failure ->
                        context.getString(R.string.restore_failed, result.reason)
                }
                snackbarHostState.showSnackbar(message)
                
                // Wait a bit for SharedPreferences to be written (apply() is asynchronous)
                kotlinx.coroutines.delay(100)
                
                // Explicitly reload values after restore to ensure UI is updated
                swipeIncrementalThreshold = SettingsManager.getSwipeIncrementalThreshold(context)
                clipboardRetentionTime = SettingsManager.getClipboardRetentionTime(context).toString()
            }
        }
    }
    
    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (navigationDirection == AdvancedNavigationDirection.Push) {
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
        label = "advanced_navigation",
        contentKey = { it::class }
    ) { destination ->
        when (destination) {
            AdvancedDestination.Main -> {
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
                                    text = stringResource(R.string.settings_category_advanced),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { paddingValues ->
                    Column(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Input Languages (moved off the front page; still reachable here)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { onOpenInputLanguages() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.custom_input_styles_title),
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

                        // Backup
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable {
                                    backupLauncher.launch(defaultBackupName())
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
                                    imageVector = Icons.Filled.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.backup_now),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.backup_now_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    
                        // Restore
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable {
                                    restoreLauncher.launch(arrayOf("application/zip"))
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
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.restore_from_file),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.restore_from_file_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    
                        // Diagnostics (physical key-event logger + debug export)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(AdvancedDestination.Diagnostics) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.diagnostics_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.diagnostics_row_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Reset device settings to stock — undoes EVERY system-wide change
                        // PhysiBoard can make (Fn->Ctrl, keyboard backlight). Survives uninstall,
                        // so the user must tap this BEFORE uninstalling.
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !resetToStockInProgress) {
                                    showResetToStockDialog = true
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SettingsBackupRestore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.reset_to_stock_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2
                                    )
                                    Text(
                                        text = stringResource(R.string.reset_to_stock_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (resetToStockInProgress) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (showResetToStockDialog) {
                            AlertDialog(
                                onDismissRequest = { showResetToStockDialog = false },
                                title = { Text(stringResource(R.string.reset_to_stock_confirm_title)) },
                                text = { Text(stringResource(R.string.reset_to_stock_confirm_message)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showResetToStockDialog = false
                                        resetToStockInProgress = true
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                SystemChangeManager.resetToStock(context)
                                            }
                                            resetToStockInProgress = false
                                            val message = when {
                                                result.allSucceeded ->
                                                    context.getString(R.string.reset_to_stock_result_success)
                                                result.needsPermission ->
                                                    context.getString(R.string.reset_to_stock_result_needs_permission)
                                                else ->
                                                    context.getString(R.string.reset_to_stock_result_partial)
                                            }
                                            snackbarHostState.showSnackbar(message)
                                        }
                                    }) {
                                        Text(stringResource(R.string.reset_to_stock_confirm_action))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showResetToStockDialog = false }) {
                                        Text(stringResource(R.string.reset_to_stock_confirm_cancel))
                                    }
                                }
                            )
                        }

                        // Swipe Bar Sensitivity hidden — it tunes the on-screen VariationBar
                        // swipe cursor, not the physical Keyboard swipe (trackpad_swipe_threshold).
                        if (false) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.swipe_incremental_threshold_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${String.format("%.1f", swipeIncrementalThreshold)} ${stringResource(R.string.dip_unit)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Slider(
                                    value = SettingsManager.getMaxSwipeIncrementalThreshold() + 
                                        SettingsManager.getMinSwipeIncrementalThreshold() - swipeIncrementalThreshold,
                                    onValueChange = { newInvertedValue ->
                                        // Invert the slider value (25 to 3) back to stored value (3 to 25)
                                        val actualValue = SettingsManager.getMaxSwipeIncrementalThreshold() + 
                                            SettingsManager.getMinSwipeIncrementalThreshold() - newInvertedValue
                                        swipeIncrementalThreshold = actualValue
                                        SettingsManager.setSwipeIncrementalThreshold(context, actualValue)
                                    },
                                    valueRange = SettingsManager.getMinSwipeIncrementalThreshold()..SettingsManager.getMaxSwipeIncrementalThreshold(),
                                    steps = 16,
                                    modifier = Modifier
                                        .weight(1.0f)
                                        .height(24.dp)
                                )
                            }
                        }
                        }

                    }
                }
            }

            AdvancedDestination.ImeTest -> {
                ImeTestScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            AdvancedDestination.Diagnostics -> {
                DiagnosticsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

        }
    }
}

private sealed class AdvancedDestination {
    object Main : AdvancedDestination()
    object ImeTest : AdvancedDestination()
    object Diagnostics : AdvancedDestination()
}

private enum class AdvancedNavigationDirection {
    Push,
    Pop
}
