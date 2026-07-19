package it.palsoftware.pastiera

import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.commands.PastieraCommandSource
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

private enum class KeyboardsDevicesDestination { Main, OnScreen, BuiltIn, PowerKeyboard }

@Composable
fun KeyboardsDevicesSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavModeSettingsClick: (Int?) -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(KeyboardsDevicesDestination.Main) }
    when (destination) {
        KeyboardsDevicesDestination.Main -> KeyboardsDevicesMainScreen(
            modifier = modifier,
            onBack = onBack,
            onOnScreen = { destination = KeyboardsDevicesDestination.OnScreen },
            onBuiltIn = { destination = KeyboardsDevicesDestination.BuiltIn },
            onPowerKeyboard = { destination = KeyboardsDevicesDestination.PowerKeyboard },
            onNavModeSettingsClick = onNavModeSettingsClick
        )
        KeyboardsDevicesDestination.OnScreen -> VirtualKeyboardBehaviorSettingsScreen(
            modifier = modifier,
            onBack = { destination = KeyboardsDevicesDestination.Main }
        )
        KeyboardsDevicesDestination.BuiltIn -> HardwareKeyboardSettingsScreen(
            modifier = modifier,
            onBack = { destination = KeyboardsDevicesDestination.Main }
        )
        KeyboardsDevicesDestination.PowerKeyboard -> ClicksPowerKeyboardSettingsStubScreen(
            modifier = modifier,
            onBack = { destination = KeyboardsDevicesDestination.Main }
        )
    }
}

@Composable
private fun KeyboardsDevicesMainScreen(
    modifier: Modifier,
    onBack: () -> Unit,
    onOnScreen: () -> Unit,
    onBuiltIn: () -> Unit,
    onPowerKeyboard: () -> Unit,
    onNavModeSettingsClick: (Int?) -> Unit
) {
    val context = LocalContext.current
    var configuredMode by remember { mutableStateOf(SettingsManager.getSoftwareKeyboardMode(context)) }
    var effectiveMode by remember { mutableStateOf(SettingsManager.resolveEffectiveSoftwareKeyboardMode(context)) }
    var showToggleToasts by remember { mutableStateOf(SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context)) }

    DisposableEffect(context) {
        val prefs = SettingsManager.getPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "software_keyboard_mode" || key == "software_keyboard_mode_runtime_override") {
                configuredMode = SettingsManager.getSoftwareKeyboardMode(context)
                effectiveMode = SettingsManager.resolveEffectiveSoftwareKeyboardMode(context)
            }
            if (key == "software_keyboard_mode_toggle_toasts") {
                showToggleToasts = SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val autoEnabled = configuredMode == SettingsManager.SoftwareKeyboardMode.AUTO
    val isVirtual = effectiveMode == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
    val runtimeOverride = SettingsManager.getSoftwareKeyboardModeRuntimeOverride(context)
    val clicksDevice = remember {
        InputDevice.getDeviceIds().asSequence()
            .mapNotNull(InputDevice::getDevice)
            .firstOrNull(DeviceSpecific::isClicksPowerKeyboard)
    }
    val builtInDetected = remember {
        DeviceSpecific.detectedInputProfiles().any {
            it.kind == DeviceSpecific.InputDeviceKind.BUILT_IN
        }
    }
    val shortcutKeyCode = remember {
        KeyMappingLoader.loadCtrlKeyMappings(context.assets, context)
            .entries.firstOrNull { (_, mapping) ->
                mapping.type == "command" &&
                    mapping.value == PastieraCommandSource.COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE
            }
            ?.key
    }
    val shortcut = shortcutKeyCode?.let { "Ctrl+${KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_")}" }

    BackHandler(onBack = onBack)
    Scaffold(topBar = { SettingsSubscreenHeader(stringResource(R.string.keyboards_devices_title), onBack) }) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SectionDivider(stringResource(R.string.keyboard_switching_section_title))
            SwitchRow(
                title = stringResource(R.string.keyboard_switching_auto_title),
                description = stringResource(R.string.keyboard_switching_auto_description),
                checked = autoEnabled,
                onCheckedChange = { enabled ->
                    val newMode = if (enabled) {
                        SettingsManager.SoftwareKeyboardMode.AUTO
                    } else {
                        effectiveMode
                    }
                    SettingsManager.setSoftwareKeyboardMode(context, newMode)
                    configuredMode = newMode
                    effectiveMode = SettingsManager.resolveEffectiveSoftwareKeyboardMode(context)
                }
            )
            InfoRow(
                title = stringResource(R.string.keyboard_switching_current_title),
                description = when {
                    runtimeOverride != null -> stringResource(R.string.keyboard_switching_status_override)
                    isVirtual && !DeviceSpecific.hasConnectedHardwareKeyboard() -> stringResource(R.string.keyboard_switching_status_no_physical)
                    isVirtual -> stringResource(R.string.keyboard_switching_status_virtual)
                    clicksDevice != null -> stringResource(R.string.keyboard_switching_status_clicks)
                    else -> stringResource(R.string.keyboard_switching_status_built_in)
                },
                value = if (isVirtual) stringResource(R.string.keyboard_source_on_screen) else stringResource(R.string.keyboard_source_physical)
            )
            NavigationRow(
                icon = Icons.Filled.Keyboard,
                title = shortcut?.let { stringResource(R.string.keyboard_switching_override_shortcut, it) }
                    ?: stringResource(R.string.keyboard_switching_shortcut_unassigned),
                description = stringResource(R.string.keyboard_switching_override_description),
                onClick = { onNavModeSettingsClick(shortcutKeyCode) }
            )
            SwitchRow(
                title = stringResource(R.string.software_keyboard_mode_toggle_toasts_title),
                description = stringResource(R.string.software_keyboard_mode_toggle_toasts_description),
                checked = showToggleToasts,
                onCheckedChange = {
                    showToggleToasts = it
                    SettingsManager.setSoftwareKeyboardModeToggleToastsEnabled(context, it)
                }
            )

            SectionDivider(stringResource(R.string.keyboard_sources_section_title))
            NavigationRow(
                icon = Icons.Filled.Keyboard,
                title = stringResource(R.string.on_screen_keyboard_title),
                description = if (isVirtual) stringResource(R.string.keyboard_source_active) else stringResource(R.string.keyboard_source_standby),
                onClick = onOnScreen
            )
            NavigationRow(
                icon = Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.built_in_keyboards_title),
                description = if (builtInDetected) stringResource(R.string.keyboard_source_device_bound_detected) else stringResource(R.string.keyboard_source_device_bound),
                onClick = onBuiltIn
            )
            NavigationRow(
                icon = Icons.Filled.Bluetooth,
                title = stringResource(R.string.keyboard_accessories_title),
                description = if (clicksDevice != null) {
                    val slot = clicksDevice.name.substringAfterLast('-', missingDelimiterValue = "?")
                    stringResource(R.string.keyboard_accessory_clicks_connected, slot)
                } else {
                    stringResource(R.string.keyboard_accessory_clicks_disconnected)
                },
                onClick = onPowerKeyboard
            )

            SectionDivider(stringResource(R.string.hardware_keyboard_custom_profiles_title))
            Text(
                text = stringResource(R.string.keyboard_custom_profiles_binding_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun SettingsSubscreenHeader(title: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_content_description))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun SectionDivider(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun NavigationRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onCheckedChange)
        }
    }
}

@Composable
private fun InfoRow(title: String, description: String, value: String) {
    Surface(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
