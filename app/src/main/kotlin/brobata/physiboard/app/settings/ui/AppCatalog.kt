package brobata.physiboard.app.settings.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** One installed app as a per-app picker row shows it: what it is called, and its identity in the store. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Backs every per-app list the settings app shows (rebuild-from-scratch.md: "a searchable app
 * picker over installed packages with a switch per app, used by exact typing, the status-bar app
 * list, the dip list and the Enter overrides"). Only `:app` may touch `PackageManager`; the typed
 * schema in `:core:settings` never sees an Android type.
 */
object AppCatalog {
    /**
     * Every launchable app, plus [alsoInclude] (packages already on a list that may no longer
     * have a launcher activity, such as an uninstalled or system-hidden app the user once picked;
     * dropping them from the picker would silently orphan the store's row).
     */
    fun installedApps(context: Context, alsoInclude: Set<String> = emptySet()): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val launchable = pm.queryIntentActivities(launcherIntent, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
        val packages = launchable + alsoInclude
        return packages.map { pkg -> InstalledApp(pkg, labelFor(pm, pkg)) }.sortedBy { it.label.lowercase() }
    }

    private fun labelFor(pm: PackageManager, packageName: String): String = try {
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
