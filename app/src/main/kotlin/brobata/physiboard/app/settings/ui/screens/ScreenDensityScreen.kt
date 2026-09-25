package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.toolbox.DisplayDensity
import brobata.physiboard.device.privileged.toolbox.DensityApplyOutcome
import brobata.physiboard.device.privileged.toolbox.DensityReadOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Screen density" (broker-privileged-toolbox.md SS13). The 15 s countdown is the safety model
 * for a change that can make the screen unreadable; "Back to stock" has none because a reset is
 * always safe.
 */
@Composable
fun ScreenDensityScreen(onBack: () -> Unit, onNavigateToolbox: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val density = application.privileged.density
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var notPaired by remember { mutableStateOf(false) }
    var physical by remember { mutableIntStateOf(0) }
    var current by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableIntStateOf(0) }
    var overridden by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }

    fun load() {
        scope.launch(Dispatchers.IO) {
            loading = true
            when (val outcome = density.read()) {
                DensityReadOutcome.NotPaired -> notPaired = true
                DensityReadOutcome.Unreadable -> notPaired = true
                is DensityReadOutcome.Loaded -> {
                    notPaired = false
                    physical = outcome.reading.physicalDpi
                    current = outcome.reading.currentDpi
                    chosen = outcome.reading.currentDpi
                    overridden = outcome.reading.overridden
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    SettingsScreenScaffold(title = "Screen density", onBack = onBack) {
        RowList {
            item {
                Text(
                    "Lower density fits more on screen; higher makes everything bigger. The Titan's screen is short, so a small reduction buys a surprising number of extra lines — and with a physical keyboard, none of that space goes back to an on-screen one.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            when {
                loading -> item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
                notPaired -> {
                    item { Text("Needs the same wireless-debugging pairing as the keyboard backlight.", modifier = Modifier.padding(16.dp)) }
                    item { ButtonRow(label = "Set up pairing", buttonText = "Open", onClick = onNavigateToolbox) }
                }
                else -> {
                    val range = DisplayDensity.safeRange(physical)
                    item {
                        IntRangeRow(
                            label = "Density",
                            description = if (chosen > physical) "Everything larger, less fits on screen" else if (chosen < physical) "Everything smaller, more fits on screen" else "The density this screen shipped with",
                            value = chosen,
                            range = IntClosedRange(range.first, range.last),
                            step = 5,
                            valueLabel = { "$it dpi (stock is $physical)" },
                            onValueChange = { value -> chosen = DisplayDensity.snapToStep(value) },
                        )
                    }
                    item {
                        // spec SS13 ("Apply is enabled only when the chosen value differs from the current one and nothing is in flight").
                        ButtonRow(
                            label = "Apply",
                            buttonText = if (applying) "Applying…" else "Apply",
                            enabled = chosen != current && !applying,
                            onClick = {
                                applying = true
                                scope.launch(Dispatchers.IO) {
                                    when (val outcome = density.apply(physical, chosen)) {
                                        is DensityApplyOutcome.Applied -> { current = chosen; overridden = true; countdown = 15 }
                                        is DensityApplyOutcome.Refused -> error = outcome.reason
                                        is DensityApplyOutcome.Failed -> error = outcome.reason
                                    }
                                    applying = false
                                }
                            },
                        )
                    }
                    if (overridden) {
                        item {
                            ButtonRow(
                                label = "Back to stock",
                                buttonText = "Reset",
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        if (density.backToStock()) {
                                            current = physical; chosen = physical; overridden = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Screen density") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }

    countdown?.let { seconds ->
        LaunchedEffect(seconds) {
            if (seconds > 0) {
                delay(1000)
                countdown = seconds - 1
            } else {
                scope.launch(Dispatchers.IO) {
                    density.revertNow()
                    load()
                }
                countdown = null
            }
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Can you still read this?") },
            text = { Text("Reverting to the stock density in $seconds seconds unless you keep it. If the screen is unusable, just wait — this undoes itself.") },
            confirmButton = {
                TextButton(onClick = {
                    // keep() commits the pending-revert record synchronously, like its three siblings here.
                    scope.launch(Dispatchers.IO) { density.keep() }
                    countdown = null
                    overridden = true
                }) { Text("Keep it") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { density.revertNow(); load() }
                    countdown = null
                }) { Text("Undo now") }
            },
        )
    }
}
