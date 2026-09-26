package brobata.physiboard.app.settings.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.device.privileged.broker.BrokerVerdict
import brobata.physiboard.device.privileged.broker.PairingWatcherService
import kotlinx.coroutines.delay

/**
 * The device setup card: the header of T2E Tools, and every screen's "Set up pairing" link routes
 * back to the hub to see it (device-backlight-ring.md SS11 Keep/Drop, "keep one home for pairing").
 *
 * spec: broker-privileged-toolbox.md SS3, SS4, SS4.5, SS5.3; device-backlight-ring.md SS6.
 */
@Composable
fun DeviceSetupCard() {
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val privileged = application.privileged

    var keyStored by remember { mutableStateOf(privileged.broker.isPaired()) }
    var developerOptionsOn by remember { mutableStateOf(readGlobalFlag(context, "development_settings_enabled")) }
    var wirelessDebuggingOn by remember { mutableStateOf(readGlobalFlag(context, "adb_wifi_enabled")) }
    var dndOn by remember { mutableStateOf(readGlobalFlag(context, "zen_mode")) }
    val verdict by privileged.broker.verdict.collectAsState()
    val checking by privileged.broker.checking.collectAsState()

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // spec SS4.1: "if granted while unpaired, the pairing watcher is re-armed so it runs under the new permission."
        if (granted && !keyStored) PairingWatcherService.arm(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted && !keyStored) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // spec SS4.1 step 3: armed the moment the card appears, while unpaired, and re-checked every 1500 ms.
    LaunchedEffect(Unit) {
        while (true) {
            val nowStored = privileged.broker.isPaired()
            if (nowStored != keyStored) {
                keyStored = nowStored
                privileged.broker.invalidate()
                if (nowStored) PairingWatcherService.stop(context)
            }
            if (!keyStored) PairingWatcherService.arm(context)
            developerOptionsOn = readGlobalFlag(context, "development_settings_enabled")
            dndOn = readGlobalFlag(context, "zen_mode")
            val nowWirelessDebuggingOn = readGlobalFlag(context, "adb_wifi_enabled")
            if (nowWirelessDebuggingOn != wirelessDebuggingOn) {
                // spec: broker-privileged-toolbox.md SS5.2 ("re-verified when the key presence changes... whenever the polled adb_wifi_enabled value flips").
                wirelessDebuggingOn = nowWirelessDebuggingOn
                if (keyStored) privileged.broker.verify(force = true)
            }
            delay(1500)
        }
    }

    LaunchedEffect(keyStored) { if (keyStored) privileged.broker.verify() }

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            HeaderLine(keyStored, checking, verdict)
            if (keyStored) {
                PairedBody(verdict, context, privileged)
            } else {
                UnpairedBody(developerOptionsOn, dndOn, context)
            }
        }
    }
}

/** spec: device-backlight-ring.md SS6: "Header line (monospace) and icon", check for a verified OK verdict, warning otherwise. */
@Composable
private fun HeaderLine(keyStored: Boolean, checking: Boolean, verdict: BrokerVerdict?) {
    val title = when {
        !keyStored -> "Setup needed"
        verdict == BrokerVerdict.OK -> "Paired"
        verdict == null || checking -> "Checking…"
        else -> "Cannot reach the system"
    }
    val isVerifiedOk = keyStored && verdict == BrokerVerdict.OK
    val color = if (isVerifiedOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (isVerifiedOk) Icons.Filled.Check else Icons.Filled.Warning, contentDescription = null, tint = color)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            color = color,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun PairedBody(
    verdict: BrokerVerdict?,
    context: android.content.Context,
    privileged: brobata.physiboard.device.privileged.PrivilegedServices,
) {
    val body = when (verdict) {
        null -> "Testing whether the tools can reach the system."
        BrokerVerdict.OK -> "The tools below can reach the system. Survives reboots."
        BrokerVerdict.WIRELESS_DEBUGGING_OFF -> "Wireless debugging is off, so the backlight setting cannot be applied. Android turns it off after a restart — turn it back on in Developer options."
        BrokerVerdict.NO_SERVICE -> "Wireless debugging is on but the phone is not advertising it. Turn it off and on again in Developer options."
        BrokerVerdict.REJECTED -> "Paired, but your phone is refusing the connection — the pairing code was probably mistyped, so the app is holding a key your phone never accepted. Re-pair to fix it."
        BrokerVerdict.NOT_PAIRED -> "Not paired. Set up wireless debugging below to apply the backlight setting."
    }
    Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (verdict == BrokerVerdict.WIRELESS_DEBUGGING_OFF || verdict == BrokerVerdict.NO_SERVICE) {
            TextButton(onClick = { openWirelessDebugging(context) }) { Text("Open Wireless debugging") }
        }
        TextButton(onClick = {
            privileged.broker.forgetPairing()
            PairingWatcherService.stop(context)
        }) { Text(if (verdict == BrokerVerdict.REJECTED) "Re-pair" else "Forget pairing") }
    }
}

@Composable
private fun UnpairedBody(developerOptionsOn: Boolean, dndOn: Boolean, context: android.content.Context) {
    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val steps = if (!developerOptionsOn) {
        listOf("Open Settings → About phone", "Tap Build number seven times", "Come back here")
    } else {
        listOf("Turn on Wireless debugging", "Tap Pair device with pairing code", "Type the code into the PhysiBoard notification")
    }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        steps.forEachIndexed { index, step -> Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
    }
    val warning = when {
        !notificationsGranted -> "Allow PhysiBoard notifications first — the pairing code arrives as one."
        dndOn -> "Do Not Disturb is on and may hide the pairing code. Turn it off until you are paired."
        else -> null
    }
    warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    TextButton(
        onClick = {
            if (!developerOptionsOn) {
                context.startActivity(Intent(AndroidSettings.ACTION_DEVICE_INFO_SETTINGS))
            } else {
                openWirelessDebugging(context)
            }
        },
        modifier = Modifier.padding(top = 8.dp),
    ) { Text(if (!developerOptionsOn) "Open About phone" else "Open Wireless debugging") }
}

private fun openWirelessDebugging(context: android.content.Context) {
    // spec: broker-privileged-toolbox.md SS4.1 ("tries android.settings.ADB_WIRELESS_SETTINGS, falls
    // back to android.settings.APPLICATION_DEVELOPMENT_SETTINGS"). Not a public SDK constant.
    val intent = Intent(ACTION_ADB_WIRELESS_SETTINGS)
    val resolves = intent.resolveActivity(context.packageManager) != null
    context.startActivity(if (resolves) intent else Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
}

private const val ACTION_ADB_WIRELESS_SETTINGS = "android.settings.ADB_WIRELESS_SETTINGS"

private fun readGlobalFlag(context: android.content.Context, key: String): Boolean =
    AndroidSettings.Global.getInt(context.contentResolver, key, 0) != 0
