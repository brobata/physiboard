package brobata.physiboard.app.shell

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import brobata.physiboard.core.shell.AutoUpdatePolicy
import brobata.physiboard.core.shell.AutoUpdatePolicy.AfterSession
import brobata.physiboard.core.shell.VersionComparison
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Install now", from the "ready, tap to install" notification or the update dialog (app-shell.md
 * SS32.4). The user is here, so this may show Android's own confirmation when a silent install is
 * not allowed. It draws nothing of its own: a toast says what is happening, and it closes when
 * Android answers. Not exported; only PhysiBoard's own notification and screens start it.
 */
class InstallUpdateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_STATUS) {
            handleStatus(intent)
            return
        }
        if (savedInstanceState != null) return
        lifecycleScope.launch { start() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_STATUS) handleStatus(intent)
    }

    private suspend fun start() {
        val app = applicationContext
        val message = withContext(Dispatchers.IO) {
            val store = UpdateStore(app)
            val ready = store.read().ready ?: return@withContext "No update is waiting to be installed."
            val installed = ApkInspector.installedApp(app) ?: return@withContext "PhysiBoard could not read its own version."
            if (AutoUpdatePolicy.isStale(ready, installed.versionCode)) {
                store.discardReady()
                return@withContext "PhysiBoard is already up to date."
            }
            val apk = AutoUpdater.checkedApk(app, store, ready, installed)
                ?: return@withContext "The update did not pass its checks and was deleted."
            val target = PendingIntent.getActivity(
                app,
                REQUEST_STATUS,
                Intent(app, InstallUpdateActivity::class.java).setAction(ACTION_STATUS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ).intentSender
            runCatching { SessionInstaller.commit(app, apk, ready.sha256, target) }
                .onFailure { Log.e(TAG, "install session failed", it) }
                .fold(
                    onSuccess = { "Installing PhysiBoard ${VersionComparison.normalize(ready.tag)}. The keyboard restarts for a moment." },
                    onFailure = { "Android's installer could not start. Try again later." },
                )
        }
        Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        if (!message.startsWith("Installing")) finish()
    }

    private fun handleStatus(intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val outcome = SessionInstaller.outcome(status)
        when (AutoUpdatePolicy.afterSession(outcome, interactive = true)) {
            AfterSession.CONFIRM -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) runCatching { startActivity(confirm) }.onFailure { Log.e(TAG, "confirmation did not open", it) }
            }
            AfterSession.REFUSE -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e(TAG, "Android refused the update: $status $message")
                Toast.makeText(applicationContext, "Android refused the update. It was deleted; the release page has it.", Toast.LENGTH_LONG).show()
                val app = applicationContext
                Thread { UpdateStore(app).read().ready?.let { AutoUpdater.refuse(app, it.tag) } }.start()
            }
            AfterSession.DONE, AfterSession.KEEP, AfterSession.NOTIFY_READY -> Unit
        }
        finish()
    }

    private companion object {
        const val TAG = "InstallUpdate"
        const val ACTION_STATUS = "brobata.physiboard.action.UPDATE_INSTALL_STATUS_INTERACTIVE"
        const val REQUEST_STATUS = 7
    }
}
