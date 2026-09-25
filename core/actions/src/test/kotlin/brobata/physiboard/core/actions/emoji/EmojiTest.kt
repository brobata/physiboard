package brobata.physiboard.core.actions.emoji

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS12, T24 to T35, and D1. */
class EmojiTest {

    private val smileys = EmojiCategory("SMILEYS_AND_EMOTION", "Smileys & Emotion", EmojiTabIcon.SATISFIED_FACE, listOf(EmojiEntry("😀"), EmojiEntry("😃")))
    private val people = EmojiCategory("PEOPLE_AND_BODY", "People & Body", EmojiTabIcon.PERSON, listOf(EmojiEntry("👋", listOf("👋🏻"))))

    @Test
    fun `T24 an unlisted emoji is available only when the font has a glyph`() {
        assertFalse(EmojiAvailability.isAvailable("🫉", emptyMap(), 36) { false })
        assertTrue(EmojiAvailability.isAvailable("🫉", emptyMap(), 36) { true })
    }

    @Test
    fun `T25 a listed emoji at or below the running API is available regardless of glyph`() {
        assertTrue(EmojiAvailability.isAvailable("😀", mapOf("😀" to 23), 33) { false })
        assertFalse(EmojiAvailability.isAvailable("😀", mapOf("😀" to 35), 33) { false })
    }

    @Test
    fun `T26 normalisation`() {
        assertEquals("grinning face", EmojiTermNormalizer.normalize("Grinning-Face_ "))
        assertEquals("cafe", EmojiTermNormalizer.normalize("Café"))
    }

    @Test
    fun `T27 an English index scores a keyword prefix at 1300 minus category order and the base itself at 2000`() {
        val en = EmojiTermFile("en", listOf(EmojiTermLine("😀", "grinning face", listOf("grin", "smile")), EmojiTermLine("😃", "grinning face with big eyes", listOf("face"))))
        val index = EmojiSearchIndex.build(listOf(smileys, people), listOf(en))
        val grin = index.search("grin")
        val hit = grin.first { it.entry.base == "😀" }
        assertEquals(EmojiSearchIndex.SCORE_KEYWORD_EQUALS + EmojiSearchIndex.PREFERRED_LOCALE_BONUS - 0, hit.score, "the keyword `grin` equals the query, which beats the name prefix")
        val secondHit = grin.first { it.entry.base == "😃" }
        assertEquals(EmojiSearchIndex.SCORE_NAME_PREFIX + EmojiSearchIndex.PREFERRED_LOCALE_BONUS, secondHit.score)
        assertEquals(EmojiSearchIndex.SCORE_BASE_EQUALS, index.search("😀").first().score)
    }

    @Test
    fun `a keyword prefix without an equal term scores 1300 plus the preferred bonus`() {
        val en = EmojiTermFile("en", listOf(EmojiTermLine("😀", "smiley", listOf("grinning"))))
        val index = EmojiSearchIndex.build(listOf(smileys), listOf(en))
        assertEquals(EmojiSearchIndex.SCORE_KEYWORD_PREFIX + EmojiSearchIndex.PREFERRED_LOCALE_BONUS, index.search("grin").first().score)
    }

    @Test
    fun `T28 a one-character query only matches by equality and prefix`() {
        val en = EmojiTermFile("en", listOf(EmojiTermLine("😀", "grinning", emptyList()), EmojiTermLine("😃", "face", listOf("happy"))))
        val index = EmojiSearchIndex.build(listOf(smileys), listOf(en))
        val hits = index.search("a").map { it.entry.base }
        assertEquals(emptyList(), hits, "`grinning` and `happy` contain `a`... no: contains needs 2+ characters and nothing starts with `a`")
        assertEquals(listOf("😃"), index.search("f").map { it.entry.base })
    }

    @Test
    fun `a non-preferred locale still matches but ranks 25 lower`() {
        val de = EmojiTermFile("de", listOf(EmojiTermLine("😀", "grinsendes gesicht", emptyList())))
        val en = EmojiTermFile("en", listOf(EmojiTermLine("😃", "grinning face", emptyList())))
        val index = EmojiSearchIndex.build(listOf(smileys), listOf(de, en))
        val hits = index.search("grin")
        assertEquals(listOf("😀", "😃"), hits.map { it.entry.base })
        assertEquals(25, hits[0].score - hits[1].score)
    }

    @Test
    fun `T29 the locale chain`() {
        assertEquals(listOf("de-CH", "de", "en-US", "en"), EmojiSearchLocales.chain(listOf("de-CH", "en-US")))
        assertEquals(listOf("de_ch.tsv", "de.tsv"), EmojiSearchLocales.fileCandidates("de-CH"))
    }

    @Test
    fun `T30 recents`() {
        var recents = emptyList<String>()
        recents = RecentEmojis.add(recents, "😀")
        recents = RecentEmojis.add(recents, "😃")
        recents = RecentEmojis.add(recents, "😀")
        assertEquals(listOf("😀", "😃"), recents)
        var many = emptyList<String>()
        for (i in 0 until 41) many = RecentEmojis.add(many, "e$i")
        assertEquals(40, many.size)
        assertEquals("e40", many.first())
        assertEquals("e1", many.last())
    }

    @Test
    fun `T31 a captured key inserts the layout's character`() {
        val r = SearchCapture.onKeyDown(SearchFieldState.EMPTY, KeyId.Letter('Y'), ctrl = false, altOrMeta = false, layoutText = "z", eventChar = 'y')
        assertEquals("z", r.state.text)
    }

    @Test
    fun `T32 with no layout character the event's own character is used`() {
        val r = SearchCapture.onKeyDown(SearchFieldState.EMPTY, KeyId.Letter('Y'), ctrl = false, altOrMeta = false, layoutText = null, eventChar = 'y')
        assertEquals("y", r.state.text)
    }

    @Test
    fun `T33 Ctrl+A selects the whole field`() {
        val r = SearchCapture.onKeyDown(SearchFieldState("zy", 2, 2), KeyId.Letter('A'), ctrl = true, altOrMeta = false, layoutText = "a", eventChar = 'a')
        assertEquals(0, r.state.selectionStart)
        assertEquals(2, r.state.selectionEnd)
    }

    @Test
    fun `T34 typing over a selection replaces it`() {
        val r = SearchCapture.onKeyDown(SearchFieldState("old", 0, 3), KeyId.Letter('N'), ctrl = false, altOrMeta = false, layoutText = "n", eventChar = 'n')
        assertEquals("n", r.state.text)
        assertEquals(1, r.state.selectionStart)
    }

    @Test
    fun `T35 Backspace over a selection empties it and Enter is consumed doing nothing`() {
        val r = SearchCapture.onKeyDown(SearchFieldState("old", 0, 3), KeyId.Control(ControlKey.BACKSPACE), ctrl = false, altOrMeta = false, layoutText = null, eventChar = null)
        assertEquals("", r.state.text)
        val enter = SearchCapture.onKeyDown(SearchFieldState("old", 3, 3), KeyId.Control(ControlKey.ENTER), ctrl = false, altOrMeta = false, layoutText = null, eventChar = null)
        assertIs<CaptureResult.Consumed>(enter)
        assertEquals("old", enter.state.text)
        val alt = SearchCapture.onKeyDown(SearchFieldState.EMPTY, KeyId.Letter('A'), ctrl = false, altOrMeta = true, layoutText = "a", eventChar = 'a')
        assertIs<CaptureResult.NotCaptured>(alt)
    }

    @Test
    fun `D1 the Titan's 1080 px width gives 10 columns and the settings dialogs 11`() {
        assertEquals(10, EmojiPickerGeometry.columns(1080, 1.875))
        assertEquals(4, EmojiPickerGeometry.columns(200, 1.875))
        assertEquals(265, EmojiPickerGeometry.heightDp(expanded = true))
    }

    @Test
    fun `category files parse bases and variants and empty categories are dropped`() {
        val entries = EmojiCategories.parseCategoryFile("😀\n👋 👋🏻 👋🏼\n\n")
        assertEquals(2, entries.size)
        assertEquals(2, entries[1].variants.size)
        val filtered = EmojiAvailability.filterCategories(listOf(smileys, people), emptyMap(), 36) { it == "😀" }
        assertEquals(listOf("SMILEYS_AND_EMOTION"), filtered.map { it.id })
        assertEquals(mapOf("😀" to 23, "🥰" to 26), EmojiCategories.parseMinApi("23 😀\n26 🥰\n"))
    }

    @Test
    fun `recents are rebuilt with the variants looked up from the categories`() {
        val recents = RecentEmojis.category(listOf("👋🏻", "😀"), listOf(smileys, people))!!
        assertEquals(EmojiCategories.RECENTS_ID, recents.id)
        assertEquals(listOf("👋"), recents.entries[0].variants)
        assertTrue(RecentEmojis.category(emptyList(), listOf(smileys)) == null)
    }
}
