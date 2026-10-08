package brobata.physiboard.core.strip

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * How far a bottom panel keeps its content from the display's rounded bottom corners. spec:
 * status-bar.md SS4 (`titan2_elite_rounded_corner_insets`) and layers-sym-alt.md SS5.7.
 *
 * The Titan 2 Elite reports a 100 px radius on every corner (`dumpsys display`, 2026-10-07), and
 * a panel flush with the bottom edge lost its corner buttons to the curve: the emoji page's
 * search and close buttons and the outer grid columns sat in it. A panel whose bottom sits within
 * one radius of the screen's bottom edge pads its sides and bottom by the distance at which the
 * curve crosses the 45 degree line, r * (1 - 1/sqrt 2), plus [MARGIN_DP]: the corner of a square
 * button padded that far just clears the curve, at about 30 px of 1076 a side on the Titan, so
 * the panel keeps nearly its whole width. The padding is drawn in the panel's own background, so
 * only that background meets the curve.
 */
data class RoundedCornerInsets(val sidePx: Int, val bottomPx: Int) {
    companion object {
        val NONE = RoundedCornerInsets(0, 0)

        /** A little air between the curve and a button's corner. */
        const val MARGIN_DP: Int = 2

        /**
         * [radiusPx] is the display's bottom-corner radius (0 or less: square corners), and
         * [panelBottomAboveScreenBottomPx] how far the panel's bottom sits above the screen's.
         */
        fun forPanel(enabled: Boolean, radiusPx: Int, panelBottomAboveScreenBottomPx: Int, pxPerDp: Float): RoundedCornerInsets {
            if (!enabled || radiusPx <= 0 || panelBottomAboveScreenBottomPx >= radiusPx) return NONE
            val clearance = ceil(radiusPx * (1 - 1 / sqrt(2.0))).toInt() + (MARGIN_DP * pxPerDp).toInt()
            return RoundedCornerInsets(sidePx = clearance, bottomPx = (clearance - panelBottomAboveScreenBottomPx).coerceAtLeast(0))
        }
    }
}
