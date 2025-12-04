package it.palsoftware.pastiera

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.R
import java.util.Locale
import android.content.res.AssetManager

/**
 * Data class representing a custom input style entry.
 */
private data class CustomInputStyle(
    val locale: String,
    val layout: String,
    val displayName: String
)

/**
 * Settings screen for managing custom input styles (additional subtypes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomInputStylesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Load custom input styles
    var inputStyles by remember {
        mutableStateOf(loadCustomInputStyles(context))
    }
    
    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteConfirmStyle by remember { mutableStateOf<CustomInputStyle?>(null) }
    
    // Snackbar host state
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.custom_input_styles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.custom_input_styles_add)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Open IME Settings button (to enable keyboard)
            OutlinedButton(
                onClick = {
                    openImePicker(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.custom_input_styles_open_ime_picker))
            }
            
            // List of custom input styles
            if (inputStyles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.custom_input_styles_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(inputStyles, key = { "${it.locale}:${it.layout}" }) { style ->
                        CustomInputStyleItem(
                            style = style,
                            onDelete = {
                                deleteConfirmStyle = style
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Add dialog
    if (showAddDialog) {
        AddCustomInputStyleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { locale, layout ->
                val duplicateErrorMsg = context.getString(R.string.custom_input_styles_duplicate_error)
                if (addCustomInputStyle(context, locale, layout)) {
                    inputStyles = loadCustomInputStyles(context)
                    showAddDialog = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            "Input style added: ${getLocaleDisplayName(locale)} - $layout"
                        )
                    }
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(duplicateErrorMsg)
                    }
                }
            }
        )
    }
    
    // Delete confirmation dialog
    deleteConfirmStyle?.let { style ->
        AlertDialog(
            onDismissRequest = { deleteConfirmStyle = null },
            title = { Text(stringResource(R.string.custom_input_styles_delete_confirm_title)) },
            text = { Text(stringResource(R.string.custom_input_styles_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeCustomInputStyle(context, style.locale, style.layout)
                        inputStyles = loadCustomInputStyles(context)
                        deleteConfirmStyle = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Input style deleted")
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmStyle = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Item in the list of custom input styles.
 */
@Composable
private fun CustomInputStyleItem(
    style: CustomInputStyle,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${style.locale} - ${style.layout}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.custom_input_styles_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Dialog for adding a new custom input style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomInputStyleDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    
    var selectedLocale by remember { mutableStateOf<String?>(null) }
    var selectedLayout by remember { mutableStateOf<String?>(null) }
    
    val availableLayouts = remember {
        LayoutMappingRepository.getAvailableLayouts(context.assets, context).sorted()
    }
    
    // Get available locales based on dictionary availability
    val availableLocales = remember {
        getLocalesWithDictionary(context).sorted()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_input_styles_add_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Locale selection
                Text(
                    text = stringResource(R.string.custom_input_styles_select_locale),
                    style = MaterialTheme.typography.labelLarge
                )
                var expandedLocale by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedLocale,
                    onExpandedChange = { expandedLocale = !expandedLocale }
                ) {
                    OutlinedTextField(
                        value = selectedLocale ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLocale) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedLocale,
                        onDismissRequest = { expandedLocale = false }
                    ) {
                        availableLocales.forEach { locale ->
                            DropdownMenuItem(
                                text = { Text("$locale (${getLocaleDisplayName(locale)})") },
                                onClick = {
                                    selectedLocale = locale
                                    expandedLocale = false
                                }
                            )
                        }
                    }
                }
                
                // Layout selection
                Text(
                    text = stringResource(R.string.custom_input_styles_select_layout),
                    style = MaterialTheme.typography.labelLarge
                )
                var expandedLayout by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedLayout,
                    onExpandedChange = { expandedLayout = !expandedLayout }
                ) {
                    OutlinedTextField(
                        value = selectedLayout ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Keyboard Layout") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLayout) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedLayout,
                        onDismissRequest = { expandedLayout = false }
                    ) {
                        availableLayouts.forEach { layout ->
                            DropdownMenuItem(
                                text = { Text(layout) },
                                onClick = {
                                    selectedLayout = layout
                                    expandedLayout = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val locale = selectedLocale
                    val layout = selectedLayout
                    if (locale != null && layout != null) {
                        onSave(locale, layout)
                    }
                },
                enabled = selectedLocale != null && selectedLayout != null
            ) {
                Text(stringResource(R.string.custom_input_styles_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.custom_input_styles_cancel))
            }
        }
    )
}

/**
 * Loads custom input styles from preferences.
 */
private fun loadCustomInputStyles(context: Context): List<CustomInputStyle> {
    val prefString = SettingsManager.getCustomInputStyles(context)
    if (prefString.isBlank()) {
        return emptyList()
    }
    
    val styles = mutableListOf<CustomInputStyle>()
    val entries = prefString.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    
    for (entry in entries) {
        val parts = entry.split(":").map { it.trim() }
        if (parts.size >= 2) {
            val locale = parts[0]
            val layout = parts[1]
            val displayName = "${getLocaleDisplayName(locale)} - $layout"
            styles.add(CustomInputStyle(locale, layout, displayName))
        }
    }
    
    return styles
}

/**
 * Adds a custom input style.
 */
private fun addCustomInputStyle(context: Context, locale: String, layout: String): Boolean {
    val currentStyles = SettingsManager.getCustomInputStyles(context)
    val entries = if (currentStyles.isBlank()) {
        emptyList()
    } else {
        currentStyles.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    // Check for duplicates
    val newEntry = "$locale:$layout"
    if (entries.any { it.startsWith("$locale:$layout") }) {
        return false
    }
    
    // Add new entry
    val newStyles = if (entries.isEmpty()) {
        newEntry
    } else {
        "$currentStyles;$newEntry"
    }
    
    SettingsManager.setCustomInputStyles(context, newStyles)
    return true
}

/**
 * Removes a custom input style.
 */
private fun removeCustomInputStyle(context: Context, locale: String, layout: String) {
    val currentStyles = SettingsManager.getCustomInputStyles(context)
    val entries = currentStyles.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    val filtered = entries.filterNot { it.startsWith("$locale:$layout") }
    val newStyles = filtered.joinToString(";")
    SettingsManager.setCustomInputStyles(context, newStyles)
}

/**
 * Gets display name for a locale.
 */
private fun getLocaleDisplayName(locale: String): String {
    return try {
        val parts = locale.split("_")
        val lang = parts[0]
        val country = if (parts.size > 1) parts[1] else ""
        val localeObj = if (country.isNotEmpty()) {
            Locale(lang, country)
        } else {
            Locale(lang)
        }
        localeObj.getDisplayName(Locale.ENGLISH)
    } catch (e: Exception) {
        locale
    }
}

/**
 * Gets list of locales that have dictionary files available.
 * Checks both serialized (.dict) and JSON (.json) formats.
 */
private fun getLocalesWithDictionary(context: Context): List<String> {
    val localesWithDict = mutableSetOf<String>()
    
    try {
        val assets = context.assets
        
        // Check serialized dictionaries first
        try {
            val serializedFiles = assets.list("common/dictionaries_serialized")
            serializedFiles?.forEach { fileName ->
                if (fileName.endsWith("_base.dict")) {
                    val langCode = fileName.removeSuffix("_base.dict")
                    // Map language code to common locale variants
                    localesWithDict.addAll(getLocaleVariantsForLanguage(langCode))
                }
            }
        } catch (e: Exception) {
            // If serialized directory doesn't exist, try JSON
        }
        
        // Also check JSON dictionaries (fallback)
        try {
            val jsonFiles = assets.list("common/dictionaries")
            jsonFiles?.forEach { fileName ->
                if (fileName.endsWith("_base.json") && fileName != "user_defaults.json") {
                    val langCode = fileName.removeSuffix("_base.json")
                    // Map language code to common locale variants
                    localesWithDict.addAll(getLocaleVariantsForLanguage(langCode))
                }
            }
        } catch (e: Exception) {
            // If dictionaries directory doesn't exist, continue
        }
    } catch (e: Exception) {
        android.util.Log.e("CustomInputStyles", "Error checking dictionaries", e)
    }
    
    return localesWithDict.toList()
}

/**
 * Maps a language code (e.g., "en", "it") to common locale variants.
 */
private fun getLocaleVariantsForLanguage(langCode: String): List<String> {
    return when (langCode.lowercase()) {
        "en" -> listOf("en_US", "en_GB", "en_CA", "en_AU")
        "it" -> listOf("it_IT", "it_CH")
        "fr" -> listOf("fr_FR", "fr_CA", "fr_CH", "fr_BE")
        "de" -> listOf("de_DE", "de_AT", "de_CH")
        "es" -> listOf("es_ES", "es_MX", "es_AR", "es_CO")
        "pt" -> listOf("pt_PT", "pt_BR")
        "pl" -> listOf("pl_PL")
        "ru" -> listOf("ru_RU")
        else -> listOf("${langCode}_${langCode.uppercase()}") // Generic fallback
    }
}

/**
 * Opens the system IME picker (selector) to choose input method.
 */
private fun openImePicker(context: Context) {
    try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    } catch (e: Exception) {
        android.util.Log.e("CustomInputStyles", "Error opening IME picker", e)
        android.widget.Toast.makeText(
            context,
            "Error opening input method picker",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

