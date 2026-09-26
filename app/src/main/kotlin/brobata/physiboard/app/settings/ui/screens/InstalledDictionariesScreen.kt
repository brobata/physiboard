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
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import brobata.physiboard.core.dict.LanguageCode
import kotlinx.coroutines.launch
import java.util.Locale

/** SS6: "the language's own name in its own language with the first letter capitalized (`Italiano`, `Русский`)". */
private fun ownLanguageName(code: String): String {
    val locale = Locale(code)
    val name = locale.getDisplayLanguage(locale)
    if (name.isBlank() || name.equals(code, ignoreCase = true)) return code.uppercase()
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/** SS6: "Size: N MB" (one decimal; KB or B below a megabyte)." */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
    bytes >= 1_000 -> "${"%.1f".format(bytes / 1_000.0)} KB"
    else -> "$bytes B"
}

/**
 * "Installed dictionaries" (dictionaries-languages.md SS6): fetch the hosted manifest, merge it
 * with what is already on the device (`:core:dict`'s [DictionaryCatalog], pure), and let the user
 * download, import or uninstall a language file. [DictionaryDownloader] and [DictionaryFileStore]
 * are the network and file glue this screen needs (rebuild-from-scratch.md: "network glue in
 * `:app`"); everything else here is presentation state.
 *
 * SPEC GAP: SS5.3 describes per-row byte progress (updated after every 8 KB chunk); this screen
 * shows an indeterminate spinner and progress bar while a download runs, since
 * [DictionaryDownloader.download] reads the whole body before returning. Byte-level progress would
 * need a streaming download API this module did not have reason to build for a first cut.
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
    // SS5.3: "Different items can download concurrently"; a set (not one nullable code) is what
    // actually lets a second row's spinner and progress bar survive a first download in flight.
    var downloadingCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var uninstallTarget by remember { mutableStateOf<DictionaryRow?>(null) }

    suspend fun refreshLocalOnly() {
        val local = fileStore.listLocal()
        rows = DictionaryCatalog.merge(local, manifestItems, ::ownLanguageName)
    }

    suspend fun refresh() {
        loading = true
        errorMessage = null
        val local = fileStore.listLocal()
        when (val result = downloader.fetchManifest()) {
            is DictionaryDownloader.ManifestResult.Success -> {
                manifestItems = result.manifest.items
                rows = DictionaryCatalog.merge(local, manifestItems, ::ownLanguageName)
            }
            is DictionaryDownloader.ManifestResult.Error -> {
                rows = DictionaryCatalog.merge(local, manifestItems, ::ownLanguageName)
                if (rows.isEmpty()) errorMessage = "Failed to load dictionary list"
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    fun startDownload(item: DictionaryManifestItem, languageCode: String) {
        downloadingCodes = downloadingCodes + languageCode
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
            downloadingCodes = downloadingCodes - languageCode
            refreshLocalOnly()
        }
    }

    // spec SS5.6: the picker accepts any file type; validation is on the name and the decoded
    // bytes. This build's dictionaries are the `.pbd` format `:core:dict`'s `PbdReader` decodes
    // (docs/dictionaries.md), the same format the bundled and downloaded tiers use, so import
    // accepts exactly that: a `<lang>.pbd` name and PBD1 bytes, not the legacy `*_base.dict` name
    // or the headerless CBOR/JSON bytes 2.x accepted.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            } ?: uri.lastPathSegment.orEmpty()
            val lower = name.lowercase()
            val languageCode = lower.removeSuffix(".pbd")
            if (!lower.endsWith(".pbd") || LanguageCode.of(languageCode) == null) {
                snackbar = "Invalid file name. Expected <lang>.pbd"
                return@launch
            }
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
            snackbar = if (installed) "Imported $languageCode.pbd" else "Import failed"
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
                val downloading = row.languageCode in downloadingCodes
                androidx.compose.foundation.layout.Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text(row.displayName)
                            val badgeText = (row.badges + if (row.updatable) setOf("Update available") else emptySet()).joinToString(", ")
                            Text("Language: ${row.languageCode.uppercase()} - $badgeText", style = MaterialTheme.typography.bodySmall)
                            // SS6: "Size: N MB"... "when the manifest knows the file, even if it is installed".
                            row.manifestItem?.let { item -> Text("Size: ${formatSize(item.bytes)}", style = MaterialTheme.typography.bodySmall) }
                        }
                        when {
                            downloading -> CircularProgressIndicator(modifier = Modifier.defaultMinSize(20.dp, 20.dp))
                            row.canUninstall -> IconButton(onClick = { uninstallTarget = row }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Uninstall")
                            }
                            row.canDownload -> IconButton(onClick = {
                                val item = row.manifestItem ?: return@IconButton
                                startDownload(item, row.languageCode)
                            }) { Icon(Icons.Filled.Download, contentDescription = "Download") }
                            // SS5.5/SS17: "Keep and wire it to the screen" -- an installed, downloaded
                            // row whose manifest entry is newer offers an update in place of Uninstall
                            // then Download.
                            row.installed && row.updatable -> IconButton(onClick = {
                                val item = row.manifestItem ?: return@IconButton
                                startDownload(item, row.languageCode)
                            }) { Icon(Icons.Filled.Update, contentDescription = "Update") }
                        }
                    }
                    // SS6: "below, a linear progress bar while downloading". Byte-level progress is not
                    // available (this screen's own SPEC GAP note), so the bar is indeterminate.
                    if (downloading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    // SS5.7: "Uninstall dictionary" / "Are you sure you want to uninstall <name>? This action
    // cannot be undone." with Uninstall in the error color and Cancel.
    uninstallTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text("Uninstall dictionary") },
            text = { Text("Are you sure you want to uninstall ${target.displayName}? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    uninstallTarget = null
                    scope.launch {
                        when (fileStore.uninstall(target.languageCode)) {
                            UninstallOutcome.SUCCESS -> snackbar = "Uninstalled ${target.displayName}"
                            UninstallOutcome.NOT_FOUND -> snackbar = "Dictionary file not found"
                            UninstallOutcome.FAILED -> snackbar = "Failed to uninstall dictionary"
                        }
                        refreshLocalOnly()
                    }
                }) { Text("Uninstall", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { uninstallTarget = null }) { Text("Cancel") } },
        )
    }
}

