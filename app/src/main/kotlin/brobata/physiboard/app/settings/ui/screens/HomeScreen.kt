package brobata.physiboard.app.settings.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.TerminalPromptStyle
import brobata.physiboard.app.shell.AutoUpdateCheckOnCreate
import brobata.physiboard.app.shell.ImeComponent
import brobata.physiboard.app.shell.ImeProbeAndroid
import brobata.physiboard.app.shell.UpdateFoundDialog
import brobata.physiboard.app.shell.rememberUpdateCheckState
import brobata.physiboard.app.shell.DeviceDetectionAndroid
import brobata.physiboard.core.shell.GithubChecks
import brobata.physiboard.core.shell.ImeProbeResult
import brobata.physiboard.core.shell.TitanModel
import brobata.physiboard.device.privileged.broker.BrokerVerdict
import kotlinx.coroutines.delay

/**
 * The home screen the launcher icon draws once setup is done (app-shell.md SS6): an action
 * surface, not a settings list. It shows only what needs attention (a setup or update card, or
 * the all-clear line) and a grid of six tiles.
 */
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val controller = LocalSettingsController.current
    val settings = controller.current.value

    var probe by remember { mutableStateOf(ImeProbeAndroid.evaluate(context, ImeComponent.SERVICE_CLASS_NAME)) }
    LaunchedEffect(Unit) {
        while (true) {
            probe = ImeProbeAndroid.evaluate(context, ImeComponent.SERVICE_CLASS_NAME)
            delay(2000)
        }
    }

    // spec: SS4.4, the one-time POST_NOTIFICATIONS prompt, ignored, once per home screen creation.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* SS4.4: the answer is ignored */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val brokerVerdict by application.privileged.broker.verdict.collectAsState()

    val updateState = rememberUpdateCheckState()
    val installer = remember { runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull() }
    val githubChecksAllowed = remember { GithubChecks.allowed(buildFlagOn = true, installerPackageName = installer) }
    if (githubChecksAllowed) {
        // spec: SS6.5, SS13.7. The daily background job this trigger would also (re)schedule is
        // not wired in this milestone (no WorkManager dependency yet); see this module's report.
        AutoUpdateCheckOnCreate(updateState, BuildConfig.VERSION_NAME, settings.shell.dismissedReleases)
    }

    var showUntestedNotice by remember {
        mutableStateOf(!settings.shell.untestedDeviceNoticeSeen && DeviceDetectionAndroid.classify() == TitanModel.TITAN_2)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeHeader()
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            HomeActionCard(probe, updateState, onNavigate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeTile("T2E Tools", subLabel = if (brokerVerdict != null && brokerVerdict != BrokerVerdict.OK) "needs pairing" else null, showDot = brokerVerdict != null && brokerVerdict != BrokerVerdict.OK, modifier = Modifier.weight(1f)) { onNavigate(Routes.T2E_TOOLS) }
                HomeTile("Keyboard", modifier = Modifier.weight(1f)) { onNavigate(Routes.KEYBOARD) }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeTile("Status Bar Theme", modifier = Modifier.weight(1f)) { onNavigate(Routes.STATUS_BAR_THEME) }
                HomeTile(
                    "Status",
                    subLabel = if (probe.enabled && probe.selected) "all good" else "needs setup",
                    showDot = !(probe.enabled && probe.selected),
                    modifier = Modifier.weight(1f),
                ) { onNavigate(Routes.STATUS) }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeTile("Extras", modifier = Modifier.weight(1f)) { onNavigate(Routes.EXTRAS) }
                HomeTile("Settings", modifier = Modifier.weight(1f)) { onNavigate(Routes.SETTINGS) }
            }
        }
    }

    UpdateFoundDialog(updateState)

    if (showUntestedNotice) {
        AlertDialog(
            onDismissRequest = {
                controller.update { it.copy(shell = it.shell.copy(untestedDeviceNoticeSeen = true)) }
                showUntestedNotice = false
            },
            title = { Text("Untested on this phone") },
            text = {
                Text(
                    "PhysiBoard is built and tested on the Unihertz Titan 2 Elite. A Titan 2 shares the " +
                        "same physical keyboard, so most things should work, but none of it is verified. " +
                        "This device is not supported, and bugs found on it may not be fixable.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.update { it.copy(shell = it.shell.copy(untestedDeviceNoticeSeen = true)) }
                    showUntestedNotice = false
                }) { Text("Got it") }
            },
        )
    }
}

@Composable
private fun HomeHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 20.dp, horizontal = 16.dp),
    ) {
        Text("physiboard:~$", style = TerminalPromptStyle, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HomeActionCard(probe: ImeProbeResult, updateState: brobata.physiboard.app.shell.UpdateCheckState, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    when {
        !probe.enabled -> ActionCard("Enable PhysiBoard", "Turn it on in system keyboard settings") {
            context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
        }
        !probe.selected -> ActionCard("Set as keyboard", "Pick PhysiBoard from the input switcher") {
            (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
        }
        updateState.foundRelease != null -> {
            val release = updateState.foundRelease!!
            ActionCard("Update available", "Version ${release.tag} is ready to install") { updateState.reopenDialog() }
        }
        else -> Text(
            "✓ all set",
            color = brobata.physiboard.app.settings.ui.PhysiBoardColors.SignalAmber,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeTile(label: String, subLabel: String? = null, showDot: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.aspectRatio(1.6f)) {
        Card(modifier = Modifier.fillMaxSize().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                if (subLabel != null) Text(subLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(9.dp)
                    .background(color = brobata.physiboard.app.settings.ui.PhysiBoardColors.SignalAmber, shape = CircleShape),
            )
        }
    }
}
