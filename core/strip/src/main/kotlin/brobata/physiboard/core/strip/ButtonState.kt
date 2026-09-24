package brobata.physiboard.core.strip

/**
 * The language button's text. spec: status-bar.md SS6.1: "the first segment of the current
 * subtype's locale in upper case ('EN' for en_US, 'DE' for de) or '??' when there is no subtype"
 * (T36).
 */
object LanguageLabel {
    const val NO_SUBTYPE: String = "??"

    fun of(locale: String?): String {
        val first = locale.orEmpty().split('_', '-').firstOrNull().orEmpty().trim()
        return if (first.isEmpty()) NO_SUBTYPE else first.uppercase()
    }
}

/**
 * The language button's tap gate. spec: status-bar.md SS6.1: "a tap within 500 ms of the last
 * accepted tap is ignored; an accepted tap disables the button at 50 percent alpha for 300 ms"
 * (SS14, SS17 "Language tapped twice within 500 ms: second tap ignored").
 */
object LanguageTapDebounce {
    const val DEBOUNCE_MS: Long = 500
    const val DISABLED_MS: Long = 300
    const val DISABLED_ALPHA: Float = 0.5f

    /** True when the tap at [nowMs] is accepted given the last accepted tap (or none). */
    fun accepts(lastAcceptedMs: Long?, nowMs: Long): Boolean = lastAcceptedMs == null || nowMs - lastAcceptedMs >= DEBOUNCE_MS

    /** True while the button is still dimmed after an accepted tap. */
    fun isDisabled(lastAcceptedMs: Long?, nowMs: Long): Boolean = lastAcceptedMs != null && nowMs - lastAcceptedMs < DISABLED_MS
}

/**
 * The microphone button's recording color. spec: status-bar.md SS6.1: initially (255, 80, 80);
 * "on every audio level report, a red between (128, 0, 0) and (255, 50, 50) chosen by the square
 * of the level normalized from -10 dB to 0 dB (clamped)" (T37). Its pressed color while active
 * is the fixed blue (100, 150, 255).
 */
object MicrophoneLevel {
    const val INITIAL_RECORDING_COLOR: Int = 0xFFFF5050.toInt()
    const val ACTIVE_PRESSED_COLOR: Int = 0xFF6496FF.toInt()
    const val MIN_DB: Double = -10.0
    const val MAX_DB: Double = 0.0

    /** The 0..1 intensity for a level in dB: normalized, clamped, squared (T37: -10, -5, 0 give 0, 0.25, 1). */
    fun intensity(levelDb: Double): Double {
        val normalized = ((levelDb - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0)
        return normalized * normalized
    }

    /** The ARGB color for a level: (128 + 127 i, 50 i, 50 i), truncated (T37: -5 dB gives (159, 12, 12)). */
    fun color(levelDb: Double): Int {
        val i = intensity(levelDb)
        val red = (128 + 127 * i).toInt()
        val green = (50 * i).toInt()
        val blue = (50 * i).toInt()
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}

/**
 * The clipboard button's badge. spec: status-bar.md SS6.1: the count "hidden at zero"; "whenever
 * the count changes to a different positive number, a red overlay flashes over the button".
 */
object ClipboardBadge {
    /** spec SS14: "Clipboard badge flash 350 ms, alpha 0 to 0.4 to 0". */
    const val FLASH_MS: Long = 350
    const val FLASH_PEAK_ALPHA: Float = 0.4f

    fun text(count: Int): String? = if (count > 0) count.toString() else null

    fun flashes(previousCount: Int, newCount: Int): Boolean = newCount > 0 && newCount != previousCount

    /** spec SS6.1: the accessibility state reads "Empty" or "N items". */
    fun accessibilityState(count: Int): String = if (count == 0) "Empty" else "$count items"
}
