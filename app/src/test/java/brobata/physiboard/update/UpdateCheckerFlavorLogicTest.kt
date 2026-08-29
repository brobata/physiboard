package brobata.physiboard.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerFlavorLogicTest {

    @Test
    fun latestReleaseSkipsPrereleases() {
        val release = findLatestRelease(sampleReleases())

        requireNotNull(release)
        assertEquals("v0.85", release.tagName)
        assertEquals("https://example.com/stable.apk", release.downloadUrl)
        assertEquals("https://example.com/releases/v0.85", release.releasePageUrl)
    }

    @Test
    fun onlyPrereleasesYieldsNothing() {
        val releases = listOf(
            GitHubRelease(
                tagName = "beta/v0.85-beta1",
                prerelease = true,
                draft = false,
                htmlUrl = "https://example.com/releases/beta",
                assets = emptyList()
            )
        )

        assertNull(findLatestRelease(releases))
    }

    @Test
    fun normalizeReleaseVersionStripsKnownPrefixes() {
        assertEquals("0.85", normalizeReleaseVersion("v0.85"))
        assertEquals("0.85", normalizeReleaseVersion("V0.85"))
    }

    private fun sampleReleases(): List<GitHubRelease> =
        listOf(
            GitHubRelease(
                tagName = "v0.86-rc1",
                prerelease = true,
                draft = false,
                htmlUrl = "https://example.com/releases/v0.86-rc1",
                assets = listOf(
                    ReleaseAsset(
                        name = "physiboard-rc.apk",
                        browserDownloadUrl = "https://example.com/rc.apk"
                    )
                )
            ),
            GitHubRelease(
                tagName = "v0.85",
                prerelease = false,
                draft = false,
                htmlUrl = "https://example.com/releases/v0.85",
                assets = listOf(
                    ReleaseAsset(
                        name = "pastiera-stable.apk",
                        browserDownloadUrl = "https://example.com/stable.apk"
                    )
                )
            ),
            GitHubRelease(
                tagName = "v0.84",
                prerelease = false,
                draft = false,
                htmlUrl = "https://example.com/releases/v0.84",
                assets = emptyList()
            )
        )
}
