package it.palsoftware.pastiera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import it.palsoftware.pastiera.R

/**
 * Screen for configuring which buttons to show in the status bar slots.
 * Layout: [Left Slot] [---variations---] [Right Slot 1] [Right Slot 2]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBarButtonsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCustomizeVariations: () -> Unit
) {
    val context = LocalContext.current
    
    // Load slot assignments from settings
    var leftSlots by remember { mutableStateOf(SettingsManager.getStatusBarSlotsLeft(context)) }
    var rightSlots by remember { mutableStateOf(SettingsManager.getStatusBarSlotsRight(context)) }
    var variationsVisible by remember {
        mutableStateOf(SettingsManager.areStatusBarVariationsEnabled(context))
    }
    var dynamicVariationSlotCount by remember {
        mutableStateOf(SettingsManager.getDynamicVariationBarSlotCount(context))
    }
    var dynamicVariationsResizeToContent by remember {
        mutableStateOf(SettingsManager.getDynamicVariationBarResizeToContent(context))
    }
    
    BackHandler { onBack() }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
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
                    text = stringResource(R.string.status_bar_buttons_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                // Reset to defaults button
                IconButton(
                    onClick = {
                        val defaults = SettingsManager.resetStatusBarSlotsToDefault(context)
                        leftSlots = listOf(defaults.left)
                        rightSlots = listOf(defaults.right1, defaults.right2)
                        variationsVisible = SettingsManager.areStatusBarVariationsEnabled(context)
                        dynamicVariationSlotCount = SettingsManager.getDynamicVariationBarSlotCount(context)
                        dynamicVariationsResizeToContent = SettingsManager.getDynamicVariationBarResizeToContent(context)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.status_bar_buttons_reset),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Description
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 0.dp
        ) {
            Text(
                text = stringResource(R.string.status_bar_buttons_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        
        // Visual preview of button layout
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    leftSlots.forEachIndexed { index, buttonId ->
                        SlotPreview(buttonId = buttonId, label = "L${index + 1}")
                    }
                }
                
                // Variations area (center)
                if (variationsVisible) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "· · ·",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // Right slots preview
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rightSlots.forEachIndexed { index, buttonId ->
                        SlotPreview(buttonId = buttonId, label = "R${index + 1}")
                    }
                }
            }
        }
        
        HorizontalDivider()
        
        Spacer(modifier = Modifier.height(8.dp))

        Surface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.status_bar_variations_visible_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.status_bar_variations_visible_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = variationsVisible,
                    onCheckedChange = { visible ->
                        variationsVisible = visible
                        SettingsManager.setStatusBarVariationsEnabled(context, visible)
                    }
                )
            }
        }

        if (variationsVisible) {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.status_bar_swipe_cursor_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Surface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dynamic_variation_slot_count_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(
                                    R.string.dynamic_variation_slot_count_description,
                                    dynamicVariationSlotCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Slider(
                        value = dynamicVariationSlotCount.toFloat(),
                        onValueChange = { value ->
                            dynamicVariationSlotCount = value.toInt().coerceIn(
                                SettingsManager.MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT,
                                SettingsManager.MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT
                            )
                        },
                        onValueChangeFinished = {
                            SettingsManager.setDynamicVariationBarSlotCount(context, dynamicVariationSlotCount)
                        },
                        valueRange = SettingsManager.MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT.toFloat()..
                            SettingsManager.MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT.toFloat(),
                        steps = SettingsManager.MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT -
                            SettingsManager.MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT - 1
                    )
                }
            }

            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dynamic_variation_resize_to_content_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.dynamic_variation_resize_to_content_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dynamicVariationsResizeToContent,
                        onCheckedChange = { enabled ->
                            dynamicVariationsResizeToContent = enabled
                            SettingsManager.setDynamicVariationBarResizeToContent(context, enabled)
                        }
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { onCustomizeVariations() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.variation_customize_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Helper function to clear a button from other slots when it's selected
        fun selectButtonForSlot(
            buttonId: String,
            targetSide: String,
            targetIndex: Int,
            updateState: (String) -> Unit
        ) {
            // If selecting "none", just update the target slot
            if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE) {
                updateState(buttonId)
                return
            }
            
            // Clear this button from other slots if it's already used
            leftSlots = leftSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "left" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else {
                    current
                }
            }
            rightSlots = rightSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "right" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else {
                    current
                }
            }
            SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
            SettingsManager.setStatusBarSlotsRight(context, rightSlots)
            
            // Update the target slot
            updateState(buttonId)
        }
        
        SlotGroup(
            title = stringResource(R.string.status_bar_slots_left),
            slots = leftSlots,
            slotPrefix = "L",
            onSlotSelected = { index, buttonId ->
                selectButtonForSlot(buttonId, "left", index) {
                    leftSlots = leftSlots.toMutableList().also { it[index] = buttonId }
                    SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
                }
            },
            onAddSlot = {
                leftSlots = leftSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
            },
            onRemoveSlot = { index ->
                leftSlots = leftSlots.toMutableList().also { it.removeAt(index) }
                SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
            }
        )
        
        SlotGroup(
            title = stringResource(R.string.status_bar_slots_right),
            slots = rightSlots,
            slotPrefix = "R",
            onSlotSelected = { index, buttonId ->
                selectButtonForSlot(buttonId, "right", index) {
                    rightSlots = rightSlots.toMutableList().also { it[index] = buttonId }
                    SettingsManager.setStatusBarSlotsRight(context, rightSlots)
                }
            },
            onAddSlot = {
                rightSlots = rightSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                SettingsManager.setStatusBarSlotsRight(context, rightSlots)
            },
            onRemoveSlot = { index ->
                rightSlots = rightSlots.toMutableList().also { it.removeAt(index) }
                SettingsManager.setStatusBarSlotsRight(context, rightSlots)
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SlotGroup(
    title: String,
    slots: List<String>,
    slotPrefix: String,
    onSlotSelected: (Int, String) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (Int) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onAddSlot) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.status_bar_slot_add)
                    )
                }
            }

            slots.forEachIndexed { index, buttonId ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SlotDropdown(
                        slotLabel = title,
                        slotNumber = "$slotPrefix${index + 1}",
                        selectedButton = buttonId,
                        excludedButtons = emptyList(),
                        modifier = Modifier.weight(1f),
                        onButtonSelected = { selected -> onSlotSelected(index, selected) }
                    )
                    IconButton(
                        onClick = { onRemoveSlot(index) },
                        enabled = slots.size > 1
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.status_bar_slot_remove),
                            tint = if (slots.size > 1) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotPreview(
    buttonId: String,
    label: String
) {
    Surface(
        modifier = Modifier.size(32.dp),
        color = if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE) 
            MaterialTheme.colorScheme.surface 
        else 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    painter = painterResource(id = getButtonIconRes(buttonId)),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotDropdown(
    slotLabel: String,
    slotNumber: String,
    selectedButton: String,
    excludedButtons: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    onButtonSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Filter out buttons that are already used in other slots (but always keep "none" available)
    val availableButtons = SettingsManager.getAvailableStatusBarButtons()
        .filter { it == SettingsManager.STATUS_BAR_BUTTON_NONE || it !in excludedButtons }
    
    Surface(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "$slotLabel ($slotNumber)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = getButtonDisplayName(selectedButton),
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        if (selectedButton == SettingsManager.STATUS_BAR_BUTTON_NONE) {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = getButtonIconRes(selectedButton)),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableButtons.forEach { buttonId ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE) {
                                        Box(
                                            modifier = Modifier.size(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "—",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Icon(
                                            painter = painterResource(id = getButtonIconRes(buttonId)),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = getButtonDisplayName(buttonId),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = getButtonDescription(buttonId),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onButtonSelected(buttonId)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getButtonDisplayName(buttonId: String): String {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_NONE -> stringResource(R.string.status_bar_button_none)
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> stringResource(R.string.status_bar_button_clipboard)
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> stringResource(R.string.status_bar_button_microphone)
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> stringResource(R.string.status_bar_button_emoji)
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> stringResource(R.string.status_bar_button_language)
        SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> stringResource(R.string.status_bar_button_hamburger)
        SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> stringResource(R.string.status_bar_button_settings)
        SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> stringResource(R.string.status_bar_button_symbols)
        SettingsManager.STATUS_BAR_BUTTON_UNDO -> stringResource(R.string.status_bar_button_undo)
        SettingsManager.STATUS_BAR_BUTTON_REDO -> stringResource(R.string.status_bar_button_redo)
        else -> buttonId
    }
}

@Composable
private fun getButtonDescription(buttonId: String): String {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_NONE -> stringResource(R.string.status_bar_button_none_description)
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> stringResource(R.string.status_bar_button_clipboard_description)
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> stringResource(R.string.status_bar_button_microphone_description)
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> stringResource(R.string.status_bar_button_emoji_description)
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> stringResource(R.string.status_bar_button_language_description)
        SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> stringResource(R.string.status_bar_button_hamburger_description)
        SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> stringResource(R.string.status_bar_button_settings_description)
        SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> stringResource(R.string.status_bar_button_symbols_description)
        SettingsManager.STATUS_BAR_BUTTON_UNDO -> stringResource(R.string.status_bar_button_undo_description)
        SettingsManager.STATUS_BAR_BUTTON_REDO -> stringResource(R.string.status_bar_button_redo_description)
        else -> ""
    }
}

/**
 * Returns the drawable resource ID for the button icon.
 * Note: STATUS_BAR_BUTTON_NONE should be handled separately (no icon).
 */
private fun getButtonIconRes(buttonId: String): Int {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> R.drawable.ic_content_paste_24
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> R.drawable.ic_baseline_mic_24
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> R.drawable.ic_emoji_emotions_24
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> R.drawable.ic_globe_24
        SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> R.drawable.ic_menu_24
        SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> R.drawable.ic_settings_24
        SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> R.drawable.ic_emoji_symbols_24
        SettingsManager.STATUS_BAR_BUTTON_UNDO -> R.drawable.ic_undo_24
        SettingsManager.STATUS_BAR_BUTTON_REDO -> R.drawable.ic_redo_24
        else -> R.drawable.ic_settings_24 // Fallback
    }
}
