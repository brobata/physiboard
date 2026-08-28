package brobata.physiboard.inputmethod

import android.content.Context
import android.content.pm.PackageManager

/**
 * Installed web apps (PWAs) are thin WebAPK shells; their text fields live inside the host
 * browser's process, so an IME sees the browser's package name — never the shell's.
 * Per-app rules that target a WebAPK therefore have to apply to its host browser as well.
 */
object WebApkHost {
    private const val WEBAPK_PREFIX = "org.chromium.webapk."
    private const val META_RUNTIME_HOST = "org.chromium.webapk.shell_apk.runtimeHost"
    private const val DEFAULT_HOST = "com.android.chrome"

    fun isWebApk(packageName: String): Boolean = packageName.startsWith(WEBAPK_PREFIX)

    /** Host browser package for a WebAPK, or null when [packageName] isn't one. */
    fun hostBrowser(context: Context, packageName: String): String? {
        if (!isWebApk(packageName)) return null
        return runCatching {
            val info = context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )
            info.metaData?.getString(META_RUNTIME_HOST)?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: DEFAULT_HOST
    }

    /** [packages] plus the host browser of every WebAPK in it. */
    fun expandWithHosts(context: Context, packages: Set<String>): Set<String> {
        if (packages.none(::isWebApk)) return packages
        val expanded = packages.toMutableSet()
        packages.forEach { pkg -> hostBrowser(context, pkg)?.let(expanded::add) }
        return expanded
    }
}
