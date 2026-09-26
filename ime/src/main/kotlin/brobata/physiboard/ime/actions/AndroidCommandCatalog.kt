package brobata.physiboard.ime.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import brobata.physiboard.core.actions.commands.BuiltInCommands
import brobata.physiboard.core.actions.commands.CommandCatalog
import brobata.physiboard.core.actions.commands.LaunchSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the live command catalogue from the device. spec: expansion-clipboard-pickers-
 * launcher.md SS8.2: apps from the installed-app list, app actions and settings intents "listed
 * only when resolvable", the PhysiBoard and navigation commands always. Which commands exist and
 * what they are called is `:core:actions`' ([BuiltInCommands]); this class only answers the
 * package manager's questions. The result is cached briefly so a key press does not re-query the
 * package manager (SS8.3 wants the live catalogue "each time"; a few seconds is live enough for
 * an install to show up on the next open).
 *
 * SS7.2: "Apps are shown at once from the app-list cache (after syncing package changes) and the
 * full catalog is reloaded in the background." [launchableApps] is the one part of [build] slow
 * enough (a `PackageManager` query over every launchable activity) to be worth answering from a
 * persisted cache instead of blocking the caller: [build] returns immediately with whatever app
 * list [loadCachedApps] already has (empty on the very first run ever), and [reloadAppsInBackground]
 * requeries the package manager off the main thread every time, the same "loader posts to a main
 * handler" shape [brobata.physiboard.ime.DictionaryAssetLoader] and its siblings already use.
 */
class AndroidCommandCatalog(private val context: Context) {

    private var cached: CommandCatalog? = null
    private var cachedAtMs: Long = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cachedApps: List<Pair<String, String>>? = loadCachedApps()
    private var reloadInFlight = false

    /** spec SS7.2: fires once a background reload actually changes the app list, so an already-open sheet can redraw. */
    var onAppsReloaded: (() -> Unit)? = null

    /** spec SS7.2: "'Loading apps...' shows while the list is empty during that reload" -- only ever true before the first app list, cached or fresh, exists. */
    var isLoadingApps: Boolean = cachedApps == null
        private set

    fun build(force: Boolean = false): CommandCatalog {
        val now = System.currentTimeMillis()
        cached?.takeIf { !force && now - cachedAtMs < CACHE_MS }?.let { return it }
        val pm = context.packageManager
        val apps = cachedApps.orEmpty().map { (pkg, label) -> BuiltInCommands.app(pkg, label) }
        val actions = BuiltInCommands.appActions({ resolves(pm, it) }, { appLabel(pm, it) })
        val device = BuiltInCommands.deviceControl(Build.VERSION.SDK_INT, { action -> resolves(pm, LaunchSpec.IntentUri(action)) }, context.packageName)
        val catalog = CommandCatalog(apps + BuiltInCommands.physiboard() + actions + device + BuiltInCommands.navigation())
        cached = catalog
        cachedAtMs = now
        reloadAppsInBackground()
        return catalog
    }

    fun appIcon(packageName: String): Drawable? = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    fun isInstalled(packageName: String): Boolean = runCatching { context.packageManager.getApplicationInfo(packageName, 0); true }.getOrDefault(false)

    /** spec SS7.2: "after syncing package changes": every [build] kicks this off, on top of the periodic reload the short in-memory cache already forces. */
    private fun reloadAppsInBackground() {
        if (reloadInFlight) return
        reloadInFlight = true
        Thread({
            val fresh = launchableApps(context.packageManager)
            saveCachedApps(fresh)
            mainHandler.post {
                reloadInFlight = false
                isLoadingApps = false
                if (fresh != cachedApps) {
                    cachedApps = fresh
                    cached = null // the in-memory catalogue above is now stale app-wise.
                    onAppsReloaded?.invoke()
                }
            }
        }, "physiboard-applist-loader").apply { isDaemon = true }.start()
    }

    private fun launchableApps(pm: PackageManager): List<Pair<String, String>> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = runCatching { pm.queryIntentActivities(launcherIntent, 0) }.getOrDefault(emptyList())
        return activities.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            pkg to (runCatching { info.loadLabel(pm).toString() }.getOrNull() ?: pkg)
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }

    private fun appLabel(pm: PackageManager, packageName: String): String? =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrNull()

    @Suppress("DEPRECATION")
    private fun resolves(pm: PackageManager, spec: LaunchSpec.IntentUri): Boolean =
        runCatching { pm.resolveActivity(spec.toIntent(), 0) != null }.getOrDefault(false)

    private fun loadCachedApps(): List<Pair<String, String>>? = runCatching {
        val raw = context.getSharedPreferences(APP_CACHE_PREFS, Context.MODE_PRIVATE).getString(APP_CACHE_KEY, null) ?: return null
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val pkg = entry.optString(KEY_PACKAGE).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            pkg to entry.optString(KEY_LABEL, pkg)
        }
    }.getOrNull()

    private fun saveCachedApps(apps: List<Pair<String, String>>) {
        runCatching {
            val array = JSONArray()
            for ((pkg, label) in apps) array.put(JSONObject().put(KEY_PACKAGE, pkg).put(KEY_LABEL, label))
            context.getSharedPreferences(APP_CACHE_PREFS, Context.MODE_PRIVATE).edit().putString(APP_CACHE_KEY, array.toString()).apply()
        }
    }

    private companion object {
        const val CACHE_MS = 5_000L
        const val APP_CACHE_PREFS = "quick_launcher_app_cache"
        const val APP_CACHE_KEY = "apps"
        const val KEY_PACKAGE = "p"
        const val KEY_LABEL = "l"
    }
}

/** spec SS8.1: an intent from a launch spec: action, optional data URI, target package or component, categories, and the `flags` strings. */
internal fun LaunchSpec.IntentUri.toIntent(): Intent {
    val intent = Intent(action)
    data?.let { intent.data = Uri.parse(it) }
    packageName?.let { intent.setPackage(it) }
    componentName?.let { android.content.ComponentName.unflattenFromString(it)?.let(intent::setComponent) }
    categories.forEach(intent::addCategory)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (flag in flags) {
        when {
            flag == LaunchSpec.FLAG_CLEAR_TOP -> intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            flag.contains('=') -> intent.putExtra(flag.substringBefore('='), flag.substringAfter('='))
        }
    }
    return intent
}
