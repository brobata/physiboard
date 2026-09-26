package brobata.physiboard.core.actions.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS7.5, the icon-derived row tint. */
class IconTintTest {

    private fun solidPixel(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 0xFF

    @Test
    fun `every pixel fully transparent yields nothing`() {
        val pixels = IntArray(32 * 32) { solidPixel(0, 200, 30, 30) }
        assertNull(IconTint.averageColor(pixels, alpha = 0x8A))
    }

    @Test
    fun `a solid opaque red icon averages to a red-hued color at the requested alpha`() {
        val pixels = IntArray(32 * 32) { solidPixel(255, 255, 0, 0) }
        val color = IconTint.averageColor(pixels, alpha = 0x8A)
        assertTrue(color != null)
        assertEquals(0x8A, channel(color, 24), "the requested alpha is used as is")
        // Pure red's saturation and value both exceed the clamp range, so the result is red-ish
        // (dominant red channel, green and blue depressed and roughly equal) rather than exactly (255,0,0).
        assertTrue(channel(color, 16) > channel(color, 8))
        assertTrue(channel(color, 16) > channel(color, 0))
    }

    @Test
    fun `half transparent pixels count for less than fully opaque ones`() {
        val opaqueRed = IntArray(16 * 32) { solidPixel(255, 255, 0, 0) }
        val transparentBlue = IntArray(16 * 32) { solidPixel(0, 0, 0, 255) }
        val color = IconTint.averageColor(opaqueRed + transparentBlue, alpha = 0xFF)
        assertTrue(color != null)
        // Fully transparent pixels contribute nothing, so the average is still red-dominant, not a red/blue blend.
        assertTrue(channel(color, 16) > channel(color, 0))
    }

    @Test
    fun `the per-source hue fallback produces a color at the requested alpha`() {
        val color = IconTint.colorForHue(hue = 214, alpha = 0x8A)
        assertEquals(0x8A, channel(color, 24))
    }
}
