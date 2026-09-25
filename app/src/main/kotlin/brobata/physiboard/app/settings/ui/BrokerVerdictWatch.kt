package brobata.physiboard.app.settings.ui

import android.content.Context
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.device.privileged.PrivilegedServices
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import brobata.physiboard.device.privileged.broker.BrokerRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * spec: broker-privileged-toolbox.md SS5.2 and SS5.3: `adb_wifi_enabled` "is polled every
 * 1500 ms while such a screen is open" and the verified status is re-checked whenever the
 * polled value flips. Every screen that shows a broker verdict needs this, not only the setup
 * card: without it a verdict shown on a toolbox screen goes stale the moment the user turns
 * Wireless debugging off or on from the shade (2026-09-25 review).
 */
@Composable
fun WatchBrokerVerdict(privileged: PrivilegedServices) {
    val context = LocalContext.current
    LaunchedEffect(privileged) {
        var previous = readWirelessDebugging(context)
        while (true) {
            delay(BrokerRules.WIRELESS_DEBUGGING_POLL_MS)
            val now = readWirelessDebugging(context)
            if (now != previous) {
                previous = now
                runCatching { privileged.broker.verify(force = true) }
            }
        }
    }
}

/**
 * The reason the broker cannot run right now, refreshed on the same poll. Reading it asks the
 * transport for the stored key and the system flag, so it belongs off the composition rather
 * than in a composable's body, where it re-ran on every unrelated recomposition and never
 * recomposed when it changed.
 */
@Composable
fun brokerBlockerState(privileged: PrivilegedServices): BrokerBlocker? {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(privileged) {
        while (true) {
            delay(BrokerRules.WIRELESS_DEBUGGING_POLL_MS)
            tick++
        }
    }
    val blocker by produceState<BrokerBlocker?>(initialValue = null, privileged, tick) {
        value = withContext(Dispatchers.IO) { runCatching { privileged.broker.blocker() }.getOrNull() }
    }
    return blocker
}

private suspend fun readWirelessDebugging(context: Context): Boolean = withContext(Dispatchers.IO) {
    runCatching { AndroidSettings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) != 0 }.getOrDefault(false)
}
