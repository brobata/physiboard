package it.palsoftware.pastiera.ui

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.update.checkForUpdate
import it.palsoftware.pastiera.update.showUpdateDialog
import it.palsoftware.pastiera.update.shouldUseGithubUpdateChecks

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.check_updates_button).setOnClickListener {
            if (!shouldUseGithubUpdateChecks(this)) {
                Toast.makeText(this, getString(R.string.settings_update_up_to_date), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkForUpdate(
                context = this,
                currentVersion = BuildConfig.VERSION_NAME,
                releaseChannel = BuildConfig.RELEASE_CHANNEL,
                ignoreDismissedReleases = false
            ) { hasUpdate, latestVersion, downloadUrl, releasePageUrl ->
                if (hasUpdate && latestVersion != null) {
                    showUpdateDialog(this, latestVersion, downloadUrl, releasePageUrl)
                } else {
                    Toast.makeText(this, getString(R.string.settings_update_up_to_date), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
