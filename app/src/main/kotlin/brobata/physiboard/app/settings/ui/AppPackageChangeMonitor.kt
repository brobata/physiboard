package brobata.physiboard.app.settings.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import brobata.physiboard.app.settings.SettingsStore
import brobata.physiboard.core.actions.launcher.LauncherShortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * spec: per-app-behavior.md SS7. Registered once at process start ([register], called from
 * `PhysiBoardApplication.onCreate`), this is the only thing PhysiBoard runs in reaction to Android
 * reporting a package add, remove, replace or change: it invalidates [AppCatalog]'s cache
 * (step 1) and, for a true uninstall (`PACKAGE_REMOVED` with `EXTRA_REPLACING` false, not an
 * update, step 2), removes every launcher shortcut that targeted the removed package.
 *
 * The sequence-number sync 2.x used to catch changes missed while the process was dead ("the
 * sequence sync exists for changes missed while the process was dead") is not carried over:
 * nothing here persists the installed-app list, so a process that was dead for an install or
 * uninstall simply rescans on its next [AppCatalog.installedApps] call, matching section 14's own
 * "drop if the list is rebuilt on open" verdict for that mechanism.
 */
class AppPackageChangeMonitor(
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) : BroadcastReceiver() {

    /** spec SS7: "A broadcast receiver registered at process start (exported, package scheme)". */
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        // spec SS7 step 1: "The in-memory list is invalidated" for every one of the four actions.
        AppCatalog.invalidate()
        // spec SS7 step 2: only a true uninstall prunes launcher shortcuts; an update
        // (PACKAGE_REPLACED, or PACKAGE_REMOVED with EXTRA_REPLACING true) "changes nothing".
        if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            // An unguarded DataStore write here (a DataStore I/O failure -- low storage, concurrent
            // writer contention) would crash the whole process from an unrelated app's uninstall;
            // guarded the same way every other settings write in the app is (StoreBridge.write).
            scope.launch {
                runCatching {
                    settingsStore.update { settings ->
                        val pruned = LauncherShortcuts.parse(settings.launcher.assignedKeysJson).removeTargeting(packageName)
                        settings.copy(launcher = settings.launcher.copy(assignedKeysJson = LauncherShortcuts.encode(pruned)))
                    }
                }.onFailure { error -> Log.e(TAG, "launcher shortcut prune on uninstall failed", error) }
            }
        }
    }

    private companion object {
        const val TAG = "AppPackageChangeMonitor"
    }
}
