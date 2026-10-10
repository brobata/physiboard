package brobata.physiboard.core.shell

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull

/** One asset attached to a GitHub release: its file name and download URL. spec: app-shell.md SS13.3. */
data class ReleaseAsset(val name: String, val downloadUrl: String)

/** One element of the GitHub releases list, kept only the fields the checker uses. spec: SS13.3. */
data class GithubRelease(
    val tagName: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
)

/**
 * The release the checker offers, resolved from the list: its tag, the page to open, and the APK if
 * one was attached. spec: SS13.3, SS13.6. [assets] is every attached file, which automatic updates
 * read for the APK and its checksum by exact name (SS32.2).
 */
data class ResolvedRelease(val tag: String, val pageUrl: String, val apkDownloadUrl: String?, val assets: List<ReleaseAsset> = emptyList())

/**
 * Parses the GitHub releases list and resolves the one release the checker offers. spec:
 * app-shell.md SS13.2, SS13.3, SS13.9. Every entry point (network failure, malformed JSON, an
 * empty array, no eligible release) degrades to "no release" instead of throwing.
 */
object ReleaseFeed {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** spec: SS13.3, SS13.9 ("malformed JSON is caught"). A non-array body or a parse failure yields an empty list. */
    fun parseReleases(body: String): List<GithubRelease> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray ?: return emptyList()
        return root.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val tag = (obj["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (tag.isBlank()) return@mapNotNull null
            val assets = (obj["assets"] as? JsonArray).orEmpty().mapNotNull { assetElement ->
                val assetObj = assetElement as? JsonObject ?: return@mapNotNull null
                val name = (assetObj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val url = (assetObj["browser_download_url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                ReleaseAsset(name, url)
            }
            GithubRelease(
                tagName = tag,
                prerelease = (obj["prerelease"] as? JsonPrimitive)?.booleanOrNull ?: false,
                draft = (obj["draft"] as? JsonPrimitive)?.booleanOrNull ?: false,
                htmlUrl = (obj["html_url"] as? JsonPrimitive)?.contentOrNull ?: "",
                assets = assets,
            )
        }
    }

    /**
     * spec: SS13.3. The chosen release is the first one that is neither a draft nor a
     * pre-release; a blank `tag_name` was already filtered out by [parseReleases]. The download
     * URL is the first asset whose lower-cased name ends in `.apk`.
     */
    fun chooseRelease(releases: List<GithubRelease>): ResolvedRelease? {
        val chosen = releases.firstOrNull { !it.draft && !it.prerelease } ?: return null
        val apk = chosen.assets.firstOrNull { it.name.lowercase().endsWith(".apk") }
        return ResolvedRelease(
            tag = chosen.tagName,
            pageUrl = chosen.htmlUrl.ifBlank { "https://github.com/brobata/physiboard/releases" },
            apkDownloadUrl = apk?.downloadUrl,
            assets = chosen.assets,
        )
    }
}

/** Whether an update check found something to offer. spec: app-shell.md SS13. */
sealed interface UpdateCheckResult {
    data class Update(val release: ResolvedRelease) : UpdateCheckResult
    data object NoUpdate : UpdateCheckResult
}

/**
 * The update checker's whole decision, given a releases list already parsed by [ReleaseFeed]. spec:
 * app-shell.md SS13.1, SS13.4, SS13.5, SS13.9. The network call and "GitHub checks allowed" gate
 * are the caller's job; this is what happens once a body (or its absence) is in hand.
 */
object UpdatePolicy {

    /**
     * [ignoreDismissedReleases] is true for the automatic triggers (home, settings, the daily
     * job) and false for the manual "Updates" row (SS13.1, SS13.5: "the manual check does not
     * ignore dismissed releases").
     */
    fun decide(
        releases: List<GithubRelease>,
        installedVersionName: String,
        dismissedReleases: Set<String>,
        ignoreDismissedReleases: Boolean,
    ): UpdateCheckResult {
        val resolved = ReleaseFeed.chooseRelease(releases) ?: return UpdateCheckResult.NoUpdate
        if (ignoreDismissedReleases && resolved.tag in dismissedReleases) return UpdateCheckResult.NoUpdate
        return if (VersionComparison.compare(resolved.tag, installedVersionName) == VersionComparisonResult.NEWER) {
            UpdateCheckResult.Update(resolved)
        } else {
            UpdateCheckResult.NoUpdate
        }
    }

    /** spec: SS13.5, SS26 `dismissed_releases`: "Later" adds the tag (with its `v`); the set only grows. */
    fun withDismissed(current: List<String>, tag: String): List<String> = if (tag in current) current else current + tag
}
