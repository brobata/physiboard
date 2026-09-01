package brobata.physiboard

import brobata.physiboard.inputmethod.KeyboardBacklightManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parsing `service call agui_functional_service 1 s16 "keyboard_brightness_timeout"`.
 *
 * The always-on case is the literal output captured from the maintainer's Titan 2 Elite on
 * 2026-08-31, which is also what established that transaction 1 is a readable GET — the
 * read-back that replaces trusting our own one-way "configured" flag.
 */
class BacklightParcelParseTest {

    private fun parse(raw: String?) = KeyboardBacklightManager.parseParcelString(raw)

    @Test
    fun readsTheAlwaysOnSentinelFromARealDeviceResponse() {
        val fromDevice = "Result: Parcel(\t00000000 00000002 0031002d 00000000 '........-.1.....')"

        assertEquals("-1", parse(fromDevice))
    }

    @Test
    fun readsTheStockTimeout() {
        // "30000" — five UTF-16 units across three words, last half-word unused.
        val raw = "Result: Parcel(00000000 00000005 00300033 00300030 00000030 '..')"

        assertEquals("30000", parse(raw))
    }

    @Test
    fun readsAnOddLengthValueWithoutTrailingPadding() {
        // "0" — a single unit, so the high half of the word must be ignored.
        val raw = "Result: Parcel(00000000 00000001 00000030 '..')"

        assertEquals("0", parse(raw))
    }

    @Test
    fun rejectsOutputWithNoParcel() {
        assertNull(parse("Result: oops"))
        assertNull(parse(""))
        assertNull(parse(null))
    }

    @Test
    fun rejectsAParcelWithNoPayload() {
        assertNull(parse("Result: Parcel(00000000 00000000 '..')"))
    }

    @Test
    fun rejectsATruncatedParcel() {
        // Claims four characters but carries one word, so it cannot be trusted.
        assertNull(parse("Result: Parcel(00000000 00000004 00310032 '..')"))
    }

    @Test
    fun rejectsAnImplausibleLength() {
        assertNull(parse("Result: Parcel(00000000 0000ffff 00310032 '..')"))
    }
}
