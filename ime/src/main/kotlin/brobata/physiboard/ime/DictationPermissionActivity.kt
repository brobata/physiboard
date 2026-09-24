package brobata.physiboard.ime

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * spec: dictation.md SS10. A keyboard service cannot show the runtime permission dialog itself, so
 * [DictationController] opens this translucent, title-less activity when a trigger fires with
 * `RECORD_AUDIO` not yet granted, and resumes the pending start once the answer comes back.
 *
 * spec SS10 describes the answer travelling back to the keyboard service over a package-restricted
 * broadcast, because the activity there could in principle run in a different process. On this
 * build the activity and the input method service are the same app's single default process, so
 * [DictationPermissionBridge] delivers the answer with a direct in-process callback instead; the
 * externally visible behaviour (asks once, resumes the pending start on grant, does nothing on
 * denial) is unchanged. SPEC GAP: if a future build ever runs the IME in its own process, this
 * needs the broadcast SS10 describes instead.
 */
class DictationPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            DictationPermissionBridge.deliverResult(granted = true)
            finish()
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        DictationPermissionBridge.deliverResult(granted)
        finish()
    }

    private companion object {
        const val REQUEST_CODE = 1
    }
}

/** See [DictationPermissionActivity]'s KDoc for why this is in-process rather than a broadcast. */
internal object DictationPermissionBridge {
    private var pending: ((granted: Boolean) -> Unit)? = null

    fun awaitResult(onResult: (granted: Boolean) -> Unit) {
        pending = onResult
    }

    fun deliverResult(granted: Boolean) {
        val callback = pending
        pending = null
        callback?.invoke(granted)
    }
}
