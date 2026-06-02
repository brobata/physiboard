package it.palsoftware.pastiera

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import it.palsoftware.pastiera.R

/**
 * Keyboard & Timing settings screen.
 */
@Composable
fun KeyboardTimingSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var longPressThreshold by remember { 
        mutableStateOf(SettingsManager.getLongPressThreshold(context))
    }
    
    var longPressModifier by remember { 
        mutableStateOf(SettingsManager.getLongPressModifier(context))
    }

    var softwareKeyboardMode by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardMode(context))
    }

    var shiftTapLatches by remember {
        mutableStateOf(SettingsManager.getShiftTapLatches(context))
    }
    var altTapLatches by remember {
        mutableStateOf(SettingsManager.getAltTapLatches(context))
    }
    var ctrlTapLatches by remember {
        mutableStateOf(SettingsManager.getCtrlTapLatches(context))
    }
    var altLatchStaysOnSpace by remember {
        mutableStateOf(SettingsManager.getAltLatchStaysOnSpace(context))
    }
    var ctrlLatchStaysOnSpace by remember {
        mutableStateOf(SettingsManager.getCtrlLatchStaysOnSpace(context))
    }
    
    
    // Handle system back button
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
                        text = stringResource(R.string.settings_category_keyboard_timing),
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
            // Long Press Threshold
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.long_press_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(
                                R.string.keyboard_timing_long_press_value,
                                longPressThreshold
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Slider(
                        value = longPressThreshold.toFloat(),
                        onValueChange = { newValue ->
                            val clampedValue = newValue.toLong().coerceIn(
                                SettingsManager.getMinLongPressThreshold(),
                                SettingsManager.getMaxLongPressThreshold()
                            )
                            longPressThreshold = clampedValue
                            SettingsManager.setLongPressThreshold(context, clampedValue)
                        },
                        valueRange = SettingsManager.getMinLongPressThreshold().toFloat()..SettingsManager.getMaxLongPressThreshold().toFloat(),
                        steps = 18,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(24.dp)
                    )
                }
            }
        
            // Long Press Modifier (Alt/Shift/Variations/Sym) - Dropdown Style
            var showModifierMenu by remember { mutableStateOf(false) }
            var showSoftwareKeyboardModeMenu by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable { showModifierMenu = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.long_press_modifier_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = when (longPressModifier) {
                                "alt" -> stringResource(R.string.long_press_modifier_alt)
                                "shift" -> stringResource(R.string.long_press_modifier_shift)
                                "variations" -> stringResource(R.string.long_press_modifier_variations)
                                "sym" -> stringResource(R.string.long_press_modifier_sym)
                                else -> stringResource(R.string.long_press_modifier_alt)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showModifierMenu,
                    onDismissRequest = { showModifierMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.long_press_modifier_alt)) },
                        onClick = {
                            longPressModifier = "alt"
                            SettingsManager.setLongPressModifier(context, "alt")
                            showModifierMenu = false
                        },
                        leadingIcon = {
                            if (longPressModifier == "alt") {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.long_press_modifier_shift)) },
                        onClick = {
                            longPressModifier = "shift"
                            SettingsManager.setLongPressModifier(context, "shift")
                            showModifierMenu = false
                        },
                        leadingIcon = {
                            if (longPressModifier == "shift") {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.long_press_modifier_variations)) },
                        onClick = {
                            longPressModifier = "variations"
                            SettingsManager.setLongPressModifier(context, "variations")
                            showModifierMenu = false
                        },
                        leadingIcon = {
                            if (longPressModifier == "variations") {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.long_press_modifier_sym)) },
                        onClick = {
                            longPressModifier = "sym"
                            SettingsManager.setLongPressModifier(context, "sym")
                            showModifierMenu = false
                        },
                        leadingIcon = {
                            if (longPressModifier == "sym") {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }

            val effectiveSoftwareMode = SettingsManager.resolveEffectiveSoftwareKeyboardMode(context)
            val softwareKeyboardModeLabel = when (softwareKeyboardMode) {
                SettingsManager.SoftwareKeyboardMode.AUTO -> {
                    val currentLabel = if (effectiveSoftwareMode == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL) {
                        stringResource(R.string.software_keyboard_mode_always_virtual)
                    } else {
                        stringResource(R.string.software_keyboard_mode_always_hardware)
                    }
                    stringResource(R.string.software_keyboard_mode_auto_current, currentLabel)
                }
                SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL -> stringResource(R.string.software_keyboard_mode_always_virtual)
                SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE -> stringResource(R.string.software_keyboard_mode_always_hardware)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clickable { showSoftwareKeyboardModeMenu = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.software_keyboard_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = softwareKeyboardModeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showSoftwareKeyboardModeMenu,
                    onDismissRequest = { showSoftwareKeyboardModeMenu = false }
                ) {
                    listOf(
                        SettingsManager.SoftwareKeyboardMode.AUTO to stringResource(R.string.software_keyboard_mode_auto_short),
                        SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL to stringResource(R.string.software_keyboard_mode_always_virtual),
                        SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE to stringResource(R.string.software_keyboard_mode_always_hardware)
                    ).forEach { (mode, title) ->
                        DropdownMenuItem(
                            text = { Text(title) },
                            onClick = {
                                softwareKeyboardMode = mode
                                SettingsManager.setSoftwareKeyboardMode(context, mode)
                                showSoftwareKeyboardModeMenu = false
                            },
                            leadingIcon = {
                                if (softwareKeyboardMode == mode) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = stringResource(R.string.modifier_tap_behavior_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            ModifierTapLatchRow(
                title = stringResource(R.string.shift_tap_latches_title),
                description = stringResource(R.string.shift_tap_latches_description),
                checked = shiftTapLatches,
                onCheckedChange = { enabled ->
                    shiftTapLatches = enabled
                    SettingsManager.setShiftTapLatches(context, enabled)
                }
            )

            ModifierTapLatchRow(
                title = stringResource(R.string.alt_tap_latches_title),
                description = stringResource(R.string.alt_tap_latches_description),
                checked = altTapLatches,
                onCheckedChange = { enabled ->
                    altTapLatches = enabled
                    SettingsManager.setAltTapLatches(context, enabled)
                }
            )

            ModifierTapLatchRow(
                title = stringResource(R.string.alt_latch_stays_on_space_title),
                description = stringResource(R.string.alt_latch_stays_on_space_description),
                checked = altLatchStaysOnSpace,
                onCheckedChange = { enabled ->
                    altLatchStaysOnSpace = enabled
                    SettingsManager.setAltLatchStaysOnSpace(context, enabled)
                }
            )

            ModifierTapLatchRow(
                title = stringResource(R.string.ctrl_tap_latches_title),
                description = stringResource(R.string.ctrl_tap_latches_description),
                checked = ctrlTapLatches,
                onCheckedChange = { enabled ->
                    ctrlTapLatches = enabled
                    SettingsManager.setCtrlTapLatches(context, enabled)
                }
            )

            if (ctrlTapLatches) {
                ModifierTapLatchRow(
                    title = stringResource(R.string.ctrl_latch_stays_on_space_title),
                    description = stringResource(R.string.ctrl_latch_stays_on_space_description),
                    checked = ctrlLatchStaysOnSpace,
                    indent = true,
                    onCheckedChange = { enabled ->
                        ctrlLatchStaysOnSpace = enabled
                        SettingsManager.setCtrlLatchStaysOnSpace(context, enabled)
                    }
                )
            }
        }
    }
}

@Composable
private fun ModifierTapLatchRow(
    title: String,
    description: String,
    checked: Boolean,
    indent: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (indent) 52.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!indent) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
