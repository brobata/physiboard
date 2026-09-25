package brobata.physiboard.app.settings.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.DictionaryDownloader
import brobata.physiboard.app.settings.DictionaryFileStore
import brobata.physiboard.app.settings.UninstallOutcome
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.dict.DictionaryCatalog
import brobata.physiboard.core.dict.DictionaryManifestItem
import brobata.physiboard.core.dict.DictionaryRow
import kotlinx.coroutines.launch

/**
 * "Installed dictionaries" (dictionaries-languages.md SS6): fetch the hosted manifest, merge it
 * with what is already on the device (`:core:dict`'s [DictionaryCatalog], pure), and let the user
 * download, import or uninstall a language file. [DictionaryDownloader] and [DictionaryFileStore]
 * are the network and file glue this screen needs (rebuild-from-scratch.md: "network glue in
 * `:app`"); everything else here is presentation state.
 *
 * SPEC GAP: SS5.3 describes per-row byte progress (updated after every 8 KB chunk); this screen
 * shows only a per-row spinner while a download runs, since [DictionaryDownloader.download] reads
 * the whole body before returning. Byte-level progress would need a streaming download API this
 * module did not have reason to build for a first cut.
 */
@Composable
fun InstalledDictionariesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fileStore = remember { DictionaryFileStore(context) }
    val downloader = remember { DictionaryDownloader() }
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<DictionaryRow>>(emptyList()) }
    var manifestItems by remember { mutableStateOf<List<DictionaryManifestItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    var downloadingCode by remember { mutableStateOf<String?>(null) }

    suspend fun refreshLocalOnly() {
        val local = fileStore.listLocal()
        rows = DictionaryCatalog.merge(local, manifestItems) { it.uppercase() }
    }

    suspend fun refresh() {
        loading = true
        errorMessage = null
        val local = fileStore.listLocal()
        when (val result = downloader.fetchManifest()) {
            is DictionaryDownloader.ManifestResult.Success -> {
                manifestItems = result.manifest.items
                rows = DictionaryCatalog.merge(local, manifestItems) { it.uppercase() }
            }
            is DictionaryDownloader.ManifestResult.Error -> {
                rows = DictionaryCatalog.merge(local, manifestItems) { it.uppercase() }
                if (rows.isEmpty()) errorMessage = "Failed to load dictionary list"
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            } ?: uri.lastPathSegment.orEmpty()
            val lower = name.lowercase()
            if (!lower.endsWith(".dict") || !lower.contains("_base")) {
                snackbar = "Invalid file name. Expected *_base.dict"
                return@launch
            }
            val languageCode = lower.removeSuffix(".dict").removeSuffix("_base")
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) {
                snackbar = "Import failed"
                return@launch
            }
            if (!fileStore.decodesAsDictionary(bytes)) {
                snackbar = "Invalid dictionary format"
                return@launch
            }
            val installed = fileStore.installImported(languageCode, bytes)
            snackbar = if (installed) "Imported ${languageCode}_base.dict" else "Import failed"
            refreshLocalOnly()
        }
    }

    SettingsScreenScaffold(
        title = "Installed dictionaries",
        onBack = onBack,
        trailingAction = {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp).defaultMinSize(24.dp, 24.dp))
            } else {
                IconButton(onClick = { scope.launch { refresh() } }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
            IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.FileOpen, contentDescription = "Import")
            }
        },
    ) {
        RowList {
            if (errorMessage != null) {
                item { Text(errorMessage!!, modifier = Modifier.padding(16.dp)) }
            }
            if (snackbar != null) {
                item { Text(snackbar!!, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            if (rows.isEmpty() && !loading && errorMessage == null) {
                item { Text("No serialized dictionaries found.", modifier = Modifier.padding(16.dp)) }
            }
            items(rows, key = { it.fileName }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        Text(row.displayName)
                        Text("Language: ${row.languageCode.uppercase()} - ${row.badges.joinToString(", ")}")
                    }
                    when {
                        downloadingCode == row.languageCode -> CircularProgressIndicator(modifier = Modifier.defaultMinSize(20.dp, 20.dp))
                        row.canUninstall -> IconButton(onClick = {
                            scope.launch {
                                when (fileStore.uninstall(row.languageCode)) {
                                    UninstallOutcome.SUCCESS -> snackbar = "Uninstalled ${row.displayName}"
                                    UninstallOutcome.NOT_FOUND -> snackbar = "Dictionary file not found"
                                    UninstallOutcome.FAILED -> snackbar = "Failed to uninstall dictionary"
                                }
                                refreshLocalOnly()
                            }
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Uninstall") }
                        row.canDownload -> IconButton(onClick = {
                            val item = row.manifestItem ?: return@IconButton
                            downloadingCode = row.languageCode
                            scope.launch {
                                when (val result = downloader.download(item, fileStore)) {
                                    is DictionaryDownloader.DownloadResult.Success -> {
                                        val ok = fileStore.installDownloaded(item, result.bytes)
                                        snackbar = if (ok) "Downloaded ${item.name}" else "Download failed"
                                    }
                                    DictionaryDownloader.DownloadResult.VerificationFailed -> snackbar = "Download verification failed"
                                    DictionaryDownloader.DownloadResult.InvalidFormat -> snackbar = "Invalid dictionary format"
                                    is DictionaryDownloader.DownloadResult.NetworkError -> snackbar = "Network error"
                                }
                                downloadingCode = null
                                refreshLocalOnly()
                            }
                        }) { Icon(Icons.Filled.Download, contentDescription = "Download") }
                    }
                }
            }
        }
    }
}
