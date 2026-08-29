package brobata.physiboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import brobata.physiboard.inputmethod.EmbeddedAdbShell
import brobata.physiboard.ring.NotificationRingActivity
import brobata.physiboard.ring.NotificationRingSetup
import brobata.physiboard.ring.RingAdjustActivity
import brobata.physiboard.ring.ColorWheel
import brobata.physiboard.ring.NotificationRingPolicy
import android.content.Intent
import brobata.physiboard.ring.RingBrightness
import brobata.physiboard.ring.RingPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import brobata.physiboard.ui.SettingsTopBar

/**
 * The ring's own page: the switch, the three grants it needs and where each stands, and the
 * two knobs that matter — how long the screen stays on and how bright the ring is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationRingScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var enabled by remember { mutableStateOf(SettingsManager.isNotificationRingEnabled(context)) }
    var minutes by remember { mutableIntStateOf(SettingsManager.getNotificationRingMinutes(context)) }
    var brightness by remember { mutableStateOf(SettingsManager.getNotificationRingBrightness(context)) }
    var icons by remember { mutableStateOf(SettingsManager.isNotificationRingIconsEnabled(context)) }
    var appColors by remember { mutableStateOf(SettingsManager.getNotificationRingAppColors(context)) }
    var defaultColor by remember { mutableStateOf(SettingsManager.getNotificationRingDefaultColor(context)) }
    var pickingDefaultColor by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf(false) }
    var colouringPackage by remember { mutableStateOf<String?>(null) }
    var listenerGranted by remember { mutableStateOf(NotificationRingSetup.isListenerGranted(context)) }
    var fullScreenAllowed by remember { mutableStateOf(NotificationRingSetup.canUseFullScreenIntent(context)) }
    var postAllowed by remember { mutableStateOf(NotificationRingSetup.canPostNotifications(context)) }
    var granting by remember { mutableStateOf(false) }
    val paired = remember { EmbeddedAdbShell.isPaired(context) }

    fun refresh() {
        listenerGranted = NotificationRingSetup.isListenerGranted(context)
        fullScreenAllowed = NotificationRingSetup.canUseFullScreenIntent(context)
        postAllowed = NotificationRingSetup.canPostNotifications(context)
    }

    // Grants change in system settings, behind this screen: re-read on every return.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun grant() {
        if (granting) return
        granting = true
        NotificationRingSetup.grantViaBroker(context) {
            // Worker thread; the composition reads state on the next frame.
            granting = false
            refresh()
        }
    }

    BackHandler { onBack() }

    if (pickingApp) {
        AppPickerDialog(
            onAppSelected = { app -> colouringPackage = app.packageName },
            onDismiss = { pickingApp = false }
        )
    }
    if (pickingDefaultColor) {
        RingColorDialog(
            title = stringResource(R.string.ring_default_color_title),
            current = defaultColor,
            onPick = { argb ->
                SettingsManager.setNotificationRingDefaultColor(context, argb)
                defaultColor = argb
                pickingDefaultColor = false
            },
            onDismiss = { pickingDefaultColor = false }
        )
    }
    colouringPackage?.let { pkg ->
        RingColorDialog(
            title = appLabel(context, pkg),
            current = appColors[pkg],
            onPick = { argb ->
                SettingsManager.setNotificationRingAppColor(context, pkg, argb)
                appColors = SettingsManager.getNotificationRingAppColors(context)
                colouringPackage = null
            },
            onDismiss = { colouringPackage = null }
        )
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.ring_title),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.ring_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ring_enable_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.ring_enable_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            enabled = on
                            SettingsManager.setNotificationRingEnabled(context, on)
                            if (on) grant()
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            GrantRow(
                title = stringResource(R.string.ring_access_title),
                granted = listenerGranted,
                grantedText = stringResource(R.string.ring_access_granted),
                missingText = stringResource(R.string.ring_access_missing),
                onOpenSettings = { context.startActivity(NotificationRingSetup.listenerSettingsIntent()) }
            )
            GrantRow(
                title = stringResource(R.string.ring_fullscreen_title),
                granted = fullScreenAllowed,
                grantedText = stringResource(R.string.ring_fullscreen_granted),
                missingText = stringResource(R.string.ring_fullscreen_missing),
                onOpenSettings = { context.startActivity(NotificationRingSetup.fullScreenSettingsIntent(context)) }
            )
            GrantRow(
                title = stringResource(R.string.ring_post_title),
                granted = postAllowed,
                grantedText = stringResource(R.string.ring_post_granted),
                missingText = stringResource(R.string.ring_post_missing),
                onOpenSettings = { context.startActivity(NotificationRingSetup.appNotificationSettingsIntent(context)) }
            )

            val allGranted = listenerGranted && fullScreenAllowed && postAllowed
            if (!allGranted) {
                if (paired) {
                    Button(
                        onClick = { grant() },
                        enabled = !granting,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (granting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.ring_grant_via_broker))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.ring_grant_pairing_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    TextButton(
                        onClick = onOpenPairing,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { Text(stringResource(R.string.bloat_set_up_pairing)) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.ring_duration_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.ring_duration_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.ring_duration_minutes, minutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt() },
                    onValueChangeFinished = {
                        SettingsManager.setNotificationRingMinutes(context, minutes)
                    },
                    valueRange = SettingsManager.NOTIFICATION_RING_MIN_MINUTES.toFloat()..
                        SettingsManager.NOTIFICATION_RING_MAX_MINUTES.toFloat(),
                    // Continuous rather than stepped. A stop per minute draws 59 tick marks, which
                    // on a 574dp-wide screen is a dotted smear; rounding the value on the way in
                    // lands on whole minutes just the same.
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.ring_brightness_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RingBrightness.entries.forEach { level ->
                        FilterChip(
                            selected = brightness == level,
                            onClick = {
                                brightness = level
                                SettingsManager.setNotificationRingBrightness(context, level)
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (level) {
                                            RingBrightness.DIM -> R.string.ring_brightness_dim
                                            RingBrightness.NORMAL -> R.string.ring_brightness_normal
                                            RingBrightness.BRIGHT -> R.string.ring_brightness_bright
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ring_icons_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.ring_icons_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = icons,
                        onCheckedChange = { on ->
                            icons = on
                            SettingsManager.setNotificationRingIconsEnabled(context, on)
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { pickingDefaultColor = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ring_default_color_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.ring_default_color_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ColorDot(argb = defaultColor, selected = false, size = 28.dp)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.ring_app_colors_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.ring_app_colors_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            appColors.entries.sortedBy { appLabel(context, it.key).lowercase() }.forEach { (pkg, argb) ->
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable { colouringPackage = pkg }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColorDot(argb = argb, selected = false, size = 22.dp)
                        Text(
                            text = appLabel(context, pkg),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            SettingsManager.setNotificationRingAppColor(context, pkg, null)
                            appColors = SettingsManager.getNotificationRingAppColors(context)
                        }) { Text(stringResource(R.string.ring_app_colors_remove)) }
                    }
                }
            }
            OutlinedButton(
                onClick = { pickingApp = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(stringResource(R.string.ring_app_colors_add)) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.ring_adjust_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, RingAdjustActivity::class.java)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(stringResource(R.string.ring_adjust)) }

            Text(
                text = stringResource(R.string.ring_try_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OutlinedButton(
                onClick = { context.startActivity(NotificationRingActivity.demoIntent(context)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(stringResource(R.string.ring_try)) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ColorDot(argb: Int, selected: Boolean, size: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)? = null) {
    ColorSwatch(argb = argb, selected = selected, size = size, onClick = onClick)
}

@Composable
private fun RingColorDialog(
    title: String,
    current: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val tooDark = stringResource(R.string.ring_color_too_dark)
    ColorPickerDialog(
        title = title,
        current = current ?: RingPalette.GREEN.argb,
        swatches = RingPalette.entries.map { it.argb },
        subtitle = stringResource(R.string.ring_app_colors_pick),
        warning = { argb -> if (NotificationRingPolicy.isTooDarkForRing(argb)) tooDark else null },
        onPick = onPick,
        onDismiss = onDismiss
    )
}

private fun appLabel(context: android.content.Context, packageName: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

@Composable
private fun GrantRow(
    title: String,
    granted: Boolean,
    grantedText: String,
    missingText: String,
    onOpenSettings: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    text = if (granted) grantedText else missingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!granted) {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.ring_grant_open_settings)) }
            }
        }
    }
}
