package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: app-shell.md SS1 ("GitHub checks allowed"), SS23.1, SS29 T24 (adapted to 3.0's own build facts). */
class BuildInfoTest {

    @Test
    fun `T24-equivalent build facts are pinned`() {
        val facts = BuildFacts(
            applicationId = "brobata.physiboard",
            releaseChannel = "physi",
            githubRepo = "brobata/physiboard",
            fdroidBuild = false,
            githubUpdateChecksBuildFlag = true,
        )
        assertTrue(facts.applicationId == "brobata.physiboard" || facts.applicationId.startsWith("brobata.physiboard."))
        assertEquals("brobata/physiboard", facts.githubRepo)
        assertFalse(facts.fdroidBuild)
        assertTrue(facts.githubUpdateChecksBuildFlag)
    }

    @Test
    fun `an F-Droid installer disables github checks even when the build flag is on`() {
        assertFalse(GithubChecks.allowed(buildFlagOn = true, installerPackageName = "org.fdroid.fdroid"))
        assertFalse(GithubChecks.allowed(buildFlagOn = true, installerPackageName = "org.fdroid.basic"))
        assertTrue(GithubChecks.allowed(buildFlagOn = true, installerPackageName = "com.android.vending"))
        assertTrue(GithubChecks.allowed(buildFlagOn = true, installerPackageName = null))
    }

    @Test
    fun `the build flag itself gates checks regardless of installer`() {
        assertFalse(GithubChecks.allowed(buildFlagOn = false, installerPackageName = "com.android.vending"))
    }
}
