package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: device-backlight-ring.md SS5.7.2, SS5.7.3, SS10 test cases T29, T30. */
class NotificationRingSettingsTest {

    @Test
    fun `T29 - brightness names map to their fractions, unknown and null read as NORMAL`() {
        assertEquals(0.6f, NotificationRingBrightness.fractionFor("BRIGHT"))
        assertEquals(0.05f, NotificationRingBrightness.fractionFor("DIM"))
        assertEquals(0.2f, NotificationRingBrightness.fractionFor("bogus"))
        assertEquals(0.2f, NotificationRingBrightness.fractionFor(null))
    }

    @Test
    fun `T30 - the default duration is 10 minutes, the first-run stamp is 2`() {
        assertEquals(10, NotificationRingDuration.DEFAULT_MINUTES)
        assertEquals(2, NotificationRingDuration.FIRST_RUN_STAMP_MINUTES)
    }

    @Test
    fun `duration clamps to 1 through 60 minutes and converts to milliseconds`() {
        assertEquals(1, NotificationRingDuration.clampMinutes(0))
        assertEquals(60, NotificationRingDuration.clampMinutes(90))
        assertEquals(120_000L, NotificationRingDuration.durationMs(2))
    }
}
