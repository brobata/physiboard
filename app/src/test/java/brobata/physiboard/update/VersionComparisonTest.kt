package brobata.physiboard.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {

    @Test
    fun newerPatchIsAnUpdate() {
        assertTrue(isNewerVersion("1.0.2", "1.0.1"))
    }

    @Test
    fun sameVersionIsNotAnUpdate() {
        assertFalse(isNewerVersion("1.0.1", "1.0.1"))
    }

    @Test
    fun localBuildAheadOfLatestReleaseIsNotAnUpdate() {
        assertFalse(isNewerVersion("1.0.1", "1.0.2"))
        assertFalse(isNewerVersion("1.0.1", "1.1"))
    }

    @Test
    fun missingComponentsAreTreatedAsZero() {
        assertTrue(isNewerVersion("1.1", "1.0.9"))
        assertFalse(isNewerVersion("1.0", "1.0.0"))
    }

    @Test
    fun suffixesAreIgnored() {
        assertTrue(isNewerVersion("1.0.2", "1.0.1-dev"))
        assertFalse(isNewerVersion("1.0.1", "1.0.1-dev"))
    }

    @Test
    fun nonNumericVersionsFallBackToInequality() {
        assertTrue(isNewerVersion("beta", "alpha"))
        assertFalse(isNewerVersion("beta", "beta"))
    }
}
