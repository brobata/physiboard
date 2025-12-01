package it.palsoftware.pastiera.core

import android.util.Log

/**
 * Tracks when the IME commits a trailing space automatically (e.g., after accepting
 * a suggestion or auto-replace). Used to selectively trim that space if the next
 * keypress is punctuation, without affecting user-typed spaces.
 */
object AutoSpaceTracker {
    private const val TAG = "AutoSpaceTracker"
    @Volatile
    private var autoSpacePending: Boolean = false

    fun markAutoSpace() {
        autoSpacePending = true
        Log.d(TAG, "markAutoSpace -> pending=true")
    }

    /**
     * Returns true if an auto-space was pending and clears the flag.
     */
    fun consumeAutoSpace(): Boolean {
        val had = autoSpacePending
        autoSpacePending = false
        Log.d(TAG, "consumeAutoSpace had=$had")
        return had
    }

    fun clear() {
        autoSpacePending = false
        Log.d(TAG, "clear pending=false")
    }
}
