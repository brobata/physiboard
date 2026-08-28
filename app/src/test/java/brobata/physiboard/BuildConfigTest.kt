package brobata.physiboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {

    /**
     * The applicationId is the app's identity to Android: change it and the update path breaks and
     * the migration can no longer read the old data directory. Assert it rather than trust it.
     */
    @Test
    fun theApplicationIdIsTheOneUsersAlreadyHaveInstalled() {
        assertEquals("brobata.physiboard", BuildConfig.APPLICATION_ID.removeSuffix(".sideload"))
    }

    @Test
    fun thereIsOneReleaseChannelAndItPointsAtThisFork() {
        assertEquals("physi", BuildConfig.RELEASE_CHANNEL)
        assertEquals("brobata/physiboard", BuildConfig.GITHUB_REPO)
        assertFalse(BuildConfig.IS_FDROID_BUILD)
        assertTrue(BuildConfig.ENABLE_GITHUB_UPDATE_CHECKS)
    }

    @Test
    fun theBuildInfoStringCarriesTheVersion() {
        assertTrue(BuildInfo.getBuildInfoString().contains(BuildConfig.VERSION_NAME))
    }
}
