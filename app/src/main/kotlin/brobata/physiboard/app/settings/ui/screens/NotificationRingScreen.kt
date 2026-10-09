package brobata.physiboard.app.settings.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.AppCatalog
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.ColorWheelPicker
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.app.settings.ui.WatchBrokerVerdict
import brobata.physiboard.app.settings.ui.WideDialogProperties
import brobata.physiboard.app.settings.ui.wideDialog
import brobata.physiboard.core.settings.RingBrightness
import brobata.physiboard.device.privileged.broker.BrokerVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Notification ring" (device-backlight-ring.md SS5.10). Grant status, colour, brightness, icons
 * and the keyboard-dark switch all read and write the typed settings; the three grants and "Fit
 * the ring to the lens" reach `:device:privileged` (SS5.9, SS5.7.5).
 */
@Composable
fun NotificationRingScreen(onBack: () -> Unit, onNavigateFit: () -> Unit, onNavigateToolbox: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as PhysiBoardApplication
    val privileged = application.privileged
    val controller = LocalSettingsController.current
    val settings = controller.current.value
    val scope = rememberCoroutineScope()
    val device = settings.device

    var listenerGranted by remember { mutableStateOf(privileged.permissions.isNotificationListenerGranted()) }
    var fullScreenGranted by remember { mutableStateOf(privileged.permissions.canUseFullScreenIntent()) }
    var notificationsGranted by remember { mutableStateOf(privileged.permissions.areNotificationsEnabled()) }
    var granting by remember { mutableStateOf(false) }
    var editingColorFor by remember { mutableStateOf<String?>(null) }
    var showAddApp by remember { mutableStateOf(false) }
    // spec broker-privileged-toolbox.md SS5.2: a screen showing a verdict polls for it.
    WatchBrokerVerdict(privileged)
    val verdict by privileged.broker.verdict.collectAsState()

    LaunchedEffect(Unit) {
        listenerGranted = privileged.permissions.isNotificationListenerGranted()
        fullScreenGranted = privileged.permissions.canUseFullScreenIntent()
        notificationsGranted = privileged.permissions.areNotificationsEnabled()
    }
    // spec: device-backlight-ring.md SS5.9: "The three grant rows are re-read every time the
    // screen resumes, since they change behind it" (granting one in Android's own settings and
    // coming back must not leave a stale row).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerGranted = privileged.permissions.isNotificationListenerGranted()
                fullScreenGranted = privileged.permissions.canUseFullScreenIntent()
                notificationsGranted = privileged.permissions.areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreenScaffold(title = "Notification ring", onBack = onBack) {
        RowList {
            item {
                Text(
                    "When a notification arrives with the screen off, a ring lights up around the camera hole in the app's colour. Touch the screen, press a key or unlock to end it.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                SwitchRow(
                    label = "Ring on new notifications",
                    description = "Only notifications you could dismiss; nothing for downloads, playback or apps running in the background.",
                    checked = device.ringEnabled,
                    onCheckedChange = { checked ->
                        controller.update { it.copy(device = it.device.copy(ringEnabled = checked)) }
                        if (checked) privileged.runSetupAsync("ring_switch")
                    },
                )
            }
            header("Permissions")
            if (!listenerGranted || !fullScreenGranted || !notificationsGranted) {
                item {
                    if (verdict == BrokerVerdict.OK) {
                        ButtonRow(
                            label = "Grant with the paired setup",
                            description = "Pair wireless debugging once and every permission here is granted without a trip into system settings.",
                            buttonText = if (granting) "Granting…" else "Grant",
                            enabled = !granting,
                            onClick = {
                                granting = true
                                scope.launch(Dispatchers.IO) {
                                    privileged.setup.run("ring_screen_grant")
                                    listenerGranted = privileged.permissions.isNotificationListenerGranted()
                                    fullScreenGranted = privileged.permissions.canUseFullScreenIntent()
                                    notificationsGranted = privileged.permissions.areNotificationsEnabled()
                                    granting = false
                                }
                            },
                        )
                    } else {
                        ButtonRow(
                            label = "Pair wireless debugging once",
                            description = "Set it up on the Keyboard backlight screen and every permission here is granted without a trip into system settings.",
                            buttonText = "Set up pairing",
                            onClick = onNavigateToolbox,
                        )
                    }
                }
            }
            item {
                GrantRow("Notification access", listenerGranted, "Granted", "Not granted, the ring cannot see notifications") {
                    context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            item {
                GrantRow("Show over the lock screen", fullScreenGranted, "Allowed", "Not allowed, the ring cannot turn the screen on") {
                    val intent = if (Build.VERSION.SDK_INT >= 34) {
                        Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:${context.packageName}"))
                    } else {
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            }
            item {
                GrantRow("PhysiBoard notifications", notificationsGranted, "Allowed", "Blocked, the ring is announced through a silent notification you never see") {
                    context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            header("Behaviour")
            item {
                IntRangeRow(
                    label = "Keep the screen on for",
                    description = "After this the ring lets go and the phone's own timeout turns the screen off. Longer costs battery: the panel is on, even if almost all of it is black.",
                    value = device.ringMinutes,
                    range = IntClosedRange(1, 60),
                    valueLabel = { "$it min" },
                    // spec: device-backlight-ring.md SS5.7.3: "the value is saved when the drag ends".
                    commitOnRelease = true,
                    onValueChange = { value -> controller.update { it.copy(device = it.device.copy(ringMinutes = value)) } },
                )
            }
            item {
                SingleChoiceChipsRow(
                    label = "Ring brightness",
                    options = RingBrightness.entries,
                    optionLabel = ::brightnessLabel,
                    selected = device.ringBrightness,
                    onSelect = { value -> controller.update { it.copy(device = it.device.copy(ringBrightness = value)) } },
                )
            }
            item {
                SwitchRow(
                    label = "Show app icons",
                    description = "Draw the waiting apps' icons below the ring. Off, it is just the ring.",
                    checked = device.ringShowIcons,
                    onCheckedChange = { checked -> controller.update { it.copy(device = it.device.copy(ringShowIcons = checked)) } },
                )
            }
            item {
                // spec: device-backlight-ring.md SS5.7.4: the base description always shows; the
                // "Unavailable until..." line is a separate note "in the error colour" below it,
                // not part of the same muted description line.
                val missingPermissionNote = if (!privileged.permissions.hasWriteSecureSettings()) {
                    "Unavailable until the phone has been paired once, on the Keyboard backlight screen."
                } else {
                    null
                }
                SwitchRow(
                    label = "Keep the keyboard dark",
                    description = "The keyboard backlight normally comes on whenever the screen does, so a ring at night lights up the whole keyboard with it.",
                    note = missingPermissionNote,
                    noteIsError = true,
                    checked = device.ringKeyboardDark,
                    onCheckedChange = { checked -> controller.update { it.copy(device = it.device.copy(ringKeyboardDark = checked)) } },
                )
            }
            header("Colour")
            item {
                ColorRow(label = "Default colour", color = device.ringDefaultColor ?: DEFAULT_GREEN) { editingColorFor = DEFAULT_COLOR_KEY }
            }
            item { Text("App colours", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
            // spec: device-backlight-ring.md SS5.4: "the list is sorted by app label, case-insensitive".
            device.ringAppColors.entries
                .map { (pkg, color) -> Triple(pkg, AppCatalog.labelFor(context, pkg), color) }
                .sortedBy { (_, label, _) -> label.lowercase() }
                .forEach { (pkg, label, color) ->
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ColorDot(color, size = 22.dp) { editingColorFor = pkg }
                            Text(label, modifier = Modifier.padding(start = 12.dp).weight(1f))
                            TextButton(onClick = { controller.update { it.copy(device = it.device.copy(ringAppColors = it.device.ringAppColors - pkg)) } }) { Text("Remove") }
                        }
                    }
                }
            item { ButtonRow(label = "Add an app", buttonText = "Choose", onClick = { showAddApp = true }) }
            header("Fit")
            item {
                ButtonRow(
                    label = "Fit the ring to the lens",
                    description = "The ring comes fitted to one Titan 2 Elite, and panels differ by a few pixels. If yours is off: on a white screen the lens shows as a dark spot: drag the ring onto it, resize it and set the thickness you want. Auto puts the fitted default back.",
                    buttonText = "Fit",
                    onClick = onNavigateFit,
                )
            }
            header("Try it")
            item {
                ButtonRow(
                    label = "Try it",
                    description = "Shows the ring for a few seconds with PhysiBoard's own icon. Lock the phone first to see it the way it will really look.",
                    buttonText = "Try it",
                    onClick = { privileged.ring.startDemo() },
                )
            }
        }
    }

    editingColorFor?.let { key ->
        val current = if (key == DEFAULT_COLOR_KEY) device.ringDefaultColor ?: DEFAULT_GREEN else device.ringAppColors[key] ?: DEFAULT_GREEN
        AlertDialog(
            onDismissRequest = { editingColorFor = null },
            title = { Text(if (key == DEFAULT_COLOR_KEY) "Default colour" else key) },
            text = {
                ColorWheelPicker(colorArgb = current) { picked ->
                    controller.update {
                        if (key == DEFAULT_COLOR_KEY) it.copy(device = it.device.copy(ringDefaultColor = picked))
                        else it.copy(device = it.device.copy(ringAppColors = it.device.ringAppColors + (key to picked)))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editingColorFor = null }) { Text("Done") } },
        )
    }

    if (showAddApp) {
        val apps = remember { AppCatalog.installedApps(context) }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddApp = false },
            properties = WideDialogProperties,
            modifier = Modifier.wideDialog(),
            title = { Text("Add an app") },
            text = {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        items(apps.filter { it.label.contains(query, ignoreCase = true) }) { app ->
                            Text(
                                app.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = MinTouchTarget)
                                    .clickable {
                                        controller.update { it.copy(device = it.device.copy(ringAppColors = it.device.ringAppColors + (app.packageName to DEFAULT_GREEN))) }
                                        showAddApp = false
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddApp = false }) { Text("Cancel") } },
        )
    }
}

private const val DEFAULT_GREEN = 0xFF34C759.toInt()
private const val DEFAULT_COLOR_KEY = "\u0000default"

@Composable
private fun GrantRow(label: String, granted: Boolean, grantedText: String, notGrantedText: String, onOpenSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (granted) grantedText else notGrantedText,
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!granted) TextButton(onClick = onOpenSettings) { Text("Open settings") }
    }
}

@Composable
private fun ColorRow(label: String, color: Int, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ColorDot(color, size = 28.dp, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ColorDot(color: Int, size: Dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick)) {
        Canvas(modifier = Modifier.size(size)) { drawCircle(Color(color)) }
    }
}

private fun brightnessLabel(brightness: RingBrightness): String = when (brightness) {
    RingBrightness.DIM -> "Dim"
    RingBrightness.NORMAL -> "Normal"
    RingBrightness.BRIGHT -> "Bright"
}
