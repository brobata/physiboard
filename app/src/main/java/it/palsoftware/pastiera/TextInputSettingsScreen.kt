package it.palsoftware.pastiera

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TextFields
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
 * Text Input settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var autoCapitalizeFirstLetter by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeFirstLetter(context))
    }

    var autoCapitalizeAfterPeriod by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeAfterPeriod(context))
    }

    var doubleSpaceToPeriod by remember {
        mutableStateOf(SettingsManager.getDoubleSpaceToPeriod(context))
    }

    var spacedHyphenToEnDash by remember {
        mutableStateOf(SettingsManager.getSpacedHyphenToEnDash(context))
    }

    var spacedHyphenDashStyle by remember {
        mutableStateOf(SettingsManager.getSpacedHyphenDashStyle(context))
    }

    var spacedHyphenDashExpanded by remember { mutableStateOf(false) }

    var smartQuotes by remember {
        mutableStateOf(SettingsManager.getSmartQuotes(context))
    }

    var smartQuotesStyle by remember {
        mutableStateOf(SettingsManager.getSmartQuotesStyle(context))
    }

    var smartQuotesExpanded by remember { mutableStateOf(false) }

    var clearAltOnSpace by remember {
        mutableStateOf(SettingsManager.getClearAltOnSpace(context))
    }
    
    var swipeToDelete by remember {
        mutableStateOf(SettingsManager.getSwipeToDelete(context))
    }
    
    var autoShowKeyboard by remember {
        mutableStateOf(SettingsManager.getAutoShowKeyboard(context))
    }
    
    var altCtrlSpeechShortcut by remember {
        mutableStateOf(SettingsManager.getAltCtrlSpeechShortcutEnabled(context))
    }

    var altShiftLayoutSwitch by remember {
        mutableStateOf(SettingsManager.isAltShiftLayoutSwitchEnabled(context))
    }

    var titan2LayoutEnabled by remember {
        mutableStateOf(SettingsManager.isTitan2LayoutEnabled(context))
    }

    var shiftBackspaceDelete by remember {
        mutableStateOf(SettingsManager.getShiftBackspaceDelete(context))
    }

    var altBackspaceDelete by remember {
        mutableStateOf(SettingsManager.getAltBackspaceDelete(context))
    }

    var backspaceAtStartDelete by remember {
        mutableStateOf(SettingsManager.getBackspaceAtStartDelete(context))
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
                        text = stringResource(R.string.settings_category_text_input),
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
            // Auto Capitalize
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
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_capitalize_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = autoCapitalizeFirstLetter,
                        onCheckedChange = { enabled ->
                            autoCapitalizeFirstLetter = enabled
                            SettingsManager.setAutoCapitalizeFirstLetter(context, enabled)
                        }
                    )
                }
            }

            // Auto Capitalize After Period
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
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_capitalize_after_period_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.auto_capitalize_after_period_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = autoCapitalizeAfterPeriod,
                        onCheckedChange = { enabled ->
                            autoCapitalizeAfterPeriod = enabled
                            SettingsManager.setAutoCapitalizeAfterPeriod(context, enabled)
                        }
                    )
                }
            }

            // Double Space to Period
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
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.double_space_to_period_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.double_space_to_period_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = doubleSpaceToPeriod,
                        onCheckedChange = { enabled ->
                            doubleSpaceToPeriod = enabled
                            SettingsManager.setDoubleSpaceToPeriod(context, enabled)
                        }
                    )
                }
            }

            // Spaced Hyphen to Dash
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.spaced_hyphen_to_en_dash_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        ExposedDropdownMenuBox(
                            expanded = spacedHyphenDashExpanded,
                            onExpandedChange = { spacedHyphenDashExpanded = !spacedHyphenDashExpanded }
                        ) {
                            OutlinedTextField(
                                value = dashStyleLabel(spacedHyphenDashStyle),
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                enabled = spacedHyphenToEnDash,
                                textStyle = MaterialTheme.typography.bodySmall,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = spacedHyphenDashExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .menuAnchor(
                                        type = MenuAnchorType.PrimaryNotEditable,
                                        enabled = spacedHyphenToEnDash
                                    )
                            )
                            ExposedDropdownMenu(
                                expanded = spacedHyphenDashExpanded,
                                onDismissRequest = { spacedHyphenDashExpanded = false }
                            ) {
                                dashStyleOptions().forEach { style ->
                                    DropdownMenuItem(
                                        text = { Text(dashStyleLabel(style)) },
                                        onClick = {
                                            spacedHyphenDashStyle = style
                                            SettingsManager.setSpacedHyphenDashStyle(context, style)
                                            spacedHyphenDashExpanded = false
                                        },
                                        enabled = spacedHyphenToEnDash
                                    )
                                }
                            }
                        }
                    }
                    Switch(
                        checked = spacedHyphenToEnDash,
                        onCheckedChange = { enabled ->
                            spacedHyphenToEnDash = enabled
                            SettingsManager.setSpacedHyphenToEnDash(context, enabled)
                            if (!enabled) {
                                spacedHyphenDashExpanded = false
                            }
                        }
                    )
                }
            }

            // Smart Quotes
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.smart_quotes_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        ExposedDropdownMenuBox(
                            expanded = smartQuotesExpanded,
                            onExpandedChange = { smartQuotesExpanded = !smartQuotesExpanded }
                        ) {
                            OutlinedTextField(
                                value = smartQuotesStyleLabel(smartQuotesStyle),
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                enabled = smartQuotes,
                                textStyle = MaterialTheme.typography.bodySmall,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = smartQuotesExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .menuAnchor(
                                        type = MenuAnchorType.PrimaryNotEditable,
                                        enabled = smartQuotes
                                    )
                            )
                            ExposedDropdownMenu(
                                expanded = smartQuotesExpanded,
                                onDismissRequest = { smartQuotesExpanded = false }
                            ) {
                                smartQuoteStyleOptions().forEach { style ->
                                    DropdownMenuItem(
                                        text = { Text(smartQuotesStyleLabel(style)) },
                                        onClick = {
                                            smartQuotesStyle = style
                                            SettingsManager.setSmartQuotesStyle(context, style)
                                            smartQuotesExpanded = false
                                        },
                                        enabled = smartQuotes
                                    )
                                }
                            }
                        }
                    }
                    Switch(
                        checked = smartQuotes,
                        onCheckedChange = { enabled ->
                            smartQuotes = enabled
                            SettingsManager.setSmartQuotes(context, enabled)
                            if (!enabled) {
                                smartQuotesExpanded = false
                            }
                        }
                    )
                }
            }

            // Clear Alt on Space
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
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.clear_alt_on_space_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.clear_alt_on_space_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = clearAltOnSpace,
                        onCheckedChange = { enabled ->
                            clearAltOnSpace = enabled
                            SettingsManager.setClearAltOnSpace(context, enabled)
                        }
                    )
                }
            }

            // Swipe to Delete
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.swipe_to_delete_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.swipe_to_delete_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = swipeToDelete,
                        onCheckedChange = { enabled ->
                            swipeToDelete = enabled
                            SettingsManager.setSwipeToDelete(context, enabled)
                        }
                    )
                }
            }
        
            // Auto Show Keyboard
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
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_show_keyboard_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = autoShowKeyboard,
                        onCheckedChange = { enabled ->
                            autoShowKeyboard = enabled
                            SettingsManager.setAutoShowKeyboard(context, enabled)
                        }
                    )
                }
            }

            // Alt+Shift Layout Switch
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
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alt_shift_layout_switch_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.alt_shift_layout_switch_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = altShiftLayoutSwitch,
                        onCheckedChange = { enabled ->
                            altShiftLayoutSwitch = enabled
                            SettingsManager.setAltShiftLayoutSwitchEnabled(context, enabled)
                        }
                    )
                }
            }

            // Alt+Ctrl Speech Recognition Shortcut
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
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alt_ctrl_speech_shortcut_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.alt_ctrl_speech_shortcut_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = altCtrlSpeechShortcut,
                        onCheckedChange = { enabled ->
                            altCtrlSpeechShortcut = enabled
                            SettingsManager.setAltCtrlSpeechShortcutEnabled(context, enabled)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete_alternatives_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.delete_alternatives_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.shift_backspace_delete_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = shiftBackspaceDelete,
                            onCheckedChange = { enabled ->
                                shiftBackspaceDelete = enabled
                                SettingsManager.setShiftBackspaceDelete(context, enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.alt_backspace_delete_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = altBackspaceDelete,
                            onCheckedChange = { enabled ->
                                altBackspaceDelete = enabled
                                SettingsManager.setAltBackspaceDelete(context, enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.backspace_at_start_delete_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = backspaceAtStartDelete,
                            onCheckedChange = { enabled ->
                                backspaceAtStartDelete = enabled
                                SettingsManager.setBackspaceAtStartDelete(context, enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.delete_alternatives_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.delete_alternatives_selection_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun dashStyleOptions(): List<String> {
    return listOf(
        SettingsManager.DASH_STYLE_EN,
        SettingsManager.DASH_STYLE_EM
    )
}

private fun dashStyleLabel(style: String): String {
    return when (style) {
        SettingsManager.DASH_STYLE_EM -> "—"
        else -> "–"
    }
}

private fun smartQuoteStyleOptions(): List<String> {
    return listOf(
        SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS,
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS,
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED,
        SettingsManager.SMART_QUOTES_STYLE_GERMAN_LOW_HIGH,
        SettingsManager.SMART_QUOTES_STYLE_ENGLISH_CURLY
    )
}

private fun smartQuotesStyleLabel(style: String): String {
    return when (style) {
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS -> "«...»"
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED -> "« ... »"
        SettingsManager.SMART_QUOTES_STYLE_GERMAN_LOW_HIGH -> "„...“"
        SettingsManager.SMART_QUOTES_STYLE_ENGLISH_CURLY -> "“...”"
        else -> "»...«"
    }
}
