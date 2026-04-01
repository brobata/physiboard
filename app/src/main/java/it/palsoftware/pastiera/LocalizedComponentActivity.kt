package it.palsoftware.pastiera

import android.content.Context
import androidx.activity.ComponentActivity

open class LocalizedComponentActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }
}

