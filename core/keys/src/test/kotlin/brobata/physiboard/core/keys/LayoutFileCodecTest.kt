package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks [LayoutFileCodec] against layers-sym-alt.md SS9.1's per-key rules and SS9.3's "Import
 * from file" behaviour (the name and description fields, the identity fallback).
 */
class LayoutFileCodecTest {

    @Test
    fun `a well formed file decodes its name, description and letter entries`() {
        val text = """
            {
              "name": "My Layout",
              "description": "a custom test layout",
              "mappings": {
                "KEYCODE_Q": { "lowercase": "q", "uppercase": "Q" },
                "KEYCODE_1": { "lowercase": "1", "uppercase": "1" },
                "KEYCODE_COMMA": { "lowercase": ",", "uppercase": "," }
              }
            }
        """.trimIndent()
        val parsed = LayoutFileCodec.decode(text)
        assertEquals("My Layout", parsed?.name)
        assertEquals("a custom test layout", parsed?.description)
        assertEquals(LetterEntry("q", "Q"), parsed?.layout?.get(KeyId.Letter('Q')))
        assertEquals(LetterEntry("1", "1"), parsed?.layout?.get(KeyId.Digit('1')))
        assertEquals(LetterEntry(",", ","), parsed?.layout?.get(KeyId.Punctuation(PunctuationKey.COMMA)))
    }

    @Test
    fun `name and description are optional`() {
        val text = """{ "mappings": { "KEYCODE_A": { "lowercase": "a", "uppercase": "A" } } }"""
        val parsed = LayoutFileCodec.decode(text)
        assertNull(parsed?.name)
        assertNull(parsed?.description)
        assertEquals("a", parsed?.layout?.get(KeyId.Letter('A'))?.lowercase)
    }

    @Test
    fun `an entry missing lowercase or uppercase is dropped, SS9_1`() {
        val text = """
            {
              "mappings": {
                "KEYCODE_A": { "lowercase": "a" },
                "KEYCODE_B": { "uppercase": "B" },
                "KEYCODE_C": { "lowercase": "c", "uppercase": "C" }
              }
            }
        """.trimIndent()
        val parsed = LayoutFileCodec.decode(text)
        assertNull(parsed?.layout?.get(KeyId.Letter('A')))
        assertNull(parsed?.layout?.get(KeyId.Letter('B')))
        assertEquals("c", parsed?.layout?.get(KeyId.Letter('C'))?.lowercase)
    }

    @Test
    fun `an unrecognised key name is skipped`() {
        val text = """
            {
              "mappings": {
                "KEYCODE_CTRL_LEFT": { "lowercase": "x", "uppercase": "X" },
                "KEYCODE_EM": { "lowercase": "x", "uppercase": "X" },
                "KEYCODE_A": { "lowercase": "a", "uppercase": "A" }
              }
            }
        """.trimIndent()
        val parsed = LayoutFileCodec.decode(text)
        assertEquals(1, parsed?.layout?.entries?.size)
        assertEquals("a", parsed?.layout?.get(KeyId.Letter('A'))?.lowercase)
    }

    @Test
    fun `multi-tap is honoured only when multiTapEnabled is true and at least two taps survive, SS9_1`() {
        val enabledTwoTaps = """
            { "mappings": { "KEYCODE_A": {
                "lowercase": "a", "uppercase": "A", "multiTapEnabled": true,
                "taps": [ { "lowercase": "a", "uppercase": "A" }, { "lowercase": "ä", "uppercase": "Ä" } ]
            } } }
        """.trimIndent()
        val entry = LayoutFileCodec.decode(enabledTwoTaps)?.layout?.get(KeyId.Letter('A'))
        assertTrue(entry != null && entry.isMultiTap)
        assertEquals(2, entry.taps.size)

        val disabledTwoTaps = enabledTwoTaps.replace("\"multiTapEnabled\": true", "\"multiTapEnabled\": false")
        val disabledEntry = LayoutFileCodec.decode(disabledTwoTaps)?.layout?.get(KeyId.Letter('A'))
        assertTrue(disabledEntry?.isMultiTap == false, "multiTapEnabled false must fall back to a single character")

        val enabledOneTap = """
            { "mappings": { "KEYCODE_A": {
                "lowercase": "a", "uppercase": "A", "multiTapEnabled": true,
                "taps": [ { "lowercase": "a", "uppercase": "A" } ]
            } } }
        """.trimIndent()
        val oneTapEntry = LayoutFileCodec.decode(enabledOneTap)?.layout?.get(KeyId.Letter('A'))
        assertTrue(oneTapEntry?.isMultiTap == false, "fewer than two surviving taps must fall back to a single character")
    }

    @Test
    fun `a taps entry with both strings empty is dropped before counting survivors, SS9_1`() {
        val text = """
            { "mappings": { "KEYCODE_A": {
                "lowercase": "a", "uppercase": "A", "multiTapEnabled": true,
                "taps": [
                  { "lowercase": "a", "uppercase": "A" },
                  { "lowercase": "", "uppercase": "" },
                  { "lowercase": "ä", "uppercase": "Ä" }
                ]
            } } }
        """.trimIndent()
        val entry = LayoutFileCodec.decode(text)?.layout?.get(KeyId.Letter('A'))
        assertEquals(2, entry?.taps?.size)
        assertEquals("ä", entry?.taps?.get(1)?.lowercase)
    }

    @Test
    fun `a file with no parsable mappings is rejected, SS9_1`() {
        assertNull(LayoutFileCodec.decode(null))
        assertNull(LayoutFileCodec.decode(""))
        assertNull(LayoutFileCodec.decode("not json"))
        assertNull(LayoutFileCodec.decode("{}"))
        assertNull(LayoutFileCodec.decode("""{ "mappings": {} }"""))
        assertNull(LayoutFileCodec.decode("""{ "mappings": { "KEYCODE_CTRL_LEFT": { "lowercase": "x", "uppercase": "X" } } }"""))
        assertNull(LayoutFileCodec.decode("""{ "mappings": { "KEYCODE_A": { "lowercase": "a" } } }"""))
    }

    @Test
    fun `the identity fallback covers a to z and A to Z on the 26 letter keys, SS9_1`() {
        for (letter in 'A'..'Z') {
            val entry = LayoutFileCodec.IDENTITY_LAYOUT[KeyId.Letter(letter)]
            assertEquals(letter.lowercaseChar().toString(), entry?.lowercase)
            assertEquals(letter.toString(), entry?.uppercase)
        }
        assertEquals(26, LayoutFileCodec.IDENTITY_LAYOUT.entries.size)
    }
}
