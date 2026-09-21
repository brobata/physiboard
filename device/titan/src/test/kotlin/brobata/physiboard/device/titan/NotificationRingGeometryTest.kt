package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: device-backlight-ring.md SS5.7.1, SS10 test cases T23-T28. */
class NotificationRingGeometryTest {

    @Test
    fun `T23 - a generic rectangle fit centres itself and pads by gap plus half the stroke`() {
        val ring = NotificationRingGeometry.genericFit(CutoutRect(0f, 0f, 123f, 123f), gapPx = 6f, strokePx = 4f)
        assertEquals(61.5f, ring.centerX)
        assertEquals(61.5f, ring.centerY)
        assertEquals(69.5f, ring.radius)
        assertEquals(4f, ring.strokeWidth)
    }

    @Test
    fun `T24 - the Titan fallback square fit through the generic formula matches T23`() {
        val fallback = NotificationRingGeometry.fallbackCutout(300f)
        val ring = NotificationRingGeometry.genericFit(fallback, gapPx = 6f, strokePx = 4f)
        assertEquals(61.5f, ring.centerX)
        assertEquals(61.5f, ring.centerY)
        assertEquals(69.5f, ring.radius)
    }

    @Test
    fun `T25 - a wide rectangle with no gap or stroke pads by nothing`() {
        val ring = NotificationRingGeometry.genericFit(CutoutRect(100f, 0f, 300f, 80f), gapPx = 0f, strokePx = 0f)
        assertEquals(200f, ring.centerX)
        assertEquals(40f, ring.centerY)
        assertEquals(100f, ring.radius)
    }

    @Test
    fun `T26 - only the Titan's own cutout box is recognised as the Titan cutout`() {
        assertTrue(NotificationRingGeometry.isTitanCutout(CutoutRect(0f, 0f, 123f, 123f), densityDpi = 300f))
        assertFalse(NotificationRingGeometry.isTitanCutout(CutoutRect(100f, 0f, 300f, 80f), densityDpi = 300f))
    }

    @Test
    fun `T27 - the fitted ring at the Titan's own 300 dpi`() {
        val ring = NotificationRingGeometry.fittedRing(300f)
        assertEquals(78f, ring.centerX)
        assertEquals(80f, ring.centerY)
        assertEquals(46f, ring.radius)
        assertEquals(6f, ring.strokeWidth)
    }

    @Test
    fun `T28 - the fitted ring scales linearly at double density`() {
        val ring = NotificationRingGeometry.fittedRing(600f)
        assertEquals(156f, ring.centerX)
        assertEquals(160f, ring.centerY)
        assertEquals(92f, ring.radius)
        assertEquals(12f, ring.strokeWidth)
    }

    @Test
    fun `an override with a zero stroke falls back to 3 dp`() {
        val override = RingOverride(centerX = 50f, centerY = 50f, radius = 40f, strokeWidth = 0f)
        val ring = NotificationRingGeometry.resolve(override, cutout = null, densityDpi = 300f)
        assertEquals(NotificationRingGeometry.dpToPx(3f, 300f), ring.strokeWidth)
    }
}
