package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** spec: per-app-behavior.md SS2.1, SS2.2, SS4.5. */
class AppProfileResolverTest {

    private val persaLink = AppProfile(packageName = "org.chromium.webapk.persalink", exactTypingEnabled = true)
    private val termux = AppProfile(packageName = "com.termux", exactTypingEnabled = true)
    private val webApkHost: (String) -> String? = { pkg -> if (pkg.startsWith("org.chromium.webapk.")) "com.android.chrome" else null }

    @Test
    fun `a null package matches nothing`() {
        assertEquals(AppProfile.default(null), AppProfileResolver.resolve(null, listOf(persaLink, termux), webApkHost))
    }

    @Test
    fun `a blank package matches nothing`() {
        assertEquals(AppProfile.default(""), AppProfileResolver.resolve("", listOf(persaLink, termux), webApkHost))
    }

    @Test
    fun `an exact package match wins over any expansion`() {
        assertSame(termux, AppProfileResolver.resolve("com.termux", listOf(persaLink, termux), webApkHost))
    }

    @Test
    fun `a web app installed to the home screen is matched by its own identity through the host expansion`() {
        // The field reports Chrome's package (per-app-behavior.md SS2.2), but the profile is filed
        // under PersaLink's own WebAPK shell identity (SS4.5); resolving "com.android.chrome" must
        // still find it via the host lookup.
        assertSame(persaLink, AppProfileResolver.resolve("com.android.chrome", listOf(persaLink, termux), webApkHost))
    }

    @Test
    fun `a profile filed directly under the reported package beats one that only expands to it`() {
        val chromeItself = AppProfile(packageName = "com.android.chrome", exactTypingEnabled = false)
        assertSame(chromeItself, AppProfileResolver.resolve("com.android.chrome", listOf(chromeItself, persaLink), webApkHost))
    }

    @Test
    fun `an unlisted app resolves to the default profile`() {
        assertEquals(AppProfile.default("com.example.unknown"), AppProfileResolver.resolve("com.example.unknown", listOf(persaLink, termux), webApkHost))
    }

    @Test
    fun `no host lookup supplied still matches an exact package`() {
        assertSame(termux, AppProfileResolver.resolve("com.termux", listOf(termux)))
    }
}
