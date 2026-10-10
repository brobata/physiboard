package brobata.physiboard.app.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Android's answer to a background install session (app-shell.md SS32.3). Not exported: only the
 * PendingIntent PhysiBoard hands its own installer session can reach it.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val pending = goAsync()
        Thread {
            try {
                AutoUpdater.onBackgroundSessionResult(context.applicationContext, status, sessionId, message)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_STATUS = "brobata.physiboard.action.UPDATE_INSTALL_STATUS"
    }
}
