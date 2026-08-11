package it.palsoftware.pastiera.data.emoji

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiAvailabilityTest {
    @Test
    fun unknownEmojiRequiresAnAvailableGlyph() {
        assertFalse(EmojiAvailability(emptyMap(), sdk = 36, hasGlyph = { false }).isAvailable("🫯"))
        assertTrue(EmojiAvailability(emptyMap(), sdk = 36, hasGlyph = { true }).isAvailable("🫯"))
    }

    @Test
    fun knownApiEmojiCanUseTheDeclaredPlatformSupport() {
        val availability = EmojiAvailability(mapOf("😀" to 23), sdk = 33, hasGlyph = { false })
        assertTrue(availability.isAvailable("😀"))
    }
}
