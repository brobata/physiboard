package brobata.physiboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import brobata.physiboard.inputmethod.EmbeddedAdbShell
import brobata.physiboard.ui.BrokerStatusMonitor
import brobata.physiboard.ui.rememberVerifiedBrokerStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.adb.AdbPairingService

/**
 * The one-time ADB pairing every privileged feature depends on - the backlight, the overlay grant,
 * removing packages, changing density.
 *
 * It used to live inside the backlight screen, gated on the backlight being switched on, so a user
 * who had not turned that feature on had nowhere to do the setup everything else needed. It belongs
 * at the top of the toolbox: one pairing, before the things that require it.
 *
 * The pairing watcher arms itself the moment this appears, so the PIN notification is already
 * waiting by the time the user reaches Android's pairing dialog. The button's job is only to take
 * them to the right settings screen - and which screen that is depends on whether Developer options
 * exist yet, so it checks rather than telling everyone to go find them.
 */
@Composable
fun DeviceSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var statusTick by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_500)
            statusTick++
        }
    }
    val paired = remember(statusTick) { EmbeddedAdbShell.isPaired(context) }
    val developerOptionsOn = remember(statusTick) { developerOptionsEnabled(context) }
    val wirelessDebuggingOn = remember(statusTick) {
        EmbeddedAdbShell.isWirelessDebuggingEnabled(context)
    }
    // Shared with every other surface. A private check here is what let this card say the tools
    // could reach the system while the home tile said "needs pairing" at the same moment.
    val brokerStatus by rememberVerifiedBrokerStatus(paired)
    val doNotDisturbOn = remember(statusTick) { doNotDisturbActive(context) }

    var notificationsAllowed by remember {
        mutableStateOf(notificationPermissionGranted(context))
    }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted
        // Re-arm so the watcher is running under the permission that just arrived.
        if (granted && !paired) armPairing(context)
    }

    // Arm the watcher whenever we are not paired. Deliberately NOT torn down when this leaves the
    // screen: the button's whole job is to send the user to Android's Wireless debugging page, and
    // stopping the watcher as they walk out of the door is precisely how they arrive at "Pair
    // device" with nothing listening. AdbPairingService stops itself once pairing succeeds.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(paired) {
        if (!paired && !armed) {
            armPairing(context)
            armed = true
        } else if (paired && armed) {
            stopPairing(context)
            armed = false
        }
    }

    // Asked for separately, and never as a precondition for arming. The pairing code arrives as a
    // notification, so a denied permission means no code - but the watcher itself still has to run.
    LaunchedEffect(Unit) {
        if (!notificationsAllowed) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Readiness is the VERIFIED status, never "a key is stored". A key is stored the moment a
    // pairing is attempted, and it stays stored when Wireless debugging is later turned off — so
    // keying this card off `paired` is what let it claim the tools could reach the system while
    // nothing could connect at all.
    val connected = brokerStatus == EmbeddedAdbShell.BrokerStatus.OK
    Surface(
        color = if (connected) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (connected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        when {
                            connected -> R.string.device_setup_ready_title
                            brokerStatus == null && paired -> R.string.device_setup_checking_title
                            paired -> R.string.device_setup_unreachable_title
                            else -> R.string.device_setup_needed_title
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (paired) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        when (brokerStatus) {
                            EmbeddedAdbShell.BrokerStatus.OK -> R.string.device_setup_ready_body
                            EmbeddedAdbShell.BrokerStatus.REJECTED -> R.string.device_setup_rejected_body
                            EmbeddedAdbShell.BrokerStatus.WIRELESS_DEBUGGING_OFF ->
                                R.string.smart_backlight_blocked_wireless_debugging
                            EmbeddedAdbShell.BrokerStatus.NO_SERVICE ->
                                R.string.smart_backlight_blocked_no_service
                            EmbeddedAdbShell.BrokerStatus.NOT_PAIRED ->
                                R.string.smart_backlight_blocked_not_paired
                            null -> R.string.device_setup_checking_body
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connected || brokerStatus == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Wireless debugging being off does not invalidate the pairing, so that case
                    // is sent to the toggle rather than being told to pair again.
                    if (brokerStatus == EmbeddedAdbShell.BrokerStatus.WIRELESS_DEBUGGING_OFF ||
                        brokerStatus == EmbeddedAdbShell.BrokerStatus.NO_SERVICE
                    ) {
                        Button(onClick = { openWirelessDebugging(context) }) {
                            Text(stringResource(R.string.device_setup_open_wireless))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = {
                        EmbeddedAdbShell.forgetPairing(context)
                        BrokerStatusMonitor.invalidate()
                        statusTick++
                    }) {
                        Text(
                            stringResource(
                                if (brokerStatus == EmbeddedAdbShell.BrokerStatus.REJECTED)
                                    R.string.smart_backlight_repair
                                else R.string.device_setup_forget_pairing
                            )
                        )
                    }
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(10.dp))
            val steps = if (!developerOptionsOn) {
                listOf(
                    R.string.device_setup_step_dev_1,
                    R.string.device_setup_step_dev_2,
                    R.string.device_setup_step_dev_3
                )
            } else {
                listOf(
                    R.string.device_setup_step_pair_1,
                    R.string.device_setup_step_pair_2,
                    R.string.device_setup_step_pair_3
                )
            }
            steps.forEachIndexed { index, res ->
                Step(number = index + 1, text = stringResource(res))
            }

            val warning = when {
                !notificationsAllowed -> R.string.device_setup_needs_notifications
                doNotDisturbOn -> R.string.device_setup_dnd_on
                else -> null
            }
            if (warning != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = {
                if (developerOptionsOn) openWirelessDebugging(context) else openAboutPhone(context)
            }) {
                Text(
                    stringResource(
                        when {
                            !developerOptionsOn -> R.string.device_setup_open_about
                            wirelessDebuggingOn -> R.string.device_setup_open_pairing
                            else -> R.string.device_setup_open_wireless
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Bedtime mode and Do Not Disturb both land here, and both can hide the pairing code. */
private fun doNotDisturbActive(context: Context): Boolean =
    runCatching {
        Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 0
    }.getOrDefault(false)

private fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun developerOptionsEnabled(context: Context): Boolean =
    runCatching {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
    }.getOrDefault(false)

/** Wireless debugging has no public intent action; fall back to Developer options. */
private fun openWirelessDebugging(context: Context) {
    val wireless = Intent("android.settings.ADB_WIRELESS_SETTINGS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (wireless.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(wireless) }.onSuccess { return }
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openAboutPhone(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun armPairing(context: Context) {
    runCatching {
        ContextCompat.startForegroundService(context, AdbPairingService.startIntent(context))
    }
}

private fun stopPairing(context: Context) {
    runCatching { context.stopService(AdbPairingService.stopIntent(context)) }
}
