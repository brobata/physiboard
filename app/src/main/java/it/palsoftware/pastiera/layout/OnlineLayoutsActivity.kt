package it.palsoftware.pastiera.layout

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutItem
import it.palsoftware.pastiera.data.layout.LayoutManifest
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.layout.LayoutRepositoryManager
import it.palsoftware.pastiera.data.layout.LayoutDownloadResult
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import kotlinx.coroutines.launch

private const val TAG = "OnlineLayouts"

class OnlineLayoutsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PastieraTheme {
                OnlineLayoutsScreen(
                    onBack = { finish() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

internal data class UnifiedLayoutItem(
    val layoutName: String,
    val displayName: String,
    val description: String,
    val bytes: Long,
    val manifestItem: LayoutItem,
    val isDownloaded: Boolean,
    val downloadProgress: Pair<Long, Long>?,
    val isDownloading: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLayoutsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var manifest by remember { mutableStateOf<LayoutManifest?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var manifestError by remember { mutableStateOf<String?>(null) }
    var downloadingItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadProgress by remember { mutableStateOf<Map<String, Pair<Long, Long>>>(emptyMap()) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var layoutToDelete by remember { mutableStateOf<UnifiedLayoutItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun refreshManifest() {
        coroutineScope.launch {
            isLoading = true
            manifestError = null
            LayoutRepositoryManager.fetchManifest()
                .onSuccess { fetched ->
                    manifest = fetched
                    isLoading = false
                    Log.d(TAG, "Loaded ${fetched.items.size} layouts from manifest")
                }
                .onFailure { error ->
                    manifestError = error.message
                    isLoading = false
                    Log.e(TAG, "Failed to load layout manifest: ${error.message}", error)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.online_layouts_manifest_error))
                    }
                }
        }
    }

    LaunchedEffect(Unit) {
        refreshManifest()
    }

    val unifiedLayouts = remember(manifest, downloadingItems, downloadProgress, refreshTrigger) {
        mergeLayouts(context, manifest, downloadingItems, downloadProgress)
    }

    fun downloadLayout(item: LayoutItem) {
        if (downloadingItems.contains(item.id)) return
        coroutineScope.launch {
            downloadingItems = downloadingItems + item.id
            downloadProgress = downloadProgress - item.id
            val result = LayoutRepositoryManager.downloadLayout(
                context = context,
                item = item
            ) { downloaded, total ->
                downloadProgress = downloadProgress + (item.id to (downloaded to total))
            }

            downloadingItems = downloadingItems - item.id
            downloadProgress = downloadProgress - item.id
            refreshTrigger++

            val message = when (result) {
                is LayoutDownloadResult.Success -> {
                    context.getString(R.string.online_layouts_download_success, result.layoutName)
                }
                LayoutDownloadResult.NetworkError -> context.getString(R.string.online_layouts_download_network_error)
                LayoutDownloadResult.InvalidFormat -> context.getString(R.string.online_layouts_download_invalid_format)
                LayoutDownloadResult.HashMismatch -> context.getString(R.string.online_layouts_download_hash_mismatch)
                LayoutDownloadResult.CopyError -> context.getString(R.string.online_layouts_download_failed)
            }
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    fun deleteDownloadedLayout(layout: UnifiedLayoutItem) {
        coroutineScope.launch {
            val deleted = LayoutFileStore.deleteLayout(context, layout.layoutName)
            val message = if (deleted) {
                refreshTrigger++
                context.getString(R.string.online_layouts_uninstall_success, layout.displayName)
            } else {
                context.getString(R.string.online_layouts_uninstall_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier,
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
                        text = stringResource(R.string.online_layouts_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { refreshManifest() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.online_layouts_refresh)
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (unifiedLayouts.isEmpty() && !isLoading) {
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
                        text = stringResource(R.string.online_layouts_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (manifestError != null) {
                        Text(
                            text = stringResource(R.string.online_layouts_manifest_error),
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(unifiedLayouts, key = { it.layoutName }) { layout ->
                    LayoutRowCompact(
                        layout = layout,
                        isDownloading = downloadingItems.contains(layout.manifestItem.id),
                        progress = downloadProgress[layout.manifestItem.id],
                        onDownload = { downloadLayout(layout.manifestItem) },
                        onDelete = { layoutToDelete = layout }
                    )
                }
            }
        }
    }

    layoutToDelete?.let { layout ->
        AlertDialog(
            onDismissRequest = { layoutToDelete = null },
            title = { Text(stringResource(R.string.online_layouts_uninstall_confirm_title)) },
            text = { Text(stringResource(R.string.online_layouts_uninstall_confirm_message, layout.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDownloadedLayout(layout)
                        layoutToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.online_layouts_uninstall))
                }
            },
            dismissButton = {
                TextButton(onClick = { layoutToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    BackHandler { onBack() }
}

@Composable
private fun LayoutRowCompact(
    layout: UnifiedLayoutItem,
    isDownloading: Boolean,
    progress: Pair<Long, Long>?,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = layout.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = layout.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(
                            R.string.online_layouts_size,
                            LayoutRepositoryManager.formatFileSize(layout.bytes)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    if (layout.isDownloaded) {
                        IconButton(
                            onClick = onDelete
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.online_layouts_uninstall)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDownload,
                        enabled = !layout.isDownloaded
                    ) {
                        Icon(
                            imageVector = if (layout.isDownloaded) Icons.Filled.Check else Icons.Filled.Download,
                            contentDescription = stringResource(R.string.online_layouts_download)
                        )
                    }
                }
            }

            if (isDownloading) {
                val totalBytes = progress?.second ?: -1L
                if (totalBytes > 0) {
                    val downloaded = progress?.first ?: 0L
                    LinearProgressIndicator(progress = downloaded.toFloat() / totalBytes.toFloat())
                } else {
                    LinearProgressIndicator()
                }
            }
        }
    }
}

private fun mergeLayouts(
    context: Context,
    manifest: LayoutManifest?,
    downloading: Set<String>,
    progress: Map<String, Pair<Long, Long>>
): List<UnifiedLayoutItem> {
    val items = manifest?.items ?: emptyList()
    // Only layouts bundled in assets are considered "already installed" for the cloud list.
    // Cloud-only layouts remain visible even after download.
    val bundledLayoutNames = LayoutMappingRepository
        .getAvailableLayouts(context.assets, null)
        .toSet()
    val merged = items.mapNotNull { item ->
        val layoutName = LayoutRepositoryManager.layoutNameFromFilename(item.filename)
        // Only show layouts that are *exclusively* on cloud (not bundled in assets)
        if (bundledLayoutNames.contains(layoutName)) {
            return@mapNotNull null
        }
        val isDownloaded = LayoutFileStore.layoutExists(context, layoutName)
        val metadata = LayoutFileStore.getLayoutMetadata(context, layoutName)
        val displayName = metadata?.name?.takeIf { it.isNotBlank() } ?: item.name.ifBlank { layoutName }
        val description = metadata?.description?.takeIf { it.isNotBlank() } ?: item.shortDescription
        UnifiedLayoutItem(
            layoutName = layoutName,
            displayName = displayName,
            description = description,
            bytes = item.bytes,
            manifestItem = item,
            isDownloaded = isDownloaded,
            downloadProgress = progress[item.id],
            isDownloading = downloading.contains(item.id)
        )
    }
    return merged.sortedBy { it.displayName }
}
