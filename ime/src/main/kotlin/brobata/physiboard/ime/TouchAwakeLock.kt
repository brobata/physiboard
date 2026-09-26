package brobata.physiboard.ime

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import brobata.physiboard.core.pointer.awake.TouchAwakeEffect
import brobata.physiboard.core.pointer.awake.TouchAwakePulse
import brobata.physiboard.core.pointer.awake.TouchAwakeResult
import brobata.physiboard.core.pointer.awake.TouchAwakeState

/**
 * Holds the real wake lock that keeps the display awake while the strip is being touched, the
 * Android half of [TouchAwakePulse].
 *
 * spec: trackpad-caret-nav.md SS6. Touches delivered to an input method window do not always count
 * as user activity, so without this a tap on a suggestion lets the screen dim and lock under the
 * user's finger. Every touch down on the keyboard's chrome takes a short screen-bright pulse; the
 * pulse always ends [TouchAwakePulse.PULSE_MS] after the last down, and the keyboard window going
 * away drops it at once.
 *
 * The decision of when to hold and when to let go is [TouchAwakePulse]'s, driven here on the real
 * clock. Every platform call is guarded: a phone that refuses a wake lock must not take the
 * keyboard's touch handling down with it.
 */
internal class TouchAwakeLock(private val context: Context) {

    private var state = TouchAwakeState()

    /**
     * spec SS6: "a screen-bright wake lock with the 'on after release' option, tag
     * 'PhysiBoard:ImeTouch', not reference-counted". Both flags are deprecated platform constants
     * with no replacement that keeps the screen bright on behalf of a window the user is touching,
     * which is exactly what an input method window needs, so they are used as named.
     */
    private val wakeLock: PowerManager.WakeLock? by lazy {
        runCatching {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            power?.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, TouchAwakePulse.TAG)
                ?.apply { setReferenceCounted(false) }
        }.onFailure { error -> Log.e(TAG, "touch-awake wake lock could not be created", error) }.getOrNull()
    }

    /** spec SS6: "every touch down (action down only; moves and ups do nothing) on the keyboard's chrome layout". */
    fun onChromeTouchDown() = apply(TouchAwakePulse.onTouchDown(state, SystemClock.uptimeMillis()))

    /** spec SS6: "when the chrome layout is detached from its window... any held pulse is released at once". */
    fun onChromeDetached() = apply(TouchAwakePulse.onChromeDetached(state, SystemClock.uptimeMillis()))

    private fun apply(result: TouchAwakeResult) {
        state = result.state
        val lock = wakeLock ?: return
        runCatching {
            when (val effect = result.effect) {
                is TouchAwakeEffect.Acquire -> {
                    // spec SS6's renewal row: the lock is not reference counted, so a held pulse is
                    // dropped before the new one, or the old timeout would end the pulse early.
                    if (effect.releaseFirst && lock.isHeld) lock.release()
                    lock.acquire(effect.timeoutMs)
                }
                TouchAwakeEffect.Release -> if (lock.isHeld) lock.release()
                TouchAwakeEffect.None -> Unit
            }
        }.onFailure { error -> Log.e(TAG, "touch-awake pulse failed", error) }
    }

    private companion object {
        const val TAG = "TouchAwakeLock"
    }
}
