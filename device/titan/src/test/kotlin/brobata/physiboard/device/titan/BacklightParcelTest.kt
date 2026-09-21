package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: device-backlight-ring.md SS3.2, SS10 test cases T1-T5. */
class BacklightParcelTest {

    @Test
    fun `T1 - a two-character always-on parcel parses to minus one`() {
        assertEquals("-1", BacklightParcel.parse("Result: Parcel(\t00000000 00000002 0031002d 00000000 '........-.1....')"))
    }

    @Test
    fun `T2 - a five-character stock parcel parses to 30000`() {
        assertEquals("30000", BacklightParcel.parse("Result: Parcel(00000000 00000005 00300033 00300030 00000030 '..')"))
    }

    @Test
    fun `T3 - a one-character parcel parses to 0`() {
        assertEquals("0", BacklightParcel.parse("Result: Parcel(00000000 00000001 00000030 '..')"))
    }

    @Test
    fun `T4 - a malformed or absent dump is unreadable`() {
        assertNull(BacklightParcel.parse("Result: oops"))
        assertNull(BacklightParcel.parse(""))
        assertNull(BacklightParcel.parse(null))
    }

    @Test
    fun `T5 - a zero count, an oversize count, or too few payload words are all unreadable`() {
        assertNull(BacklightParcel.parse("Result: Parcel(00000000 00000000 '')"))
        assertNull(BacklightParcel.parse("Result: Parcel(00000000 00000004 00300030 '.')")) // count 4 needs 2 payload words, only 1 given
        assertNull(BacklightParcel.parse("Result: Parcel(00000000 0000ffff 00300030 '.')"))
    }
}
