package brobata.physiboard.app.settings.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import brobata.physiboard.core.text.WebApkHost
import brobata.physiboard.ime.WebApkHostLookup

/** One installed app as a per-app picker row shows it: what it is called, and its identity in the store. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Backs every per-app list the settings app shows (rebuild-from-scratch.md: "a searchable app
 * picker over installed packages with a switch per app, used by Terminal mode, the status-bar app
 * list, the dip list and the Enter overrides"). Only `:app` may touch `PackageManager`; the typed
 * schema in `:core:settings` never sees an Android type.
 *
 * spec: per-app-behavior.md SS7, "The installed-app list used by the picker is cached in memory."
 * [invalidate] is called by [AppPackageChangeMonitor] whenever Android reports a package add,
 * remove, replace or change, so a picker opened after such an event re-scans instead of showing a
 * stale list; nothing else in this object talks to that receiver.
 */
object AppCatalog {
    @Volatile
    private var cachedLaunchable: Map<String, String>? = null

    /**
     * Every launchable app, plus [alsoInclude] (packages already on a list that may no longer
     * have a launcher activity, such as an uninstalled or system-hidden app the user once picked;
     * dropping them from the picker would silently orphan the store's row).
     */
    fun installedApps(context: Context, alsoInclude: Set<String> = emptySet()): List<InstalledApp> {
        val pm = context.packageManager
        val launchable = cachedLaunchable ?: queryLaunchable(pm).also { cachedLaunchable = it }
        val packages = launchable.keys + alsoInclude
        return packages.map { pkg -> InstalledApp(pkg, launchable[pkg] ?: labelFor(pm, pkg)) }.sortedBy { it.label.lowercase() }
    }

    /** spec SS7 step 1: "the in-memory list is invalidated" on any package-change broadcast. */
    fun invalidate() {
        cachedLaunchable = null
    }

    /**
     * spec: per-app-behavior.md SS4.3: the "Web app - types inside Chrome, so Chrome is excluded
     * too" note the exact-typing list shows under a WebAPK row; null for anything that is not a
     * WebAPK. The host label falls back to the host's own package name when it cannot be read
     * ("package name if it cannot be read").
     */
    fun webApkNote(context: Context, packageName: String): String? {
        if (!WebApkHost.isWebApk(packageName)) return null
        val host = WebApkHostLookup.forContext(context)(packageName) ?: WebApkHost.DEFAULT_HOST
        val label = labelFor(context.packageManager, host)
        return "Web app - types inside $label, so $label is excluded too"
    }

    /** spec: device-backlight-ring.md SS5.4: the "App colours" list shows the app's label, package name when it cannot be read, never the bare package id. */
    fun labelFor(context: Context, packageName: String): String = labelFor(context.packageManager, packageName)

    /** spec: per-app-behavior.md SS3.11 step 3, "every installed favourite" needs this fact to decide which rows a preset writes. */
    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Every launchable package with the label its launcher entry carries. The launcher activity's
     * own label is read here rather than the application's because a WebAPK has no application
     * label at all: PersaLink came out as the forty-character
     * `org.chromium.webapk.a5d49fddf77614419_v2` on the exact-typing screen, which is the one
     * screen that decides how that terminal types (2026-09-29). One query answers every package,
     * so this costs no more than the package list it already fetched.
     */
    private fun queryLaunchable(pm: PackageManager): Map<String, String> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(launcherIntent, 0)
        val labels = LinkedHashMap<String, String>()
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() && it != pkg }
            labels.putIfAbsent(pkg, label ?: labelFor(pm, pkg))
        }
        return labels
    }

    private fun labelFor(pm: PackageManager, packageName: String): String = try {
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
