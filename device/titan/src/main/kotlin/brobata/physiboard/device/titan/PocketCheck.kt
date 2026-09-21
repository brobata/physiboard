package brobata.physiboard.device.titan

/**
 * Whether the phone counts as pocketed, from a single proximity reading. spec:
 * device-backlight-ring.md SS5.5. A missed reading always counts as clear, deliberately: "a
 * missed ring is cheaper than a phone that never rings".
 */
object PocketCheck {

    const val TIMEOUT_MS = 300L
    const val COVERED_THRESHOLD_CM = 5f

    /**
     * [readingCm] is null for "no sensor, a registration failure, or no reading within
     * [TIMEOUT_MS]", all of which read as clear. [sensorMaxRangeCm] is null when the range is
     * unknown, in which case only the fixed 5 cm threshold applies.
     */
    fun isCovered(readingCm: Float?, sensorMaxRangeCm: Float?): Boolean {
        if (readingCm == null) return false
        val threshold = if (sensorMaxRangeCm == null) COVERED_THRESHOLD_CM else minOf(sensorMaxRangeCm, COVERED_THRESHOLD_CM)
        return readingCm < threshold
    }
}
