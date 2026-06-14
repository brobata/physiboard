package it.palsoftware.pastiera.inputmethod

import android.os.SystemClock
import android.util.Log
import it.palsoftware.pastiera.BuildConfig

object ImePerfLogger {
    private const val TAG = "PastieraPerf"

    fun mark(): Long = SystemClock.elapsedRealtimeNanos()

    fun elapsedMs(startNanos: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L

    fun logDuration(
        label: String,
        startNanos: Long,
        thresholdMs: Long = 16L,
        details: String = ""
    ) {
        if (!BuildConfig.DEBUG) return

        val durationMs = elapsedMs(startNanos)
        if (durationMs >= thresholdMs) {
            val suffix = if (details.isBlank()) "" else " $details"
            Log.d(TAG, "$label took ${durationMs}ms$suffix")
        }
    }
}
