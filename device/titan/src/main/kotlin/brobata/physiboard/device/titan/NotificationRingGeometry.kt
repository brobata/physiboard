package brobata.physiboard.device.titan

import kotlin.math.abs

/** A display cutout rectangle in window pixels, as the system reports it. */
data class CutoutRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A user-fitted ring, present only when `notification_ring_radius` is stored. spec: SS5.7.4. */
data class RingOverride(val centerX: Float, val centerY: Float, val radius: Float, val strokeWidth: Float)

/** The ring PhysiBoard should draw: a centre, a radius and a stroke width, all in window pixels. */
data class RingGeometry(val centerX: Float, val centerY: Float, val radius: Float, val strokeWidth: Float)

/**
 * The notification ring's geometry around the Titan 2 Elite's camera hole, as pure math over the
 * cutout the system reports (or does not).
 *
 * spec: device-backlight-ring.md SS5.7.1, D23, D24, D28. All the pixel constants here are stated
 * at the Titan's own 300 dpi; [scaleToDensity] is how every one of them adapts to another density,
 * per the section's own rule ("on any other density they scale by density/300").
 */
object NotificationRingGeometry {

    private const val REFERENCE_DENSITY_DPI = 300f

    /** D23: the reported cutout bounding box is a 123 px square at the panel's top-left corner. */
    private const val TITAN_CUTOUT_SIZE_PX = 123f
    private const val TITAN_CUTOUT_TOLERANCE_PX = 2f

    /** D24: the lens sits lower and further right than the centre of that box, and is smaller than it. */
    private const val FITTED_CENTER_X_PX = 78f
    private const val FITTED_CENTER_Y_PX = 80f
    private const val FITTED_RADIUS_PX = 46f
    private const val FITTED_STROKE_PX = 6f

    /** spec: SS5.7.1 row 3 ("gap 3 dp, stroke 3 dp"); the default when a caller does not fit its own. */
    const val DEFAULT_GAP_DP = 3f
    const val DEFAULT_STROKE_DP = 3f

    /** spec: SS5.7.1 row 1 ("a stored stroke of 0 is replaced by 3 dp"). */
    const val OVERRIDE_FALLBACK_STROKE_DP = 3f

    private fun scaleToDensity(referenceValuePx: Float, densityDpi: Float): Float = referenceValuePx * (densityDpi / REFERENCE_DENSITY_DPI)

    /** spec: `text-input.md`-style dp/px conversion, used only for the gap and default stroke here. */
    fun dpToPx(dp: Float, densityDpi: Float): Float = dp * (densityDpi / 160f)

    /** spec: SS5.7.1 row 2's trigger condition ("left 0, top 0, right and bottom each within 2 px of 123"). */
    fun isTitanCutout(cutout: CutoutRect, densityDpi: Float): Boolean {
        val size = scaleToDensity(TITAN_CUTOUT_SIZE_PX, densityDpi)
        val tolerance = scaleToDensity(TITAN_CUTOUT_TOLERANCE_PX, densityDpi)
        return cutout.left == 0f && cutout.top == 0f &&
            abs(cutout.right - size) <= tolerance && abs(cutout.bottom - size) <= tolerance
    }

    /** spec: SS5.7.1 row 2, D24, D28: the hand-measured fit, scaled to the real device density. */
    fun fittedRing(densityDpi: Float): RingGeometry = RingGeometry(
        centerX = scaleToDensity(FITTED_CENTER_X_PX, densityDpi),
        centerY = scaleToDensity(FITTED_CENTER_Y_PX, densityDpi),
        radius = scaleToDensity(FITTED_RADIUS_PX, densityDpi),
        strokeWidth = scaleToDensity(FITTED_STROKE_PX, densityDpi),
    )

    /**
     * spec: SS5.7.1 row 3 ("Centre of the rectangle; radius = half the longer side + gap + half
     * the stroke"). [gapPx] and [strokePx] are already-converted pixel values, not dp: production
     * callers pass [dpToPx] of the 3 dp defaults, but the test corpus (T23, T25) exercises this
     * with arbitrary px values directly.
     */
    fun genericFit(cutout: CutoutRect, gapPx: Float, strokePx: Float): RingGeometry {
        val width = cutout.right - cutout.left
        val height = cutout.bottom - cutout.top
        val radius = maxOf(width, height) / 2f + gapPx + strokePx / 2f
        return RingGeometry(
            centerX = (cutout.left + cutout.right) / 2f,
            centerY = (cutout.top + cutout.bottom) / 2f,
            radius = radius,
            strokeWidth = strokePx,
        )
    }

    /** spec: SS5.7.1 row 4 ("assumed as a 123 px square at (0, 0)"), scaled like every other pixel constant here. */
    fun fallbackCutout(densityDpi: Float): CutoutRect {
        val size = scaleToDensity(TITAN_CUTOUT_SIZE_PX, densityDpi)
        return CutoutRect(0f, 0f, size, size)
    }

    /**
     * The full priority order of SS5.7.1: a user override first (with the zero-stroke fallback),
     * else the fitted Titan geometry when the reported cutout matches, else the generic rectangle
     * formula for either a genuinely different cutout or (per row 4, "rung as the previous row")
     * the assumed 123 px square when no cutout was reported at all.
     */
    fun resolve(override: RingOverride?, cutout: CutoutRect?, densityDpi: Float): RingGeometry {
        if (override != null) {
            val stroke = if (override.strokeWidth == 0f) dpToPx(OVERRIDE_FALLBACK_STROKE_DP, densityDpi) else override.strokeWidth
            return RingGeometry(override.centerX, override.centerY, override.radius, stroke)
        }
        if (cutout != null && isTitanCutout(cutout, densityDpi)) return fittedRing(densityDpi)

        val effectiveCutout = cutout ?: fallbackCutout(densityDpi)
        return genericFit(effectiveCutout, dpToPx(DEFAULT_GAP_DP, densityDpi), dpToPx(DEFAULT_STROKE_DP, densityDpi))
    }
}
