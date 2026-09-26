package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: autocorrect-suggestions.md SS6.2, first clause. */
class AddWordCandidateTest {

    private val en = LanguageCode.of("en")!!
    private val dict = DictionaryIndex.build(en, listOf(WordFrequency("hello", 200)))

    @Test
    fun `an unknown word is offered`() {
        assertEquals("physiboard", AddWordCandidate.forCurrentWord("physiboard", primaryDictionaryLoaded = true, dictionaries = listOf(dict), userWords = UserWordStore.empty()))
    }

    @Test
    fun `a known word is not offered`() {
        assertNull(AddWordCandidate.forCurrentWord("hello", primaryDictionaryLoaded = true, dictionaries = listOf(dict), userWords = UserWordStore.empty()))
    }

    @Test
    fun `a word already in the personal dictionary is not offered`() {
        val userWords = UserWordStore.of(defaultWords = emptyList(), personalWords = listOf(PersonalWord("physiboard", 1, 0L)))
        assertNull(AddWordCandidate.forCurrentWord("physiboard", primaryDictionaryLoaded = true, dictionaries = listOf(dict), userWords = userWords))
    }

    @Test
    fun `nothing is offered before the primary dictionary has loaded`() {
        assertNull(AddWordCandidate.forCurrentWord("physiboard", primaryDictionaryLoaded = false, dictionaries = listOf(dict), userWords = UserWordStore.empty()))
    }

    @Test
    fun `a blank or symbol-only word is never offered`() {
        assertNull(AddWordCandidate.forCurrentWord("  ", primaryDictionaryLoaded = true, dictionaries = listOf(dict), userWords = UserWordStore.empty()))
        assertNull(AddWordCandidate.forCurrentWord("'", primaryDictionaryLoaded = true, dictionaries = listOf(dict), userWords = UserWordStore.empty()))
    }

    @Test
    fun `the word is trimmed before the lookup`() {
        assertEquals("physiboard", AddWordCandidate.forCurrentWord("  physiboard  ", primaryDictionaryLoaded = true, dictionaries = listOf(dict), userWords = UserWordStore.empty()))
    }
}
