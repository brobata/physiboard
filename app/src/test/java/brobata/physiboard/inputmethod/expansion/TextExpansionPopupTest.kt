package brobata.physiboard.inputmethod.expansion

import org.junit.Assert.assertEquals
import org.junit.Test

class TextExpansionPopupTest {
    @Test
    fun popupIsOffsetByItsFullHeightAboveTheImeParentFrame() {
        assertEquals(-500, TextExpansionPopup.popupOffsetY(popupHeight = 500))
    }
}
