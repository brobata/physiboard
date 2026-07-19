package it.palsoftware.pastiera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ClicksPowerKeyboardSettingsStubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var closeInputOnDisconnect by remember {
        mutableStateOf(SettingsManager.getClicksCloseInputOnDisconnect(context))
    }
    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.clicks_power_keyboard_title),
        description = stringResource(R.string.clicks_power_keyboard_stub_description),
        onBack = onBack
    ) {
        StubSection(stringResource(R.string.clicks_section_device))
        PlannedSettingsRow(
            icon = Icons.Filled.Bluetooth,
            title = stringResource(R.string.clicks_device_status_title),
            description = stringResource(R.string.clicks_device_status_description)
        )

        StubSection(stringResource(R.string.clicks_section_keyboard_behavior))
        PlannedSettingsRow(
            icon = Icons.Filled.Keyboard,
            title = stringResource(R.string.clicks_modifier_behavior_title),
            description = stringResource(R.string.clicks_modifier_behavior_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Keyboard,
            title = stringResource(R.string.clicks_release_order_title),
            description = stringResource(R.string.clicks_release_order_description)
        )

        StubSection(stringResource(R.string.clicks_section_key_mappings))
        PlannedSettingsRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.clicks_special_key_mappings_title),
            description = stringResource(R.string.clicks_special_key_mappings_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.clicks_all_key_mappings_title),
            description = stringResource(R.string.clicks_all_key_mappings_description)
        )

        StubSection(stringResource(R.string.clicks_section_backlight_power))
        PlannedSettingsRow(
            icon = Icons.Filled.LightMode,
            title = stringResource(R.string.clicks_backlight_title),
            description = stringResource(R.string.clicks_backlight_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.clicks_timeouts_title),
            description = stringResource(R.string.clicks_timeouts_description)
        )

        StubSection(stringResource(R.string.clicks_section_wireless_charging))
        PlannedSettingsRow(
            icon = Icons.Filled.BatteryChargingFull,
            title = stringResource(R.string.clicks_wireless_charging_title),
            description = stringResource(R.string.clicks_wireless_charging_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.BatteryChargingFull,
            title = stringResource(R.string.clicks_phone_battery_threshold_title),
            description = stringResource(R.string.clicks_phone_battery_threshold_description)
        )

        StubSection(stringResource(R.string.clicks_section_automation))
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_close_input_on_disconnect_title),
            description = stringResource(R.string.clicks_close_input_on_disconnect_description),
            checked = closeInputOnDisconnect,
            onCheckedChange = { enabled ->
                closeInputOnDisconnect = enabled
                SettingsManager.setClicksCloseInputOnDisconnect(context, enabled)
            }
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.clicks_connection_automation_title),
            description = stringResource(R.string.clicks_connection_automation_description)
        )
    }
}

@Composable
private fun ClicksSettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun CuratedHardwareProfileStubScreen(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    onBack: () -> Unit
) {
    HardwareProfileScaffold(
        modifier = modifier,
        title = title,
        description = description,
        onBack = onBack
    ) {
        StubSection(stringResource(R.string.hardware_profile_stub_section))
        PlannedSettingsRow(
            icon = Icons.Filled.Bluetooth,
            title = stringResource(R.string.hardware_profile_detection_title),
            description = stringResource(R.string.hardware_profile_detection_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Keyboard,
            title = stringResource(R.string.hardware_profile_key_events_title),
            description = stringResource(R.string.hardware_profile_key_events_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.hardware_profile_device_controls_title),
            description = stringResource(R.string.hardware_profile_device_controls_description)
        )
    }
}

@Composable
fun AltKeyEditorStubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.alt_key_editor_title),
        description = stringResource(R.string.alt_key_editor_stub_description),
        onBack = onBack
    ) {
        StubSection(stringResource(R.string.alt_key_editor_create_section))
        PlannedSettingsRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.alt_key_editor_blank_profile_title),
            description = stringResource(R.string.alt_key_editor_blank_profile_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Keyboard,
            title = stringResource(R.string.alt_key_editor_clone_profile_title),
            description = stringResource(R.string.alt_key_editor_clone_profile_description)
        )

        StubSection(stringResource(R.string.alt_key_editor_scope_section))
        PlannedSettingsRow(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.alt_key_editor_matching_title),
            description = stringResource(R.string.alt_key_editor_matching_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.alt_key_editor_mappings_title),
            description = stringResource(R.string.alt_key_editor_mappings_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.alt_key_editor_transfer_title),
            description = stringResource(R.string.alt_key_editor_transfer_description)
        )
    }
}

@Composable
private fun HardwareProfileScaffold(
    modifier: Modifier,
    title: String,
    description: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
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
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
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
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun StubSection(title: String) {
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
            text = title,
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
private fun PlannedSettingsRow(
    icon: ImageVector,
    title: String,
    description: String
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FeatureStatusIcon(FeatureStatus.Construction)
        }
    }
}
