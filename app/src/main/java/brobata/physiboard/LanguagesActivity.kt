package brobata.physiboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import brobata.physiboard.ui.theme.PhysiBoardTheme

class LanguagesActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            applySlideInFromRightTransition()
        }
        enableEdgeToEdge()
        setContent {
            PhysiBoardTheme {
                LanguagesScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    override fun finish() {
        super.finish()
        applySlideOutToRightTransition()
    }
}
