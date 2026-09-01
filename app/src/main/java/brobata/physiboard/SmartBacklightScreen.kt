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
import brobata.physiboard.inputmethod.PrivilegedDiagnostics
import brobata.physiboard.inputmethod.PrivilegedSetup
import kotlinx.coroutines.delay
import moe.shizuku.manager.adb.AdbPairingService



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
    // Why the broker cannot run right now, if it cannot. Android turns Wireless debugging off
    // across reboots, so a paired device is routinely unable to apply anything.
    val blocker = remember(statusTick) { PrivilegedDiagnostics.brokerBlocker(context) }
    val lastBacklightFailure = remember(statusTick) {
        PrivilegedDiagnostics.last(context, PrivilegedDiagnostics.Step.BACKLIGHT)
            ?.takeIf { !it.ok }
            ?.reason
    }

    // If the feature is enabled and pairing has completed, write the persistent setting now
    // (covers "enable first, pair second"). On success the manager flips `configured` true.
    LaunchedEffect(enabled, paired) {
        if (enabled && paired) {
            PrivilegedSetup.applyAll(context, reason = "backlight_screen")
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
                // "Configured" is a one-way latch, so on its own it cannot tell you the feature
                // has since stopped working - which is exactly how a user ends up with a toggle
                // that reads on, a backlight that times out, and nothing explaining why. If the
                // broker cannot run right now, say so and say which switch to flip.
                if (blocker != null) {
                    Text(
                        text = "! " + when (blocker) {
                            PrivilegedDiagnostics.REASON_WIRELESS_DEBUGGING_OFF ->
                                stringResource(R.string.smart_backlight_blocked_wireless_debugging)
                            else ->
                                stringResource(R.string.smart_backlight_blocked_not_paired)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    Text(
                        text = "✓ " + stringResource(R.string.smart_backlight_setup_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                lastBacklightFailure?.let { failure ->
                    Text(
                        text = stringResource(R.string.smart_backlight_last_error, failure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    )
                }
            } else if (enabled) {
                // One pairing serves every privileged feature, so the card lives in T2E Tools and
                // is shown here too rather than reimplemented.
                DeviceSetupCard()
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
