package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: device-backlight-ring.md SS5.5. */
class PocketCheckTest {

    @Test
    fun `a reading below 5 cm counts as covered`() {
        assertEquals(true, PocketCheck.isCovered(readingCm = 2f, sensorMaxRangeCm = 8f))
    }

    @Test
    fun `a reading at or above 5 cm is clear`() {
        assertEquals(false, PocketCheck.isCovered(readingCm = 5f, sensorMaxRangeCm = 8f))
    }

    @Test
    fun `no reading within the timeout counts as clear`() {
        assertEquals(false, PocketCheck.isCovered(readingCm = null, sensorMaxRangeCm = 8f))
    }

    @Test
    fun `a sensor whose max range is under 5 cm uses that smaller range`() {
        assertEquals(true, PocketCheck.isCovered(readingCm = 2.5f, sensorMaxRangeCm = 3f))
        assertEquals(false, PocketCheck.isCovered(readingCm = 2.5f, sensorMaxRangeCm = 2f))
    }
}
