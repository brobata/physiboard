package it.palsoftware.pastiera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import it.palsoftware.pastiera.ui.theme.PastieraTheme

class SymCustomizationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                SymCustomizationScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = {
                        // Confirm pending restore when user presses back
                        SettingsManager.confirmPendingRestoreSymPage(this@SymCustomizationActivity)
                        finish()
                    }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // If activity is destroyed without finish() (e.g., user goes to another app),
        // clear the pending restore to avoid restoring SYM layout
        if (!isFinishing) {
            SettingsManager.clearPendingRestoreSymPage(this)
        }
    }
}

