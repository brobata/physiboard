package brobata.physiboard.design

import brobata.physiboard.design.DesignTokens.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** docs/design/design-system.md, "Colour": every text role meets WCAG AA on the surfaces it sits on, in both themes. */
class DesignTokensTest {

    private fun assertAA(name: String, text: Int, on: Int) {
        val ratio = DesignTokens.contrast(text, on)
        assertTrue(ratio >= 4.5, "$name is %.2f:1, under 4.5:1".format(ratio))
    }

    @Test
    fun `contrast is the WCAG ratio`() {
        assertEquals(21.0, DesignTokens.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, DesignTokens.contrast(Palette.INK, Palette.INK), 0.0001)
    }

    @Test
    fun `every text role reads on the page and on a pane, in both themes`() {
        for ((theme, s) in listOf("dark" to DesignTokens.DARK, "light" to DesignTokens.LIGHT)) {
            for ((surfaceName, surface) in listOf("page" to s.page, "pane" to s.pane)) {
                assertAA("$theme text on $surfaceName", s.text, surface)
                assertAA("$theme muted on $surfaceName", s.muted, surface)
                assertAA("$theme accent on $surfaceName", s.accent, surface)
                assertAA("$theme comment on $surfaceName", s.comment, surface)
            }
            assertAA("$theme ink on the accent (a selected chip)", s.onAccent, s.accent)
        }
    }

    @Test
    fun `the launcher mark's amber reads on its navy`() {
        assertTrue(DesignTokens.contrast(Palette.SIGNAL_AMBER, Palette.INK) >= 7.0)
    }

    @Test
    fun `corners only grow from fields to dialogs`() {
        with(DesignTokens.Radius) { assertTrue(FIELD < KEY && KEY < PANE && PANE < DIALOG) }
    }
}
