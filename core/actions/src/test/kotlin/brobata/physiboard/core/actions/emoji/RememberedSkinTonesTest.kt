package brobata.physiboard.core.actions.emoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS4.7, "Remembered tones"; SS12 T98 to T104. */
class RememberedSkinTonesTest {
    private val wave = "👋"
    private val waveMedium = "👋🏽"
    private val waveDark = "👋🏿"
    private val thumbs = "👍"
    private val thumbsLight = "👍🏻"
    private val waveVariants = SkinTone.TONES.map { SkinTones.apply(wave, it) }
    private val thumbsVariants = SkinTone.TONES.map { SkinTones.apply(thumbs, it) }

    @Test
    fun `T98 - a toned pick is remembered for that emoji only`() {
        val after = RememberedSkinTones.afterPick(emptyMap(), waveMedium, learningAllowed = true)
        assertEquals(waveMedium, RememberedSkinTones.displayForm(wave, waveVariants, after, SkinTone.NONE))
        assertEquals(thumbs, RememberedSkinTones.displayForm(thumbs, thumbsVariants, after, SkinTone.NONE))
    }

    @Test
    fun `T99 - the plain form forgets it, and the emoji follows the default again`() {
        val remembered = RememberedSkinTones.afterPick(emptyMap(), waveDark, learningAllowed = true)
        val cleared = RememberedSkinTones.afterPick(remembered, wave, learningAllowed = true)
        assertTrue(cleared.isEmpty())
        assertEquals(waveMedium, RememberedSkinTones.displayForm(wave, waveVariants, cleared, SkinTone.MEDIUM))
    }

    @Test
    fun `T100 - the default tone still applies to emoji with nothing remembered, and a remembered one beats it`() {
        val remembered = RememberedSkinTones.afterPick(emptyMap(), waveDark, learningAllowed = true)
        assertEquals(waveDark, RememberedSkinTones.displayForm(wave, waveVariants, remembered, SkinTone.LIGHT))
        assertEquals(thumbsLight, RememberedSkinTones.displayForm(thumbs, thumbsVariants, remembered, SkinTone.LIGHT))
    }

    @Test
    fun `T101 - private mode records nothing`() {
        val before = mapOf(SkinTones.toneKeyOf(wave)!! to waveDark)
        assertSame(before, RememberedSkinTones.afterPick(before, waveMedium, learningAllowed = false))
        assertSame(before, RememberedSkinTones.afterPick(before, wave, learningAllowed = false), "not even a forget")
    }

    @Test
    fun `T102 - a form the device cannot draw is not shown, and an emoji with no tones is never touched`() {
        val remembered = RememberedSkinTones.afterPick(emptyMap(), waveDark, learningAllowed = true)
        assertEquals(wave, RememberedSkinTones.displayForm(wave, emptyList(), remembered, SkinTone.NONE))
        val heart = "❤️"
        assertSame(remembered, RememberedSkinTones.afterPick(remembered, heart, learningAllowed = true))
        assertEquals(heart, RememberedSkinTones.displayForm(heart, emptyList(), remembered, SkinTone.DARK))
    }

    @Test
    fun `T103 - a toned recent from before collapses into its emoji, listed once`() {
        val base = RememberedSkinTones.baseByKey(listOf(EmojiCategory("P", "People", EmojiTabIcon.PERSON, listOf(EmojiEntry(wave, waveVariants)))))
        assertEquals(listOf(wave, thumbs), RememberedSkinTones.collapseRecents(listOf(waveMedium, thumbs, wave, waveDark), base))
    }

    @Test
    fun `T104 - the stored row round-trips, newest last, and is capped`() {
        var map: Map<String, String> = emptyMap()
        map = RememberedSkinTones.afterPick(map, waveDark, learningAllowed = true)
        map = RememberedSkinTones.afterPick(map, thumbsLight, learningAllowed = true)
        map = RememberedSkinTones.afterPick(map, waveMedium, learningAllowed = true)
        assertEquals(listOf(thumbsLight, waveMedium), map.values.toList())
        assertEquals(map, RememberedSkinTones.decode(RememberedSkinTones.encode(map)))
        assertEquals(emptyMap(), RememberedSkinTones.decode("garbage\n\tx\n"))
        val many = (0 until RememberedSkinTones.MAX).associate { "k$it" to "v$it" }
        val capped = RememberedSkinTones.afterPick(many, waveDark, learningAllowed = true)
        assertEquals(RememberedSkinTones.MAX, capped.size)
        assertEquals(waveDark, capped.values.last())
    }

    @Test
    fun `the same pick again changes nothing`() {
        val once = RememberedSkinTones.afterPick(emptyMap(), waveDark, learningAllowed = true)
        assertSame(once, RememberedSkinTones.afterPick(once, waveDark, learningAllowed = true))
    }
}
