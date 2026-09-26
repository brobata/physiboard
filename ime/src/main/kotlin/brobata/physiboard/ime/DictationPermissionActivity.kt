package brobata.physiboard.ime

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * spec: dictation.md SS10. A keyboard service cannot show the runtime permission dialog itself, so
 * [DictationController] opens this translucent, title-less activity when a trigger fires with
 * `RECORD_AUDIO` not yet granted, and resumes the pending start once the answer comes back.
 *
 * The answer travels back over the package-restricted broadcast pair spec SS10 describes:
 * [ACTION_GRANTED] or [ACTION_DENIED], each sent with [Intent.setPackage] pinned to this app's own
 * package, so nothing outside `brobata.physiboard` can see or spoof it. [DictationController]
 * registers the receiver that answers it.
 */
class DictationPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            sendResult(granted = true)
            finish()
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        sendResult(granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        finish()
    }

    private fun sendResult(granted: Boolean) {
        sendBroadcast(Intent(if (granted) ACTION_GRANTED else ACTION_DENIED).setPackage(packageName))
    }

    companion object {
        /** spec SS10 step 2: `brobata.physiboard.PERMISSION_GRANTED`. */
        const val ACTION_GRANTED = "brobata.physiboard.PERMISSION_GRANTED"

        /** spec SS10 step 2: `brobata.physiboard.PERMISSION_DENIED`. */
        const val ACTION_DENIED = "brobata.physiboard.PERMISSION_DENIED"
        private const val REQUEST_CODE = 1
    }
}
