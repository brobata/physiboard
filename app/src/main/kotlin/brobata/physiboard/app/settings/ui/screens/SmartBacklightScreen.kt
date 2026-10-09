package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.brokerBlockerState
import brobata.physiboard.app.settings.ui.WatchBrokerVerdict
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.device.privileged.PrivilegedStep
import brobata.physiboard.device.privileged.broker.BrokerVerdict
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Smart keyboard backlight" (device-backlight-ring.md SS3.4). 3.0 drops embedding the device
 * setup card here (SS11 Keep/Drop, "keep one home for pairing"); an unconfigured switch instead
 * links back to the T2E Tools hub, which is the card's one home.
 */
@Composable
fun SmartBacklightScreen(onBack: () -> Unit, onNavigateToolbox: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val privileged = application.privileged
    val controller = LocalSettingsController.current
    val settings = controller.current.value
    val scope = rememberCoroutineScope()

    val verdict by privileged.broker.verdict.collectAsState()
    var checkingNow by remember { mutableStateOf(false) }
    var deviceValueStale by remember { mutableStateOf(false) }
    var lastFailureReason by remember { mutableStateOf<String?>(null) }

    val enabled = settings.device.smartBacklightEnabled
    val latch = settings.captures.smartBacklightApplied
    WatchBrokerVerdict(privileged)
    val blocker = brokerBlockerState(privileged)

    LaunchedEffect(enabled, latch, verdict) {
        while (true) {
            lastFailureReason = privileged.diagnostics.step(PrivilegedStep.BACKLIGHT)?.takeIf { !it.ok }?.reason
            if (enabled && latch && verdict == BrokerVerdict.OK) {
                // spec SS3.4: "asks the phone for the timeout's actual value (blocking read on a background thread) whenever the verified status is OK".
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { privileged.backlight.readDeviceTimeout() }
                deviceValueStale = privileged.backlight.isDeviceValueStale(true)
            }
            delay(2000)
        }
    }

    LaunchedEffect(enabled, verdict, privileged.broker.isPaired()) {
        if (enabled && privileged.broker.isPaired()) privileged.broker.verify()
        // spec SS3.2 event 4: enabled first, pair second re-runs the setup pass once a key appears.
        if (enabled && privileged.broker.isPaired() && !latch) privileged.runSetupAsync("backlight_screen")
    }

    SettingsScreenScaffold(title = "Smart keyboard backlight", onBack = onBack) {
        RowList {
            item {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
                    when {
                        enabled && latch -> {
                            val nonOkMessage = when (verdict) {
                                BrokerVerdict.WIRELESS_DEBUGGING_OFF -> "Wireless debugging is off, so the backlight setting cannot be applied. Android turns it off after a restart. Turn it back on in Developer options."
                                BrokerVerdict.NOT_PAIRED -> "Not paired. Set up wireless debugging below to apply the backlight setting."
                                BrokerVerdict.NO_SERVICE -> "Wireless debugging is on but the phone is not advertising it. Turn it off and on again in Developer options."
                                BrokerVerdict.REJECTED -> "The phone refused the pairing. This happens when a pairing code was mistyped: the app kept a key your phone never accepted. Re-pair to fix it."
                                else -> null
                            }
                            when {
                                nonOkMessage != null -> {
                                    Text("! $nonOkMessage", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                    Row(modifier = Modifier.padding(top = 8.dp)) {
                                        TextButton(onClick = {
                                            privileged.broker.forgetPairing()
                                            controller.update { it.copy(captures = it.captures.copy(smartBacklightApplied = false)) }
                                        }) { Text("Re-pair") }
                                        TextButton(
                                            onClick = { checkingNow = true; scope.launch { privileged.broker.verify(force = true); checkingNow = false } },
                                            enabled = !checkingNow,
                                        ) { Text(if (checkingNow) "Checking…" else "Check again") }
                                    }
                                }
                                blocker != null -> Text("! ${blockerMessage(blocker)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                else -> Text("✓ Always on, set up once, survives reboots.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (verdict == BrokerVerdict.OK && deviceValueStale) {
                                Text(
                                    "! Your phone is no longer holding the always-on setting. A system update or another app reset it. The backlight will time out until it is applied again.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                TextButton(onClick = {
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        privileged.backlight.applyNow(true)
                                        deviceValueStale = privileged.backlight.isDeviceValueStale(true)
                                    }
                                }) { Text("Apply again") }
                            }
                            lastFailureReason?.let { Text("Last attempt failed: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                        }
                        enabled && !latch -> {
                            Text(
                                "Pairing is needed before this can apply.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = onNavigateToolbox, modifier = Modifier.padding(top = 8.dp)) { Text("Set up pairing") }
                        }
                        else -> Unit
                    }
                }
            }
            item {
                SwitchRow(
                    label = "Smart backlight",
                    description = "Keeps the keyboard backlight on whenever the screen is on. It needs a one-time pairing, and survives reboots.",
                    checked = enabled,
                    onCheckedChange = { checked ->
                        controller.update { it.copy(device = it.device.copy(smartBacklightEnabled = checked)) }
                        scope.launch { privileged.backlight.applyAsync(checked) }
                    },
                )
            }
        }
    }
}

private fun blockerMessage(blocker: brobata.physiboard.device.privileged.broker.BrokerBlocker): String = when (blocker) {
    brobata.physiboard.device.privileged.broker.BrokerBlocker.NOT_PAIRED -> "Not paired. Set up wireless debugging below to apply the backlight setting."
    brobata.physiboard.device.privileged.broker.BrokerBlocker.WIRELESS_DEBUGGING_OFF -> "Wireless debugging is off, so the backlight setting cannot be applied. Android turns it off after a restart. Turn it back on in Developer options."
}
