package brobata.physiboard.app.shell

import brobata.physiboard.core.shell.FetchResult
import brobata.physiboard.core.shell.GatedFetcher
import brobata.physiboard.core.shell.NetworkPurpose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * The keyboard's way onto the network (app-shell.md SS31.2): every GET opens through [GatedHttp],
 * so private mode refuses it before anything is sent, and nothing in `:ime` opens a connection of
 * its own. Used by the GIF page (layers-sym-alt.md SS4.5).
 */
object GatedHttpFetcher : GatedFetcher {
    private const val TIMEOUT_MS = 10_000

    /**
     * A cancelled caller (the page closed, the query changed) does not wait for a blocking read to
     * time out: a watcher disconnects the connection the moment the caller is cancelled, which
     * makes the read fail at once and frees the slot it held.
     */
    override suspend fun get(purpose: NetworkPurpose, url: String, maxBytes: Int): FetchResult = try {
        val connection = GatedHttp.open(purpose, url).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
        }
        coroutineScope {
            // On IO: closing a socket is not work for whichever thread the caller is on.
            val watcher = launch(Dispatchers.IO) {
                try {
                    awaitCancellation()
                } finally {
                    connection.disconnect()
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    connection.connect()
                    val code = connection.responseCode
                    if (code !in 200..299) return@withContext FetchResult.Failed("HTTP $code")
                    val body = connection.inputStream.use { readUpTo(it, maxBytes) } ?: return@withContext FetchResult.Failed("Too large")
                    FetchResult.Ok(body)
                }
            } finally {
                watcher.cancel()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: NetworkBlockedException) {
        FetchResult.Blocked(e.message ?: "Offline")
    } catch (e: Exception) {
        FetchResult.Failed(e.message ?: "Network error")
    }

    private fun readUpTo(input: InputStream, maxBytes: Int): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
