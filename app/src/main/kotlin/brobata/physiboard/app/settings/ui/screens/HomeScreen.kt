package brobata.physiboard.app.settings.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import brobata.physiboard.app.settings.ui.terminalPane
import brobata.physiboard.app.settings.ui.SpellCheckerSettings
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.KeycapIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
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
    var keyStored by remember { mutableStateOf(application.privileged.broker.isPaired()) }
    // spec: SS6.3, "Turn on spell checking": offered only while unpaired, since pairing does it.
    var offerSpellCheck by remember { mutableStateOf(false) }
    // The accessibility service is off while one of its two features is wanted: a warning line on
    // the status card, as other apps flag a permission they still need (maintainer, 2026-10-09).
    var offerAccessibility by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            probe = ImeProbeAndroid.evaluate(context, ImeComponent.SERVICE_CLASS_NAME)
            keyStored = application.privileged.broker.isPaired()
            offerSpellCheck = !keyStored && SpellCheckerSettings.shouldOfferTurnOn(context, controller.current.value.device.autoSelectSpellChecker)
            val keys = controller.current.value.keys
            offerAccessibility = (keys.accessibilityFocusField || keys.accessibilityFnShortcuts) &&
                !brobata.physiboard.device.privileged.setup.AndroidPermissionProbe.accessibilityServiceEnabled(context)
            delay(2000)
        }
    }

    // spec: SS4.7, the one-time POST_NOTIFICATIONS prompt, ignored, once per home screen creation.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* SS4.7: the answer is ignored */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val brokerVerdict by application.privileged.broker.verdict.collectAsState()
    val toolboxDensity = rememberToolboxDensity(keyStored && brokerVerdict == BrokerVerdict.OK)

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
                plainItem(key = "status") { HomeStatusCard(probe, updateState, brokerLabel, offerSpellCheck, offerAccessibility, onNavigate) }
                if (query.isBlank()) {
                    plainItem(key = "toolbox") { TitanToolboxCard(settings.device, brokerVerdict, keyStored, toolboxDensity, onNavigate) }
                }
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
                    homeIndex(settings, onNavigate)
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
 * The category index (docs/plans/settings-reorganization.md): four panes, from what changes
 * every sentence to what is touched once. Titan tools is the featured toolbox pane above, not a row. A category with one screen opens it directly; the rest
 * open a small screen that gathers theirs.
 */
private fun SettingsListScope.homeIndex(settings: Settings, onNavigate: (String) -> Unit) {
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
    // Titan tools is not repeated here: the toolbox pane above the search is its entry (SS6.4a).
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
private fun HomeHeader() = TerminalHeader()

/**
 * The header Home draws, shared with the first-run pages (SS4): `physiboard:~$`, then [command]
 * when given (`physiboard:~$ setup`), the breathing cursor, and [trailing] at the far end (the
 * first-run step counter).
 */
@Composable
internal fun TerminalHeader(command: String? = null, cursorPeriodMillis: Int = 600, trailing: (@Composable () -> Unit)? = null) {
    // The prompt sits on the page itself (2026-10-09, the maintainer: the dark band read as a
    // separate, darker title on the light theme), set off by the same 1 dp amber rule the panes
    // use, so the header belongs to the skin in both themes.
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = 2.dp)
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(if (command == null) "physiboard:~$" else "physiboard:~$ $command", style = PhysiBoardType.prompt, color = accent)
            TerminalCursor(modifier = Modifier.padding(start = 6.dp, bottom = 4.dp), periodMillis = cursorPeriodMillis)
            if (trailing != null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomEnd) { trailing() }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(accent.copy(alpha = 0.45f)))
    }
}

/**
 * The prompt's idle cursor (app-shell.md SS22.1): an amber underscore, the same `_` the search
 * field ends in, breathing softly rather than hard-blinking a block. Held steady under reduced
 * motion.
 */
@Composable
fun TerminalCursor(modifier: Modifier = Modifier, periodMillis: Int = 600) {
    val reducedMotion = rememberReducedMotion()
    val alpha = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "terminal_cursor")
        val breath by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(animation = tween(periodMillis * 2, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "terminal_cursor_breath",
        )
        breath
    }
    Box(modifier = modifier.size(width = 12.dp, height = 3.dp).alpha(alpha).background(MaterialTheme.colorScheme.primary))
}

/**
 * The one status card (app-shell.md SS6.3): the thing that needs doing, in an amber-bordered
 * pane, or a calm "ready" pane that opens the full status check. Only one is ever shown. Below
 * either, while unpaired and PhysiBoard is not the spell checker it would choose itself, a second
 * line offers "Turn on spell checking", which opens Android's spell checker screen.
 */
@Composable
private fun HomeStatusCard(
    probe: ImeProbeResult,
    updateState: brobata.physiboard.app.shell.UpdateCheckState,
    brokerLabel: String?,
    offerSpellCheck: Boolean,
    offerAccessibility: Boolean,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val (title, subtitle, icon, action) = when {
        !probe.enabled -> StatusCardContent("Enable PhysiBoard", "Turn it on in system keyboard settings", Icons.Outlined.ToggleOn) {
            context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
        }
        !probe.selected -> StatusCardContent("Set as keyboard", "Pick PhysiBoard from the input switcher", Icons.Outlined.Keyboard) {
            (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
        }
        updateState.foundRelease != null -> {
            val release = updateState.foundRelease!!
            StatusCardContent("Update available", "Version ${release.tag} is ready to install", Icons.Outlined.SystemUpdate) { updateState.reopenDialog() }
        }
        else -> StatusCardContent(
            "Ready to type",
            if (brokerLabel == null) "PhysiBoard is your keyboard" else "PhysiBoard is your keyboard · Titan tools $brokerLabel",
            null,
        ) { onNavigate(Routes.STATUS) }
    }
    val ready = icon == null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .terminalPane(featured = !ready),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = action).padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ready) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = CategoryTint.EMERALD.glyph(dark), modifier = Modifier.size(28.dp))
            } else {
                KeycapIcon(icon!!, size = 40.dp, iconSize = 22.dp, category = CategoryTint.AMBER)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (ready) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (offerAccessibility) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onNavigate(Routes.ACCESSIBILITY_SERVICE) }
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                    Text("Accessibility service is off", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Needed for Fn shortcuts everywhere and focusing the text box", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (offerSpellCheck) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { SpellCheckerSettings.open(context) }
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Spellcheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                    Text("Turn on spell checking", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Pick PhysiBoard so apps underline misspellings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** What the status card shows; a null [icon] is the calm "ready" card. */
private data class StatusCardContent(val title: String, val subtitle: String, val icon: ImageVector?, val action: () -> Unit)

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
