package brobata.physiboard.core.actions.emoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS4.7. */
class SkinTonesTest {

    @Test
    fun `the table lists the Unicode 17 toneable emoji`() {
        assertEquals(330, SkinTones.size)
    }

    @Test
    fun `a single person takes every tone and no tone gives the untoned form back`() {
        assertEquals("👋🏽", SkinTones.apply("👋", SkinTone.MEDIUM))
        assertEquals("👋🏿", SkinTones.apply("👋🏻", SkinTone.DARK))
        assertEquals("👋", SkinTones.apply("👋🏾", SkinTone.NONE))
        assertEquals(listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), SkinTones.forms("👍"))
    }

    @Test
    fun `the variation selector a base carries is dropped by the modifier and restored without one`() {
        // ☝️ is U+261D U+FE0F; its toned form is U+261D U+1F3FD with no selector.
        assertEquals("☝🏽", SkinTones.apply("☝️", SkinTone.MEDIUM))
        assertEquals("☝🏽", SkinTones.apply("☝", SkinTone.MEDIUM))
        assertEquals("☝️", SkinTones.apply("☝🏽", SkinTone.NONE))
        assertEquals("🕵🏼‍♀️", SkinTones.apply("🕵️‍♀️", SkinTone.MEDIUM_LIGHT))
    }

    @Test
    fun `a ZWJ sequence with one person tones that person only`() {
        assertEquals("👩🏾‍💻", SkinTones.apply("👩‍💻", SkinTone.MEDIUM_DARK))
        assertEquals("🚶🏻‍➡️", SkinTones.apply("🚶‍➡️", SkinTone.LIGHT))
    }

    @Test
    fun `a pair Unicode tones as a whole gets the tone on both people and not on the joiner`() {
        assertEquals("🧑🏽‍🤝‍🧑🏽", SkinTones.apply("🧑‍🤝‍🧑", SkinTone.MEDIUM))
        assertEquals("👩🏿‍❤️‍👨🏿", SkinTones.apply("👩‍❤️‍👨", SkinTone.DARK))
        // A mixed pair maps back to its untoned form and to the uniform tones.
        assertEquals("🧑‍🤝‍🧑", SkinTones.apply("🧑🏻‍🤝‍🧑🏿", SkinTone.NONE))
        assertNull(SkinTones.toneOf("🧑🏻‍🤝‍🧑🏿"))
    }

    @Test
    fun `emoji that take no modifier are never changed`() {
        for (emoji in listOf("❤️", "😀", "🇫🇷", "👨‍👩‍👧", "", "a", "😂😂")) {
            assertFalse(SkinTones.isToneable(emoji), emoji)
            assertEquals(emoji, SkinTones.apply(emoji, SkinTone.DARK), emoji)
            assertEquals(emoji, SkinTones.withDefault(emoji, SkinTone.DARK), emoji)
            assertTrue(SkinTones.forms(emoji).isEmpty(), emoji)
        }
        // A family has toned members in Unicode only one by one, never as a family: left alone.
        assertFalse(SkinTones.isToneable("👨‍👩‍👧"))
        assertFalse(SkinTones.isToneable("❤️"))
        assertFalse(SkinTones.isToneable("😀"))
    }

    @Test
    fun `the default tone applies to an untoned emoji and never overrides a chosen one`() {
        assertEquals("👍🏾", SkinTones.withDefault("👍", SkinTone.MEDIUM_DARK))
        assertEquals("👍🏻", SkinTones.withDefault("👍🏻", SkinTone.MEDIUM_DARK))
        assertEquals("👍", SkinTones.withDefault("👍", SkinTone.NONE))
        assertEquals("🧑🏼‍🤝‍🧑🏼", SkinTones.withDefault("🧑‍🤝‍🧑", SkinTone.MEDIUM_LIGHT))
        assertEquals("🤷🏼‍♂️", SkinTones.withDefault("🤷‍♂️", SkinTone.MEDIUM_LIGHT))
    }

    @Test
    fun `tones read back from what they produced`() {
        for (tone in SkinTone.entries) assertEquals(tone, SkinTones.toneOf(SkinTones.apply("🧑‍🤝‍🧑", tone)))
        assertEquals(SkinTone.NONE, SkinTones.toneOf("😀"))
    }

    @Test
    fun `stored values round-trip and anything unknown is no tone`() {
        for (tone in SkinTone.entries) assertEquals(tone, SkinTone.fromStored(tone.storedValue))
        assertEquals(SkinTone.NONE, SkinTone.fromStored(null))
        assertEquals(SkinTone.NONE, SkinTone.fromStored("purple"))
    }

    @Test
    fun `the hardware chooser picks by the digit printed on the key`() {
        val forms = SkinTones.forms("👋")
        // Titan 2 Elite device layer: Q is 0, W 1, E 2, R 3, S 4, D 5.
        assertEquals(0, SkinToneChooser.digitFor("0", null))
        assertEquals(4, SkinToneChooser.digitFor("4", null))
        assertNull(SkinToneChooser.digitFor("7", null))
        assertNull(SkinToneChooser.digitFor("@", null))
        assertEquals(3, SkinToneChooser.digitFor(null, '3'))
        assertEquals(SkinToneChooser.KeyOutcome.Pick("👋🏽"), SkinToneChooser.onKeyDown(forms, isBack = false, isHeldKeyRepeat = false, digit = 3))
        assertEquals(SkinToneChooser.KeyOutcome.Pick("👋"), SkinToneChooser.onKeyDown(forms, isBack = false, isHeldKeyRepeat = false, digit = 0))
        assertIs<SkinToneChooser.KeyOutcome.Swallow>(SkinToneChooser.onKeyDown(forms, isBack = false, isHeldKeyRepeat = true, digit = 3))
        assertIs<SkinToneChooser.KeyOutcome.Dismiss>(SkinToneChooser.onKeyDown(forms, isBack = true, isHeldKeyRepeat = false, digit = null))
        // Alt pressed out of habit before the digit neither closes the chooser nor stays armed.
        assertIs<SkinToneChooser.KeyOutcome.Swallow>(SkinToneChooser.onKeyDown(forms, isBack = false, isHeldKeyRepeat = false, digit = null, isAltOrShift = true))
        assertIs<SkinToneChooser.KeyOutcome.CloseAndPassOn>(SkinToneChooser.onKeyDown(forms, isBack = false, isHeldKeyRepeat = false, digit = null))
    }

    @Test
    fun `only a toneable emoji arms a hold`() {
        assertTrue(SkinToneChooser.arms("👍"))
        assertTrue(SkinToneChooser.arms("👍🏽"))
        assertFalse(SkinToneChooser.arms("😀"))
        assertFalse(SkinToneChooser.arms("q"))
        assertFalse(SkinToneChooser.arms(""))
        val hold = SkinToneChooser.Hold(brobata.physiboard.core.keys.KeyId.Letter('Y'), "👍", armedAtMs = 1_000, thresholdMs = 300)
        assertFalse(hold.hasFired(1_299))
        assertTrue(hold.hasFired(1_300))
        assertEquals(1_300, hold.deadlineMs())
    }
}
