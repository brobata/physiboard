package it.palsoftware.pastiera.inputmethod.expansion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import it.palsoftware.pastiera.data.emoji.EmojiAvailability

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AssetExpansionSourcesTest {
    private val assets = RuntimeEnvironment.getApplication().assets
    private val engine = TextExpansionEngine()

    @Test
    fun assetsAreLoadedLazilyAndOnlyWhenTheirProviderIsQueried() {
        val emoji = EmojiShortcodeSource(assets)
        val symbol = SymbolShortcodeSource(assets)
        assertFalse(emoji.isLoadedForTests())
        assertFalse(symbol.isLoadedForTests())

        val query = engine.shortcodeQuery(":heart")!!
        assertTrue(emoji.matches(query).isNotEmpty())
        assertTrue(emoji.isLoadedForTests())
        assertFalse(symbol.isLoadedForTests())
    }

    @Test
    fun emojiAndSymbolMatchesRemainSeparateOnNameCollisions() {
        val query = engine.shortcodeQuery(":heart")!!
        val matches = engine.collect(
            query,
            listOf(EmojiShortcodeSource(assets), SymbolShortcodeSource(assets)),
            limit = 100
        )
        assertTrue(matches.any { it.provider == ExpansionProvider.EMOJI })
        assertTrue(matches.any { it.provider == ExpansionProvider.SYMBOL })
        assertEquals(null, engine.uniqueExact(query, matches))
    }

    @Test
    fun correctedQuoteSymbolsAreValidIndependentEntries() {
        val source = SymbolShortcodeSource(assets)
        val left = source.matches(engine.shortcodeQuery(":ldquo")!!).single { it.shortcut == "ldquo" }
        val right = source.matches(engine.shortcodeQuery(":rdquo")!!).single { it.shortcut == "rdquo" }
        assertEquals("“", left.replacement)
        assertEquals("”", right.replacement)
    }

    @Test
    fun unavailableEmojiGlyphsNeverBecomeShortcodeMatches() {
        val unavailable = EmojiAvailability(emptyMap(), sdk = 36, hasGlyph = { false })
        val source = EmojiShortcodeSource(assets, unavailable)
        assertTrue(source.matches(engine.shortcodeQuery(":heart")!!).isEmpty())
    }
}
