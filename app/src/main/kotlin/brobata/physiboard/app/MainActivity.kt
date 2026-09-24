package brobata.physiboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.SettingsApp
import brobata.physiboard.app.settings.ui.rememberSettingsController

/**
 * The settings app (settings-catalog.md SS9, "the settings app"): a single activity holding the
 * whole push/pop screen stack (SettingsApp / SettingsNavHost). The milestone-3 typing field this
 * class used to be the entire content of now lives at the "Test field" row
 * (rebuild-from-scratch.md, "Leave MainActivity's typing field reachable from a Test field row").
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as PhysiBoardApplication
        setContent {
            val controller = rememberSettingsController(application.settingsSource.settings, application.settingsStore)
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalSettingsController provides controller) {
                        SettingsApp()
                    }
                }
            }
        }
    }
}
