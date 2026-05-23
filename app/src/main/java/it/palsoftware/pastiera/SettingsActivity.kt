package it.palsoftware.pastiera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import it.palsoftware.pastiera.ui.theme.PastieraTheme

class SettingsActivity : LocalizedComponentActivity() {
    companion object {
        const val EXTRA_DESTINATION = "it.palsoftware.pastiera.SETTINGS_DESTINATION"
        const val DESTINATION_CUSTOMIZATION = "customization"
        const val EXTRA_CUSTOMIZATION_DESTINATION = "it.palsoftware.pastiera.CUSTOMIZATION_DESTINATION"
        const val CUSTOMIZATION_DESTINATION_VARIATIONS = "variations"
        const val CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS = "launcher_shortcuts"
        const val CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR = "app_enter_behavior"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            overridePendingTransition(R.anim.slide_in_from_right, 0)
        }
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    initialDestination = intent.getStringExtra(EXTRA_DESTINATION),
                    initialCustomizationDestination = intent.getStringExtra(EXTRA_CUSTOMIZATION_DESTINATION)
                )
            }
        }
    }
    
    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_out_to_right)
    }
}
