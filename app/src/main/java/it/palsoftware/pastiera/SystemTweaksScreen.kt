package it.palsoftware.pastiera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.toolbox.SystemTweaks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings Android supports and this ROM never exposes. Each one applies immediately and can
 * be put back; none of them can leave the phone unusable, which is why there is no countdown
 * here the way there is on density.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemTweaksScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<SystemTweaks.State?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        scope.launch {
            state = withContext(Dispatchers.IO) { SystemTweaks.read(context) }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    fun run(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { block() }
            busy = false
            load()
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
                        text = stringResource(R.string.tweaks_title),
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
                text = stringResource(R.string.tweaks_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                return@Column
            }
            val current = state
            if (current == null) {
                Text(
                    text = stringResource(R.string.bloat_not_paired),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Button(
                    onClick = onOpenPairing,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(stringResource(R.string.bloat_set_up_pairing)) }
                return@Column
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.tweaks_animation_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.tweaks_animation_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SystemTweaks.AnimationSpeed.entries.forEach { speed ->
                        FilterChip(
                            selected = current.animation == speed,
                            enabled = !busy,
                            onClick = { run { SystemTweaks.setAnimationSpeed(context, speed) } },
                            label = {
                                Text(
                                    stringResource(
                                        when (speed) {
                                            SystemTweaks.AnimationSpeed.OFF -> R.string.tweaks_animation_off
                                            SystemTweaks.AnimationSpeed.FAST -> R.string.tweaks_animation_fast
                                            SystemTweaks.AnimationSpeed.NORMAL -> R.string.tweaks_animation_normal
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
            }

            TweakSwitch(
                title = stringResource(R.string.tweaks_notification_history_title),
                description = stringResource(R.string.tweaks_notification_history_description),
                checked = current.toggles[SystemTweaks.Toggle.NOTIFICATION_HISTORY] == true,
                enabled = !busy,
                onCheckedChange = { on ->
                    run { SystemTweaks.setToggle(context, SystemTweaks.Toggle.NOTIFICATION_HISTORY, on) }
                }
            )
            TweakSwitch(
                title = stringResource(R.string.tweaks_one_handed_title),
                description = stringResource(R.string.tweaks_one_handed_description),
                checked = current.toggles[SystemTweaks.Toggle.ONE_HANDED_MODE] == true,
                enabled = !busy,
                onCheckedChange = { on ->
                    run { SystemTweaks.setToggle(context, SystemTweaks.Toggle.ONE_HANDED_MODE, on) }
                }
            )

            TextButton(
                enabled = !busy,
                onClick = { run { SystemTweaks.resetAll(context) } },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) { Text(stringResource(R.string.tweaks_reset)) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TweakSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}
