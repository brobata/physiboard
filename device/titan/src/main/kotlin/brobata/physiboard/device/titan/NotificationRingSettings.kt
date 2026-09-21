package brobata.physiboard.device.titan

/**
 * The ring window's screen brightness levels. spec: device-backlight-ring.md SS5.7.2. On an
 * AMOLED panel the black rest of the screen costs nothing, so this is the only knob that trades
 * visibility for battery.
 */
object NotificationRingBrightness {
    const val DIM = 0.05f
    const val NORMAL = 0.2f
    const val BRIGHT = 0.6f

    /** spec: SS5.7.2 ("unknown reads as NORMAL"), also the fallback for a null/unset preference. */
    fun fractionFor(settingValue: String?): Float = when (settingValue) {
        "DIM" -> DIM
        "NORMAL" -> NORMAL
        "BRIGHT" -> BRIGHT
        else -> NORMAL
    }
}

/**
 * How long the ring holds the screen on after its latest notification. spec: SS5.7.3.
 */
object NotificationRingDuration {
    const val DEFAULT_MINUTES = 10
    const val FIRST_RUN_STAMP_MINUTES = 2
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 60

    /** spec: SS5.7.6 ("Try it" demo rings use 8000 ms regardless of the stored minutes). */
    const val DEMO_DURATION_MS = 8_000L

    fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    /** spec: SS5.7.3 ("restarts the timer at minutes x 60000 ms"). */
    fun durationMs(minutes: Int): Long = clampMinutes(minutes) * 60_000L
}
