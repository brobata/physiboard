package it.palsoftware.pastiera

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.StatusBarController
import it.palsoftware.pastiera.inputmethod.suggestions.ui.FullSuggestionsBar
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import it.palsoftware.pastiera.inputmethod.ui.LedStatusView
import it.palsoftware.pastiera.inputmethod.ui.VariationBarView
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun KeyboardThemeScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val builtInPresets = remember { keyboardThemePresets() }
    var savedThemes by remember {
        mutableStateOf(SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() })
    }
    val themeOptions = builtInPresets.map { KeyboardThemeOption("builtin:${it.name}", it, it) } + savedThemes
    val initialPreviewPage = remember {
        if (
            SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) ==
                SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) {
            1
        } else {
            0
        }
    }
    val previewPagerState = rememberPagerState(initialPage = initialPreviewPage, pageCount = { 2 })
    var customizationTab by remember { mutableStateOf(0) }
    var hardwarePreset by remember { mutableStateOf(SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE).toKeyboardThemePreset("Custom")) }
    var softwarePreset by remember { mutableStateOf(SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE).toKeyboardThemePreset("Custom")) }
    var hardwareTheme by remember { mutableStateOf(hardwarePreset) }
    var softwareTheme by remember { mutableStateOf(softwarePreset) }
    var hardwareSelectionKey by remember { mutableStateOf("custom:hardware") }
    var softwareSelectionKey by remember { mutableStateOf("custom:software") }
    var exportTheme by remember { mutableStateOf<KeyboardThemePreset?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }

    val activePreviewPage = previewPagerState.currentPage
    val activeTheme = if (activePreviewPage == 0) hardwareTheme else softwareTheme
    val activePreset = if (activePreviewPage == 0) hardwarePreset else softwarePreset
    val activeSelectionKey = if (activePreviewPage == 0) hardwareSelectionKey else softwareSelectionKey

    fun updateActiveTheme(theme: KeyboardThemePreset) {
        if (activePreviewPage == 0) {
            hardwareTheme = theme
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, theme.toSettingsTheme())
        } else {
            softwareTheme = theme
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, theme.toSettingsTheme())
        }
        if (activeSelectionKey.startsWith("saved:")) {
            val savedName = activeSelectionKey.removePrefix("saved:")
            SettingsManager.saveKeyboardTheme(context, savedName, theme.copy(name = savedName).toSettingsTheme())
            savedThemes = SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() }
        }
    }

    fun applyPreset(option: KeyboardThemeOption) {
        val preset = option.preset
        if (activeSelectionKey == option.key) {
            return
        }
        if (activePreviewPage == 0) {
            hardwareSelectionKey = option.key
            hardwarePreset = option.resetPreset
            hardwareTheme = preset
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, preset.toSettingsTheme())
        } else {
            softwareSelectionKey = option.key
            softwarePreset = option.resetPreset
            softwareTheme = preset
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, preset.toSettingsTheme())
        }
    }

    fun applyImportedTheme(theme: KeyboardThemePreset) {
        if (activePreviewPage == 0) {
            hardwareSelectionKey = "custom:imported:hardware"
            hardwarePreset = theme
            hardwareTheme = theme
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, theme.toSettingsTheme())
        } else {
            softwareSelectionKey = "custom:imported:software"
            softwarePreset = theme
            softwareTheme = theme
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, theme.toSettingsTheme())
        }
    }

    fun saveActiveThemeAs(name: String) {
        val normalizedName = name.trim().ifEmpty { "Custom" }
        val savedPreset = activeTheme.copy(name = normalizedName)
        SettingsManager.saveKeyboardTheme(context, normalizedName, savedPreset.toSettingsTheme())
        savedThemes = SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() }
        if (activePreviewPage == 0) {
            hardwareSelectionKey = "saved:$normalizedName"
            hardwarePreset = savedPreset
            hardwareTheme = savedPreset
        } else {
            softwareSelectionKey = "saved:$normalizedName"
            softwarePreset = savedPreset
            softwareTheme = savedPreset
        }
    }

    BackHandler(onBack = onBack)

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
                        text = stringResource(R.string.keyboard_theme_title),
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.keyboard_theme_preset_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                lazyItems(themeOptions) { option ->
                    KeyboardThemePresetCard(
                        preset = option.preset,
                        selected = activeSelectionKey == option.key,
                        onClick = { applyPreset(option) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { showSaveAsDialog = true }) {
                    Text("Save copy as")
                }
                Button(onClick = { exportTheme = activeTheme }) {
                    Text("Export")
                }
                Button(onClick = { showImportDialog = true }) {
                    Text("Import")
                }
            }

            Text(
                text = stringResource(R.string.keyboard_theme_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
            KeyboardThemePreviewCarousel(
                theme = if (previewPagerState.currentPage == 0) hardwareTheme else softwareTheme,
                currentPage = previewPagerState.currentPage,
                pageContent = {
                    HorizontalPager(
                        state = previewPagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> HardwareKeyboardThemePreview(theme = hardwareTheme)
                            else -> VirtualKeyboardThemePreview(theme = softwareTheme)
                        }
                    }
                }
            )

            Text(
                text = stringResource(R.string.keyboard_theme_customize_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
            TabRow(selectedTabIndex = customizationTab) {
                Tab(
                    selected = customizationTab == 0,
                    onClick = { customizationTab = 0 },
                    text = { Text("Farben") }
                )
                Tab(
                    selected = customizationTab == 1,
                    onClick = { customizationTab = 1 },
                    text = { Text("Tasten") }
                )
            }
            if (customizationTab == 0) {
                KeyboardThemeColorsEditor(
                    theme = activeTheme,
                    preset = activePreset,
                    isSoftware = activePreviewPage == 1,
                    onThemeChanged = ::updateActiveTheme
                )
            } else {
                KeyboardThemeKeysEditor(
                    theme = activeTheme,
                    preset = activePreset,
                    isSoftware = activePreviewPage == 1,
                    onThemeChanged = ::updateActiveTheme
                )
            }
        }
    }

    exportTheme?.let { theme ->
        KeyboardThemeExportDialog(
            theme = theme,
            onDismiss = { exportTheme = null }
        )
    }
    if (showImportDialog) {
        KeyboardThemeImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { importedTheme ->
                showImportDialog = false
                applyImportedTheme(importedTheme)
            }
        )
    }
    if (showSaveAsDialog) {
        KeyboardThemeSaveAsDialog(
            initialName = activeTheme.name,
            onDismiss = { showSaveAsDialog = false },
            onSave = { name ->
                showSaveAsDialog = false
                saveActiveThemeAs(name)
            }
        )
    }
}

@Composable
private fun KeyboardThemePresetCard(
    preset: KeyboardThemePreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(176.dp)
            .height(76.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 0.9f else 0.45f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color(preset.accent) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyboardThemeMiniPreview(preset)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                if (selected) {
                    Text(
                        text = stringResource(R.string.keyboard_theme_selected),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(preset.accent),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardThemeExportDialog(
    theme: KeyboardThemePreset,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val exportString = remember(theme) {
        SettingsManager.keyboardThemeToJsonString(theme.toSettingsTheme())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export theme") },
        text = {
            OutlinedTextField(
                value = exportString,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(exportString))
                    onDismiss()
                }
            ) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun KeyboardThemeImportDialog(
    onDismiss: () -> Unit,
    onImport: (KeyboardThemePreset) -> Unit
) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    label = { Text("Theme string") }
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val imported = SettingsManager.keyboardThemeFromJsonString(value)
                    if (imported == null) {
                        error = "Invalid theme string"
                    } else {
                        onImport(imported.toKeyboardThemePreset("Imported"))
                    }
                }
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun KeyboardThemeSaveAsDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName.takeUnless { it == "Custom" } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save copy as") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Theme name") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = { onSave(name) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun KeyboardThemeColorsEditor(
    theme: KeyboardThemePreset,
    preset: KeyboardThemePreset,
    isSoftware: Boolean,
    onThemeChanged: (KeyboardThemePreset) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isSoftware) 672.dp else 504.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        item {
            KeyboardThemeColorRow(
                label = stringResource(R.string.keyboard_theme_background),
                color = theme.background,
                presetColor = preset.background,
                onColorChanged = { onThemeChanged(theme.copy(background = it)) }
            )
        }
        item {
            KeyboardThemeColorRow(
                label = stringResource(R.string.keyboard_theme_dividers),
                color = theme.divider,
                presetColor = preset.divider,
                onColorChanged = { onThemeChanged(theme.copy(divider = it)) }
            )
        }
        item {
            KeyboardThemeColorRow(
                label = stringResource(R.string.keyboard_theme_normal_keys),
                color = theme.normalKey,
                presetColor = preset.normalKey,
                onColorChanged = { onThemeChanged(theme.copy(normalKey = it, suggestion = it)) }
            )
        }
        item {
            KeyboardThemeColorRow(
                label = stringResource(R.string.keyboard_theme_special_keys),
                color = theme.specialKey,
                presetColor = preset.specialKey,
                onColorChanged = { onThemeChanged(theme.copy(specialKey = it, statusBarButton = it)) }
            )
        }
        item {
            KeyboardThemeColorRow(
                label = stringResource(R.string.keyboard_theme_text_icons),
                color = theme.textAndIcons,
                presetColor = preset.textAndIcons,
                onColorChanged = { onThemeChanged(theme.copy(textAndIcons = it)) }
            )
        }
        item {
            KeyboardThemeColorRow(
                label = "Suggestions",
                color = theme.suggestion,
                presetColor = preset.suggestion,
                onColorChanged = { onThemeChanged(theme.copy(suggestion = it)) }
            )
        }
        item {
            KeyboardThemeColorRow(
                label = "Status bar buttons",
                color = theme.statusBarButton,
                presetColor = preset.statusBarButton,
                onColorChanged = { onThemeChanged(theme.copy(statusBarButton = it)) }
            )
        }
        item {
            KeyboardThemeColorRow(
                label = "Cursor swipe",
                color = theme.cursorSwipe,
                presetColor = preset.cursorSwipe,
                onColorChanged = { onThemeChanged(theme.copy(cursorSwipe = it)) }
            )
        }
        if (isSoftware) {
            item {
                KeyboardThemeColorRow(
                    label = "Key popup",
                    color = theme.keyPopup,
                    presetColor = preset.keyPopup,
                    onColorChanged = { onThemeChanged(theme.copy(keyPopup = it)) }
                )
            }
            item {
                KeyboardThemeColorRow(
                    label = "Selected popup key",
                    color = theme.keyPopupSelected,
                    presetColor = preset.keyPopupSelected,
                    onColorChanged = { onThemeChanged(theme.copy(keyPopupSelected = it)) }
                )
            }
        }
        item {
            KeyboardThemeColorRow(
                label = stringResource(R.string.keyboard_theme_led_inactive),
                color = theme.ledInactive,
                presetColor = preset.ledInactive,
                onColorChanged = { onThemeChanged(theme.copy(ledInactive = it)) }
            )
        }
        item {
            KeyboardThemeColorRow(
                label = stringResource(R.string.keyboard_theme_led_active),
                color = theme.ledActive,
                presetColor = preset.ledActive,
                onColorChanged = { onThemeChanged(theme.copy(ledActive = it)) }
            )
        }
        item {
            KeyboardThemeColorRow(
                label = stringResource(R.string.keyboard_theme_led_locked),
                color = theme.ledLocked,
                presetColor = preset.ledLocked,
                onColorChanged = { onThemeChanged(theme.copy(ledLocked = it)) }
            )
        }
    }
}

@Composable
private fun KeyboardThemeKeysEditor(
    theme: KeyboardThemePreset,
    preset: KeyboardThemePreset,
    isSoftware: Boolean,
    onThemeChanged: (KeyboardThemePreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isSoftware) {
            KeyboardThemeSliderRow(
                label = "Key rounding",
                value = theme.keyCornerRadiusRatio,
                presetValue = preset.keyCornerRadiusRatio,
                valueRange = 0f..0.35f,
                onValueChanged = { onThemeChanged(theme.copy(keyCornerRadiusRatio = it)) }
            )
        }
        KeyboardThemeSliderRow(
            label = "Suggestions / variations rounding",
            value = theme.chromeCornerRadiusRatio,
            presetValue = preset.chromeCornerRadiusRatio,
            valueRange = 0f..0.35f,
            onValueChanged = { onThemeChanged(theme.copy(chromeCornerRadiusRatio = it)) }
        )
        if (isSoftware) {
            KeyboardThemeSwitchRow(
                label = "Show LEDs",
                checked = theme.showLeds,
                presetChecked = preset.showLeds,
                onCheckedChanged = { onThemeChanged(theme.copy(showLeds = it)) }
            )
            KeyboardThemeSliderRow(
                label = "Key height",
                value = theme.keyHeightScale,
                presetValue = preset.keyHeightScale,
                valueRange = 0.72f..1.45f,
                onValueChanged = { onThemeChanged(theme.copy(keyHeightScale = it)) }
            )
            KeyboardThemeSliderRow(
                label = "Key width",
                value = theme.keyWidthScale,
                presetValue = preset.keyWidthScale,
                valueRange = 0.72f..1.12f,
                onValueChanged = { onThemeChanged(theme.copy(keyWidthScale = it)) }
            )
            KeyboardThemeSliderRow(
                label = "Row spacing",
                value = theme.rowGapScale,
                presetValue = preset.rowGapScale,
                valueRange = 0f..2f,
                onValueChanged = { onThemeChanged(theme.copy(rowGapScale = it)) }
            )
            KeyboardThemeSwitchRow(
                label = "Distribute spacing",
                checked = theme.distributeHorizontalSpacing,
                presetChecked = preset.distributeHorizontalSpacing,
                onCheckedChanged = { onThemeChanged(theme.copy(distributeHorizontalSpacing = it)) }
            )
            KeyboardThemeSwitchRow(
                label = "Ortholinear",
                checked = theme.ortholinear,
                presetChecked = preset.ortholinear,
                onCheckedChanged = { onThemeChanged(theme.copy(ortholinear = it)) }
            )
        }
    }
}

@Composable
private fun KeyboardThemeMiniPreview(preset: KeyboardThemePreset) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .height(42.dp)
            .background(Color(preset.background), MaterialTheme.shapes.extraSmall)
            .border(1.dp, Color(preset.divider), MaterialTheme.shapes.extraSmall)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .weight(1f)
                        .background(Color(preset.normalKey), MaterialTheme.shapes.extraSmall)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .weight(1f)
                        .background(Color(preset.normalKey), MaterialTheme.shapes.extraSmall)
                )
            }
            Box(
                modifier = Modifier
                    .height(18.dp)
                    .weight(1f)
                    .background(Color(preset.specialKey), MaterialTheme.shapes.extraSmall)
            )
        }
    }
}

@Composable
private fun KeyboardThemePreviewCarousel(
    theme: KeyboardThemePreset,
    currentPage: Int,
    pageContent: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color(theme.background),
        border = BorderStroke(1.dp, Color(theme.divider))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pageContent()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (currentPage == index) 8.dp else 6.dp)
                            .background(
                                color = if (currentPage == index) Color(theme.accent) else Color(theme.divider),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareKeyboardThemePreview(theme: KeyboardThemePreset) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        factory = { context -> createHardwareKeyboardThemePreviewView(context, theme) },
        update = { view -> updateHardwareKeyboardThemePreviewView(view, theme) }
    )
}

@Composable
private fun PreviewHardwareSuggestion(label: String, theme: KeyboardThemePreset, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(42.dp)
            .background(Color(theme.background)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color(theme.textAndIcons),
            maxLines = 1
        )
    }
}

@Composable
private fun PreviewHardwareSymbolKey(label: String, theme: KeyboardThemePreset, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreviewVerticalDivider(theme)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(theme.textAndIcons),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PreviewHardwareActionKey(label: String, theme: KeyboardThemePreset, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(44.dp)
            .padding(horizontal = 2.dp)
            .background(Color(theme.specialKey), MaterialTheme.shapes.small)
            .border(1.dp, Color(theme.divider), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = Color(theme.textAndIcons),
            maxLines = 1
        )
    }
}

@Composable
private fun PreviewVerticalDivider(theme: KeyboardThemePreset) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(Color(theme.divider))
    )
}

@Composable
private fun PreviewLedStrip(color: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(6.dp)
            .background(Color(color), MaterialTheme.shapes.extraSmall)
    )
}

private fun createHardwareKeyboardThemePreviewView(
    context: android.content.Context,
    theme: KeyboardThemePreset
): LinearLayout {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(theme.background)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    val suggestionsBar = FullSuggestionsBar(context).apply {
        themeOverride = theme.toKeyboardThemeColors()
    }
    root.addView(suggestionsBar.ensureView())
    suggestionsBar.showPreview(listOf("Werk", "Team", "Park"))

    val registry = StatusBarButtonRegistry()
    val variationBar = VariationBarView(context, buttonRegistry = registry).apply {
        themeOverride = theme.toKeyboardThemeColors()
    }
    root.addView(variationBar.ensureView())
    variationBar.showVariations(
        StatusBarController.StatusSnapshot(
            capsLockEnabled = false,
            shiftPhysicallyPressed = false,
            shiftOneShot = false,
            ctrlLatchActive = true,
            ctrlPhysicallyPressed = false,
            ctrlOneShot = false,
            ctrlLatchFromNavMode = true,
            altLatchActive = false,
            altPhysicallyPressed = false,
            altOneShot = false,
            symPage = 1,
            clipboardCount = 2,
            variations = listOf("@", "\"", ":", "!", "?", ",", ".")
        ),
        inputConnection = null
    )

    val leds = LedStatusView(context).apply {
        themeOverride = theme.toKeyboardThemeColors()
    }
    root.addView(leds.ensureView())
    leds.update(
        StatusBarController.StatusSnapshot(
            capsLockEnabled = false,
            shiftPhysicallyPressed = true,
            shiftOneShot = false,
            ctrlLatchActive = true,
            ctrlPhysicallyPressed = false,
            ctrlOneShot = false,
            ctrlLatchFromNavMode = true,
            altLatchActive = false,
            altPhysicallyPressed = false,
            altOneShot = false,
            symPage = 2
        )
    )
    root.setTag(R.id.keyboard_theme_preview_suggestions_bar, suggestionsBar)
    root.setTag(R.id.keyboard_theme_preview_variation_bar, variationBar)
    root.setTag(R.id.keyboard_theme_preview_led_view, leds)
    return root
}

private fun updateHardwareKeyboardThemePreviewView(view: android.view.View, theme: KeyboardThemePreset) {
    view.setBackgroundColor(theme.background)
    val colors = theme.toKeyboardThemeColors()
    (view.getTag(R.id.keyboard_theme_preview_suggestions_bar) as? FullSuggestionsBar)?.apply {
        themeOverride = colors
        showPreview(listOf("Werk", "Team", "Park"))
    }
    (view.getTag(R.id.keyboard_theme_preview_variation_bar) as? VariationBarView)?.apply {
        themeOverride = colors
        resetVariationsState()
        showVariations(
            StatusBarController.StatusSnapshot(
                capsLockEnabled = false,
                shiftPhysicallyPressed = false,
                shiftOneShot = false,
                ctrlLatchActive = true,
                ctrlPhysicallyPressed = false,
                ctrlOneShot = false,
                ctrlLatchFromNavMode = true,
                altLatchActive = false,
                altPhysicallyPressed = false,
                altOneShot = false,
                symPage = 1,
                clipboardCount = 2,
                variations = listOf("@", "\"", ":", "!", "?", ",", ".")
            ),
            inputConnection = null
        )
    }
    (view.getTag(R.id.keyboard_theme_preview_led_view) as? LedStatusView)?.apply {
        themeOverride = colors
        update(
            StatusBarController.StatusSnapshot(
                capsLockEnabled = false,
                shiftPhysicallyPressed = true,
                shiftOneShot = false,
                ctrlLatchActive = true,
                ctrlPhysicallyPressed = false,
                ctrlOneShot = false,
                ctrlLatchFromNavMode = true,
                altLatchActive = false,
                altPhysicallyPressed = false,
                altOneShot = false,
                symPage = 2
            )
        )
    }
}

@Composable
private fun VirtualKeyboardThemePreview(theme: KeyboardThemePreset) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height((260f + 58f * (theme.keyHeightScale.coerceIn(0.72f, 1.45f) - 1f)).dp),
        factory = { context -> createVirtualKeyboardThemePreviewView(context, theme) },
        update = { view -> updateVirtualKeyboardThemePreviewView(view, theme) }
    )
}

private fun createVirtualKeyboardThemePreviewView(
    context: android.content.Context,
    theme: KeyboardThemePreset
): LinearLayout {
    val colors = theme.toKeyboardThemeColors()
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(theme.background)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val suggestionsBar = FullSuggestionsBar(context).apply {
        themeOverride = colors
        requireDictionaryForSuggestions = false
    }
    root.addView(suggestionsBar.ensureView())
    suggestionsBar.showPreview(listOf("Werk", "Team", "Park"))

    val variationBar = VariationBarView(context, buttonRegistry = StatusBarButtonRegistry()).apply {
        themeOverride = colors
        forceVariationAreaVisible = true
    }
    root.addView(variationBar.ensureView())
    showVirtualPreviewVariations(variationBar)

    val keyboardView = AospKeyboardView(context).apply {
        layoutName = SettingsManager.getKeyboardLayout(context)
        layoutStyle = softwareKeyboardPreviewLayoutStyle(context)
        shifted = false
        spacebarLabel = "space"
        themeOverride = theme.toAospThemeOverride()
    }
    root.addView(
        keyboardView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(context, 164f * theme.keyHeightScale.coerceIn(0.72f, 1.45f))
        )
    )

    val leds = LedStatusView(context).apply {
        themeOverride = colors
    }
    root.addView(leds.ensureView())
    leds.getView()?.visibility = if (theme.showLeds) android.view.View.VISIBLE else android.view.View.GONE
    leds.update(virtualPreviewLedSnapshot())

    root.setTag(R.id.keyboard_theme_preview_suggestions_bar, suggestionsBar)
    root.setTag(R.id.keyboard_theme_preview_variation_bar, variationBar)
    root.setTag(R.id.keyboard_theme_preview_aosp_keyboard, keyboardView)
    root.setTag(R.id.keyboard_theme_preview_led_view, leds)
    return root
}

private fun updateVirtualKeyboardThemePreviewView(view: android.view.View, theme: KeyboardThemePreset) {
    val root = view as? LinearLayout ?: return
    val colors = theme.toKeyboardThemeColors()
    root.setBackgroundColor(theme.background)
    (root.getTag(R.id.keyboard_theme_preview_suggestions_bar) as? FullSuggestionsBar)?.apply {
        themeOverride = colors
        requireDictionaryForSuggestions = false
        showPreview(listOf("Werk", "Team", "Park"))
    }
    (root.getTag(R.id.keyboard_theme_preview_variation_bar) as? VariationBarView)?.apply {
        themeOverride = colors
        forceVariationAreaVisible = true
        resetVariationsState()
        showVirtualPreviewVariations(this)
    }
    (root.getTag(R.id.keyboard_theme_preview_aosp_keyboard) as? AospKeyboardView)?.apply {
        layoutName = SettingsManager.getKeyboardLayout(context)
        layoutStyle = softwareKeyboardPreviewLayoutStyle(context)
        themeOverride = theme.toAospThemeOverride()
        layoutParams = (layoutParams as? LinearLayout.LayoutParams)?.apply {
            height = dpToPx(context, 164f * theme.keyHeightScale.coerceIn(0.72f, 1.45f))
        } ?: LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(context, 164f * theme.keyHeightScale.coerceIn(0.72f, 1.45f))
        )
    }
    (root.getTag(R.id.keyboard_theme_preview_led_view) as? LedStatusView)?.apply {
        themeOverride = colors
        getView()?.visibility = if (theme.showLeds) android.view.View.VISIBLE else android.view.View.GONE
        update(virtualPreviewLedSnapshot())
    }
}

private fun softwareKeyboardPreviewLayoutStyle(context: android.content.Context): AospKeyboardView.SoftwareLayoutStyle =
    when (SettingsManager.getSoftwareKeyboardLayoutStyle(context)) {
        SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT -> AospKeyboardView.SoftwareLayoutStyle.COMPACT
        SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO -> AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO
        SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI -> AospKeyboardView.SoftwareLayoutStyle.FULL_ANSI
        SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO -> AospKeyboardView.SoftwareLayoutStyle.FULL_ISO
    }

private fun showVirtualPreviewVariations(variationBar: VariationBarView) {
    variationBar.showVariations(
        StatusBarController.StatusSnapshot(
            capsLockEnabled = false,
            shiftPhysicallyPressed = false,
            shiftOneShot = false,
            ctrlLatchActive = false,
            ctrlPhysicallyPressed = false,
            ctrlOneShot = false,
            ctrlLatchFromNavMode = false,
            altLatchActive = false,
            altPhysicallyPressed = false,
            altOneShot = false,
            symPage = 0,
            clipboardCount = 2,
            variations = listOf("@", "\"", ":", "!", "?", ",", ".")
        ),
        inputConnection = null
    )
}

private fun virtualPreviewLedSnapshot(): StatusBarController.StatusSnapshot =
    StatusBarController.StatusSnapshot(
        capsLockEnabled = false,
        shiftPhysicallyPressed = true,
        shiftOneShot = false,
        ctrlLatchActive = false,
        ctrlPhysicallyPressed = false,
        ctrlOneShot = false,
        ctrlLatchFromNavMode = false,
        altLatchActive = false,
        altPhysicallyPressed = false,
        altOneShot = false,
        symPage = 0
    )

private fun dpToPx(context: android.content.Context, dp: Float): Int {
    return android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP,
        dp,
        context.resources.displayMetrics
    ).toInt()
}

@Composable
private fun PreviewNormalKey(label: String, theme: KeyboardThemePreset, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(42.dp)
            .background(Color(theme.normalKey), MaterialTheme.shapes.extraSmall)
            .border(1.dp, Color(theme.divider), MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color(theme.textAndIcons),
            maxLines = 1
        )
    }
}

@Composable
private fun PreviewSpecialKey(label: String, theme: KeyboardThemePreset, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(42.dp)
            .background(Color(theme.specialKey), MaterialTheme.shapes.extraSmall)
            .border(1.dp, Color(theme.divider), MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color(theme.textAndIcons),
            maxLines = 1
        )
    }
}

@Composable
private fun PreviewLed(color: Int) {
    Box(
        modifier = Modifier
            .size(width = 12.dp, height = 24.dp)
            .background(Color(color), MaterialTheme.shapes.extraSmall)
    )
}

@Composable
private fun KeyboardThemeColorRow(
    label: String,
    color: Int,
    presetColor: Int,
    onColorChanged: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyboardThemeSwatchButton(color = color, onColorChanged = onColorChanged)
                Text(
                    text = color.toHexColorLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(
                    onClick = { onColorChanged(presetColor) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = stringResource(R.string.keyboard_theme_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardThemeSliderRow(
    label: String,
    value: Float,
    presetValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChanged: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(
                    onClick = { onValueChanged(presetValue) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = stringResource(R.string.keyboard_theme_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { onValueChanged(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
                valueRange = valueRange
            )
        }
    }
}

@Composable
private fun KeyboardThemeSwitchRow(
    label: String,
    checked: Boolean,
    presetChecked: Boolean,
    onCheckedChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(
                onClick = { onCheckedChanged(presetChecked) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = stringResource(R.string.keyboard_theme_reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChanged
            )
        }
    }
}


@Composable
private fun KeyboardThemeSwatchButton(
    color: Int,
    onColorChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var pickerColor by remember(color) { mutableStateOf(color) }
    val shape = MaterialTheme.shapes.small
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(color), shape)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), shape)
                .clickable {
                    pickerColor = color
                    expanded = true
                }
        )
        if (expanded) {
            KeyboardThemeColorPickerDialog(
                initialColor = pickerColor,
                onDismiss = { expanded = false },
                onColorSelected = {
                    expanded = false
                    onColorChanged(it)
                },
                onPreviewColorChanged = { pickerColor = it }
            )
        }
    }
}

@Composable
private fun KeyboardThemeColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
    onPreviewColorChanged: (Int) -> Unit
) {
    var color by remember(initialColor) { mutableStateOf(initialColor) }
    val hsv = remember(color) {
        FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
    }
    var hue by remember(initialColor) { mutableStateOf(hsv[0]) }
    var saturation by remember(initialColor) { mutableStateOf(hsv[1]) }
    var value by remember(initialColor) { mutableStateOf(hsv[2]) }

    fun updateColor(newHue: Float = hue, newSaturation: Float = saturation, newValue: Float = value) {
        hue = newHue
        saturation = newSaturation
        value = newValue
        color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        onPreviewColorChanged(color)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    keyboardThemeSwatches().take(18).forEach { swatch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    color = swatch
                                    AndroidColor.colorToHSV(swatch, hsv)
                                    updateColor(hsv[0], hsv[1], hsv[2])
                                }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(20.dp),
                                shape = MaterialTheme.shapes.extraSmall,
                                color = Color(swatch)
                            ) {}
                            Text(
                                text = swatch.toHexColorLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1.35f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeyboardThemeColorWheel(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onColorChanged = { newHue, newSaturation, newValue ->
                            updateColor(newHue, newSaturation, newValue)
                        }
                    )
                    Text(color.toHexColorLabel(), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { onColorSelected(color) }) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardThemeColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChanged: (Float, Float, Float) -> Unit
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    fun handlePosition(offset: Offset) {
        val side = min(canvasSize.width, canvasSize.height)
        if (side <= 0f) return
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val radius = side / 2f
        val ringStroke = radius * 0.18f
        val ringCenterRadius = radius - ringStroke / 2f
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val distance = sqrt(dx * dx + dy * dy)
        val squareSide = radius * 1.18f
        val squareLeft = center.x - squareSide / 2f
        val squareTop = center.y - squareSide / 2f
        val squareRight = squareLeft + squareSide
        val squareBottom = squareTop + squareSide

        if (distance in (ringCenterRadius - ringStroke)..(ringCenterRadius + ringStroke)) {
            val angle = (atan2(dy, dx) * 180f / PI.toFloat() + 360f) % 360f
            onColorChanged(angle, saturation, value)
            return
        }

        if (offset.x in squareLeft..squareRight && offset.y in squareTop..squareBottom) {
            val newSaturation = ((offset.x - squareLeft) / squareSide).coerceIn(0f, 1f)
            val newValue = (1f - ((offset.y - squareTop) / squareSide)).coerceIn(0f, 1f)
            onColorChanged(hue, newSaturation, newValue)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .onSizeChanged {
                canvasSize = Size(it.width.toFloat(), it.height.toFloat())
            }
            .pointerInput(hue, saturation, value, canvasSize) {
                detectTapGestures { offset -> handlePosition(offset) }
            }
            .pointerInput(hue, saturation, value, canvasSize) {
                detectDragGestures { change, _ -> handlePosition(change.position) }
            }
    ) {
        val side = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = side / 2f
        val ringStroke = radius * 0.18f
        val ringRadius = radius - ringStroke / 2f
        val squareSide = radius * 1.18f
        val squareLeft = center.x - squareSide / 2f
        val squareTop = center.y - squareSide / 2f
        val squareSize = Size(squareSide, squareSide)
        val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))

        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red
                ),
                center = center
            ),
            radius = ringRadius,
            center = center,
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
        )

        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, hueColor),
                startX = squareLeft,
                endX = squareLeft + squareSide
            ),
            topLeft = Offset(squareLeft, squareTop),
            size = squareSize
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = squareTop,
                endY = squareTop + squareSide
            ),
            topLeft = Offset(squareLeft, squareTop),
            size = squareSize
        )

        val ringAngle = hue * PI.toFloat() / 180f
        val ringThumb = Offset(
            x = center.x + cos(ringAngle) * ringRadius,
            y = center.y + sin(ringAngle) * ringRadius
        )
        drawCircle(Color.White, radius = 11.dp.toPx(), center = ringThumb)
        drawCircle(Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))), radius = 8.dp.toPx(), center = ringThumb)

        val squareThumb = Offset(
            x = squareLeft + saturation.coerceIn(0f, 1f) * squareSide,
            y = squareTop + (1f - value.coerceIn(0f, 1f)) * squareSide
        )
        drawCircle(Color.White, radius = 10.dp.toPx(), center = squareThumb)
        drawCircle(Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))), radius = 7.dp.toPx(), center = squareThumb)
    }
}

private fun Int.toHexColorLabel(): String = "#${toUInt().toString(16).uppercase().takeLast(6)}"
