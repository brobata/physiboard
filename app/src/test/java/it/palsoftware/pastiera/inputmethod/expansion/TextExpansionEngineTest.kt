package it.palsoftware.pastiera.inputmethod.expansion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextExpansionEngineTest {
    private val engine = TextExpansionEngine()

    @Test
    fun `snippet requires a token boundary and preserves its complete token`() {
        assertNull(engine.snippetQuery("mail!sig", '!'))
        val query = engine.snippetQuery("Hello !sig", '!')
        assertEquals("!sig", query?.token)
        assertEquals("sig", query?.typedShortcut)
    }

    @Test
    fun `snippet prefix alone can list configured entries`() {
        assertEquals("", engine.snippetQuery("!", '!')?.typedShortcut)
    }

    @Test
    fun `colon shortcode supports open and explicitly closed forms`() {
        assertFalse(engine.shortcodeQuery(":id")!!.closed)
        assertTrue(engine.shortcodeQuery(":id:")!!.closed)
        assertEquals(":id:", engine.shortcodeQuery(":id:")!!.token)
    }

    @Test
    fun `colon inside urls and times does not start a shortcode`() {
        assertNull(engine.shortcodeQuery("https://example.org/:id"))
        assertNull(engine.shortcodeQuery("12:30"))
    }

    @Test
    fun `an exact match must be unique across providers`() {
        val query = engine.shortcodeQuery(":id")!!
        val emoji = ExpansionMatch(ExpansionProvider.EMOJI, "id", "🪪", query.token, query.tokenStart)
        val symbol = ExpansionMatch(ExpansionProvider.SYMBOL, "id", "ℹ", query.token, query.tokenStart)
        assertEquals(emoji, engine.uniqueExact(query, listOf(emoji)))
        assertNull(engine.uniqueExact(query, listOf(emoji, symbol)))
    }

    @Test
    fun `snippet prefixes are one printable non colon symbol`() {
        assertTrue(TextExpansionEngine.isValidSnippetPrefix("!"))
        assertFalse(TextExpansionEngine.isValidSnippetPrefix(":"))
        assertFalse(TextExpansionEngine.isValidSnippetPrefix("aa"))
        assertFalse(TextExpansionEngine.isValidSnippetPrefix(" "))
    }

    @Test
    fun `snippet source preserves multiline replacement and surrounding whitespace`() {
        val replacement = "  first\nsecond\n  "
        val query = engine.snippetQuery("!sig", '!')!!
        val match = SnippetExpansionSource { mapOf("sig" to replacement) }.matches(query).single()
        assertEquals(replacement, match.replacement)
    }
}
