package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: app-shell.md SS13.2, SS13.3, SS13.5, SS29 T8-T11. */
class ReleaseFeedTest {

    private val t8Json = """
        [
          {"tag_name": "v2.1.0", "prerelease": true, "draft": false, "html_url": "https://x/2.1.0", "assets": []},
          {"tag_name": "v2.0.8", "prerelease": false, "draft": true, "html_url": "https://x/2.0.8", "assets": []},
          {"tag_name": "v2.0.7", "prerelease": false, "draft": false, "html_url": "https://x/2.0.7",
           "assets": [{"name": "a.txt", "browser_download_url": "https://x/a.txt"},
                      {"name": "physiboard-2.0.7.APK", "browser_download_url": "https://x/physiboard-2.0.7.APK"}]},
          {"tag_name": "v2.0.6", "prerelease": false, "draft": false, "html_url": "https://x/2.0.6", "assets": []}
        ]
    """.trimIndent()

    @Test
    fun `T8 chooses the first non-draft non-prerelease and its apk asset`() {
        val resolved = ReleaseFeed.chooseRelease(ReleaseFeed.parseReleases(t8Json))
        assertEquals("v2.0.7", resolved?.tag)
        assertEquals("https://x/physiboard-2.0.7.APK", resolved?.apkDownloadUrl)
        assertEquals("https://x/2.0.7", resolved?.pageUrl)
    }

    @Test
    fun `T9 only pre-releases resolve to nothing`() {
        val json = """[{"tag_name":"v1","prerelease":true,"draft":false,"assets":[]},{"tag_name":"v2","prerelease":true,"draft":false,"assets":[]}]"""
        assertNull(ReleaseFeed.chooseRelease(ReleaseFeed.parseReleases(json)))
    }

    @Test
    fun `T10 a release with no apk asset has no download url`() {
        val json = """[{"tag_name":"v1","prerelease":false,"draft":false,"html_url":"https://x","assets":[{"name":"readme.txt","browser_download_url":"https://x/r.txt"}]}]"""
        val resolved = ReleaseFeed.chooseRelease(ReleaseFeed.parseReleases(json))
        assertNull(resolved?.apkDownloadUrl)
    }

    @Test
    fun `T11 a dismissed release is ignored only when the trigger ignores dismissals`() {
        val json = """[{"tag_name":"v2.0.7","prerelease":false,"draft":false,"html_url":"https://x","assets":[]}]"""
        val releases = ReleaseFeed.parseReleases(json)
        val dismissed = setOf("v2.0.7")

        val ignoring = UpdatePolicy.decide(releases, installedVersionName = "2.0.6", dismissedReleases = dismissed, ignoreDismissedReleases = true)
        assertEquals(UpdateCheckResult.NoUpdate, ignoring)

        val notIgnoring = UpdatePolicy.decide(releases, installedVersionName = "2.0.6", dismissedReleases = dismissed, ignoreDismissedReleases = false)
        assertTrue(notIgnoring is UpdateCheckResult.Update)
    }

    @Test
    fun `a blank tag_name is skipped`() {
        val json = """[{"tag_name":"","prerelease":false,"draft":false,"assets":[]},{"tag_name":"v1.0.0","prerelease":false,"draft":false,"assets":[]}]"""
        assertEquals("v1.0.0", ReleaseFeed.chooseRelease(ReleaseFeed.parseReleases(json))?.tag)
    }

    @Test
    fun `malformed json yields no releases instead of throwing`() {
        assertEquals(emptyList(), ReleaseFeed.parseReleases("<html>rate limited</html>"))
        assertEquals(emptyList(), ReleaseFeed.parseReleases("{}"))
    }

    @Test
    fun `withDismissed only grows and never duplicates a tag`() {
        val once = UpdatePolicy.withDismissed(emptyList(), "v2.0.7")
        assertEquals(listOf("v2.0.7"), once)
        assertEquals(listOf("v2.0.7"), UpdatePolicy.withDismissed(once, "v2.0.7"))
    }
}
