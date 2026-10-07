package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** Which stretches of a text the system spell checker judges ([SpellTokens]); autocorrect-suggestions.md §18. */
class SpellTokensTest {

    /** Each token as its text, skipped ones in brackets. */
    private fun split(text: String): List<String> =
        SpellTokens.split(text).map { t -> text.substring(t.start, t.end).let { if (t.skipped) "[$it]" else it } }

    @Test
    fun `plain words split at spaces and punctuation`() {
        assertEquals(listOf("Hello", "world", "How", "are", "you"), split("Hello, world! How are you?"))
        assertEquals(listOf("well", "known"), split("well-known"))
        assertEquals(listOf("hello"), split("  \"hello,\"  "))
    }

    @Test
    fun `apostrophes inside a word stay, a closing quote does not`() {
        assertEquals(listOf("don't", "it's"), split("don't it's"))
        assertEquals(listOf("hello"), split("'hello'"))
        assertEquals(listOf("players'", "ball"), split("the players' ball").drop(1))
        assertEquals(listOf("don’t"), split("don’t"))
    }

    @Test
    fun `URLs, addresses, paths, tags and mentions are one skipped token each`() {
        assertEquals(listOf("see", "[https://exampel.com/teh]", "now"), split("see https://exampel.com/teh now"))
        assertEquals(listOf("[www.exampel.com]"), split("www.exampel.com"))
        assertEquals(listOf("visit", "[site.com/its]"), split("visit site.com/its."))
        assertEquals(listOf("mail", "[jon@exampel.org]"), split("mail jon@exampel.org"))
        assertEquals(listOf("[#throwbackthursdy]", "[@jonnny]"), split("#throwbackthursdy @jonnny"))
        assertEquals(listOf("[snake_case_nmae]", "[~/dcouments]"), split("snake_case_nmae ~/dcouments"))
        assertEquals(listOf("[e.g]", "this"), split("(e.g. this)"))
    }

    @Test
    fun `numbers and words with digits are skipped`() {
        assertEquals(listOf("at", "[4pm]", "the", "[2nd]", "[1.5]", "[42]"), split("at 4pm the 2nd 1.5 42"))
    }

    @Test
    fun `offsets point into the original text`() {
        val text = "Teh “dog”"
        val tokens = SpellTokens.split(text)
        assertEquals(listOf(0 to 3, 5 to 8), tokens.map { it.start to it.end })
    }

    @Test
    fun `empty and blank text has no tokens`() {
        assertEquals(emptyList(), split(""))
        assertEquals(emptyList(), split("   \n "))
        assertEquals(emptyList(), split("... !!"))
    }
}
