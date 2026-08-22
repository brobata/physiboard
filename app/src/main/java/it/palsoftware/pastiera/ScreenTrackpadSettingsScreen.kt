package it.palsoftware.pastiera

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Settings for the screen trackpad: hold (or tap) a trigger key and swipe anywhere on the
 * display to move the text cursor.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScreenTrackpadSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SettingsManager.isScreenTrackpadEnabled(context)) }
    var triggerKey by remember { mutableStateOf(SettingsManager.getScreenTrackpadTriggerKey(context)) }
    var activation by remember { mutableStateOf(SettingsManager.getScreenTrackpadActivation(context)) }
    var stepPx by remember { mutableStateOf(SettingsManager.getScreenTrackpadStepPx(context).toFloat()) }
    var showHint by remember { mutableStateOf(SettingsManager.isScreenTrackpadHintEnabled(context)) }
    var triggerMenuExpanded by remember { mutableStateOf(false) }
    var activationMenuExpanded by remember { mutableStateOf(false) }

    // Poll the overlay permission so the status updates when the user comes back from system settings.
    var statusTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            statusTick++
            delay(1500)
        }
    }
    val overlayGranted = remember(statusTick) { Settings.canDrawOverlays(context) }

    val triggerOptions = listOf(
        SettingsManager.SCREEN_TRACKPAD_TRIGGER_SPACE to stringResource(R.string.screen_trackpad_trigger_space),
        SettingsManager.SCREEN_TRACKPAD_TRIGGER_SHIFT_LEFT to stringResource(R.string.screen_trackpad_trigger_shift_left),
        SettingsManager.SCREEN_TRACKPAD_TRIGGER_SHIFT_RIGHT to stringResource(R.string.screen_trackpad_trigger_shift_right),
        SettingsManager.SCREEN_TRACKPAD_TRIGGER_SHIFT_EITHER to stringResource(R.string.screen_trackpad_trigger_shift_either),
        SettingsManager.SCREEN_TRACKPAD_TRIGGER_SYM to stringResource(R.string.screen_trackpad_trigger_sym)
    )
    val activationOptions = listOf(
        SettingsManager.SCREEN_TRACKPAD_ACTIVATION_HOLD to stringResource(R.string.screen_trackpad_activation_hold),
        SettingsManager.SCREEN_TRACKPAD_ACTIVATION_DOUBLE_TAP to stringResource(R.string.screen_trackpad_activation_double_tap),
        SettingsManager.SCREEN_TRACKPAD_ACTIVATION_SINGLE_TAP to stringResource(R.string.screen_trackpad_activation_single_tap)
    )

    fun openOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

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
                        text = stringResource(R.string.screen_trackpad_title),
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
            ScreenTrackpadSwitchRow(
                icon = Icons.Filled.TouchApp,
                title = stringResource(R.string.screen_trackpad_enabled_title),
                description = stringResource(R.string.screen_trackpad_enabled_description),
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    SettingsManager.setScreenTrackpadEnabled(context, it)
                    if (it && !Settings.canDrawOverlays(context)) openOverlayPermission()
                }
            )

            // Permission status row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !overlayGranted) { openOverlayPermission() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (overlayGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (overlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.screen_trackpad_permission_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(
                                if (overlayGranted) R.string.screen_trackpad_permission_granted
                                else R.string.screen_trackpad_permission_missing
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overlayGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ScreenTrackpadDropdown(
                label = stringResource(R.string.screen_trackpad_trigger_key_title),
                options = triggerOptions,
                selected = triggerKey,
                expanded = triggerMenuExpanded,
                onExpandedChange = { triggerMenuExpanded = it },
                enabled = enabled,
                onSelect = {
                    triggerKey = it
                    SettingsManager.setScreenTrackpadTriggerKey(context, it)
                }
            )

            ScreenTrackpadDropdown(
                label = stringResource(R.string.screen_trackpad_activation_title),
                options = activationOptions,
                selected = activation,
                expanded = activationMenuExpanded,
                onExpandedChange = { activationMenuExpanded = it },
                enabled = enabled,
                onSelect = {
                    activation = it
                    SettingsManager.setScreenTrackpadActivation(context, it)
                }
            )

            Text(
                text = stringResource(
                    if (activation == SettingsManager.SCREEN_TRACKPAD_ACTIVATION_HOLD) {
                        R.string.screen_trackpad_activation_hold_note
                    } else {
                        R.string.screen_trackpad_activation_sticky_note
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Sensitivity slider
            Surface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.screen_trackpad_sensitivity_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.screen_trackpad_sensitivity_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${stepPx.toInt()} px",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = stepPx,
                        onValueChange = { stepPx = it },
                        onValueChangeFinished = { SettingsManager.setScreenTrackpadStepPx(context, stepPx.toInt()) },
                        valueRange = SettingsManager.MIN_SCREEN_TRACKPAD_STEP_PX.toFloat()..SettingsManager.MAX_SCREEN_TRACKPAD_STEP_PX.toFloat(),
                        steps = 13,
                        enabled = enabled
                    )
                }
            }

            ScreenTrackpadSwitchRow(
                icon = Icons.Filled.Visibility,
                title = stringResource(R.string.screen_trackpad_hint_enabled_title),
                description = stringResource(R.string.screen_trackpad_hint_enabled_description),
                checked = showHint,
                enabled = enabled,
                onCheckedChange = {
                    showHint = it
                    SettingsManager.setScreenTrackpadHintEnabled(context, it)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScreenTrackpadSwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScreenTrackpadDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) onExpandedChange(it) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        onExpandedChange(false)
                    },
                    leadingIcon = {
                        if (selected == value) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
