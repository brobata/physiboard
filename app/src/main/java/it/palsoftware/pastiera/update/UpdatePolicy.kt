package it.palsoftware.pastiera.update

import android.content.Context
import android.os.Build
import it.palsoftware.pastiera.BuildConfig

private val FDROID_INSTALLERS = setOf(
    "org.fdroid.fdroid",
    "org.fdroid.basic"
)

internal fun isInstalledFromFdroid(context: Context): Boolean {
    val installerPackage = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    } catch (_: Exception) {
        null
    } ?: return false

    return installerPackage in FDROID_INSTALLERS
}

fun shouldUseGithubUpdateChecks(context: Context): Boolean {
    return BuildConfig.ENABLE_GITHUB_UPDATE_CHECKS && !isInstalledFromFdroid(context)
}
