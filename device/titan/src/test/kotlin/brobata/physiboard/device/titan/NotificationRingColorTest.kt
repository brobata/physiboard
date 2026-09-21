package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: device-backlight-ring.md SS5.4, SS10 test cases T21, T22, T41. */
class NotificationRingColorTest {

    @Test
    fun `T21 - a bright declared colour is used as-is`() {
        val color = 0xFF1E88E5.toInt()
        assertEquals(color, NotificationRingColor.resolve("com.example.app", color, emptyMap()))
    }

    @Test
    fun `T22 - an undeclared colour and a too-dark colour both fall back to the default`() {
        assertEquals(NotificationRingColor.DEFAULT_COLOR_ARGB, NotificationRingColor.resolve("com.example.app", 0, emptyMap()))
        assertEquals(NotificationRingColor.DEFAULT_COLOR_ARGB, NotificationRingColor.resolve("com.example.app", 0xFF101010.toInt(), emptyMap()))
    }

    @Test
    fun `T41 - the default colour's own luminance is not too dark`() {
        assertTrue(NotificationRingColor.relativeLuminance(NotificationRingColor.DEFAULT_COLOR_ARGB) >= NotificationRingColor.DARK_LUMINANCE_THRESHOLD)
    }

    @Test
    fun `a per-app override wins even over a bright declared colour`() {
        val declared = 0xFF1E88E5.toInt()
        val override = 0xFFF97316.toInt()
        assertEquals(override, NotificationRingColor.resolve("com.example.app", declared, mapOf("com.example.app" to override)))
    }
}
