package brobata.physiboard.app.shell

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs [AutoUpdater.installWhenQuiet] once the screen has been off for two minutes (app-shell.md
 * SS32.4). If the install replaces PhysiBoard while this runs, the process ends with it; WorkManager
 * runs the work again in the new process, which finds the update installed and only tidies up.
 */
class UpdateInstallWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runCatching { AutoUpdater.installWhenQuiet(applicationContext) }.onFailure { Log.e("UpdateInstall", "install attempt crashed", it) }
        return Result.success()
    }
}
