package it.palsoftware.pastiera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import it.palsoftware.pastiera.inputmethod.EmbeddedAdbShell
import it.palsoftware.pastiera.ring.NotificationRingActivity
import it.palsoftware.pastiera.ring.NotificationRingSetup
import it.palsoftware.pastiera.ring.RingBrightness

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

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.ring_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsManager.NOTIFICATION_RING_MINUTE_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = minutes == option,
                            onClick = {
                                minutes = option
                                SettingsManager.setNotificationRingMinutes(context, option)
                            },
                            label = { Text(stringResource(R.string.ring_duration_minutes, option)) }
                        )
                    }
                }
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
