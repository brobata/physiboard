package it.palsoftware.pastiera

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import it.palsoftware.pastiera.commands.CommandLaunchSpec

/**
 * Data class describing an installed app.
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false
)

/**
 * Helper that lists every installed app that can be launched.
 */
object AppListHelper {
    private const val TAG = "AppListHelper"
    private const val PREFS_NAME = "app_list_cache_prefs"
    private const val KEY_PACKAGE_CHANGE_SEQUENCE = "package_change_sequence"
    private const val KEY_PACKAGE_CHANGE_BOOT_COUNT = "package_change_boot_count"
    @Volatile
    private var cachedInstalledApps: List<InstalledApp>? = null

    fun getCachedInstalledApps(): List<InstalledApp>? {
        return cachedInstalledApps
    }

    fun invalidateInstalledApps() {
        cachedInstalledApps = null
    }

    fun refreshInstalledApps(context: Context): List<InstalledApp> {
        return getInstalledApps(context, forceRefresh = true)
    }

    fun syncPackageChanges(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentBootCount = Settings.Global.getInt(
            appContext.contentResolver,
            Settings.Global.BOOT_COUNT,
            -1
        )
        val lastBootCount = prefs.getInt(KEY_PACKAGE_CHANGE_BOOT_COUNT, Int.MIN_VALUE)
        val lastSequence = if (lastBootCount == currentBootCount) {
            prefs.getInt(KEY_PACKAGE_CHANGE_SEQUENCE, 0)
        } else {
            0
        }

        val changedPackages = appContext.packageManager.getChangedPackages(lastSequence)
            ?: run {
                if (lastBootCount != currentBootCount) {
                    prefs.edit()
                        .putInt(KEY_PACKAGE_CHANGE_BOOT_COUNT, currentBootCount)
                        .putInt(KEY_PACKAGE_CHANGE_SEQUENCE, 0)
                        .apply()
                }
                return false
            }

        prefs.edit()
            .putInt(KEY_PACKAGE_CHANGE_BOOT_COUNT, currentBootCount)
            .putInt(KEY_PACKAGE_CHANGE_SEQUENCE, changedPackages.sequenceNumber)
            .apply()

        val changedPackageNames = changedPackages.packageNames.orEmpty()
        if (changedPackageNames.isNotEmpty()) {
            invalidateInstalledApps()
            Log.d(TAG, "Package changes detected: $changedPackageNames")
            return true
        }
        return false
    }

    fun handlePackagesChanged(
        context: Context,
        packageNames: Collection<String>,
        removeShortcuts: Boolean = false
    ) {
        if (packageNames.isEmpty()) return

        invalidateInstalledApps()
        if (removeShortcuts) {
            removeLauncherShortcutsForPackages(context, packageNames.toSet())
        }
    }
    
    /**
     * Returns every installed app that can be launched.
     */
    fun getInstalledApps(context: Context, forceRefresh: Boolean = false): List<InstalledApp> {
        if (!forceRefresh) {
            cachedInstalledApps?.let { return it }
        }

        val pm = context.packageManager
        val apps = mutableListOf<InstalledApp>()
        
        try {
            // Every app that has a launcher activity
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            
            // Group by package to avoid duplicates
            val packageNames = mutableSetOf<String>()
            
            for (resolveInfo in resolveInfos) {
                val packageName = resolveInfo.activityInfo.packageName
                
                // Evita duplicati
                if (packageNames.contains(packageName)) {
                    continue
                }
                packageNames.add(packageName)
                
                try {
                    val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(packageName)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    apps.add(InstalledApp(
                        packageName = packageName,
                        appName = appName,
                        icon = icon,
                        isSystemApp = isSystemApp
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load info for $packageName", e)
                }
            }
            
            // Sort alphabetically by name
            apps.sortBy { it.appName.lowercase() }
            cachedInstalledApps = apps.toList()
            
            Log.d(TAG, "Caricate ${apps.size} app installate")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read the installed apps", e)
        }
        
        return apps
    }

    private fun removeLauncherShortcutsForPackages(context: Context, packageNames: Set<String>) {
        SettingsManager.getLauncherShortcuts(context).forEach { (keyCode, shortcut) ->
            val shortcutPackage = shortcut.packageName
                ?: (shortcut.commandLaunch as? CommandLaunchSpec.AppPackage)?.packageName
            if (shortcutPackage in packageNames) {
                SettingsManager.removeLauncherShortcut(context, keyCode)
            }
        }
    }
}
