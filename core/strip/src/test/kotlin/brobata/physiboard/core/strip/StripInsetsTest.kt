package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: status-bar.md SS11, SS18 rows T15 to T17. */
class StripInsetsTest {

    @Test
    fun `T15 candidates-only rewrites the content top to the visible top and bounds touches to the strip band`() {
        val decision = StripInsets.decide(candidatesOnly = true, contentTopPx = 122, visibleTopPx = 0, windowWidthPx = 1080, windowHeightPx = 229)
        assertEquals(0, decision.contentTopPx)
        assertEquals(TouchableArea.REGION, decision.touchable)
        assertEquals(PxRect(0, 0, 1080, 229), decision.touchableRect)
    }

    @Test
    fun `T16 with the input view showing the platform's insets are left alone`() {
        val decision = StripInsets.decide(candidatesOnly = false, contentTopPx = 122, visibleTopPx = 0, windowWidthPx = 1080, windowHeightPx = 229)
        assertEquals(122, decision.contentTopPx)
        assertNull(decision.touchable)
        assertNull(decision.touchableRect)
    }

    @Test
    fun `T17 no width, or a height not above the content top, falls back to content`() {
        val noWidth = StripInsets.decide(candidatesOnly = true, contentTopPx = 122, visibleTopPx = 100, windowWidthPx = 0, windowHeightPx = 229)
        assertEquals(TouchableArea.CONTENT, noWidth.touchable)
        assertNull(noWidth.touchableRect)
        val flat = StripInsets.decide(candidatesOnly = true, contentTopPx = 122, visibleTopPx = 229, windowWidthPx = 1080, windowHeightPx = 229)
        assertEquals(TouchableArea.CONTENT, flat.touchable)
        assertEquals(229, flat.contentTopPx)
    }

    @Test
    fun `SS11 the band starts where the strip is visible, not at the window top`() {
        val decision = StripInsets.decide(candidatesOnly = true, contentTopPx = 200, visibleTopPx = 124, windowWidthPx = 1080, windowHeightPx = 229)
        assertEquals(124, decision.contentTopPx)
        assertEquals(PxRect(0, 124, 1080, 229), decision.touchableRect)
    }

    @Test
    fun `T15b the strip's own top edge wins over a stale platform snapshot while it is on screen`() {
        // Titan 2026-09-25: platform reported content 105 / visible 105 in a 105 px window (strip counted as hidden) while the strip was drawn.
        val decision = StripInsets.decide(candidatesOnly = true, contentTopPx = 105, visibleTopPx = 105, windowWidthPx = 1076, windowHeightPx = 105, stripTopPx = 0)
        assertEquals(0, decision.contentTopPx)
        assertEquals(TouchableArea.REGION, decision.touchable)
        assertEquals(PxRect(0, 0, 1076, 105), decision.touchableRect)
    }

    @Test
    fun `T15c with the strip off screen the platform snapshot stands`() {
        val decision = StripInsets.decide(candidatesOnly = true, contentTopPx = 105, visibleTopPx = 105, windowWidthPx = 1076, windowHeightPx = 105, stripTopPx = null)
        assertEquals(105, decision.contentTopPx)
    }
}
