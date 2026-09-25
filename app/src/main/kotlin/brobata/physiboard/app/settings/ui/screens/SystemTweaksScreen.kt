package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.toolbox.AnimationSpeed
import brobata.physiboard.device.privileged.toolbox.TweaksReadOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** "System tweaks" (broker-privileged-toolbox.md SS14). No countdown: none of these can make the phone unusable. */
@Composable
fun SystemTweaksScreen(onBack: () -> Unit, onNavigateToolbox: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val tweaks = application.privileged.tweaks
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var notPaired by remember { mutableStateOf(false) }
    var animation by remember { mutableStateOf(AnimationSpeed.NORMAL) }
    var historyOn by remember { mutableStateOf(false) }
    var oneHandedOn by remember { mutableStateOf(false) }

    fun load() {
        scope.launch(Dispatchers.IO) {
            loading = true
            when (val outcome = tweaks.read()) {
                is TweaksReadOutcome.Loaded -> {
                    notPaired = false
                    animation = outcome.reading.animation
                    historyOn = outcome.reading.notificationHistoryOn
                    oneHandedOn = outcome.reading.oneHandedOn
                }
                else -> notPaired = true
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    SettingsScreenScaffold(title = "System tweaks", onBack = onBack) {
        RowList {
            item {
                Text(
                    "Android supports all of these; Unihertz just never surfaced them. Each one applies straight away and every one can be put back.",
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
                    item {
                        SingleChoiceChipsRow(
                            label = "Animation speed",
                            options = listOf(AnimationSpeed.OFF, AnimationSpeed.FAST, AnimationSpeed.NORMAL),
                            optionLabel = ::animationLabel,
                            selected = animation,
                            onSelect = { speed ->
                                animation = speed
                                scope.launch(Dispatchers.IO) { tweaks.setAnimationSpeed(speed) }
                            },
                        )
                    }
                    item {
                        SwitchRow(
                            label = "Notification history",
                            description = "Keeps a log of notifications you already dismissed, so a swiped-away message is still findable. Standard on Pixel.",
                            checked = historyOn,
                            onCheckedChange = { checked ->
                                historyOn = checked
                                scope.launch(Dispatchers.IO) { tweaks.setNotificationHistory(checked) }
                            },
                        )
                    }
                    item {
                        SwitchRow(
                            label = "One-handed mode",
                            description = "Pull the top of the screen down into reach with a swipe on the navigation bar.",
                            checked = oneHandedOn,
                            onCheckedChange = { checked ->
                                oneHandedOn = checked
                                scope.launch(Dispatchers.IO) { tweaks.setOneHanded(checked) }
                            },
                        )
                    }
                    item {
                        ButtonRow(
                            label = "Put all of these back to stock",
                            buttonText = "Reset",
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    tweaks.resetAll()
                                    animation = AnimationSpeed.NORMAL
                                    historyOn = false
                                    oneHandedOn = false
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun animationLabel(speed: AnimationSpeed): String = when (speed) {
    AnimationSpeed.OFF -> "Off"
    AnimationSpeed.FAST -> "Fast"
    AnimationSpeed.NORMAL -> "Normal"
}
