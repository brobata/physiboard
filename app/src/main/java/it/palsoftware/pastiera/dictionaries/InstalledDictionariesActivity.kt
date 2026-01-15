package it.palsoftware.pastiera.dictionaries

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import java.io.File
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.launch
import android.provider.OpenableColumns
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import it.palsoftware.pastiera.core.suggestions.DictionaryIndex

/**
 * Activity that shows the list of serialized dictionaries bundled with the app
 * and available for download from the online repository.
 */
class InstalledDictionariesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            overridePendingTransition(R.anim.slide_in_from_right, 0)
        }
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                InstalledDictionariesScreen(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    onBack = { finish() }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_out_to_right)
    }
}

/**
 * Unified dictionary item that can represent both installed and available online dictionaries.
 */
internal data class UnifiedDictionaryItem(
    val languageCode: String,
    val displayName: String,
    val fileName: String,
    val isInstalled: Boolean,
    val source: DictionarySource,
    val onlineItem: DictionaryItem? = null,
    val downloadProgress: Pair<Long, Long>? = null,
    val isDownloading: Boolean = false
)

internal enum class DictionarySource {
    Asset,
    Imported,
    Online
}

@Composable
fun InstalledDictionariesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var installedDictionaries by remember { mutableStateOf(loadSerializedDictionaries(context)) }
    var onlineManifest by remember { mutableStateOf<DictionaryManifest?>(null) }
    var isLoadingManifest by remember { mutableStateOf(false) }
    var manifestError by remember { mutableStateOf<String?>(null) }
    var downloadingItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadProgress by remember { mutableStateOf<Map<String, Pair<Long, Long>>>(emptyMap()) }
    var dictionaryToDelete by remember { mutableStateOf<UnifiedDictionaryItem?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Load online manifest on first launch
    LaunchedEffect(Unit) {
        Log.d("InstalledDictionaries", "Starting manifest load...")
        isLoadingManifest = true
        manifestError = null
        DictionaryRepositoryManager.fetchManifest()
            .onSuccess { manifest ->
                Log.d("InstalledDictionaries", "Manifest loaded successfully: ${manifest.items.size} items")
                onlineManifest = manifest
                isLoadingManifest = false
            }
            .onFailure { error ->
                manifestError = error.message
                isLoadingManifest = false
                Log.e("InstalledDictionaries", "Failed to load manifest: ${error.message}", error)
            }
    }
    
    // Merge installed and online dictionaries
    val unifiedDictionaries = remember(installedDictionaries, onlineManifest, downloadingItems, downloadProgress) {
        mergeDictionaries(installedDictionaries, onlineManifest, downloadingItems, downloadProgress)
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val result = importDictionaryFromSaf(context, uri)
                val message = when (result) {
                    is ImportResult.Success -> {
                        installedDictionaries = loadSerializedDictionaries(context)
                        context.getString(
                            R.string.installed_dictionaries_import_success,
                            result.fileName
                        )
                    }
                    is ImportResult.InvalidName -> context.getString(R.string.installed_dictionaries_import_invalid_name)
                    is ImportResult.InvalidFormat -> context.getString(R.string.installed_dictionaries_import_invalid_format)
                    is ImportResult.CopyError -> context.getString(R.string.installed_dictionaries_import_failed)
                    ImportResult.UnsupportedUri -> context.getString(R.string.installed_dictionaries_import_failed)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    
    fun refreshManifest() {
        Log.d("InstalledDictionaries", "Refreshing manifest...")
        coroutineScope.launch {
            isLoadingManifest = true
            manifestError = null
            DictionaryRepositoryManager.fetchManifest()
                .onSuccess { manifest ->
                    Log.d("InstalledDictionaries", "Manifest refreshed: ${manifest.items.size} items")
                    onlineManifest = manifest
                    isLoadingManifest = false
                }
                .onFailure { error ->
                    manifestError = error.message
                    isLoadingManifest = false
                    Log.e("InstalledDictionaries", "Failed to refresh manifest: ${error.message}", error)
                }
        }
    }
    
    fun downloadDictionary(item: DictionaryItem) {
        if (downloadingItems.contains(item.id)) return
        
        coroutineScope.launch {
            downloadingItems = downloadingItems + item.id
            downloadProgress = downloadProgress - item.id
            
            val result = DictionaryRepositoryManager.downloadDictionary(
                context = context,
                item = item,
                onProgress = { downloaded, total ->
                    downloadProgress = downloadProgress + (item.id to (downloaded to total))
                }
            )
            
            downloadingItems = downloadingItems - item.id
            downloadProgress = downloadProgress - item.id
            
            val message = when (result) {
                is DownloadResult.Success -> {
                    installedDictionaries = loadSerializedDictionaries(context)
                    context.getString(R.string.installed_dictionaries_download_success, item.name)
                }
                DownloadResult.NetworkError -> context.getString(R.string.installed_dictionaries_download_network_error)
                DownloadResult.InvalidFormat -> context.getString(R.string.installed_dictionaries_download_invalid_format)
                DownloadResult.HashMismatch -> context.getString(R.string.installed_dictionaries_download_hash_mismatch)
                DownloadResult.CopyError -> context.getString(R.string.installed_dictionaries_download_failed)
            }
            
            snackbarHostState.showSnackbar(message)
        }
    }
    
    fun uninstallDictionary(dictionary: UnifiedDictionaryItem) {
        coroutineScope.launch {
            val result = deleteDictionaryFile(context, dictionary)
            val message = when (result) {
                is UninstallResult.Success -> {
                    installedDictionaries = loadSerializedDictionaries(context)
                    context.getString(R.string.installed_dictionaries_uninstall_success, dictionary.displayName)
                }
                UninstallResult.NotFound -> context.getString(R.string.installed_dictionaries_uninstall_not_found)
                UninstallResult.CannotDeleteAsset -> context.getString(R.string.installed_dictionaries_uninstall_cannot_delete_asset)
                UninstallResult.DeleteError -> context.getString(R.string.installed_dictionaries_uninstall_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                        text = stringResource(R.string.installed_dictionaries_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                    if (isLoadingManifest) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { refreshManifest() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.installed_dictionaries_refresh)
                            )
                        }
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.installed_dictionaries_import)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (unifiedDictionaries.isEmpty() && !isLoadingManifest) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.installed_dictionaries_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (manifestError != null) {
                        Text(
                            text = stringResource(R.string.installed_dictionaries_manifest_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(unifiedDictionaries, key = { it.fileName }) { dictionary ->
                    UnifiedDictionaryItem(
                        dictionary = dictionary,
                        onDownload = { onlineItem ->
                            if (onlineItem != null) {
                                downloadDictionary(onlineItem)
                            }
                        },
                        onUninstall = { dict ->
                            dictionaryToDelete = dict
                        }
                    )
                }
            }
        }
    }
    
    // Uninstall confirmation dialog
    dictionaryToDelete?.let { dictionary ->
        AlertDialog(
            onDismissRequest = { dictionaryToDelete = null },
            title = {
                Text(stringResource(R.string.installed_dictionaries_uninstall_confirm_title))
            },
            text = {
                Text(stringResource(R.string.installed_dictionaries_uninstall_confirm_message, dictionary.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        uninstallDictionary(dictionary)
                        dictionaryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.installed_dictionaries_uninstall))
                }
            },
            dismissButton = {
                TextButton(onClick = { dictionaryToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun UnifiedDictionaryItem(
    dictionary: UnifiedDictionaryItem,
    onDownload: (DictionaryItem?) -> Unit,
    onUninstall: (UnifiedDictionaryItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dictionary.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(
                            R.string.installed_dictionaries_language_code,
                            dictionary.languageCode.uppercase(Locale.getDefault())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (dictionary.onlineItem != null) {
                        Text(
                            text = stringResource(
                                R.string.installed_dictionaries_size,
                                DictionaryRepositoryManager.formatFileSize(dictionary.onlineItem.bytes)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dictionary.isInstalled) {
                            Text(
                                text = stringResource(R.string.installed_dictionaries_installed_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (dictionary.source == DictionarySource.Imported) {
                            Text(
                                text = stringResource(R.string.installed_dictionaries_imported_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (dictionary.onlineItem != null && !dictionary.isInstalled) {
                            Text(
                                text = stringResource(R.string.installed_dictionaries_available_online),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Uninstall button (only for imported/downloaded dictionaries, not assets)
                    if (dictionary.isInstalled && dictionary.source != DictionarySource.Asset) {
                        IconButton(
                            onClick = { onUninstall(dictionary) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.installed_dictionaries_uninstall),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    // Download button (only for online dictionaries not yet installed)
                    if (dictionary.onlineItem != null && !dictionary.isInstalled) {
                        if (dictionary.isDownloading) {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(onClick = { onDownload(dictionary.onlineItem) }) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = stringResource(R.string.installed_dictionaries_download)
                                )
                            }
                        }
                    }
                }
            }
            if (dictionary.isDownloading && dictionary.downloadProgress != null) {
                val (downloaded, total) = dictionary.downloadProgress
                val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Text(
                    text = stringResource(
                        R.string.installed_dictionaries_download_progress,
                        DictionaryRepositoryManager.formatFileSize(downloaded),
                        DictionaryRepositoryManager.formatFileSize(total)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Merges installed dictionaries with online available dictionaries.
 */
private fun mergeDictionaries(
    installed: List<InstalledDictionary>,
    onlineManifest: DictionaryManifest?,
    downloadingItems: Set<String>,
    downloadProgress: Map<String, Pair<Long, Long>>
): List<UnifiedDictionaryItem> {
    Log.d("InstalledDictionaries", "Merging dictionaries - Installed: ${installed.size}, Online: ${onlineManifest?.items?.size ?: 0}")
    val installedMap = installed.associateBy { it.fileName.lowercase(Locale.getDefault()) }
    val onlineMap = onlineManifest?.items?.associateBy { it.filename.lowercase(Locale.getDefault()) } ?: emptyMap()
    
    Log.d("InstalledDictionaries", "Installed files: ${installedMap.keys.joinToString(", ")}")
    Log.d("InstalledDictionaries", "Online files: ${onlineMap.keys.joinToString(", ")}")
    
    val allFileNames = (installedMap.keys + onlineMap.keys).distinct()
    Log.d("InstalledDictionaries", "Total unique files: ${allFileNames.size}")
    
    return allFileNames.map { fileName ->
        val installed = installedMap[fileName]
        val online = onlineMap[fileName]
        
        val languageCode = installed?.languageCode 
            ?: online?.let { DictionaryRepositoryManager.extractLanguageCode(it.filename) }
            ?: fileName.removeSuffix("_base.dict").removeSuffix(".dict")
        
        val displayName = installed?.displayName
            ?: online?.name
            ?: getLanguageDisplayName(languageCode)
        
        val item = UnifiedDictionaryItem(
            languageCode = languageCode,
            displayName = displayName,
            fileName = online?.filename ?: installed?.fileName ?: fileName,
            isInstalled = installed != null,
            source = installed?.source ?: DictionarySource.Online,
            onlineItem = online,
            downloadProgress = downloadProgress[online?.id],
            isDownloading = online?.id in downloadingItems
        )
        Log.d("InstalledDictionaries", "Merged: ${item.displayName} (installed=${item.isInstalled}, online=${item.onlineItem != null})")
        item
    }.sortedBy { it.displayName.lowercase(Locale.getDefault()) }.also { result ->
        Log.d("InstalledDictionaries", "Final merged list: ${result.size} items")
        result.forEach { item ->
            Log.d("InstalledDictionaries", "  - ${item.displayName}: installed=${item.isInstalled}, hasOnline=${item.onlineItem != null}, canDownload=${item.onlineItem != null && !item.isInstalled}")
        }
    }
}

private data class InstalledDictionary(
    val languageCode: String,
    val displayName: String,
    val fileName: String,
    val source: DictionarySource
)

private fun loadSerializedDictionaries(context: Context): List<InstalledDictionary> {
    return try {
        Log.d("InstalledDictionaries", "Loading installed dictionaries...")
        val assetFiles = context.assets
            .list("common/dictionaries_serialized")
            ?.filter { it.endsWith("_base.dict") }
            ?: emptyList()
        Log.d("InstalledDictionaries", "Found ${assetFiles.size} dictionaries in assets: ${assetFiles.joinToString(", ")}")

        val importedDir = File(context.filesDir, "dictionaries_serialized/custom")
        val importedFiles = importedDir.listFiles { file ->
            file.isFile && file.name.endsWith(".dict")
        }?.map { it.name } ?: emptyList()
        Log.d("InstalledDictionaries", "Found ${importedFiles.size} imported dictionaries: ${importedFiles.joinToString(", ")}")

        val allFiles = (assetFiles.map { DictionarySource.Asset to it } +
                importedFiles.map { DictionarySource.Imported to it })
            .distinctBy { it.second.lowercase(Locale.getDefault()) }

        val result = allFiles.map { (source, fileName) ->
            val languageCode = fileName.removeSuffix("_base.dict").removeSuffix(".dict")
            InstalledDictionary(
                languageCode = languageCode,
                displayName = getLanguageDisplayName(languageCode),
                fileName = fileName,
                source = source
            )
        }.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        
        Log.d("InstalledDictionaries", "Loaded ${result.size} installed dictionaries: ${result.map { "${it.displayName} (${it.fileName})" }.joinToString(", ")}")
        result
    } catch (e: Exception) {
        Log.e("InstalledDictionaries", "Error reading serialized dictionaries", e)
        emptyList()
    }
}

private sealed interface ImportResult {
    data class Success(val fileName: String) : ImportResult
    object InvalidName : ImportResult
    object InvalidFormat : ImportResult
    object CopyError : ImportResult
    object UnsupportedUri : ImportResult
}

private sealed interface UninstallResult {
    data class Success(val fileName: String) : UninstallResult
    object NotFound : UninstallResult
    object CannotDeleteAsset : UninstallResult
    object DeleteError : UninstallResult
}

@OptIn(ExperimentalSerializationApi::class)
private fun importDictionaryFromSaf(context: Context, uri: android.net.Uri): ImportResult {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else null
    } ?: return ImportResult.InvalidName
    if (!name.endsWith(".dict", ignoreCase = true) || !name.contains("_base", ignoreCase = true)) {
        return ImportResult.InvalidName
    }

    val destDir = File(context.filesDir, "dictionaries_serialized/custom").apply { mkdirs() }
    val destFile = File(destDir, name)

    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            // Validate by attempting to deserialize
            validateDictionaryStream(input)
        } ?: return ImportResult.CopyError

        // Copy once more because stream was consumed during validation
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return ImportResult.CopyError

        ImportResult.Success(destFile.name)
    } catch (e: SerializationException) {
        Log.e("InstalledDictionaries", "Invalid dictionary format", e)
        ImportResult.InvalidFormat
    } catch (e: Exception) {
        Log.e("InstalledDictionaries", "Error importing dictionary", e)
        ImportResult.CopyError
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun validateDictionaryStream(input: InputStream) {
    val json = Json { ignoreUnknownKeys = true }
    json.decodeFromStream<DictionaryIndex>(input)
}

private fun deleteDictionaryFile(context: Context, dictionary: UnifiedDictionaryItem): UninstallResult {
    return try {
        // Cannot delete asset dictionaries
        if (dictionary.source == DictionarySource.Asset) {
            Log.w("InstalledDictionaries", "Cannot delete asset dictionary: ${dictionary.fileName}")
            return UninstallResult.CannotDeleteAsset
        }
        
        // Delete from custom directory
        val customDir = File(context.filesDir, "dictionaries_serialized/custom")
        val dictFile = File(customDir, dictionary.fileName)
        
        if (!dictFile.exists()) {
            Log.w("InstalledDictionaries", "Dictionary file not found: ${dictFile.absolutePath}")
            return UninstallResult.NotFound
        }
        
        if (dictFile.delete()) {
            Log.d("InstalledDictionaries", "Successfully deleted dictionary: ${dictionary.fileName}")
            UninstallResult.Success(dictionary.fileName)
        } else {
            Log.e("InstalledDictionaries", "Failed to delete dictionary file: ${dictFile.absolutePath}")
            UninstallResult.DeleteError
        }
    } catch (e: Exception) {
        Log.e("InstalledDictionaries", "Error deleting dictionary: ${dictionary.fileName}", e)
        UninstallResult.DeleteError
    }
}

private fun getLanguageDisplayName(languageCode: String): String {
    return try {
        val locale = Locale.forLanguageTag(languageCode)
        locale.getDisplayLanguage(locale).replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(locale)
            } else {
                char.toString()
            }
        }
    } catch (e: Exception) {
        languageCode.uppercase(Locale.getDefault())
    }
}
