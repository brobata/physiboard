package it.palsoftware.pastiera.ring

import org.junit.Assert.assertEquals
import org.junit.Test

class RingGeometryTest {

    @Test
    fun `ring is centred on the cutout and clears it by the gap`() {
        // The Titan 2 Elite's cutout as the system reports it: a 123 px square at the corner.
        val ring = RingGeometry.aroundCutout(0, 0, 123, 123, gap = 6f, stroke = 4f)
        assertEquals(61.5f, ring.cx, 0.01f)
        assertEquals(61.5f, ring.cy, 0.01f)
        // hole radius 61.5 + gap 6 + half the stroke 2
        assertEquals(69.5f, ring.radius, 0.01f)
        assertEquals(4f, ring.stroke, 0f)
    }

    @Test
    fun `fallback at the Titan's density matches the real cutout`() {
        val real = RingGeometry.aroundCutout(0, 0, 123, 123, gap = 6f, stroke = 4f)
        val fallback = RingGeometry.titanFallback(densityDpi = 300, gap = 6f, stroke = 4f)
        assertEquals(real, fallback)
    }

    @Test
    fun `a wide cutout uses its longer side`() {
        val ring = RingGeometry.aroundCutout(100, 0, 300, 80, gap = 0f, stroke = 0f)
        assertEquals(200f, ring.cx, 0f)
        assertEquals(40f, ring.cy, 0f)
        assertEquals(100f, ring.radius, 0f)
    }

    @Test
    fun `the Titan's own cutout is recognised and gets the fitted ring`() {
        assertEquals(true, RingGeometry.isTitanCutout(0, 0, 123, 123, densityDpi = 300))
        assertEquals(false, RingGeometry.isTitanCutout(100, 0, 300, 80, densityDpi = 300))
        val fitted = RingGeometry.titanFitted(300)
        assertEquals(78f, fitted.cx, 0f)
        assertEquals(80f, fitted.cy, 0f)
        assertEquals(46f, fitted.radius, 0f)
    }
}
