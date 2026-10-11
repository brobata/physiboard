package brobata.physiboard.core.actions.snippets

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: expansion-clipboard-pickers-launcher.md SS2.5, SS11: the popup by the caret, never over the line being typed. Titan screen 1200 px tall. */
class SnippetPopupPlacementTest {

    private fun margin(top: Float?, bottom: Float?, popup: Int = 300) =
        SnippetPopupPlacement.bottomMarginPx(top, bottom, screenHeightPx = 1200, popupHeightPx = popup, gapPx = 15, topLimitPx = 45)

    @Test
    fun `a compose box at the bottom gets the popup just above its line`() {
        // Caret line 1050..1100: the popup's bottom edge sits 15 px above the line's top, at 1035.
        assertEquals(1200 - 1035, margin(1050f, 1100f))
    }

    @Test
    fun `a field near the top gets the popup just below its line`() {
        // Caret line 150..200: no room above under the 45 px status bar, so the top edge sits at 215.
        assertEquals(1200 - (200 + 15 + 300), margin(150f, 200f))
    }

    @Test
    fun `no caret report puts the popup at the top of the screen`() {
        assertEquals(1200 - 45 - 300, margin(null, null))
    }

    @Test
    fun `a popup taller than the room on either side stays on screen`() {
        val m = margin(500f, 550f, popup = 1100)
        assertEquals(0, m)
    }
}
