package it.palsoftware.pastiera.update

internal data class ReleaseAsset(
    val name: String,
    val browserDownloadUrl: String?
)

internal data class GitHubRelease(
    val tagName: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val htmlUrl: String?,
    val assets: List<ReleaseAsset>
)

internal data class ReleaseInfo(
    val tagName: String,
    val downloadUrl: String?,
    val releasePageUrl: String?
)

internal fun findLatestRelease(releases: List<GitHubRelease>, releaseChannel: String): ReleaseInfo? {
    val normalizedChannel = releaseChannel.lowercase()

    for (release in releases) {
        if (release.draft) continue

        val matchesChannel = when (normalizedChannel) {
            "nightly" -> release.prerelease && release.tagName.startsWith("nightly/")
            else -> !release.prerelease
        }
        if (!matchesChannel) continue

        return ReleaseInfo(
            tagName = release.tagName,
            downloadUrl = findApkDownloadUrl(release.assets),
            releasePageUrl = release.htmlUrl?.takeIf(String::isNotBlank)
        )
    }

    return null
}

internal fun findApkDownloadUrl(assets: List<ReleaseAsset>): String? =
    assets.firstNotNullOfOrNull { asset ->
        val isApk = asset.name.lowercase().endsWith(".apk")
        if (isApk) asset.browserDownloadUrl?.takeIf(String::isNotBlank) else null
    }

internal fun normalizeReleaseVersion(version: String): String =
    version.removePrefix("nightly/").removePrefix("v").removePrefix("V")
