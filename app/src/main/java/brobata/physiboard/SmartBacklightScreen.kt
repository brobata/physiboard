package brobata.physiboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import brobata.physiboard.inputmethod.EmbeddedAdbShell
import brobata.physiboard.inputmethod.KeyboardBacklightManager
import brobata.physiboard.inputmethod.PrivilegedSetup
import kotlinx.coroutines.delay
import moe.shizuku.manager.adb.AdbPairingService

/**
 * Starts the app's OWN embedded wireless-ADB pairing flow (same [AdbPairingService] the
 * test activity uses) so the user can pair without any separate app. Requests the
 * notification permission first on Android 13+ (the pairing PIN arrives as a notification).
 */
private fun startPairing(context: Context, showToast: Boolean = true) {
    try {
        ContextCompat.startForegroundService(context, AdbPairingService.startIntent(context))
        if (showToast) {
            Toast.makeText(
                context,
                context.getString(R.string.smart_backlight_setup_pair_hint),
                Toast.LENGTH_LONG
            ).show()
        }
    } catch (t: Throwable) {
        if (showToast) {
            Toast.makeText(context, "Pairing failed: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Stops the pairing watcher and dismisses its PIN notification. Uses stopService so
 * it also works when the service was auto-armed and the caller isn't holding a start.
 */
private fun stopPairing(context: Context) {
    try {
        context.stopService(AdbPairingService.stopIntent(context))
    } catch (_: Throwable) {
        // Service may already be gone; nothing to stop.
    }
}

/**
 * Opens a system settings screen, trying [action] first and falling back to
 * [fallbackAction]. Some OEMs restrict these deep links, so both are wrapped in
 * try/catch and the user is told to navigate manually if nothing opens.
 */
private fun openSystemSettings(context: Context, action: String, fallbackAction: String? = null) {
    val actions = listOfNotNull(action, fallbackAction)
    for (a in actions) {
        try {
            context.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Throwable) {
            // Try the next action.
        }
    }
    Toast.makeText(
        context,
        context.getString(R.string.smart_backlight_guide_open_failed),
        Toast.LENGTH_LONG
    ).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartBacklightScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(SettingsManager.getSmartBacklightEnabled(context)) }

    // Poll the embedded-broker state so the setup card reflects pairing / wireless-debug
    // changes made while this screen is open (both reads are cheap: prefs + a global flag).
    var statusTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            statusTick++
            delay(2000)
        }
    }
    val paired = remember(statusTick) { EmbeddedAdbShell.isPaired(context) }
    // Readiness keys off the persisted "configured once" flag, NOT live Wireless-debugging.
    // The always-on vendor value survives reboots and outlives Wireless debugging, so once it
    // has been written the feature is set up even if debugging is later turned off.
    val configured = remember(statusTick) { SettingsManager.getSmartBacklightApplied(context) }

    // If the feature is enabled and pairing has completed, write the persistent setting now
    // (covers "enable first, pair second"). On success the manager flips `configured` true.
    LaunchedEffect(enabled, paired) {
        if (enabled && paired) {
            PrivilegedSetup.applyAll(context, reason = "backlight_screen")
        }
    }

    // Auto-arm the pairing watcher while this screen is open and the device isn't paired
    // yet, so the PIN-entry notification is already listening BEFORE the user reaches
    // Android's "Pair device with pairing code" prompt. Foreground-only (this is not the
    // boot scenario) so starting the foreground service here is allowed. If notification
    // permission isn't granted the service simply won't post — it won't crash.
    var pairingArmed by remember { mutableStateOf(false) }
    LaunchedEffect(paired) {
        if (!paired && !pairingArmed) {
            startPairing(context, showToast = false)
            pairingArmed = true
        } else if (paired && pairingArmed) {
            // Pairing succeeded — stop the watcher and drop the PIN notification.
            stopPairing(context)
            pairingArmed = false
        }
    }
    // Stop the watcher (and its notification) when the user leaves this screen, so the
    // PIN notification only exists while setup is actively on screen.
    DisposableEffect(Unit) {
        onDispose {
            if (pairingArmed) {
                stopPairing(context)
                pairingArmed = false
            }
        }
    }

    // Guided one-time-setup walkthrough (collapsed by default; auto-open until ready).
    var guideExpanded by rememberSaveable { mutableStateOf(false) }

    // Pairing PIN arrives as a notification; ask for POST_NOTIFICATIONS on Android 13+.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startPairing(context)
        pairingArmed = true
    }

    // Manual re-arm / retry. Auto-arm already runs on entry; this restarts the watcher
    // (and requests notification permission if needed) if the user wants to retry.
    fun onPairClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPairing(context)
            pairingArmed = true
        }
    }

    BackHandler { onBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.smart_backlight_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // When set up, this collapses to a single quiet line — no card, no steps.
            // "Configured" (the persistent value written once) is the readiness signal, so the
            // ready line stays even after Wireless debugging is turned off. The full pairing
            // walkthrough only appears when the feature is enabled but not yet configured.
            if (enabled && configured) {
                Text(
                    text = "✓ " + stringResource(R.string.smart_backlight_setup_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else if (enabled) {
                // Not set up yet — a compact, non-alarming prompt. The step-by-step walkthrough
                // stays tucked behind a collapsed "How?" toggle so it isn't clutter for most.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                if (!paired) R.string.smart_backlight_setup_not_paired
                                else R.string.smart_backlight_setup_wireless_off
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!paired) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = { onPairClicked() }) {
                                Text(stringResource(R.string.smart_backlight_setup_pair_button))
                            }
                        }

                        // Collapsed "How do I turn on Wireless debugging?" walkthrough.
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { guideExpanded = !guideExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.smart_backlight_guide_toggle),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (guideExpanded) Icons.Filled.ExpandLess
                                              else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (guideExpanded) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.smart_backlight_guide_intro),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(
                                R.string.smart_backlight_guide_step1,
                                R.string.smart_backlight_guide_step2,
                                R.string.smart_backlight_guide_step3,
                                R.string.smart_backlight_guide_step4
                            ).forEach { stepRes ->
                                Text(
                                    text = stringResource(stepRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    openSystemSettings(
                                        context,
                                        Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                                    )
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.smart_backlight_guide_open_dev),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.smart_backlight_enable_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.smart_backlight_enable_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        SettingsManager.setSmartBacklightEnabled(context, it)
                        // One-time write of the persistent vendor setting (survives reboots).
                        // Both calls no-op safely if the broker isn't paired/connectable.
                        if (it) {
                            KeyboardBacklightManager.applyAlwaysOn(context)
                        } else {
                            KeyboardBacklightManager.revertToDefault(context)
                        }
                    }
                )
            }
        }
    }
}
