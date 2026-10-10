package brobata.physiboard.app.shell

import brobata.physiboard.core.shell.ChecksumFile
import brobata.physiboard.core.shell.NetworkPurpose
import brobata.physiboard.core.shell.UpdateAssets
import brobata.physiboard.core.shell.UpdateHosts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.security.MessageDigest

/**
 * Downloads a release's checksum and APK (app-shell.md SS32.2, SS32.5). Every request goes
 * through [GatedHttp], so private mode refuses it before anything is sent, and is a plain GET with
 * no body and no identifying header beyond what Android's HTTP client always sends. Redirects are
 * not followed by the client: each hop is checked against [UpdateHosts] first, so a download can
 * only ever be fetched from github.com and GitHub's own file servers, over https.
 */
object UpdateDownloader {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /** Ten times the 3.2.0 APK (10.9 MB); a response larger than this is not PhysiBoard. */
    const val MAX_APK_BYTES = 100L * 1024 * 1024

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>

        /** Private mode, or settings that could not be read: nothing was sent. */
        data class Blocked(val reason: String) : Result<Nothing>

        /** The network or GitHub failed; worth another try later. */
        data class Failed(val message: String) : Result<Nothing>

        /** GitHub answered with something that is not what the release promised; not worth another try. */
        data class Invalid(val message: String) : Result<Nothing>
    }

    /** The release's SHA-256 for its APK, read from the `.sha256` asset. */
    suspend fun fetchChecksum(assets: UpdateAssets): Result<String> = withContext(Dispatchers.IO) {
        guarded {
            val bytes = get(assets.checksumUrl) { input -> readUpTo(input, ChecksumFile.MAX_BYTES.toLong()) }
                ?: return@guarded Result.Invalid("the checksum file is too large")
            val digest = ChecksumFile.parse(bytes.decodeToString(), assets.apkName)
                ?: return@guarded Result.Invalid("the checksum file does not name ${assets.apkName}")
            Result.Ok(digest)
        }
    }

    /** Streams the APK into [destination] and answers its SHA-256, computed on the way in. */
    suspend fun fetchApk(assets: UpdateAssets, destination: File): Result<String> = withContext(Dispatchers.IO) {
        guarded {
            val digest = MessageDigest.getInstance("SHA-256")
            val complete = get(assets.apkUrl) { input ->
                destination.outputStream().buffered().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_APK_BYTES) return@get false
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                    }
                }
                true
            }
            if (!complete) {
                destination.delete()
                return@guarded Result.Invalid("the download is larger than any PhysiBoard release")
            }
            Result.Ok(digest.digest().joinToString("") { "%02x".format(it) })
        }.also { if (it !is Result.Ok) destination.delete() }
    }

    private inline fun <T> guarded(block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: NetworkBlockedException) {
        Result.Blocked(e.message ?: "Offline")
    } catch (e: InvalidHopException) {
        Result.Invalid(e.message ?: "redirected somewhere not allowed")
    } catch (e: Exception) {
        Result.Failed(e.message ?: e::class.simpleName ?: "network error")
    }

    /** GETs [url], following up to [UpdateHosts.MAX_REDIRECTS] checked redirects, and reads the body with [read]. */
    private suspend fun <T> get(url: String, read: (InputStream) -> T): T {
        var current = url
        repeat(UpdateHosts.MAX_REDIRECTS + 1) {
            if (!UpdateHosts.isAllowed(current)) throw InvalidHopException("refused to fetch from ${hostOf(current)}")
            val connection = GatedHttp.open(NetworkPurpose.UPDATE_DOWNLOAD, current)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.useCaches = false
                val code = connection.responseCode
                when {
                    code in 200..299 -> return connection.inputStream.use(read)
                    code in REDIRECTS -> {
                        current = UpdateHosts.redirectTarget(current, connection.getHeaderField("Location"))
                            ?: throw InvalidHopException("redirected outside GitHub")
                    }
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw InvalidHopException("too many redirects")
    }

    private fun readUpTo(input: InputStream, maxBytes: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull() ?: "an invalid address"

    private val REDIRECTS = setOf(
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        307,
        308,
    )

    private class InvalidHopException(message: String) : IOException(message)
}
