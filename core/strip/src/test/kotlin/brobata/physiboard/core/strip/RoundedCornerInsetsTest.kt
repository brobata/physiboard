package brobata.physiboard.core.strip

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: status-bar.md SS4, layers-sym-alt.md SS5.7: panels keep clear of the rounded corners. */
class RoundedCornerInsetsTest {

    @Test
    fun `the Titan's 100 px corners give a panel at the bottom about 30 px a side and below`() {
        val insets = RoundedCornerInsets.forPanel(enabled = true, radiusPx = 100, panelBottomAboveScreenBottomPx = 0, pxPerDp = 1.875f)
        assertEquals(RoundedCornerInsets(33, 33), insets)
    }

    @Test
    fun `the padded corner lies inside the curve`() {
        val r = 100.0
        val insets = RoundedCornerInsets.forPanel(true, 100, 0, 1.875f)
        // The point (side, bottom) measured from the screen's corner, against the corner circle's centre at (r, r).
        val dx = r - insets.sidePx
        val dy = r - insets.bottomPx
        assertTrue(sqrt(dx * dx + dy * dy) <= r)
    }

    @Test
    fun `no insets with the setting off, square corners, or a panel lifted clear of the curve`() {
        assertEquals(RoundedCornerInsets.NONE, RoundedCornerInsets.forPanel(false, 100, 0, 1.875f))
        assertEquals(RoundedCornerInsets.NONE, RoundedCornerInsets.forPanel(true, 0, 0, 1.875f))
        assertEquals(RoundedCornerInsets.NONE, RoundedCornerInsets.forPanel(true, 100, 100, 1.875f))
    }
}
