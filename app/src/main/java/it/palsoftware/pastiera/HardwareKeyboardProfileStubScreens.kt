package it.palsoftware.pastiera

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

private enum class ClicksMappingPage { HostSlots, SpecialKeys, NumberRow }

@Composable
fun ClicksPowerKeyboardSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var closeInputOnDisconnect by remember {
        mutableStateOf(SettingsManager.getClicksCloseInputOnDisconnect(context))
    }
    var showKeyboardOnlyWithTextFocus by remember {
        mutableStateOf(SettingsManager.getClicksShowKeyboardOnlyWithTextFocus(context))
    }
    var hasBluetoothPermission by remember {
        mutableStateOf(hasClicksBluetoothPermission(context))
    }
    var showBluetoothPermissionExplanation by remember { mutableStateOf(false) }
    var powerState by remember { mutableStateOf(ClicksPowerKeyboardState()) }
    var gattClient by remember { mutableStateOf<ClicksPowerKeyboardGattClient?>(null) }
    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    var manualChargingUntil by remember { mutableStateOf(0L) }
    var chargingAutomation by remember {
        mutableStateOf(SettingsManager.isClicksChargingAutomationEnabled(context))
    }
    var chargingStartSlider by remember {
        mutableStateOf(SettingsManager.getClicksChargingStartPercent(context).toFloat())
    }
    var chargingStopSlider by remember {
        mutableStateOf(SettingsManager.getClicksChargingStopPercent(context).toFloat())
    }
    var backlightSlider by remember { mutableStateOf(100f) }
    var reserveSlider by remember { mutableStateOf(0f) }
    var mappingPage by remember { mutableStateOf<ClicksMappingPage?>(null) }
    var hostSlotToEdit by remember { mutableStateOf<Int?>(null) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBluetoothPermission = granted
        if (granted) ClicksPowerKeyboardController.onBluetoothPermissionChanged()
    }

    LaunchedEffect(Unit) {
        if (!hasBluetoothPermission && !SettingsManager.hasExplainedClicksBluetoothPermission(context)) {
            showBluetoothPermissionExplanation = true
        }
    }

    val clicksAppInstalled = remember {
        context.packageManager.getLaunchIntentForPackage(CLICKS_COMPANION_PACKAGE) != null
    }

    DisposableEffect(Unit) {
        val observation = ClicksPowerKeyboardController.observe { controllerState ->
            connectedDeviceName = controllerState.deviceName
            manualChargingUntil = controllerState.manualChargingUntil
            powerState = controllerState.keyboard
            gattClient = ClicksPowerKeyboardController.activeClient()
        }
        onDispose { observation.close() }
    }

    LaunchedEffect(powerState.backlightBrightness) {
        powerState.backlightBrightness?.let { backlightSlider = it.toFloat() }
    }
    LaunchedEffect(powerState.chargingReservePercent) {
        powerState.chargingReservePercent?.let { reserveSlider = it.toFloat() }
    }

    if (showBluetoothPermissionExplanation) {
        AlertDialog(
            onDismissRequest = {
                SettingsManager.setClicksBluetoothPermissionExplained(context)
                showBluetoothPermissionExplanation = false
            },
            title = { Text(stringResource(R.string.clicks_bluetooth_permission_title)) },
            text = { Text(stringResource(R.string.clicks_bluetooth_permission_description)) },
            confirmButton = {
                TextButton(onClick = {
                    SettingsManager.setClicksBluetoothPermissionExplained(context)
                    showBluetoothPermissionExplanation = false
                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }) {
                    Text(stringResource(R.string.clicks_bluetooth_permission_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    SettingsManager.setClicksBluetoothPermissionExplained(context)
                    showBluetoothPermissionExplanation = false
                }) {
                    Text(stringResource(R.string.clicks_bluetooth_permission_not_now))
                }
            }
        )
    }

    if (mappingPage == ClicksMappingPage.HostSlots) {
        ClicksHostSlotsScreen(
            modifier = modifier,
            state = powerState,
            onBack = { mappingPage = null },
            onEdit = { hostSlotToEdit = it }
        )
        hostSlotToEdit?.let { slotIndex ->
            ClicksHostNameDialog(
                state = powerState,
                slotIndex = slotIndex,
                onApply = { selectedSlot, name ->
                    gattClient?.setHostName(selectedSlot, name)
                    hostSlotToEdit = null
                },
                onDismiss = { hostSlotToEdit = null }
            )
        }
        return
    }

    if (mappingPage == ClicksMappingPage.SpecialKeys) {
        ClicksSpecialKeyMappingsScreen(
            modifier = modifier,
            state = powerState,
            onBack = { mappingPage = null },
            onSelected = { command, bytes -> gattClient?.setSpecialKeyRemap(command, bytes) }
        )
        return
    }

    if (mappingPage == ClicksMappingPage.NumberRow) {
        ClicksNumberRowMappingsScreen(
            modifier = modifier,
            state = powerState,
            onBack = { mappingPage = null },
            onSelected = { command, bytes -> gattClient?.setSpecialKeyRemap(command, bytes) }
        )
        return
    }

    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.clicks_power_keyboard_title),
        description = stringResource(R.string.clicks_power_keyboard_description),
        onBack = onBack
    ) {
        StubSection(stringResource(R.string.clicks_section_device))
        ClicksDeviceInfoRow(
            icon = Icons.Filled.Bluetooth,
            title = stringResource(R.string.clicks_device_status_title),
            description = connectedDeviceName?.let { deviceName ->
                val slot = deviceName.substringAfterLast('-', missingDelimiterValue = "?")
                when {
                    !hasBluetoothPermission -> stringResource(R.string.clicks_device_status_firmware_permission, slot)
                    powerState.firmwareVersion != null && powerState.batteryPercent != null -> stringResource(
                        R.string.clicks_device_status_with_firmware_and_battery,
                        slot,
                        powerState.firmwareVersion!!,
                        powerState.batteryPercent!!.toString()
                    )
                    powerState.firmwareVersion != null -> stringResource(
                        R.string.clicks_device_status_with_firmware, slot, powerState.firmwareVersion!!
                    )
                    powerState.batteryPercent != null -> stringResource(
                        R.string.clicks_device_status_with_battery, slot, powerState.batteryPercent!!
                    )
                    powerState.error != null -> powerState.error!!
                    else -> stringResource(R.string.clicks_device_status_connected, slot)
                }
            } ?: stringResource(R.string.clicks_device_status_disconnected),
            onClick = when {
                connectedDeviceName == null -> null
                !hasBluetoothPermission -> ({ showBluetoothPermissionExplanation = true })
                powerState.error != null -> ({ ClicksPowerKeyboardController.onBluetoothPermissionChanged() })
                else -> null
            }
        )
        if (powerState.model != null || powerState.serialNumber != null) {
            ClicksDeviceInfoRow(
                icon = Icons.Filled.Keyboard,
                title = stringResource(R.string.clicks_gatt_identity_title),
                description = stringResource(
                    R.string.clicks_gatt_identity_value,
                    powerState.model ?: "?",
                    powerState.serialNumber ?: "?"
                )
            )
        }
        val firmwareVersion = powerState.firmwareVersion
        val firmwareSupported = firmwareVersion?.let(ClicksFirmwareVersionReader::isSupported) == true
        val controlsEnabled = powerState.ready && firmwareSupported
        if (powerState.activeHostSlot != null) {
            ClicksDeviceInfoRow(
                icon = Icons.Filled.Bluetooth,
                title = stringResource(R.string.clicks_host_slots_title),
                description = hostSlotsSummary(powerState),
                onClick = if (controlsEnabled && powerState.supportsHostNameWrites()) {
                    ({ mappingPage = ClicksMappingPage.HostSlots })
                } else {
                    null
                }
            )
        }
        ClicksDeviceInfoRow(
            icon = if (firmwareSupported) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            title = stringResource(R.string.clicks_firmware_status_title),
            description = when {
                firmwareSupported -> stringResource(R.string.clicks_firmware_status_supported, firmwareVersion!!)
                firmwareVersion != null -> stringResource(R.string.clicks_firmware_status_update_required, firmwareVersion!!)
                else -> stringResource(R.string.clicks_firmware_minimum_description)
            }
        )
        ClicksDeviceInfoRow(
            icon = Icons.Filled.SystemUpdate,
            title = stringResource(R.string.clicks_firmware_updates_title),
            description = if (clicksAppInstalled) {
                stringResource(R.string.clicks_firmware_updates_open_app)
            } else {
                stringResource(R.string.clicks_firmware_updates_install_app)
            },
            onClick = { openClicksFirmwareUpdates(context) }
        )

        StubSection(stringResource(R.string.clicks_section_keyboard_behavior))
        ClicksDeviceInfoRow(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.clicks_recommended_settings_title),
            description = stringResource(R.string.clicks_recommended_settings_description),
            onClick = {
                gattClient?.setCapsLock(false)
                gattClient?.setCursorMode(false)
            }
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_sticky_alt_title),
            description = stringResource(R.string.clicks_sticky_alt_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_SYM_LOCK) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setSymLock(it) },
            infoText = stringResource(R.string.clicks_sticky_alt_help)
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_sticky_shift_title),
            description = stringResource(R.string.clicks_sticky_shift_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_CAPS_LOCK) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setCapsLock(it) },
            infoText = stringResource(R.string.clicks_sticky_shift_help)
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_soft_return_title),
            description = stringResource(R.string.clicks_soft_return_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_SOFT_RETURN) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setSoftReturn(it) },
            infoText = stringResource(R.string.clicks_soft_return_help)
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_cursor_mode_title),
            description = stringResource(R.string.clicks_cursor_mode_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_CURSOR_MODE) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setCursorMode(it) },
            infoText = stringResource(R.string.clicks_cursor_mode_help)
        )

        StubSection(stringResource(R.string.clicks_section_key_mappings))
        ClicksDeviceInfoRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.clicks_special_key_mappings_title),
            description = stringResource(R.string.clicks_special_key_mappings_description),
            onClick = if (controlsEnabled && powerState.specialKeyEnableFlags != null) {
                ({ mappingPage = ClicksMappingPage.SpecialKeys })
            } else {
                null
            }
        )
        ClicksDeviceInfoRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.clicks_number_row_title),
            description = stringResource(R.string.clicks_number_row_description),
            onClick = if (
                controlsEnabled &&
                powerState.specialKeyEnableFlags != null &&
                powerState.numberKeyEnableFlags != null
            ) {
                ({ mappingPage = ClicksMappingPage.NumberRow })
            } else {
                null
            }
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.clicks_all_key_mappings_title),
            description = stringResource(R.string.clicks_all_key_mappings_description)
        )

        StubSection(stringResource(R.string.clicks_section_backlight_power))
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_backlight_title),
            description = stringResource(R.string.clicks_backlight_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_BACKLIGHT) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setBacklightEnabled(it) }
        )
        ClicksSliderRow(
            title = stringResource(R.string.clicks_backlight_brightness_title),
            valueLabel = "${backlightSlider.toInt()} %",
            value = backlightSlider,
            range = 0f..100f,
            steps = 19,
            enabled = controlsEnabled,
            onValueChange = { backlightSlider = it },
            onValueChangeFinished = { gattClient?.setBacklightBrightness(backlightSlider.toInt()) }
        )
        ClicksIntDropdownRow(
            title = stringResource(R.string.clicks_backlight_timeout_dialog_title),
            selected = powerState.backlightTimeoutSeconds?.takeIf { it in CLICKS_BACKLIGHT_TIMEOUT_OPTIONS },
            options = CLICKS_BACKLIGHT_TIMEOUT_OPTIONS,
            label = { stringResource(R.string.clicks_seconds_value, it) },
            enabled = controlsEnabled,
            onSelected = { gattClient?.setBacklightTimeout(it) }
        )
        ClicksIntDropdownRow(
            title = stringResource(R.string.clicks_idle_timeout_dialog_title),
            selected = powerState.idleTimeoutSeconds
                ?.takeIf { it % 60 == 0 }
                ?.div(60)
                ?.takeIf { it in CLICKS_IDLE_TIMEOUT_MINUTE_OPTIONS },
            options = CLICKS_IDLE_TIMEOUT_MINUTE_OPTIONS,
            label = { stringResource(R.string.clicks_minutes_value, it) },
            enabled = controlsEnabled,
            onSelected = { gattClient?.setIdleTimeout(it * 60) }
        )

        StubSection(stringResource(R.string.clicks_section_wireless_charging))
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_charging_automation_title),
            description = stringResource(R.string.clicks_charging_automation_description),
            checked = chargingAutomation,
            infoText = stringResource(R.string.clicks_charging_connection_boundary_description),
            onCheckedChange = {
                chargingAutomation = it
                SettingsManager.setClicksChargingAutomationEnabled(context, it)
            }
        )
        ClicksSliderRow(
            title = stringResource(R.string.clicks_charging_start_title),
            valueLabel = "${chargingStartSlider.toInt()} %",
            value = chargingStartSlider,
            range = 5f..90f,
            steps = 16,
            enabled = chargingAutomation,
            onValueChange = {
                chargingStartSlider = it.coerceAtMost(chargingStopSlider - 1f)
            },
            onValueChangeFinished = {
                SettingsManager.setClicksChargingStartPercent(context, chargingStartSlider.toInt())
                chargingStopSlider = SettingsManager.getClicksChargingStopPercent(context).toFloat()
            }
        )
        ClicksSliderRow(
            title = stringResource(R.string.clicks_charging_stop_title),
            valueLabel = "${chargingStopSlider.toInt()} %",
            value = chargingStopSlider,
            range = 6f..95f,
            steps = 17,
            enabled = chargingAutomation,
            onValueChange = {
                chargingStopSlider = it.coerceAtLeast(chargingStartSlider + 1f)
            },
            onValueChangeFinished = {
                SettingsManager.setClicksChargingStopPercent(context, chargingStopSlider.toInt())
            }
        )
        ClicksSliderRow(
            title = stringResource(R.string.clicks_charging_reserve_title),
            valueLabel = "${reserveSlider.toInt()} %",
            value = reserveSlider,
            range = 0f..50f,
            steps = 4,
            enabled = controlsEnabled && powerState.chargingReservePercent != null,
            onValueChange = { reserveSlider = it },
            onValueChangeFinished = { gattClient?.setChargingReserve(reserveSlider.toInt()) }
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_manual_wireless_charging_title),
            description = stringResource(R.string.clicks_manual_wireless_charging_description),
            checked = manualChargingUntil > System.currentTimeMillis(),
            enabled = controlsEnabled && powerState.chargingReservePercent != null,
            infoText = stringResource(R.string.clicks_charging_connection_boundary_description),
            onCheckedChange = { ClicksPowerKeyboardController.setManualChargingOverride(it) }
        )
        StubSection(stringResource(R.string.clicks_section_automation))
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_show_keyboard_only_with_text_focus_title),
            description = stringResource(R.string.clicks_show_keyboard_only_with_text_focus_description),
            checked = showKeyboardOnlyWithTextFocus,
            onCheckedChange = { enabled ->
                showKeyboardOnlyWithTextFocus = enabled
                SettingsManager.setClicksShowKeyboardOnlyWithTextFocus(context, enabled)
            }
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_close_input_on_disconnect_title),
            description = stringResource(R.string.clicks_close_input_on_disconnect_description),
            checked = closeInputOnDisconnect,
            onCheckedChange = { enabled ->
                closeInputOnDisconnect = enabled
                SettingsManager.setClicksCloseInputOnDisconnect(context, enabled)
            }
        )
    }

}

@Composable
private fun ClicksSpecialKeyMappingsScreen(
    modifier: Modifier,
    state: ClicksPowerKeyboardState,
    onBack: () -> Unit,
    onSelected: (Int, ByteArray) -> Unit
) {
    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.clicks_special_key_mappings_title),
        description = stringResource(R.string.clicks_special_key_mappings_description),
        onBack = onBack
    ) {
        listOf(
            Triple(ClicksPowerKeyboardProtocol.COMMAND_TAB_REMAP, "Tab", state.tabRemap),
            Triple(ClicksPowerKeyboardProtocol.COMMAND_GEMINI_REMAP, "Gemini", state.geminiRemap),
            Triple(ClicksPowerKeyboardProtocol.COMMAND_ALT_REMAP, "Alt", state.altRemap)
        ).forEach { (command, nativeAction, bytes) ->
            val displayedBytes = bytes.takeUnless {
                nativeAction == "Alt" && it?.contentEquals(byteArrayOf(0xe2.toByte(), 0x00)) == true
            }
            ClicksRemapDropdownRow(
                title = nativeAction,
                selectedBytes = displayedBytes,
                presets = specialRemapPresets(nativeAction),
                onSelected = { onSelected(command, it) }
            )
        }
    }
}

@Composable
private fun ClicksNumberRowMappingsScreen(
    modifier: Modifier,
    state: ClicksPowerKeyboardState,
    onBack: () -> Unit,
    onSelected: (Int, ByteArray) -> Unit
) {
    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.clicks_number_row_title),
        description = stringResource(R.string.clicks_number_row_description),
        onBack = onBack
    ) {
        state.numberRemaps.forEachIndexed { index, bytes ->
            ClicksRemapDropdownRow(
                title = "SYM + ${index + 1}",
                selectedBytes = bytes,
                presets = numberRemapPresets(),
                onSelected = {
                    onSelected(ClicksPowerKeyboardProtocol.NUMBER_REMAP_COMMANDS[index], it)
                }
            )
        }
    }
}

@Composable
private fun ClicksHostSlotsScreen(
    modifier: Modifier,
    state: ClicksPowerKeyboardState,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit
) {
    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.clicks_host_slots_title),
        description = stringResource(R.string.clicks_host_slots_description),
        onBack = onBack
    ) {
        state.hostConfigurations.forEachIndexed { index, configuration ->
            if (configuration != null && configuration != 0) {
                ClicksValueRow(
                    title = if (state.activeHostSlot == index + 1) {
                        stringResource(R.string.clicks_host_slot_active_label, index + 1)
                    } else {
                        stringResource(R.string.clicks_host_slot_label, index + 1)
                    },
                    value = state.hostNames[index].orEmpty().ifBlank {
                        stringResource(R.string.clicks_host_name_unnamed)
                    },
                    onClick = { onEdit(index) }
                )
            }
        }
    }
}

private fun hasClicksBluetoothPermission(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
}

private fun openClicksFirmwareUpdates(context: android.content.Context) {
    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(CLICKS_COMPANION_PACKAGE)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val intent = launchIntent ?: Intent(
        Intent.ACTION_VIEW,
        Uri.parse(CLICKS_COMPANION_PLAY_STORE_URL)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private const val CLICKS_COMPANION_PACKAGE = "com.clicks.companionapp"
private const val CLICKS_COMPANION_PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=com.clicks.companionapp&hl=en"

@Composable
private fun ClicksDeviceInfoRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClicksSettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    infoText: String? = null
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo && infoText != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            text = { Text(infoText) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(stringResource(android.R.string.ok)) }
            }
        )
    }
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
            if (infoText != null) {
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                }
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ClicksValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClicksRemapDropdownRow(
    title: String,
    selectedBytes: ByteArray?,
    presets: List<ClicksRemapPreset>,
    onSelected: (ByteArray) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = selectedBytes?.let { current ->
        presets.firstOrNull { it.bytes.contentEquals(current) }
            ?: ClicksRemapPreset(
                stringResource(R.string.clicks_remap_custom_value, current.toHexPair()),
                current
            )
    } ?: presets.first()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        expanded = false
                        onSelected(preset.bytes)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClicksIntDropdownRow(
    title: String,
    selected: Int?,
    options: List<Int>,
    label: @Composable (Int) -> String,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = selected?.let { label(it) } ?: "–",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun specialRemapPresets(nativeAction: String): List<ClicksRemapPreset> = buildList {
    add(ClicksRemapPreset(
        stringResource(R.string.clicks_remap_native_action, nativeAction),
        byteArrayOf(0x00, 0x00)
    ))
    add(ClicksRemapPreset("Ctrl + Space", byteArrayOf(0xe0.toByte(), 0x2c)))
    if (nativeAction != "Alt") {
        add(ClicksRemapPreset("Alt", byteArrayOf(0xe2.toByte(), 0x00)))
    }
    add(ClicksRemapPreset("Alt + D", byteArrayOf(0xe2.toByte(), 0x07)))
    add(ClicksRemapPreset("Alt + K", byteArrayOf(0xe2.toByte(), 0x0e)))
    add(ClicksRemapPreset("Alt + S", byteArrayOf(0xe2.toByte(), 0x16)))
    add(ClicksRemapPreset("Alt + .", byteArrayOf(0xe2.toByte(), 0x37)))
    add(ClicksRemapPreset("−", byteArrayOf(0x00, 0x2d)))
}

@Composable
private fun numberRemapPresets(): List<ClicksRemapPreset> = listOf(
    ClicksRemapPreset(stringResource(R.string.clicks_remap_native_number_action), byteArrayOf(0x00, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_escape), byteArrayOf(0x29, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_volume_up), byteArrayOf(0x80.toByte(), 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_volume_down), byteArrayOf(0x81.toByte(), 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_volume_mute), byteArrayOf(0x7f, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_play_pause), byteArrayOf(0xcd.toByte(), 0xff.toByte())),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_media_next), byteArrayOf(0xb5.toByte(), 0xff.toByte())),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_media_previous), byteArrayOf(0xb6.toByte(), 0xff.toByte())),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_page_up), byteArrayOf(0x4b, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_page_down), byteArrayOf(0x4e, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_home), byteArrayOf(0x4a, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_end), byteArrayOf(0x4d, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_left_bracket), byteArrayOf(0x2f, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_right_bracket), byteArrayOf(0x30, 0x00))
)

@Composable
private fun ClicksSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
    }
}

@Composable
private fun hostSlotsSummary(state: ClicksPowerKeyboardState): String {
    val occupied = state.hostConfigurations.mapIndexedNotNull { index, configuration ->
        if (configuration != null && configuration != 0) {
            val name = state.hostNames[index]?.takeIf(String::isNotBlank)
            if (name == null) "${index + 1}" else "${index + 1} ($name)"
        } else {
            null
        }
    }
    return stringResource(
        R.string.clicks_host_slots_value,
        state.activeHostSlot?.toString() ?: "?",
        occupied.joinToString().ifBlank { stringResource(R.string.clicks_host_slots_none) }
    )
}

@Composable
private fun remapLabel(bytes: ByteArray): String = when {
    bytes.contentEquals(byteArrayOf(0x29, 0x00)) -> stringResource(R.string.clicks_number_preset_escape)
    bytes.contentEquals(byteArrayOf(0x80.toByte(), 0x00)) -> stringResource(R.string.clicks_number_preset_volume_up)
    bytes.contentEquals(byteArrayOf(0x81.toByte(), 0x00)) -> stringResource(R.string.clicks_number_preset_volume_down)
    bytes.contentEquals(byteArrayOf(0x7f, 0x00)) -> stringResource(R.string.clicks_number_preset_volume_mute)
    bytes.contentEquals(byteArrayOf(0xcd.toByte(), 0xff.toByte())) -> stringResource(R.string.clicks_number_preset_play_pause)
    bytes.contentEquals(byteArrayOf(0xb5.toByte(), 0xff.toByte())) -> stringResource(R.string.clicks_number_preset_media_next)
    bytes.contentEquals(byteArrayOf(0xb6.toByte(), 0xff.toByte())) -> stringResource(R.string.clicks_number_preset_media_previous)
    bytes.contentEquals(byteArrayOf(0x4b, 0x00)) -> stringResource(R.string.clicks_number_preset_page_up)
    bytes.contentEquals(byteArrayOf(0x4e, 0x00)) -> stringResource(R.string.clicks_number_preset_page_down)
    bytes.contentEquals(byteArrayOf(0x4a, 0x00)) -> stringResource(R.string.clicks_number_preset_home)
    bytes.contentEquals(byteArrayOf(0x4d, 0x00)) -> stringResource(R.string.clicks_number_preset_end)
    bytes.contentEquals(byteArrayOf(0x2f, 0x00)) -> stringResource(R.string.clicks_number_preset_left_bracket)
    bytes.contentEquals(byteArrayOf(0x30, 0x00)) -> stringResource(R.string.clicks_number_preset_right_bracket)
    bytes.contentEquals(byteArrayOf(0xe0.toByte(), 0x2c)) -> "Ctrl+Space"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x00)) -> "Alt"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x07)) -> "Alt+D"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x0e)) -> "Alt+K"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x16)) -> "Alt+S"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x37)) -> "Alt+."
    bytes.contentEquals(byteArrayOf(0x00, 0x2d)) -> "−"
    else -> stringResource(R.string.clicks_remap_custom_value, bytes.toHexPair())
}

private fun ByteArray?.toHexPair(): String = this
    ?.takeIf { it.size == 2 }
    ?.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
    ?: "–"

@Composable
private fun ClicksSingleChoiceDialog(
    title: String,
    choices: List<Int>,
    label: @Composable (Int) -> String,
    onChoice: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { value -> TextButton(onClick = { onChoice(value) }) { Text(label(value)) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private data class ClicksRemapPreset(val label: String, val bytes: ByteArray)

private val CLICKS_BACKLIGHT_TIMEOUT_OPTIONS = listOf(2, 5, 15, 30, 45, 60)
private val CLICKS_IDLE_TIMEOUT_MINUTE_OPTIONS = listOf(2, 5, 15, 30, 45, 60)

@Composable
private fun ClicksHostNameDialog(
    state: ClicksPowerKeyboardState,
    slotIndex: Int,
    onApply: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(slotIndex) { mutableStateOf(state.hostNames[slotIndex].orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clicks_host_name_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.clicks_host_slot_label, slotIndex + 1))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.clicks_host_name_label)) },
                    supportingText = { Text(stringResource(R.string.clicks_host_name_limit)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(slotIndex, name) }) {
                Text(stringResource(R.string.clicks_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ClicksNumberRemapDialog(
    keyIndex: Int,
    state: ClicksPowerKeyboardState,
    onApply: (Int, ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val labels = listOf(
        R.string.clicks_number_preset_disabled,
        R.string.clicks_number_preset_escape,
        R.string.clicks_number_preset_volume_up,
        R.string.clicks_number_preset_volume_down,
        R.string.clicks_number_preset_volume_mute,
        R.string.clicks_number_preset_play_pause,
        R.string.clicks_number_preset_media_next,
        R.string.clicks_number_preset_media_previous,
        R.string.clicks_number_preset_page_up,
        R.string.clicks_number_preset_page_down,
        R.string.clicks_number_preset_home,
        R.string.clicks_number_preset_end,
        R.string.clicks_number_preset_left_bracket,
        R.string.clicks_number_preset_right_bracket
    ).map { stringResource(it) }
    val values = listOf(
        byteArrayOf(0x00, 0x00),
        byteArrayOf(0x29, 0x00),
        byteArrayOf(0x80.toByte(), 0x00),
        byteArrayOf(0x81.toByte(), 0x00),
        byteArrayOf(0x7f, 0x00),
        byteArrayOf(0xcd.toByte(), 0xff.toByte()),
        byteArrayOf(0xb5.toByte(), 0xff.toByte()),
        byteArrayOf(0xb6.toByte(), 0xff.toByte()),
        byteArrayOf(0x4b, 0x00),
        byteArrayOf(0x4e, 0x00),
        byteArrayOf(0x4a, 0x00),
        byteArrayOf(0x4d, 0x00),
        byteArrayOf(0x2f, 0x00),
        byteArrayOf(0x30, 0x00)
    )
    val knownPresets = labels.zip(values).map { (label, bytes) -> ClicksRemapPreset(label, bytes) }
    val currentBytes = state.numberRemaps[keyIndex]
    val presets = if (
        currentBytes != null && knownPresets.none { it.bytes.contentEquals(currentBytes) }
    ) {
        listOf(
            ClicksRemapPreset(
                stringResource(R.string.clicks_remap_custom_value, currentBytes.toHexPair()),
                currentBytes.copyOf()
            )
        ) + knownPresets
    } else {
        knownPresets
    }
    var selected by remember(keyIndex, currentBytes?.contentHashCode()) {
        mutableStateOf(
            presets.firstOrNull { preset ->
                currentBytes != null && preset.bytes.contentEquals(currentBytes)
            } ?: presets.first()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clicks_number_row_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("SYM + ${keyIndex + 1}", style = MaterialTheme.typography.titleMedium)
                presets.forEach { preset ->
                    val isSelected = selected.bytes.contentEquals(preset.bytes)
                    TextButton(onClick = { selected = preset }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isSelected) "✓ ${preset.label}" else preset.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(ClicksPowerKeyboardProtocol.NUMBER_REMAP_COMMANDS[keyIndex], selected.bytes)
            }) {
                Text(stringResource(R.string.clicks_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ClicksChoiceButtonGrid(
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onChoice: (Int) -> Unit
) {
    values.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { value ->
                TextButton(onClick = { onChoice(value) }) {
                    Text(if (selected == value) "✓ ${label(value)}" else label(value))
                }
            }
        }
    }
}

@Composable
private fun ClicksRemapDialog(
    command: Int,
    state: ClicksPowerKeyboardState,
    onApply: (Int, ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val disabledLabel = stringResource(R.string.clicks_remap_disabled)
    val presets = remember(disabledLabel) {
        listOf(
            ClicksRemapPreset(disabledLabel, byteArrayOf(0x00, 0x00)),
            ClicksRemapPreset("Ctrl + Space", byteArrayOf(0xe0.toByte(), 0x2c)),
            ClicksRemapPreset("Alt", byteArrayOf(0xe2.toByte(), 0x00)),
            ClicksRemapPreset("Alt + D", byteArrayOf(0xe2.toByte(), 0x07)),
            ClicksRemapPreset("Alt + K", byteArrayOf(0xe2.toByte(), 0x0e)),
            ClicksRemapPreset("Alt + S", byteArrayOf(0xe2.toByte(), 0x16)),
            ClicksRemapPreset("Alt + .", byteArrayOf(0xe2.toByte(), 0x37)),
            ClicksRemapPreset("Minus", byteArrayOf(0x00, 0x2d))
        )
    }
    val currentBytes = when (command) {
        ClicksPowerKeyboardProtocol.COMMAND_TAB_REMAP -> state.tabRemap
        ClicksPowerKeyboardProtocol.COMMAND_GEMINI_REMAP -> state.geminiRemap
        else -> state.altRemap
    }
    val availablePresets = if (
        currentBytes != null && presets.none { it.bytes.contentEquals(currentBytes) }
    ) {
        listOf(
            ClicksRemapPreset(
                stringResource(R.string.clicks_remap_custom_value, currentBytes.toHexPair()),
                currentBytes.copyOf()
            )
        ) + presets
    } else {
        presets
    }
    var selected by remember(command, currentBytes?.contentHashCode()) {
        mutableStateOf(
            availablePresets.firstOrNull { preset ->
                currentBytes != null && preset.bytes.contentEquals(currentBytes)
            } ?: availablePresets.first()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clicks_special_key_mappings_title)) },
        text = {
            Column {
                availablePresets.forEach { preset ->
                    val isSelected = selected.bytes.contentEquals(preset.bytes)
                    TextButton(onClick = { selected = preset }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isSelected) "✓ ${preset.label}" else preset.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(command, selected.bytes) }) {
                Text(stringResource(R.string.clicks_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun DeviceSymLayerEditorStubScreen(
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
