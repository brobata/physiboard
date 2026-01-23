package it.palsoftware.pastiera.data.layout

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

private const val TAG = "LayoutRepositoryManager"
private const val MANIFEST_URL = "https://palsoftware.github.io/pastiera-dict/layouts-manifest.json"
private val httpClient = OkHttpClient()

@Serializable
data class LayoutManifest(
    @SerialName("schemaVersion") val schemaVersion: Int,
    @SerialName("generatedAt") val generatedAt: String,
    @SerialName("releaseTag") val releaseTag: String,
    @SerialName("items") val items: List<LayoutItem>
)

@Serializable
data class LayoutItem(
    @SerialName("id") val id: String,
    @SerialName("filename") val filename: String,
    @SerialName("url") val url: String,
    @SerialName("bytes") val bytes: Long,
    @SerialName("sha256") val sha256: String,
    @SerialName("updatedAt") val updatedAt: String,
    @SerialName("name") val name: String,
    @SerialName("shortDescription") val shortDescription: String,
    @SerialName("languageTag") val languageTag: String
)

sealed class LayoutDownloadResult {
    data class Success(val layoutName: String) : LayoutDownloadResult()
    object NetworkError : LayoutDownloadResult()
    object InvalidFormat : LayoutDownloadResult()
    object HashMismatch : LayoutDownloadResult()
    object CopyError : LayoutDownloadResult()
}

object LayoutRepositoryManager {
    suspend fun fetchManifest(): Result<LayoutManifest> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(MANIFEST_URL)
                .header("Accept", "application/json")
                .build()

            Log.d(TAG, "Fetching layout manifest from $MANIFEST_URL")
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Layout manifest request failed: HTTP ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                Log.e(TAG, "Empty layout manifest response")
                return@withContext Result.failure(Exception("Empty response"))
            }

            val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<LayoutManifest>(body)
            Log.d(TAG, "Parsed ${manifest.items.size} layout entries")
            Result.success(manifest)
        } catch (e: SerializationException) {
            Log.e(TAG, "Failed to parse layout manifest", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching layout manifest", e)
            Result.failure(e)
        }
    }

    suspend fun downloadLayout(
        context: Context,
        item: LayoutItem,
        onProgress: ((Long, Long) -> Unit)? = null
    ): LayoutDownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(item.url)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download ${item.filename}: HTTP ${response.code}")
                return@withContext LayoutDownloadResult.NetworkError
            }

            val body = response.body ?: return@withContext LayoutDownloadResult.NetworkError
            val contentLength = body.contentLength()
            val cacheDir = File(context.cacheDir, "layout_downloads").apply { mkdirs() }
            val tempFile = File(cacheDir, "${item.filename}.tmp")

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        onProgress?.invoke(totalBytesRead, contentLength)
                    }
                }
            }

            val calculatedHash = calculateSHA256(tempFile)
            if (!calculatedHash.equals(item.sha256, ignoreCase = true)) {
                Log.e(TAG, "Hash mismatch for ${item.filename}. Expected ${item.sha256}, got $calculatedHash")
                tempFile.delete()
                return@withContext LayoutDownloadResult.HashMismatch
            }

            val layoutName = layoutNameFromFilename(item.filename)
            val validated = LayoutFileStore.loadLayoutFromStream(tempFile.inputStream())
            if (validated == null) {
                Log.e(TAG, "Downloaded layout ${item.filename} failed validation")
                tempFile.delete()
                return@withContext LayoutDownloadResult.InvalidFormat
            }

            val destFile = LayoutFileStore.getLayoutFile(context, layoutName)
            if (destFile.exists()) {
                destFile.delete()
            }

            if (!tempFile.renameTo(destFile)) {
                tempFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
            }

            Log.i(TAG, "Layout downloaded: $layoutName")
            LayoutDownloadResult.Success(layoutName)
        } catch (e: SerializationException) {
            Log.e(TAG, "Invalid layout format for ${item.filename}", e)
            LayoutDownloadResult.InvalidFormat
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading layout ${item.filename}", e)
            LayoutDownloadResult.CopyError
        }
    }

    fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun layoutNameFromFilename(filename: String): String {
        return filename.removeSuffix(".json")
    }

    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> "%.1f MB".format(mb)
            kb >= 1 -> "%.1f KB".format(kb)
            else -> "$bytes B"
        }
    }
}
