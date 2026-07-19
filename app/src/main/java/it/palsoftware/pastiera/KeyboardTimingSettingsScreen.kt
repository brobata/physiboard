package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.saveable.rememberSaveable
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
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun KeyboardTimingSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var longPressThreshold by remember { 
        mutableStateOf(SettingsManager.getLongPressThreshold(context))
    }
    KeyboardTimingMainContent(
        modifier = modifier,
        onBack = onBack,
        longPressThreshold = longPressThreshold,
        onLongPressThresholdChange = { longPressThreshold = it }
    )
}

@Composable
private fun KeyboardTimingMainContent(
    modifier: Modifier,
    onBack: () -> Unit,
    longPressThreshold: Long,
    onLongPressThresholdChange: (Long) -> Unit
) {
    val context = LocalContext.current

    
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
                            onLongPressThresholdChange(clampedValue)
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
        
        }
    }
}

@Composable
internal fun VirtualKeyboardBehaviorSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var softwareKeyboardLayoutStyle by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardLayoutStyle(context))
    }
    var nearestKeyTouchEnabled by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardNearestKeyTouchEnabled(context))
    }
    var longPressLayerPopupEnabled by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardLongPressLayerPopupEnabled(context))
    }
    var longPressLayerPopupBelowKey by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardLongPressLayerPopupBelowKey(context))
    }
    var numberRowEnabled by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardNumberRowEnabled(context))
    }
    var leftModifierKey by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardLeftModifierKey(context))
    }
    var rightModifierKey by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardRightModifierKey(context))
    }
    var showSoftwareKeyboardLayoutStyleMenu by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val prefs = SettingsManager.getPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "software_keyboard_left_modifier_key" -> {
                    leftModifierKey = SettingsManager.getSoftwareKeyboardLeftModifierKey(context)
                }
                "software_keyboard_right_modifier_key" -> {
                    rightModifierKey = SettingsManager.getSoftwareKeyboardRightModifierKey(context)
                }
                "software_keyboard_long_press_layer_popup_enabled" -> {
                    longPressLayerPopupEnabled =
                        SettingsManager.getSoftwareKeyboardLongPressLayerPopupEnabled(context)
                }
                "software_keyboard_long_press_layer_popup_below_key" -> {
                    longPressLayerPopupBelowKey =
                        SettingsManager.getSoftwareKeyboardLongPressLayerPopupBelowKey(context)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
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
                        text = stringResource(R.string.virtual_keyboard_behaviour_title),
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
            VirtualKeyboardSectionTitle(stringResource(R.string.on_screen_keyboard_section_layout))
            val softwareKeyboardLayoutStyleLabel = when (softwareKeyboardLayoutStyle) {
                SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT ->
                    stringResource(R.string.software_keyboard_layout_style_compact)
                SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO ->
                    stringResource(R.string.software_keyboard_layout_style_extended_iso)
                SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI ->
                    stringResource(R.string.software_keyboard_layout_style_full_ansi)
                SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO ->
                    stringResource(R.string.software_keyboard_layout_style_full_iso)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clickable { showSoftwareKeyboardLayoutStyleMenu = true }
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
                            text = stringResource(R.string.software_keyboard_layout_style_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(
                                R.string.software_keyboard_layout_style_description,
                                softwareKeyboardLayoutStyleLabel
                            ),
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
                    expanded = showSoftwareKeyboardLayoutStyleMenu,
                    onDismissRequest = { showSoftwareKeyboardLayoutStyleMenu = false }
                ) {
                    listOf(
                        SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT to stringResource(R.string.software_keyboard_layout_style_compact),
                        SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO to stringResource(R.string.software_keyboard_layout_style_extended_iso),
                        SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI to stringResource(R.string.software_keyboard_layout_style_full_ansi),
                        SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO to stringResource(R.string.software_keyboard_layout_style_full_iso)
                    ).forEach { (style, title) ->
                        DropdownMenuItem(
                            text = { Text(title) },
                            onClick = {
                                softwareKeyboardLayoutStyle = style
                                SettingsManager.setSoftwareKeyboardLayoutStyle(context, style)
                                showSoftwareKeyboardLayoutStyleMenu = false
                            },
                            leadingIcon = {
                                if (softwareKeyboardLayoutStyle == style) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }

            ModifierTapLatchRow(
                title = stringResource(R.string.software_keyboard_number_row_title),
                description = stringResource(R.string.software_keyboard_number_row_description),
                checked = numberRowEnabled,
                onCheckedChange = { enabled ->
                    numberRowEnabled = enabled
                    SettingsManager.setSoftwareKeyboardNumberRowEnabled(context, enabled)
                }
            )

            Text(
                text = stringResource(R.string.software_keyboard_modifier_keys_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SoftwareKeyboardModifierSelection(
                    title = stringResource(R.string.software_keyboard_left_modifier_key_title),
                    selected = leftModifierKey,
                    modifier = Modifier.weight(1f),
                    onSelected = { selected ->
                        leftModifierKey = selected
                        SettingsManager.setSoftwareKeyboardLeftModifierKey(context, selected)
                    }
                )
                SoftwareKeyboardModifierSelection(
                    title = stringResource(R.string.software_keyboard_right_modifier_key_title),
                    selected = rightModifierKey,
                    modifier = Modifier.weight(1f),
                    onSelected = { selected ->
                        rightModifierKey = selected
                        SettingsManager.setSoftwareKeyboardRightModifierKey(context, selected)
                    }
                )
            }

            VirtualKeyboardSectionTitle(stringResource(R.string.on_screen_keyboard_section_touch))

            ModifierTapLatchRow(
                title = stringResource(R.string.software_keyboard_nearest_key_touch_title),
                description = stringResource(R.string.software_keyboard_nearest_key_touch_description),
                checked = nearestKeyTouchEnabled,
                onCheckedChange = { enabled ->
                    nearestKeyTouchEnabled = enabled
                    SettingsManager.setSoftwareKeyboardNearestKeyTouchEnabled(context, enabled)
                }
            )

            ModifierTapLatchRow(
                title = stringResource(R.string.software_keyboard_long_press_layer_popup_title),
                description = stringResource(R.string.software_keyboard_long_press_layer_popup_description),
                checked = longPressLayerPopupEnabled,
                onCheckedChange = { enabled ->
                    longPressLayerPopupEnabled = enabled
                    SettingsManager.setSoftwareKeyboardLongPressLayerPopupEnabled(context, enabled)
                }
            )

            if (longPressLayerPopupEnabled) {
                ModifierTapLatchRow(
                    title = stringResource(R.string.software_keyboard_long_press_layer_popup_below_key_title),
                    description = stringResource(R.string.software_keyboard_long_press_layer_popup_below_key_description),
                    checked = longPressLayerPopupBelowKey,
                    indent = true,
                    onCheckedChange = { enabled ->
                        longPressLayerPopupBelowKey = enabled
                        SettingsManager.setSoftwareKeyboardLongPressLayerPopupBelowKey(context, enabled)
                    }
                )
            }

            VirtualKeyboardSectionTitle(stringResource(R.string.on_screen_keyboard_section_appearance))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clickable {
                        context.startActivity(
                            Intent(context, SettingsActivity::class.java)
                                .putExtra(SettingsActivity.EXTRA_DESTINATION, SettingsActivity.DESTINATION_CUSTOMIZATION)
                                .putExtra(SettingsActivity.EXTRA_CUSTOMIZATION_DESTINATION, SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME)
                                .putExtra(SettingsActivity.EXTRA_KEYBOARD_THEME_TARGET, SettingsActivity.KEYBOARD_THEME_TARGET_SOFTWARE)
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Keyboard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.software_keyboard_theme_link_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.software_keyboard_theme_link_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VirtualKeyboardSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun SoftwareKeyboardModifierSelection(
    title: String,
    selected: SettingsManager.SoftwareKeyboardModifierKey,
    modifier: Modifier = Modifier,
    onSelected: (SettingsManager.SoftwareKeyboardModifierKey) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .height(82.dp)
            .clickable { expanded = true },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = softwareKeyboardModifierKeyLabel(selected),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SettingsManager.SoftwareKeyboardModifierKey.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(softwareKeyboardModifierKeyLabel(option)) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                        leadingIcon = {
                            if (selected == option) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun softwareKeyboardModifierKeyLabel(
    modifierKey: SettingsManager.SoftwareKeyboardModifierKey
): String {
    return when (modifierKey) {
        SettingsManager.SoftwareKeyboardModifierKey.CTRL ->
            stringResource(R.string.software_keyboard_modifier_key_ctrl)
        SettingsManager.SoftwareKeyboardModifierKey.ALT ->
            stringResource(R.string.software_keyboard_modifier_key_alt)
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
