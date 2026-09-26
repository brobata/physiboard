package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: autocorrect-suggestions.md SS1, "Test cases" T1-T6. */
class CurrentWordTrackerTest {

    @Test
    fun `SS1_2 cursor-move debounce is 120ms, not a setting`() {
        assertEquals(120L, CurrentWordTracker.CURSOR_MOVE_DEBOUNCE_MS)
    }

    @Test
    fun `T1 tracks two characters`() {
        var t = CurrentWordTracker.empty()
        t = t.onCharacterCommitted('H')
        assertEquals("H", t.word)
        t = t.onCharacterCommitted('i')
        assertEquals("Hi", t.word)
    }

    @Test
    fun `T2 resets after boundary punctuation`() {
        var t = CurrentWordTracker.empty()
        for (c in "Hello") t = t.onCharacterCommitted(c)
        t = t.onCharacterCommitted('!')
        assertEquals("", t.word)
    }

    @Test
    fun `T3 backspace shrinks one character at a time`() {
        var t = CurrentWordTracker.empty()
        for (c in "Tests") t = t.onCharacterCommitted(c)
        assertEquals("Tests", t.word)
        t = t.onBackspace(); assertEquals("Test", t.word)
        t = t.onBackspace(); assertEquals("Tes", t.word)
        t = t.onBackspace(); assertEquals("Te", t.word)
        t = t.onBackspace(); assertEquals("T", t.word)
        t = t.onBackspace(); assertEquals("", t.word)
    }

    @Test
    fun `T4 multi-tap replace keeps the tracker in step`() {
        var t = CurrentWordTracker.empty()
        t = t.onCharacterCommitted('a')
        t = t.onCharacterReplaced('b')
        assertEquals("b", t.word)
    }

    @Test
    fun `T5 apostrophe joins after a letter`() {
        var t = CurrentWordTracker.empty()
        t = t.onCharacterCommitted('l')
        t = t.onCharacterCommitted('\'')
        assertEquals("l'", t.word)
        t = t.onCharacterCommitted('a')
        assertEquals("l'a", t.word)
    }

    @Test
    fun `T6 caps at the configured maximum length`() {
        var t = CurrentWordTracker.empty(maxLength = 5)
        for (c in "1234567") t = t.onCharacterCommitted(c)
        assertEquals("12345", t.word)
    }

    @Test
    fun `syncedFrom re-derives the word from field text`() {
        val t = CurrentWordTracker.empty().syncedFrom("hello world he")
        assertEquals("he", t.word)
    }

    @Test
    fun `leading apostrophe never starts a word`() {
        var t = CurrentWordTracker.empty()
        t = t.onCharacterCommitted('\'')
        assertEquals("", t.word)
        t = t.onCharacterCommitted('h')
        assertEquals("h", t.word)
    }
}
