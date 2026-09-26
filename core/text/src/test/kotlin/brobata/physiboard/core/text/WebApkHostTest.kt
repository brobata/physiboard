package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: per-app-behavior.md SS2.2, SS13 T37-T39. */
class WebApkHostTest {

    @Test
    fun `T37 a webapk shell package is recognised by its prefix`() {
        assertTrue(WebApkHost.isWebApk("org.chromium.webapk.a5d49fddf77614419_v2"))
        assertFalse(WebApkHost.isWebApk("com.termux"))
    }

    @Test
    fun `a package with no host reads none`() {
        assertNull(WebApkHost.resolve("com.termux", rawRuntimeHost = "anything"))
    }

    @Test
    fun `T38 no metadata value falls back to the default host`() {
        assertEquals("com.android.chrome", WebApkHost.resolve("org.chromium.webapk.missing", rawRuntimeHost = null))
    }

    @Test
    fun `T39 a blank metadata value falls back to the default host`() {
        assertEquals("com.android.chrome", WebApkHost.resolve("org.chromium.webapk.blank", rawRuntimeHost = "  "))
    }

    @Test
    fun `an installed webapk with a declared host uses it`() {
        assertEquals("com.brave.browser", WebApkHost.resolve("org.chromium.webapk.persalink", rawRuntimeHost = "com.brave.browser"))
    }
}
