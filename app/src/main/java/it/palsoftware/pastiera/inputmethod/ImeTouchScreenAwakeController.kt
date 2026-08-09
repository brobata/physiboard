package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.os.PowerManager
import android.view.MotionEvent

/**
 * Restarts the display timeout after an IME touch.
 *
 * Touches delivered to an IME are not always treated as user activity while the
 * keyguard is focused. A brief screen wake lock with [PowerManager.ON_AFTER_RELEASE]
 * makes Android restart its normal user-activity timeout when the pulse ends.
 */
@Suppress("DEPRECATION")
internal class ImeTouchScreenAwakeController(
    context: Context,
    private val pulseDurationMillis: Long = DEFAULT_PULSE_DURATION_MILLIS
) {
    private val wakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
        }

    fun onTouchAction(action: Int) {
        if (action != MotionEvent.ACTION_DOWN) return

        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        wakeLock.acquire(pulseDurationMillis)
    }

    fun release() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private companion object {
        const val DEFAULT_PULSE_DURATION_MILLIS = 200L
        const val WAKE_LOCK_TAG = "Pastiera:ImeTouch"
    }
}
