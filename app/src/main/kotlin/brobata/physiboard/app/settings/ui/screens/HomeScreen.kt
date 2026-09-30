package brobata.physiboard.app.settings.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.PhysiBoardColors
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.rememberReducedMotion
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
        // spec: SS6.5. The interactive check this screen also runs on its own creation. The daily
        // background job (SS13.7) is armed once per process start from PhysiBoardApplication, not
        // re-enqueued here: its keep-if-existing policy makes that equivalent for an always-on
        // single-process app, and it keeps the job's scheduling out of any path `:ime` alone starts.
        AutoUpdateCheckOnCreate(updateState, BuildConfig.VERSION_NAME, settings.shell.dismissedReleases)
    }

    var showUntestedNotice by remember {
        mutableStateOf(!settings.shell.untestedDeviceNoticeSeen && DeviceDetectionAndroid.classify() == TitanModel.TITAN_2)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeHeader()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(16.dp),
        ) {
            HomeActionCard(probe, updateState, onNavigate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeTile("T2E Tools", subLabel = brokerTileLabel(brokerVerdict), showDot = brokerTileLabel(brokerVerdict) != null, modifier = Modifier.weight(1f)) { onNavigate(Routes.T2E_TOOLS) }
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

/**
 * The terminal header (app-shell.md SS22.1): an Ink band, regardless of the app's own light or
 * dark theme, with a 2 dp amber hairline along its top, `physiboard:~$` in bold 18 sp amber and a
 * 10x20 dp amber block cursor fading every 600 ms (held static under reduced motion). The prompt
 * itself is inset below the status bar and the display cutout so it is never hidden behind
 * either (the maintainer's complaint this rebuild fixes); the band's own background reaches the
 * true top of the window, matching the status bar colour set in [brobata.physiboard.app.MainActivity]
 * so the hairline reads as the top edge of one continuous surface. Section 6.1's translucent
 * status-bar scrim (black 30% dark theme, white 20% light) sits over just the status-bar strip.
 */
@Composable
private fun HomeHeader() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    Box(modifier = Modifier.fillMaxWidth().background(PhysiBoardColors.Ink)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = 2.dp)
                .padding(vertical = 20.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("physiboard:~$", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp, color = PhysiBoardColors.SignalAmber)
            TerminalCursor(modifier = Modifier.padding(start = 6.dp))
        }
        // spec: SS6.1, "a translucent overlay ... covers the status-bar area."
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(if (dark) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f)),
        )
        // The 2 dp amber hairline sits at the true top edge of the band.
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(PhysiBoardColors.SignalAmber).align(Alignment.TopStart))
    }
}

/** The 10x20 dp amber block cursor (app-shell.md SS22.1), shared by every terminal header. */
@Composable
fun TerminalCursor(modifier: Modifier = Modifier, periodMillis: Int = 600) {
    val reducedMotion = rememberReducedMotion()
    val alpha = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "terminal_cursor")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(animation = tween(periodMillis), repeatMode = RepeatMode.Reverse),
            label = "terminal_cursor_alpha",
        )
        animated
    }
    Box(modifier = modifier.size(width = 10.dp, height = 20.dp).alpha(alpha).background(PhysiBoardColors.SignalAmber))
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
        // A visible border rather than a soft elevation shadow: the "terminal panel" reading
        // item 6 asked for, on the screen the maintainer named first.
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeTile(label: String, subLabel: String? = null, showDot: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.aspectRatio(2.6f)) {
        Card(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            // Read as a terminal listing rather than a card: the prompt marker, then the name,
            // anchored top-left the way a line of output is.
            Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Top) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$",
                        style = MaterialTheme.typography.titleSmall,
                        color = brobata.physiboard.app.settings.ui.PhysiBoardColors.SignalAmber,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
                if (subLabel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 18.dp),
                    )
                }
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

/**
 * What the T2E tile says under its name when the broker is not usable. Every verdict used to read
 * "needs pairing", which sent the maintainer to re-pair a pairing that was intact: theirs was
 * [BrokerVerdict.WIRELESS_DEBUGGING_OFF], which Android causes by itself after a restart and which
 * the screen behind this tile already describes correctly (2026-09-29). Null means nothing is
 * wrong and the tile shows no warning dot.
 */
private fun brokerTileLabel(verdict: BrokerVerdict?): String? = when (verdict) {
    null, BrokerVerdict.OK -> null
    BrokerVerdict.NOT_PAIRED -> "needs pairing"
    BrokerVerdict.WIRELESS_DEBUGGING_OFF -> "debugging off"
    BrokerVerdict.NO_SERVICE -> "unreachable"
    BrokerVerdict.REJECTED -> "pairing refused"
}
