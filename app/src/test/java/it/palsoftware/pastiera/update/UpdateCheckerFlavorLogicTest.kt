package it.palsoftware.pastiera.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerFlavorLogicTest {

    @Test
    fun stableChannelUsesLatestNonPrerelease() {
        val release = findLatestRelease(sampleReleases(), "stable")

        requireNotNull(release)
        assertEquals("v0.85", release.tagName)
        assertEquals("https://example.com/stable.apk", release.downloadUrl)
        assertEquals("https://example.com/releases/v0.85", release.releasePageUrl)
    }

    @Test
    fun nightlyChannelUsesLatestNightlyPrerelease() {
        val release = findLatestRelease(sampleReleases(), "nightly")

        requireNotNull(release)
        assertEquals("nightly/v0.85-nightly.20260306.214144", release.tagName)
        assertEquals("https://example.com/nightly.apk", release.downloadUrl)
        assertEquals("https://example.com/releases/nightly-v0.85-nightly.20260306.214144", release.releasePageUrl)
    }

    @Test
    fun nightlyChannelIgnoresNonNightlyPrereleases() {
        val releases = listOf(
            GitHubRelease(
                tagName = "beta/v0.85-beta1",
                prerelease = true,
                draft = false,
                htmlUrl = "https://example.com/releases/beta",
                assets = emptyList()
            )
        )

        assertNull(findLatestRelease(releases, "nightly"))
    }

    @Test
    fun normalizeReleaseVersionStripsKnownPrefixes() {
        assertEquals("0.85", normalizeReleaseVersion("v0.85"))
        assertEquals("0.85", normalizeReleaseVersion("V0.85"))
        assertEquals("0.85-nightly.20260306.214144", normalizeReleaseVersion("nightly/v0.85-nightly.20260306.214144"))
    }

    private fun sampleReleases(): List<GitHubRelease> =
        listOf(
            GitHubRelease(
                tagName = "nightly/v0.85-nightly.20260306.214144",
                prerelease = true,
                draft = false,
                htmlUrl = "https://example.com/releases/nightly-v0.85-nightly.20260306.214144",
                assets = listOf(
                    ReleaseAsset(
                        name = "pastiera-nightly.apk",
                        browserDownloadUrl = "https://example.com/nightly.apk"
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
