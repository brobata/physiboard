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

/**
 * True when [latest] is strictly newer than [current]. Compares dot-separated numeric
 * components (any non-numeric suffix such as "-dev" is ignored), so a local build that is
 * ahead of the newest published release is not reported as outdated. Falls back to a plain
 * inequality when either side has no numeric components.
 */
internal fun isNewerVersion(latest: String, current: String): Boolean {
    val a = versionComponents(latest)
    val b = versionComponents(current)
    if (a.isEmpty() || b.isEmpty()) return latest != current
    val size = maxOf(a.size, b.size)
    for (i in 0 until size) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun versionComponents(version: String): List<Int> =
    version.substringBefore('-').substringBefore('+').split('.')
        .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }
