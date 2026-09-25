package brobata.physiboard.app.shell

import brobata.physiboard.core.shell.ReleaseFeed
import brobata.physiboard.core.shell.UpdateCheckResult
import brobata.physiboard.core.shell.UpdatePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The network half of the update checker (app-shell.md SS13.2): one GET to the GitHub releases
 * list, 10s connect/read timeouts, always resolved on the calling coroutine's context switch back
 * (the caller decides which dispatcher that is, matching SS13.2's "always delivered on the main
 * thread" once the caller awaits this from `Dispatchers.Main`).
 */
object GithubUpdateClient {
    private const val RELEASES_URL = "https://api.github.com/repos/brobata/physiboard/releases?per_page=20"
    private const val TIMEOUT_MS = 10_000

    /** Null on any network failure (SS13.9): a caught exception, not a crash. */
    suspend fun fetchReleasesBody(): String? = withContext(Dispatchers.IO) {
        runCatching {
            (URL(RELEASES_URL).openConnection() as HttpURLConnection).run {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    if (responseCode !in 200..299) return@withContext null
                    inputStream.bufferedReader().use { it.readText() }
                } finally {
                    disconnect()
                }
            }
        }.getOrNull()
    }

    /** spec: SS13.1-SS13.5, SS13.9. A network failure resolves the same as an empty release list: [UpdateCheckResult.NoUpdate]. */
    suspend fun check(installedVersionName: String, dismissedReleases: Set<String>, ignoreDismissedReleases: Boolean): UpdateCheckResult {
        val body = fetchReleasesBody() ?: return UpdateCheckResult.NoUpdate
        val releases = ReleaseFeed.parseReleases(body)
        return UpdatePolicy.decide(releases, installedVersionName, dismissedReleases, ignoreDismissedReleases)
    }
}
