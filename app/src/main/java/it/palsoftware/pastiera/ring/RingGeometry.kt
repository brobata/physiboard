package it.palsoftware.pastiera.ring

import kotlin.math.max

/**
 * Where the ring goes. The camera hole is reported by the system as a display cutout; the ring
 * hugs its bounding box with a small gap so the glow reads as coming from the hole itself.
 */
object RingGeometry {

    data class Ring(val cx: Float, val cy: Float, val radius: Float, val stroke: Float)

    /**
     * A ring around the cutout rectangle [left, top, right, bottom] (window pixels).
     * [gap] is the clear space between the hole and the inner edge of the stroke.
     */
    fun aroundCutout(
        left: Int, top: Int, right: Int, bottom: Int,
        gap: Float, stroke: Float
    ): Ring {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val holeRadius = max(right - left, bottom - top) / 2f
        return Ring(cx, cy, holeRadius + gap + stroke / 2f, stroke)
    }

    /** The bounding box the Titan 2 Elite's firmware reports for its camera hole, in pixels at 300 dpi. */
    private const val TITAN_CUTOUT_PX = 123

    /**
     * The ring fitted by eye to the Titan 2 Elite's actual lens (2026-08-26): the lens sits
     * lower and further right than the centre of the reported box, and is smaller than it.
     * Pixels at the panel's 300 dpi, scaled for anything else.
     */
    fun titanFitted(densityDpi: Int): Ring {
        val k = densityDpi / 300f
        return Ring(cx = 78f * k, cy = 80f * k, radius = 45f * k, stroke = 4f * k)
    }

    /** True when [left, top, right, bottom] is the Titan's own cutout, so the fitted ring applies. */
    fun isTitanCutout(left: Int, top: Int, right: Int, bottom: Int, densityDpi: Int): Boolean {
        val side = (TITAN_CUTOUT_PX * densityDpi / 300f).toInt()
        return left == 0 && top == 0 && kotlin.math.abs(right - side) <= 2 && kotlin.math.abs(bottom - side) <= 2
    }

    /**
     * The Titan 2 Elite's hole when the system reports no cutout (a test device, or a window
     * that was not laid out into the cutout): a 123 px square in the top-left corner of the
     * 1080-wide panel, scaled by the density ratio to that panel's 300 dpi.
     */
    fun titanFallback(densityDpi: Int, gap: Float, stroke: Float): Ring {
        val side = (TITAN_CUTOUT_PX * densityDpi / 300f).toInt()
        return aroundCutout(0, 0, side, side, gap, stroke)
    }
}
