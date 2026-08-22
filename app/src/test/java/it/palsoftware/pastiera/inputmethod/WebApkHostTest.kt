package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebApkHostTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun recognisesWebApkPackages() {
        assertTrue(WebApkHost.isWebApk("org.chromium.webapk.a5d49fddf77614419_v2"))
        assertFalse(WebApkHost.isWebApk("com.termux"))
    }

    @Test
    fun nonWebApkHasNoHost() {
        assertEquals(null, WebApkHost.hostBrowser(context, "com.termux"))
    }

    @Test
    fun uninstalledWebApkFallsBackToChrome() {
        assertEquals("com.android.chrome", WebApkHost.hostBrowser(context, "org.chromium.webapk.missing"))
    }

    @Test
    fun expandAddsHostBrowserForWebApksOnly() {
        val expanded = WebApkHost.expandWithHosts(context, setOf("com.termux", "org.chromium.webapk.x"))
        assertEquals(setOf("com.termux", "org.chromium.webapk.x", "com.android.chrome"), expanded)
        assertEquals(setOf("com.termux"), WebApkHost.expandWithHosts(context, setOf("com.termux")))
    }
}
