package it.palsoftware.pastiera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppPackageChangeMonitor {
    private const val TAG = "AppPackageChangeMonitor"
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handlePackageIntent(context, intent)
        }
    }

    @Synchronized
    fun register(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        registered = true
        if (AppListHelper.syncPackageChanges(appContext)) {
            monitorScope.launch {
                AppListHelper.refreshInstalledApps(appContext)
            }
        }
    }

    internal fun handlePackageIntent(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val packageName = intent.data?.schemeSpecificPart ?: return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val removeShortcuts = intent.action == Intent.ACTION_PACKAGE_REMOVED && !replacing

        AppListHelper.handlePackagesChanged(
            context = appContext,
            packageNames = listOf(packageName),
            removeShortcuts = removeShortcuts
        )
        monitorScope.launch {
            AppListHelper.syncPackageChanges(appContext)
            AppListHelper.refreshInstalledApps(appContext)
        }
        Log.d(TAG, "Package cache invalidated for ${intent.action}: $packageName")
    }
}
