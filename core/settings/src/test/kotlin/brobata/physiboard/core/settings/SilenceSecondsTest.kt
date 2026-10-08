package brobata.physiboard.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SilenceSecondsTest {
    @Test
    fun `typed seconds become milliseconds`() {
        assertEquals(2500, SilenceSeconds.parse("2.5"))
        assertEquals(2500, SilenceSeconds.parse(" 2,5 "))
        assertEquals(1000, SilenceSeconds.parse("1"))
        assertEquals(60000, SilenceSeconds.parse("60"))
    }

    @Test
    fun `anything outside 1 to 60 or not a number is refused`() {
        for (bad in listOf("", "0.5", "61", "abc", "NaN", "-3")) assertNull(SilenceSeconds.parse(bad), bad)
    }

    @Test
    fun `stored milliseconds show as plain seconds, and the default is 2_5`() {
        assertEquals("2.5", SilenceSeconds.format(2500))
        assertEquals("5", SilenceSeconds.format(5000))
        assertEquals("3.2", SilenceSeconds.format(3200))
        assertEquals(SilenceSeconds.DEFAULT_MS, Settings().dictation.stopAfterSilenceMs)
    }
}
