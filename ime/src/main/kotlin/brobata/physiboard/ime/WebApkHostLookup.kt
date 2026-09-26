package brobata.physiboard.ime

import android.content.Context
import android.content.pm.PackageManager
import brobata.physiboard.core.text.WebApkHost

/**
 * The `:ime` half of per-app-behavior.md SS2.2: only this object touches `PackageManager`, to
 * read the one manifest fact [WebApkHost.resolve] needs (the shell package's own
 * `org.chromium.webapk.shell_apk.runtimeHost` metadata value, or null when the package is not
 * installed or declares none); the pure module decides what that value means. Without this, a
 * WebAPK like the maintainer's own terminal web app can never be told apart from its host browser,
 * so exact typing can only be turned on for the whole browser (every tab), not the one web app.
 */
object WebApkHostLookup {
    private const val RUNTIME_HOST_META_DATA = "org.chromium.webapk.shell_apk.runtimeHost"

    /** A `webApkHost` function bound to [context], suitable for [KeyboardSession]'s constructor. */
    fun forContext(context: Context): (String) -> String? {
        val packageManager = context.packageManager
        return { packageName ->
            if (WebApkHost.isWebApk(packageName)) {
                WebApkHost.resolve(packageName, rawRuntimeHost(packageManager, packageName))
            } else {
                null
            }
        }
    }

    private fun rawRuntimeHost(packageManager: PackageManager, packageName: String): String? = try {
        @Suppress("DEPRECATION")
        val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        info.metaData?.getString(RUNTIME_HOST_META_DATA)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
