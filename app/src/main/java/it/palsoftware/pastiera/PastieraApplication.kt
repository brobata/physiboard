package it.palsoftware.pastiera

import android.app.Application

class PastieraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPackageChangeMonitor.register(this)
    }
}
