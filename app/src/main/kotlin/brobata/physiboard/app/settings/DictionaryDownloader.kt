package brobata.physiboard.app.settings

import brobata.physiboard.core.dict.DictionaryManifest
import brobata.physiboard.core.dict.DictionaryManifestCodec
import brobata.physiboard.core.dict.DictionaryManifestItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.io.InputStream
import brobata.physiboard.app.shell.GatedHttp
import brobata.physiboard.app.shell.NetworkBlockedException
import brobata.physiboard.core.shell.NetworkPurpose

/**
 * The network half of the installed-dictionaries screen (rebuild-from-scratch.md: "Downloading is
 * network glue in `:app`"). spec: dictionaries-languages.md SS5.1-SS5.3. Parsing the manifest body
 * and deciding install state are pure and live in `:core:dict`'s `DictionaryManifestCodec` /
 * `DictionaryCatalog`; this class only moves bytes.
 *
 * SPEC GAP: SS5.2/SS5.3 describe "the HTTP client's defaults" (10 s connect/read/write, one
 * transparent retry on a connection failure) as if a shared client already exists elsewhere in the
 * app; no such client is visible to this module, so a plain `HttpURLConnection` from [GatedHttp] is used
 * with those same timeouts and no retry logic added here (a single request per call, matching "the
 * app itself never retries").
 *
 * SS17's Keep/Drop ("Manifest fetch with client defaults only | Keep, add an overall deadline |
 * 10 s per read is fine; add a call deadline and a size cap per item"): [fetchManifest] adds
 * [MANIFEST_DEADLINE_MS] as a whole-call ceiling (a stalled connection could otherwise renew its
 * own 10 s read timeout forever) and [MANIFEST_MAX_BYTES] as a size cap, since the manifest is a
 * few kilobytes of JSON and never legitimately large. [download] keeps SS5.3's own "no overall
 * deadline... a 33 MB file on a slow link simply takes its time", but still caps the body at
 * [DOWNLOAD_MAX_BYTES] so a malformed or hostile response cannot be read fully into memory
 * unbounded.
 *
 * FORMAT NOTE: this build's dictionaries are `:core:dict`'s headered `.pbd` format
 * (`DictionaryIndex.fromPbdBytes`/`PbdReader`), the same format the bundled `en.pbd` asset uses
 * (docs/dictionaries.md). The manifest at [MANIFEST_URL] (fetched 2026-09-26 to verify this) still
 * serves the *old* headerless format for every one of its 19 items, including `en_base.dict`
 * (21,491,827 bytes, sha `19138c21...`, matching dictionaries-languages.md SS2.2's *old-format*
 * shipped-file table, not the ~1.2 MB `.pbd` this app bundles): downloading any of them will pass
 * SHA-256 verification and then fail [DictionaryFileStore.decodesAsDictionary]'s PBD1 check with
 * "Invalid dictionary format", exactly as the old CBOR/JSON decoder was dropped to do (SS17: "Two
 * accepted encodings by first byte | Drop | One format, one decoder"). That is the correct
 * behavior, not a bug here: this class deliberately does not add a second decoder for the old
 * format. A real download will start working only once `github.com/brobata/physiboard-dict`
 * publishes `.pbd`-format releases; import already accepts exactly that format today (a `<lang>.pbd`
 * file, PBD1 bytes), so it is the only way to add a non-English dictionary until then.
 */
class DictionaryDownloader {

    sealed class ManifestResult {
        data class Success(val manifest: DictionaryManifest) : ManifestResult()
        data class Error(val message: String) : ManifestResult()

        /** app-shell.md SS31.2: refused by the network gate (private mode); nothing was sent. */
        data class Blocked(val message: String) : ManifestResult()
    }

    /** SS5.2's outcome table, plus SS17's added overall deadline and size cap. */
    suspend fun fetchManifest(url: String = MANIFEST_URL): ManifestResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(MANIFEST_DEADLINE_MS) {
                val connection = GatedHttp.open(NetworkPurpose.DICTIONARY_MANIFEST, url).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                }
                connection.connect()
                val code = connection.responseCode
                if (code !in 200..299) return@withTimeout ManifestResult.Error("HTTP $code")
                val body = connection.inputStream.use { readUpTo(it, MANIFEST_MAX_BYTES) }?.decodeToString()
                    ?: return@withTimeout ManifestResult.Error("Manifest too large")
                if (body.isBlank()) return@withTimeout ManifestResult.Error("Empty response")
                val manifest = DictionaryManifestCodec.parse(body) ?: return@withTimeout ManifestResult.Error("Manifest did not parse")
                ManifestResult.Success(manifest)
            }
        } catch (e: TimeoutCancellationException) {
            ManifestResult.Error("Network error")
        } catch (e: NetworkBlockedException) {
            ManifestResult.Blocked(e.message ?: "Offline")
        } catch (e: Exception) {
            ManifestResult.Error(e.message ?: "Network error")
        }
    }

    sealed class DownloadResult {
        data class Success(val bytes: ByteArray) : DownloadResult()
        object VerificationFailed : DownloadResult()
        object InvalidFormat : DownloadResult()
        data class NetworkError(val message: String) : DownloadResult()

        /** app-shell.md SS31.2: refused by the network gate (private mode); nothing was sent. */
        data class Blocked(val message: String) : DownloadResult()
    }

    /**
     * SS5.3 steps 1-4: streams the item's [DictionaryManifestItem.url], verifies its SHA-256, and
     * leaves the format check to the caller, which owns `:core:dict`'s decoder via
     * [DictionaryFileStore.decodesAsDictionary].
     */
    suspend fun download(item: DictionaryManifestItem, fileStore: DictionaryFileStore): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val connection = GatedHttp.open(NetworkPurpose.DICTIONARY_DOWNLOAD, item.url).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return@withContext DownloadResult.NetworkError("HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { readUpTo(it, DOWNLOAD_MAX_BYTES) }
                ?: return@withContext DownloadResult.NetworkError("Download too large")
            val actualSha = fileStore.sha256Hex(bytes)
            if (!actualSha.equals(item.sha256, ignoreCase = true)) return@withContext DownloadResult.VerificationFailed
            if (!fileStore.decodesAsDictionary(bytes)) return@withContext DownloadResult.InvalidFormat
            DownloadResult.Success(bytes)
        } catch (e: NetworkBlockedException) {
            DownloadResult.Blocked(e.message ?: "Offline")
        } catch (e: Exception) {
            DownloadResult.NetworkError(e.message ?: "Download failed")
        }
    }

    /** Reads [input] fully, or returns null the moment it would exceed [maxBytes] (SS17's per-item size cap). */
    private fun readUpTo(input: InputStream, maxBytes: Long): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = try {
                input.read(chunk)
            } catch (e: EOFException) {
                -1
            }
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    companion object {
        const val MANIFEST_URL: String = "https://brobata.github.io/physiboard-dict/dicts-manifest.json"
        private const val TIMEOUT_MS = 10_000
        private const val MANIFEST_DEADLINE_MS = 20_000L
        private const val MANIFEST_MAX_BYTES = 2L shl 20 // 2 MB; the real manifest is a few KB.
        private const val DOWNLOAD_MAX_BYTES = 64L shl 20 // 64 MB; the largest documented list is ~33 MB.
    }
}
