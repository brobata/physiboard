package it.palsoftware.pastiera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import it.palsoftware.pastiera.ui.theme.PastieraTheme

class SymCustomizationActivity : LocalizedComponentActivity() {
    companion object {
        const val EXTRA_INITIAL_PAGE = "it.palsoftware.pastiera.extra.INITIAL_SYM_PAGE"
        const val EXTRA_INITIAL_KEY_CODE = "it.palsoftware.pastiera.extra.INITIAL_SYM_KEY_CODE"
        const val EXTRA_OPEN_PICKER = "it.palsoftware.pastiera.extra.OPEN_SYM_PICKER"
        const val EXTRA_RETURN_AFTER_PICKER = "it.palsoftware.pastiera.extra.RETURN_AFTER_PICKER"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            applySlideInFromRightTransition()
        }
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                SymCustomizationScreen(
                    modifier = Modifier.fillMaxSize(),
                    initialPage = intent.getIntExtra(EXTRA_INITIAL_PAGE, 0),
                    initialKeyCode = intent.getIntExtra(EXTRA_INITIAL_KEY_CODE, 0).takeIf { it > 0 },
                    openInitialPicker = intent.getBooleanExtra(EXTRA_OPEN_PICKER, false),
                    returnAfterInitialPicker = intent.getBooleanExtra(EXTRA_RETURN_AFTER_PICKER, false),
                    onInitialPickerClosed = {
                        SettingsManager.confirmPendingRestoreSymPage(this@SymCustomizationActivity)
                        finish()
                    },
                    onBack = {
                        // This is only called from the UI back button (arrow icon)
                        SettingsManager.confirmPendingRestoreSymPage(this@SymCustomizationActivity)
                        finish()
                    }
                )
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Confirm pending restore when activity is paused (user navigates away)
        // This handles both back button and other navigation methods
        if (isFinishing) {
            SettingsManager.confirmPendingRestoreSymPage(this)
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
    
    override fun finish() {
        super.finish()
        applySlideOutToRightTransition()
    }
}
