package brobata.physiboard.device.titan

/**
 * The notification ring's colour resolution: an app's own colour, unless it is unset, too dark to
 * see on a black panel, or overridden per app.
 *
 * spec: device-backlight-ring.md SS5.4. The luminance floor exists because this ring is drawn on
 * an AMOLED panel that is truly off everywhere it draws nothing (D3): a colour invisible against
 * black is worse than the default.
 */
object NotificationRingColor {

    const val DEFAULT_COLOR_ARGB = 0xFF34C759.toInt()

    /** WCAG relative luminance below this is "too dark to see". spec: SS5.4 point 3. */
    const val DARK_LUMINANCE_THRESHOLD = 0.12

    /** spec: SS5.4 ("WCAG relative luminance ... computed from the sRGB channels"). */
    fun relativeLuminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        fun linearize(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
    }

    /**
     * spec: SS5.4 points 1-4: a per-app override wins outright; else an undeclared colour (0) or
     * one too dark to see falls back to [DEFAULT_COLOR_ARGB]; otherwise the declared colour stands.
     */
    fun resolve(packageName: String, declaredColorArgb: Int, perAppColors: Map<String, Int>): Int {
        perAppColors[packageName]?.let { return it }
        if (declaredColorArgb == 0) return DEFAULT_COLOR_ARGB
        if (relativeLuminance(declaredColorArgb) < DARK_LUMINANCE_THRESHOLD) return DEFAULT_COLOR_ARGB
        return declaredColorArgb
    }
}
