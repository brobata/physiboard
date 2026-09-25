package brobata.physiboard.ime.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import brobata.physiboard.core.actions.commands.BuiltInCommands
import brobata.physiboard.core.actions.commands.CommandCatalog
import brobata.physiboard.core.actions.commands.LaunchSpec

/**
 * Builds the live command catalogue from the device. spec: expansion-clipboard-pickers-
 * launcher.md SS8.2: apps from the installed-app list, app actions and settings intents "listed
 * only when resolvable", the PhysiBoard and navigation commands always. Which commands exist and
 * what they are called is `:core:actions`' ([BuiltInCommands]); this class only answers the
 * package manager's questions. The result is cached briefly so a key press does not re-query the
 * package manager (SS8.3 wants the live catalogue "each time"; a few seconds is live enough for
 * an install to show up on the next open).
 */
class AndroidCommandCatalog(private val context: Context) {

    private var cached: CommandCatalog? = null
    private var cachedAtMs: Long = 0

    fun build(force: Boolean = false): CommandCatalog {
        val now = System.currentTimeMillis()
        cached?.takeIf { !force && now - cachedAtMs < CACHE_MS }?.let { return it }
        val pm = context.packageManager
        val apps = launchableApps(pm).map { (pkg, label) -> BuiltInCommands.app(pkg, label) }
        val actions = BuiltInCommands.appActions({ resolves(pm, it) }, { appLabel(pm, it) })
        val device = BuiltInCommands.deviceControl(Build.VERSION.SDK_INT, { action -> resolves(pm, LaunchSpec.IntentUri(action)) }, context.packageName)
        val catalog = CommandCatalog(apps + BuiltInCommands.physiboard() + actions + device + BuiltInCommands.navigation())
        cached = catalog
        cachedAtMs = now
        return catalog
    }

    fun appIcon(packageName: String): Drawable? = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    fun isInstalled(packageName: String): Boolean = runCatching { context.packageManager.getApplicationInfo(packageName, 0); true }.getOrDefault(false)

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

    private companion object {
        const val CACHE_MS = 5_000L
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
