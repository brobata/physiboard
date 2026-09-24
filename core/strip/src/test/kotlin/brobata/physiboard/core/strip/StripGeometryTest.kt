package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: status-bar.md SS2, SS18 rows T24 and T25. */
class StripGeometryTest {

    private val titan = StripGeometry.forBar(barHeightDp = 56, pxPerDp = StripGeometry.TITAN_PX_PER_DP)

    @Test
    fun `T25 a 56 dp bar on the Titan is 105 px with 98 px buttons, 142 px edge buttons, 5 px gaps, 44 px edge inset`() {
        assertEquals(105, titan.barHeightPx)
        assertEquals(98, titan.buttonPx)
        assertEquals(142, titan.edgeButtonPx)
        assertEquals(5, titan.gapPx)
        assertEquals(44, titan.edgeExtraInsetPx)
    }

    @Test
    fun `T25 one edge button on the left insets the slots 147 px, two buttons on the right 250 px`() {
        assertEquals(147, titan.sideInsetPx(buttonCount = 1, edgeButton = true))
        assertEquals(250, titan.sideInsetPx(buttonCount = 2, edgeButton = true))
        assertEquals(0, titan.sideInsetPx(buttonCount = 0, edgeButton = true), "a side with no buttons adds no inset")
        assertEquals(103, titan.sideInsetPx(buttonCount = 1, edgeButton = false))
    }

    @Test
    fun `SS2 the default configuration leaves 683 px for the slots on the 1080 px screen`() {
        val remaining = StripGeometry.TITAN_SCREEN_WIDTH_PX - titan.sideInsetPx(1, true) - titan.sideInsetPx(2, true)
        assertEquals(683, remaining)
    }

    @Test
    fun `SS2 the other table rows for the Titan`() {
        assertEquals(22, titan.slotPaddingHorizontalPx)
        assertEquals(7, titan.slotPaddingVerticalPx)
        assertEquals(45, titan.bottomCornerFallbackPx)
        assertEquals(88, titan.edgeCornerPx)
        assertEquals(9.8f, titan.buttonCornerPx, 0.01f)
        assertEquals(10.5f, titan.slotCornerPx, 0.01f)
        assertEquals(1, titan.borderPx)
        assertEquals(10, titan.ledHeightPx)
        assertEquals(1, titan.ledTopPaddingPx)
        assertEquals(2, titan.ledGapPx)
        assertEquals(5, titan.ledCornerPx)
    }

    @Test
    fun `SS2 the bar height options are 67, 90, 105, 120 px`() {
        assertEquals(listOf(67, 90, 105, 120), StripGeometry.BAR_HEIGHT_OPTIONS_DP.map { StripGeometry.forBar(it).barHeightPx })
    }

    @Test
    fun `SS17 height 36 gives a 32 dp button, above the 24 dp floor`() {
        val small = StripGeometry.forBar(36)
        assertEquals(60, small.buttonPx, "67 minus 7 px")
        val tiny = StripGeometry.forBar(20)
        assertEquals(45, tiny.buttonPx, "never under 24 dp")
    }

    @Test
    fun `T24 slot text bounds at scale 1_4, 0_65 and 2_2`() {
        assertEquals(SlotTextSizeSp(minSp = 9, maxSp = 19), SlotTextSizeSp.forScale(1.4))
        assertEquals(SlotTextSizeSp(minSp = 7, maxSp = 12), SlotTextSizeSp.forScale(0.65))
        assertEquals(SlotTextSizeSp(minSp = 12, maxSp = 20), SlotTextSizeSp.forScale(2.2))
    }
}
