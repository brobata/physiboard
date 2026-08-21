package it.palsoftware.pastiera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R

/**
 * Voice (dictation) settings screen — the dedicated home for PhysiBoard's
 * signature hold-Fn dictation. Surfaces the triggers, transcription and
 * feedback controls that used to live inside the Smart Features screen; every
 * row reads and writes the same [SettingsManager] keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenSoundHaptics: () -> Unit = {}
) {
    val context = LocalContext.current

    var fnLongPressSpeech by remember {
        mutableStateOf(SettingsManager.getFnLongPressSpeechEnabled(context))
    }
    var altCtrlSpeechShortcut by remember {
        mutableStateOf(SettingsManager.getAltCtrlSpeechShortcutEnabled(context))
    }
    var dictationMaskOffensive by remember {
        mutableStateOf(SettingsManager.getDictationMaskOffensive(context))
    }
    var dictationEndSilenceMs by remember {
        mutableStateOf(SettingsManager.getDictationEndSilenceMs(context))
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_category_voice),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.voice_screen_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            VoiceSectionHeader(text = stringResource(R.string.voice_section_triggers))
            VoiceSwitchRow(
                title = stringResource(R.string.fn_long_press_speech_title),
                description = stringResource(R.string.fn_long_press_speech_description),
                checked = fnLongPressSpeech,
                onCheckedChange = { enabled ->
                    fnLongPressSpeech = enabled
                    SettingsManager.setFnLongPressSpeechEnabled(context, enabled)
                }
            )
            VoiceSwitchRow(
                title = stringResource(R.string.alt_ctrl_speech_shortcut_title),
                description = stringResource(R.string.alt_ctrl_speech_shortcut_description),
                checked = altCtrlSpeechShortcut,
                onCheckedChange = { enabled ->
                    altCtrlSpeechShortcut = enabled
                    SettingsManager.setAltCtrlSpeechShortcutEnabled(context, enabled)
                }
            )

            VoiceSectionHeader(text = stringResource(R.string.voice_section_transcription))
            VoiceSwitchRow(
                title = stringResource(R.string.dictation_mask_offensive_title),
                description = stringResource(R.string.dictation_mask_offensive_description),
                checked = dictationMaskOffensive,
                onCheckedChange = { enabled ->
                    dictationMaskOffensive = enabled
                    SettingsManager.setDictationMaskOffensive(context, enabled)
                }
            )
            Surface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dictation_end_silence_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (dictationEndSilenceMs <= 0) {
                            stringResource(R.string.dictation_end_silence_system_default)
                        } else {
                            stringResource(
                                R.string.dictation_end_silence_seconds,
                                dictationEndSilenceMs / 1000f
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = dictationEndSilenceMs.toFloat(),
                        onValueChange = { value ->
                            // Snap to 500ms steps; 0 = system default.
                            val snapped = ((value / 500f).toInt() * 500)
                            dictationEndSilenceMs = snapped
                            SettingsManager.setDictationEndSilenceMs(context, snapped)
                        },
                        valueRange = 0f..10000f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.dictation_end_silence_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            VoiceSectionHeader(text = stringResource(R.string.voice_section_feedback))
            VoiceNavigationRow(
                title = stringResource(R.string.voice_vibration_link_title),
                description = stringResource(R.string.voice_vibration_link_description),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                onClick = onOpenSoundHaptics
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VoiceSectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun VoiceSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (description == null) 64.dp else 72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun VoiceNavigationRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
