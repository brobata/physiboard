package it.palsoftware.pastiera.inputmethod.trackpad

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Listens to trackpad events via Shizuku and triggers callbacks on swipe.
 * Keeps gesture logic isolated so the IME can stay lean and only react to events.
 */
class TrackpadGestureDetector(
    private val isEnabled: () -> Boolean,
    private val onSwipeUp: (third: Int) -> Unit,
    private val scope: CoroutineScope,
    private val eventDevice: String = DEFAULT_EVENT_DEVICE,
    private val trackpadMaxX: Int = DEFAULT_TRACKPAD_MAX_X,
    private val swipeUpThreshold: Int = DEFAULT_SWIPE_UP_THRESHOLD,
    private val minVelocityThreshold: Double = DEFAULT_MIN_VELOCITY_THRESHOLD,
    private val logTag: String = DEFAULT_LOG_TAG,
    private val shizukuPing: () -> Boolean = { 
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED 
    }
) {

    private var geteventJob: Job? = null
    private var touchDown = false
    private var startX = 0
    private var startY = 0
    private var currentX = 0
    private var currentY = 0
    private var startPosSet = false
    private var startTime: Long = 0
    private var endTime: Long = 0

    fun start() {
        // Guard: if already running, do nothing
        if (isRunning()) {
            Log.d(DEBUG_TAG, "start() SKIPPED: detector already running")
            return
        }
        
        val enabled = isEnabled()
        Log.d(DEBUG_TAG, "start() called - isEnabled=$enabled, swipeUpThreshold=$swipeUpThreshold, eventDevice=$eventDevice")
        
        if (!enabled) {
            Log.d(DEBUG_TAG, "start() ABORTED: gestures disabled in settings")
            Log.d(logTag, "Trackpad gestures disabled in settings")
            return
        }

        val shizukuRunning = try { Shizuku.pingBinder() } catch (e: Exception) { false }
        val shizukuAuthorized = try { 
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED 
        } catch (e: Exception) { false }
        val shizukuAvailable = shizukuRunning && shizukuAuthorized
        Log.d(DEBUG_TAG, "start() Shizuku status: running=$shizukuRunning, authorized=$shizukuAuthorized, available=$shizukuAvailable")
        
        if (!shizukuAvailable) {
            val reason = when {
                !shizukuRunning -> "Shizuku not running"
                !shizukuAuthorized -> "App not authorized in Shizuku"
                else -> "Unknown"
            }
            Log.d(DEBUG_TAG, "start() ABORTED: $reason")
            Log.w(logTag, "Shizuku not available ($reason), trackpad gesture detection disabled")
            return
        }

        geteventJob?.cancel()
        Log.d(DEBUG_TAG, "start() launching getevent coroutine...")
        geteventJob = scope.launch(Dispatchers.IO) {
            try {
                Log.d(DEBUG_TAG, "getevent coroutine started, getting Shizuku.newProcess method...")
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                newProcessMethod.isAccessible = true

                Log.d(DEBUG_TAG, "Invoking Shizuku.newProcess for getevent -l $eventDevice")
                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("getevent", "-l", eventDevice),
                    null,
                    null
                ) as Process

                Log.d(DEBUG_TAG, "getevent process started successfully, reading events...")
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        parseTrackpadEvent(line)
                    }
                }
                Log.d(DEBUG_TAG, "getevent reader loop ended")
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "getevent coroutine FAILED: ${e.message}", e)
                Log.e(logTag, "Trackpad getevent failed", e)
            }
        }
        Log.d(DEBUG_TAG, "start() completed - getevent job launched")
        Log.d(logTag, "Trackpad gesture detection started")
    }

    fun stop() {
        Log.d(DEBUG_TAG, "stop() called - had active job: ${geteventJob != null}")
        geteventJob?.cancel()
        geteventJob = null
        Log.d(logTag, "Trackpad gesture detection stopped")
    }

    /**
     * Returns true if the detector is currently running (has an active getevent job).
     */
    fun isRunning(): Boolean {
        return geteventJob != null && geteventJob?.isActive == true
    }

    private fun parseTrackpadEvent(line: String) {
        when {
            line.contains("BTN_TOUCH") && line.contains("DOWN") -> {
                touchDown = true
                startPosSet = false
                startTime = System.nanoTime()
            }

            line.contains("BTN_TOUCH") && line.contains("UP") -> {
                if (touchDown) {
                    endTime = System.nanoTime()
                    detectGesture()
                }
                touchDown = false
                startPosSet = false
            }

            line.contains("ABS_MT_POSITION_X") -> {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val hexValue = parts.last()
                    val newX = hexValue.toIntOrNull(16)
                    if (newX != null) {
                        currentX = newX
                        if (touchDown && !startPosSet) {
                            startX = newX
                        }
                    }
                }
            }

            line.contains("ABS_MT_POSITION_Y") -> {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val hexValue = parts.last()
                    val newY = hexValue.toIntOrNull(16)
                    if (newY != null) {
                        currentY = newY
                        if (touchDown && !startPosSet) {
                            startY = newY
                            startPosSet = true
                        }
                    }
                }
            }
        }
    }

    private fun detectGesture() {
        val deltaY = startY - currentY  // Positive = swipe up
        val deltaX = currentX - startX
        val absDeltaX = kotlin.math.abs(deltaX)

        // Calculate duration in milliseconds
        val durationMs = (endTime - startTime) / 1_000_000.0
        
        // Calculate velocity (pixels per millisecond)
        val velocity = if (durationMs > 0) deltaY / durationMs else 0.0

        // Require primarily vertical swipe: deltaY must be at least 5x larger than horizontal drift
        // AND velocity must exceed minimum threshold
        if (deltaY > swipeUpThreshold && absDeltaX < deltaY / 4 && velocity >= minVelocityThreshold) {
            val third = when {
                startX < trackpadMaxX / 3 -> 0  // Left
                startX < (trackpadMaxX * 2) / 3 -> 1  // Center
                else -> 2  // Right
            }

            Log.d(
                logTag,
                ">>> SWIPE UP DETECTED in third $third (deltaY=$deltaY, absDeltaX=$absDeltaX, velocity=${String.format("%.2f", velocity)} px/ms, duration=${String.format("%.1f", durationMs)}ms, startX=$startX) <<<"
            )
            onSwipeUp(third)
        }
    }

    companion object {
        const val DEFAULT_TRACKPAD_MAX_X = 1440
        const val DEFAULT_SWIPE_UP_THRESHOLD = 300
        const val DEFAULT_MIN_VELOCITY_THRESHOLD = 2.0  // pixels per millisecond (e.g., 1.0 px/ms = 1000 px/s)
        const val DEFAULT_EVENT_DEVICE = "/dev/input/event7"
        const val DEFAULT_LOG_TAG = "PastieraIME"
        private const val DEBUG_TAG = "TrackpadDebug"
    }
}



