package brobata.physiboard.app.settings

import brobata.physiboard.core.dict.DictionaryManifest
import brobata.physiboard.core.dict.DictionaryManifestCodec
import brobata.physiboard.core.dict.DictionaryManifestItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The network half of the installed-dictionaries screen (rebuild-from-scratch.md: "Downloading is
 * network glue in `:app`"). spec: dictionaries-languages.md SS5.1-SS5.3. Parsing the manifest body
 * and deciding install state are pure and live in `:core:dict`'s `DictionaryManifestCodec` /
 * `DictionaryCatalog`; this class only moves bytes.
 *
 * SPEC GAP: SS5.2/SS5.3 describe "the HTTP client's defaults" (10 s connect/read/write, one
 * transparent retry on a connection failure) as if a shared client already exists elsewhere in the
 * app; no such client is visible to this module, so [java.net.HttpURLConnection] is used directly
 * with those same timeouts and no retry logic added here (a single request per call, matching "the
 * app itself never retries").
 */
class DictionaryDownloader {

    sealed class ManifestResult {
        data class Success(val manifest: DictionaryManifest) : ManifestResult()
        data class Error(val message: String) : ManifestResult()
    }

    /** SS5.2's outcome table. */
    suspend fun fetchManifest(url: String = MANIFEST_URL): ManifestResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) return@withContext ManifestResult.Error("HTTP $code")
            val body = connection.inputStream.use { it.readBytes() }.decodeToString()
            if (body.isBlank()) return@withContext ManifestResult.Error("Empty response")
            val manifest = DictionaryManifestCodec.parse(body) ?: return@withContext ManifestResult.Error("Manifest did not parse")
            ManifestResult.Success(manifest)
        } catch (e: Exception) {
            ManifestResult.Error(e.message ?: "Network error")
        }
    }

    sealed class DownloadResult {
        data class Success(val bytes: ByteArray) : DownloadResult()
        object VerificationFailed : DownloadResult()
        object InvalidFormat : DownloadResult()
        data class NetworkError(val message: String) : DownloadResult()
    }

    /**
     * SS5.3 steps 1-4: streams the item's [DictionaryManifestItem.url], verifies its SHA-256, and
     * leaves the format check ([isValidFormat]) to the caller, which owns `:core:dict`'s decoder.
     */
    suspend fun download(item: DictionaryManifestItem, fileStore: DictionaryFileStore): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(item.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return@withContext DownloadResult.NetworkError("HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readBytes() }
            val actualSha = fileStore.sha256Hex(bytes)
            if (!actualSha.equals(item.sha256, ignoreCase = true)) return@withContext DownloadResult.VerificationFailed
            if (!fileStore.decodesAsDictionary(bytes)) return@withContext DownloadResult.InvalidFormat
            DownloadResult.Success(bytes)
        } catch (e: Exception) {
            DownloadResult.NetworkError(e.message ?: "Download failed")
        }
    }

    companion object {
        const val MANIFEST_URL: String = "https://brobata.github.io/physiboard-dict/dicts-manifest.json"
        private const val TIMEOUT_MS = 10_000
    }
}
