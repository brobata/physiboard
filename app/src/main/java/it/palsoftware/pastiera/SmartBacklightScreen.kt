package it.palsoftware.pastiera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import it.palsoftware.pastiera.inputmethod.EmbeddedAdbShell
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
    var luxThreshold by remember { mutableStateOf(SettingsManager.getSmartBacklightLuxThreshold(context)) }

    // Live ambient light readout so the user can calibrate by looking at the room.
    var currentLux by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                currentLux = event.values.firstOrNull()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager?.unregisterListener(listener) }
    }

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
    val wirelessOn = remember(statusTick) { EmbeddedAdbShell.isWirelessDebuggingEnabled(context) }
    val ready = paired && wirelessOn

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
            // Self-contained setup / troubleshooting surface. Green when the embedded
            // broker is paired and Wireless debugging is on; otherwise a prompt to pair.
            Surface(
                color = if (ready) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.smart_backlight_setup_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            when {
                                !paired -> R.string.smart_backlight_setup_not_paired
                                !wirelessOn -> R.string.smart_backlight_setup_wireless_off
                                else -> R.string.smart_backlight_setup_ready
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (!paired) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { onPairClicked() }) {
                            Text(stringResource(R.string.smart_backlight_setup_pair_button))
                        }
                    }

                    val guideTint = if (ready) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onErrorContainer

                    // Expandable "How do I turn on Wireless debugging?" walkthrough.
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
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = guideTint,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (guideExpanded) Icons.Filled.ExpandLess
                                          else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = guideTint
                        )
                    }

                    if (guideExpanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.smart_backlight_guide_intro),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = guideTint
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
                                color = guideTint,
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
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                openSystemSettings(
                                    context,
                                    Settings.ACTION_DEVICE_INFO_SETTINGS,
                                    Settings.ACTION_SETTINGS
                                )
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.smart_backlight_guide_open_about),
                                fontFamily = FontFamily.Monospace
                            )
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
                    }
                )
            }

            HorizontalDivider()

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.smart_backlight_lux_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.smart_backlight_lux_value, luxThreshold),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = luxThreshold.toFloat(),
                    onValueChange = {
                        luxThreshold = it.toInt().coerceIn(1, 50)
                        SettingsManager.setSmartBacklightLuxThreshold(context, luxThreshold)
                    },
                    valueRange = 1f..50f,
                    modifier = Modifier.fillMaxWidth()
                )
                currentLux?.let { lux ->
                    val luxInt = lux.toInt()
                    val state = if (luxInt <= luxThreshold) {
                        stringResource(R.string.smart_backlight_lux_dark)
                    } else {
                        stringResource(R.string.smart_backlight_lux_bright)
                    }
                    Text(
                        text = stringResource(R.string.smart_backlight_lux_now, luxInt, state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
