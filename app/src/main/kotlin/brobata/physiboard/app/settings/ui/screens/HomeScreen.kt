package brobata.physiboard.app.settings.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.CategoryRow
import brobata.physiboard.app.settings.ui.CategoryTint
import brobata.physiboard.app.settings.ui.EmptyState
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.PhysiBoardColors
import brobata.physiboard.app.settings.ui.PhysiBoardType
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SearchCatalog
import brobata.physiboard.app.settings.ui.SearchPill
import brobata.physiboard.app.settings.ui.SettingsListScope
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.app.settings.ui.Summaries
import brobata.physiboard.app.settings.ui.rememberReducedMotion
import brobata.physiboard.app.shell.AutoUpdateCheckOnCreate
import brobata.physiboard.app.shell.DeviceDetectionAndroid
import brobata.physiboard.app.shell.ImeComponent
import brobata.physiboard.app.shell.ImeProbeAndroid
import brobata.physiboard.app.shell.UpdateFoundDialog
import brobata.physiboard.app.shell.rememberUpdateCheckState
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.shell.GithubChecks
import brobata.physiboard.core.shell.ImeProbeResult
import brobata.physiboard.core.shell.TitanModel
import brobata.physiboard.device.privileged.broker.BrokerVerdict
import kotlinx.coroutines.delay

/**
 * The home screen the launcher icon draws once setup is done (app-shell.md SS6): the terminal
 * header, one status card that says whether PhysiBoard is ready (or the one thing that needs
 * doing), the settings search, and the category index. Each index row shows its category's
 * icon, name and a live one-line summary of where its settings stand ([Summaries]), the settings
 * standard the maintainer's other apps share: a category index into detail screens, with the
 * rows drawn at once and nothing to wait for.
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
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { SearchCatalog.search(query) }
    val brokerLabel = brokerTileLabel(brokerVerdict)

    Column(modifier = Modifier.fillMaxSize()) {
        HomeHeader()
        Box(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
            RowList {
                plainItem(key = "status") { HomeStatusCard(probe, updateState, brokerLabel, onNavigate) }
                plainItem(key = "search") {
                    SearchPill(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search settings…",
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                    )
                }
                if (query.isNotBlank()) {
                    if (results.isEmpty()) {
                        plainItem(key = "no_results") { EmptyState(Icons.Filled.SearchOff, "No settings match “$query”. Try a shorter word.") }
                    } else {
                        header("")
                        items(results, key = { "result:${it.title}" }) { entry ->
                            NavigateRow(
                                label = entry.title,
                                description = if (entry.screenTitle != entry.title) "In ${entry.screenTitle}" else null,
                                icon = Icons.Filled.Search,
                            ) {
                                query = ""
                                onNavigate(entry.route)
                            }
                        }
                    }
                } else {
                    homeIndex(settings, brokerLabel, onNavigate)
                }
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
 * The category index (docs/plans/settings-reorganization.md): four cards, from what changes
 * every sentence to what is touched once. A category with one screen opens it directly; the rest
 * open a small screen that gathers theirs.
 */
private fun SettingsListScope.homeIndex(settings: Settings, brokerLabel: String?, onNavigate: (String) -> Unit) {
    header("")
    item { CategoryRow("Typing", Summaries.typing(settings), Icons.Outlined.TextFields, CategoryTint.AMBER) { onNavigate(Routes.TYPING) } }
    item { CategoryRow("Autocorrect & words", Summaries.autocorrect(settings), Icons.Outlined.Spellcheck, CategoryTint.EMERALD) { onNavigate(Routes.AUTO_CORRECTION) } }
    item { CategoryRow("Languages & layouts", Summaries.languages(settings), Icons.Outlined.Language, CategoryTint.SKY) { onNavigate(Routes.INPUT_LANGUAGES) } }
    header("")
    item { CategoryRow("Long press & accents", Summaries.longPress(settings), Icons.Outlined.Timer, CategoryTint.VIOLET) { onNavigate(Routes.LONG_PRESS) } }
    item { CategoryRow("Sym pages", Summaries.symPages(settings), Icons.Outlined.EmojiSymbols, CategoryTint.ORANGE) { onNavigate(Routes.CUSTOMIZE_SYM_KEYBOARD) } }
    item { CategoryRow("Voice", Summaries.voice(settings), Icons.Outlined.Mic, CategoryTint.ROSE) { onNavigate(Routes.VOICE) } }
    item { CategoryRow("Keys & shortcuts", Summaries.keys(settings), Icons.Outlined.KeyboardCommandKey, CategoryTint.INDIGO) { onNavigate(Routes.KEYS) } }
    item { CategoryRow("Apps", Summaries.apps(settings), Icons.Outlined.Apps, CategoryTint.TEAL) { onNavigate(Routes.APPS) } }
    header("")
    item { CategoryRow("Look & feel", Summaries.look(settings), Icons.Outlined.Palette, CategoryTint.AMBER) { onNavigate(Routes.LOOK) } }
    item { CategoryRow("Privacy", Summaries.privacy(settings), Icons.Outlined.Shield, CategoryTint.EMERALD) { onNavigate(Routes.PRIVACY) } }
    item {
        CategoryRow(
            "Titan tools",
            brokerLabel?.let { "Titan tools $it" } ?: "Backlight, notification ring, screen",
            Icons.Outlined.Handyman,
            CategoryTint.SKY,
            attention = brokerLabel != null,
        ) { onNavigate(Routes.T2E_TOOLS) }
    }
    header("")
    item { CategoryRow("Backup & restore", "Save your settings to a file, or reset them", Icons.Outlined.SettingsBackupRestore, CategoryTint.SLATE) { onNavigate(Routes.BACKUP) } }
    item { CategoryRow("Help", "Status check, test field, diagnostics", Icons.AutoMirrored.Outlined.HelpOutline, CategoryTint.SLATE) { onNavigate(Routes.HELP) } }
    item { CategoryRow("About", "Version ${BuildConfig.VERSION_NAME}", Icons.Outlined.Info, CategoryTint.SLATE) { onNavigate(Routes.ABOUT) } }
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
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("physiboard:~$", style = PhysiBoardType.prompt, color = PhysiBoardColors.SignalAmber)
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

/**
 * The one status card (app-shell.md SS6.2): the thing that needs doing, in the accent's container
 * colour, or a calm "ready" card that opens the full status check. Only one is ever shown.
 */
@Composable
private fun HomeStatusCard(probe: ImeProbeResult, updateState: brobata.physiboard.app.shell.UpdateCheckState, brokerLabel: String?, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    when {
        !probe.enabled -> ActionCard("Enable PhysiBoard", "Turn it on in system keyboard settings", Icons.Outlined.ToggleOn) {
            context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
        }
        !probe.selected -> ActionCard("Set as keyboard", "Pick PhysiBoard from the input switcher", Icons.Outlined.Keyboard) {
            (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
        }
        updateState.foundRelease != null -> {
            val release = updateState.foundRelease!!
            ActionCard("Update available", "Version ${release.tag} is ready to install", Icons.Outlined.SystemUpdate) { updateState.reopenDialog() }
        }
        else -> ReadyCard(
            subtitle = if (brokerLabel == null) "PhysiBoard is your keyboard" else "PhysiBoard is your keyboard · Titan tools $brokerLabel",
            onClick = { onNavigate(Routes.STATUS) },
        )
    }
}

/** The one thing that needs doing, in the accent's container colour so it stands apart from the index. */
@Composable
private fun ActionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Row(modifier = Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.l)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** All clear: a quiet card on the page's own surface, a green check and one line, tappable for the details. */
@Composable
private fun ReadyCard(subtitle: String, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Row(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = CategoryTint.EMERALD.glyph(dark),
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.l)) {
                Text("Ready to type", style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * What the Titan tools row says when the broker is not usable. Every verdict used to read
 * "needs pairing", which sent the maintainer to re-pair a pairing that was intact: theirs was
 * [BrokerVerdict.WIRELESS_DEBUGGING_OFF], which Android causes by itself after a restart and which
 * the screen behind this row already describes correctly (2026-09-29). Null means nothing is
 * wrong and the row shows no warning.
 */
private fun brokerTileLabel(verdict: BrokerVerdict?): String? = when (verdict) {
    null, BrokerVerdict.OK -> null
    BrokerVerdict.NOT_PAIRED -> "needs pairing"
    BrokerVerdict.WIRELESS_DEBUGGING_OFF -> "debugging off"
    BrokerVerdict.NO_SERVICE -> "unreachable"
    BrokerVerdict.REJECTED -> "pairing refused"
}
