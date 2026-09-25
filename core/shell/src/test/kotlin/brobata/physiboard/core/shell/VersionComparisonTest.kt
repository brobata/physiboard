package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: app-shell.md SS13.4, SS29 T1-T7. */
class VersionComparisonTest {

    private fun update(latest: String, current: String) = VersionComparison.compare(latest, current)

    @Test
    fun `T1 latest 1_0_2 vs current 1_0_1 is an update`() {
        assertEquals(VersionComparisonResult.NEWER, update("1.0.2", "1.0.1"))
    }

    @Test
    fun `T2 equal versions are not an update`() {
        assertEquals(VersionComparisonResult.NOT_NEWER, update("1.0.1", "1.0.1"))
    }

    @Test
    fun `T3 a local build ahead of the release is not an update`() {
        assertEquals(VersionComparisonResult.NOT_NEWER, update("1.0.1", "1.0.2"))
        assertEquals(VersionComparisonResult.NOT_NEWER, update("1.0.1", "1.1"))
    }

    @Test
    fun `T4 missing parts count as zero`() {
        assertEquals(VersionComparisonResult.NEWER, update("1.1", "1.0.9"))
        assertEquals(VersionComparisonResult.NOT_NEWER, update("1.0", "1.0.0"))
    }

    @Test
    fun `T5 a pre-release suffix on the local build is ignored`() {
        assertEquals(VersionComparisonResult.NEWER, update("1.0.2", "1.0.1-dev"))
        assertEquals(VersionComparisonResult.NOT_NEWER, update("1.0.1", "1.0.1-dev"))
    }

    @Test
    fun `T6 non-numeric versions fall back to string comparison`() {
        assertEquals(VersionComparisonResult.NEWER, update("beta", "alpha"))
        assertEquals(VersionComparisonResult.NOT_NEWER, update("beta", "beta"))
    }

    @Test
    fun `T7 the v prefix is normalized away`() {
        assertEquals("2.0.7", VersionComparison.normalize("v2.0.7"))
        assertEquals("2.0.7", VersionComparison.normalize("V2.0.7"))
        assertEquals("2.0.7", VersionComparison.normalize("2.0.7"))
    }
}
