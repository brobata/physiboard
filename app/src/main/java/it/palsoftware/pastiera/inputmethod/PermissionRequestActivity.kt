package it.palsoftware.pastiera.inputmethod

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper activity for requesting RECORD_AUDIO permission.
 * Used by services that cannot request runtime permissions directly.
 */
class PermissionRequestActivity : Activity() {
    companion object {
        private const val TAG = "PermissionRequest"
        const val ACTION_PERMISSION_GRANTED = "it.palsoftware.pastiera.PERMISSION_GRANTED"
        const val ACTION_PERMISSION_DENIED = "it.palsoftware.pastiera.PERMISSION_DENIED"
        const val REQUEST_CODE_PERMISSION = 1002
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "PermissionRequestActivity onCreate - requesting RECORD_AUDIO permission")
        
        // Check if permission is already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "RECORD_AUDIO permission already granted")
            sendPermissionResult(true)
            finish()
            return
        }
        
        // Request permission
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_CODE_PERMISSION
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_CODE_PERMISSION) {
            val granted = grantResults.isNotEmpty() && 
                         grantResults[0] == PackageManager.PERMISSION_GRANTED
            
            if (granted) {
                Log.i(TAG, "RECORD_AUDIO permission granted")
            } else {
                Log.w(TAG, "RECORD_AUDIO permission denied by user")
            }
            
            sendPermissionResult(granted)
            finish()
        }
    }
    
    private fun sendPermissionResult(granted: Boolean) {
        val action = if (granted) ACTION_PERMISSION_GRANTED else ACTION_PERMISSION_DENIED
        val broadcastIntent = Intent(action).apply {
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
        Log.d(TAG, "Broadcast sent: $action")
    }
    
    override fun finish() {
        super.finish()
        // Remove activity from recent tasks
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                finishAndRemoveTask()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}

